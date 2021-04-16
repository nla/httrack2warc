/*
 * Copyright (c) 2017 National Library of Australia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.gov.nla.httrack2warc.httrack;

import au.gov.nla.httrack2warc.ParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class HttrackCrawl implements Closeable {
    private static Pattern DEBUG_RECORD_RE = Pattern.compile("(\\d\\d:\\d\\d:\\d\\d)\tDebug: \tRecord: (.*) -> (.*)");
    private static Pattern WARN_MOVED_RE = Pattern.compile("(\\d\\d:\\d\\d:\\d\\d)\tWarning: \tFile has moved from (.*) to (.*)");

    private final Path dir;

    private String httrackVersion;
    private LocalDateTime launchTime;
    private String httrackOptions;
    private String outputDir;
    private final Map<String, Queue<String>> requestHeaders = new HashMap<>();
    private final Map<String, Queue<String>> responseHeaders = new HashMap<>();
    private static final ArrayDeque<String> EMPTY_QUEUE = new ArrayDeque<>();
    private static final String[] LOG_FILE_NAMES = new String[]{"hts-log.txt", "logs/gen"};
    private LocalDate date;
    private LocalTime previousTime;
    private final Cache cache;
    private Logger log = LoggerFactory.getLogger(HttrackCrawl.class);

    public HttrackCrawl(Path dir) throws IOException {
        this.dir = dir;

        parseHtsLog();
        parseDoitLog();
        parseIoinfo();

        cache = openCache();
    }

    private void parseIoinfo() throws IOException {
        try (HtsIoinfoParser ioinfo = new HtsIoinfoParser(Files.newInputStream(dir.resolve("hts-ioinfo.txt")))) {
            while (ioinfo.parseRecord()) {
                // XXX: in all examples I've seen hts-ioinfo.txt has the scheme part of the URL stripped
                // this leaves us with a conflict in the common case of crawls with two urls
                // only differing by http:// and https:// (often the former is a redirect)
                // so we add both to a queue and remove them when looking up and hope the order is preserved
                // I'm uncomfortable with this but don't see any other options and it seems to work ok so far
                String url = ioinfo.url;
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    log.warn("URL in hts-ioinfo.txt unexpectedly has a scheme. We may not be handling this case correctly.");
                }
                Map<String, Queue<String>> map = ioinfo.request ? requestHeaders : responseHeaders;
                map.computeIfAbsent(makeHeaderKey(url), k -> new ArrayDeque<>()).add(ioinfo.header);
            }
        } catch (NoSuchFileException e) {
            // that's ok
        }
    }

    private static String makeHeaderKey(String url) {
        return HtsUtil.stripProtocol(HtsUtil.fixupUrl(url));
    }

    private void parseHtsLog() throws IOException {
        for (String file: LOG_FILE_NAMES) {
            try (HtsLogParser htsLog = new HtsLogParser(Files.newInputStream(dir.resolve(file)))) {
                httrackVersion = htsLog.version;
                launchTime = htsLog.launchTime;
                outputDir = htsLog.outputDir;
                httrackOptions = htsLog.commandLine;
            } catch (NoSuchFileException e) {
                // try next
            }
        }

    }

    private void parseDoitLog() throws IOException {
        Path logFile = dir.resolve("hts-cache/doit.log");
        if (!Files.exists(logFile)) return;
        try (InputStream stream = Files.newInputStream(logFile)) {
            HtsDoitParser doitLog = new HtsDoitParser(stream);
            launchTime = doitLog.crawlStartTime;
            outputDir = doitLog.outputDir;
            httrackOptions = doitLog.commandLine;
        }
    }

    public void forEach(RecordConsumer action) throws IOException {
        if (Files.exists(dir.resolve("hts-cache/new.txt"))) {
            forEachByTxt(action);
        } else if (Files.exists(dir.resolve("logs/debug"))) {
            forEachByDebugLogs(action);
        } else {
            throw new IOException("Both hts-cache/new.txt and logs/debug are missing. I can't handle this crawl.");
        }
    }

    private void forEachByTxt(RecordConsumer action) throws IOException {
        resetDateHeuristic();

        HashSet<String> seen = new HashSet<>();
        try (HtsTxtParser parser = new HtsTxtParser(Files.newInputStream(dir.resolve("hts-cache/new.txt")))) {
            while (parser.readRecord()) {
                String rawfile = parser.localfile();
                if (rawfile.isEmpty()) {
                    continue; // skip 404 errors
                }

                seen.add(rawfile);

                HttrackRecord record = buildRecord(parser.time(), parser.url(), rawfile, parser.mime(),
                        parser.referrer(), parser.status());
                action.accept(record);
            }
        }

        forEachRedirectInWarnLog(dir.resolve("hts-err.txt"), seen, action);
    }

    private void resetDateHeuristic() {
        previousTime = null;
        date = launchTime.toLocalDate();
    }

    private HttrackRecord buildRecord(LocalTime time, String url, String rawfile, String mime,
                                      String referrer, Integer status) throws IOException {
        LocalDateTime timestamp = applyDateHeuristic(time);

        if (!rawfile.startsWith(outputDir)) {
            throw new ParsingException("new.txt localfile (" + rawfile + ") outside output dir (" + outputDir + ")");
        }

        String relfile = rawfile.substring(outputDir.length());

        String fixedUrl = HtsUtil.fixupUrl(url);
        CacheEntry cacheEntry = cache == null ? null : cache.getEntry(fixedUrl);

        String filename = percentDecode(relfile);
        Path file = dir.resolve(filename);
        if (!file.toAbsolutePath().startsWith(dir.toAbsolutePath())) {
            throw new IOException(file + " is outside of " + dir);
        }

        String requestHeader = requestHeaders.getOrDefault(makeHeaderKey(url), EMPTY_QUEUE).poll();
        String responseHeader = responseHeaders.getOrDefault(makeHeaderKey(url), EMPTY_QUEUE).poll();

        if (status == null) {
            if (responseHeader != null) {
                status = Integer.parseInt(responseHeader.split(" ", 3)[1]);
            } else {
                status = 200;
            }
        }

        return new HttrackRecord(
                filename,
                timestamp,
                fixedUrl,
                mime,
                requestHeader,
                responseHeader,
                referrer,
                file,
                cacheEntry,
                status);
    }

    private LocalDateTime applyDateHeuristic(LocalTime time) {
        if (previousTime != null && time.isBefore(previousTime)) {
            // if we go backwards in time, assume we've wrapped around to the next day
            date = date.plusDays(1);
        }
        LocalDateTime timestamp = time.atDate(date);
        previousTime = time;
        return timestamp;
    }

    private void forEachByDebugLogs(RecordConsumer action) throws IOException {
        resetDateHeuristic();

        Set<String> seen = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(dir.resolve("logs/debug"), ISO_8859_1)) {
            for (;;) {
                String line = reader.readLine();
                if (line == null) break;

                Matcher m = DEBUG_RECORD_RE.matcher(line);
                if (!m.matches()) continue;

                LocalTime time = LocalTime.parse(m.group(1));
                String url = m.group(2);
                String file = m.group(3);

                if (!seen.add(file)) {
                    log.debug("Skipping duplicate file {}", file);
                    continue;
                }

                HttrackRecord record = buildRecord(time, url, file, null, null, null);
                action.accept(record);
            }
        }

        forEachRedirectInWarnLog(dir.resolve("logs/warn"), seen, action);

        resetDateHeuristic();
    }

    private void forEachRedirectInWarnLog(Path logFile, Set<String> seen, RecordConsumer action) throws IOException {
        if (!Files.exists(logFile)) return;

        resetDateHeuristic();

        try (BufferedReader reader = Files.newBufferedReader(logFile, ISO_8859_1)) {
            for (; ; ) {
                String line = reader.readLine();
                if (line == null) break;

                Matcher m = WARN_MOVED_RE.matcher(line);
                if (!m.matches()) continue;

                LocalTime time = LocalTime.parse(m.group(1));
                String url = m.group(2);
                String dst = m.group(3);

                if (!seen.add(url)) {
                    log.debug("Skipping duplicate redirect {} -> {}", url, dst);
                    continue;
                }

                String fixedUrl = HtsUtil.fixupUrl(url);
                String request = requestHeaders.getOrDefault(makeHeaderKey(url), EMPTY_QUEUE).poll();
                String response = responseHeaders.getOrDefault(makeHeaderKey(url), EMPTY_QUEUE).poll();
                int status;
                if (response == null) {
                    // if we don't have any response header we fabricate one as there's no way to record a redirect
                    // without one
                    status = 302;
                    response = "HTTP/1.0 302 Found\r\nLocation: " + dst + "\r\nServer: httrack2warc reconstructed header\r\n\r\n";
                } else {
                    status = Integer.parseInt(response.split("[ \r\n]")[1]);
                }

                HttrackRecord record = new HttrackRecord(null, applyDateHeuristic(time), fixedUrl, null,
                        request, response, null, null, null, status);
                action.accept(record);
            }
        }
    }

    private String percentDecode(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int x = Integer.parseUnsignedInt(s.substring(i + 1, i + 3), 16);
                out.append((char)x);
                i += 2;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private Cache openCache() throws IOException {
        Path zipFile = dir.resolve("hts-cache/new.zip");
        if (Files.exists(zipFile)) {
            return new ZipCache(zipFile);
        } else if (Files.exists(dir.resolve("hts-cache/new.ndx"))) {
            return new NdxCache(dir);
        } else {
            log.warn("Cache not found, proceeding anyway.");
            return null;
        }
    }

    public String getHttrackVersion() {
        return httrackVersion;
    }

    public String getHttrackOptions() {
        return httrackOptions;
    }

    public LocalDateTime getLaunchTime() {
        return launchTime;
    }

    @Override
    public void close() throws IOException {
        if (cache != null) {
            cache.close();
        }
    }

    public interface RecordConsumer {
        void accept(HttrackRecord record) throws IOException;
    }

}
