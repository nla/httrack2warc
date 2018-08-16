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
import java.util.HashMap;
import java.util.Map;
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
    private final Map<String,String> requestHeaders = new HashMap<>();
    private final Map<String,String> responseHeaders = new HashMap<>();
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
                String url = HtsUtil.fixupUrl(ioinfo.url);
                Map<String, String> map = ioinfo.request ? requestHeaders : responseHeaders;
                map.put(url, ioinfo.header);
            }
        } catch (NoSuchFileException e) {
            // that's ok
        }
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

        try (HtsTxtParser parser = new HtsTxtParser(Files.newInputStream(dir.resolve("hts-cache/new.txt")))) {
            while (parser.readRecord()) {
                String rawfile = parser.localfile();
                if (rawfile.isEmpty()) {
                    continue; // skip 404 errors
                }

                HttrackRecord record = buildRecord(parser.time(), parser.url(), rawfile, parser.mime(),
                        parser.referrer(), parser.status());
                action.accept(record);
            }
        }

        // TODO: redirects
    }

    private void resetDateHeuristic() {
        previousTime = null;
        date = launchTime.toLocalDate();
    }

    private HttrackRecord buildRecord(LocalTime time, String url, String rawfile, String mime,
                                      String referrer, int status) throws IOException {
        if (previousTime != null && time.isBefore(previousTime)) {
            // if we go backwards in time, assume we've wrapped around to the next day
            date = date.plusDays(1);
        }
        LocalDateTime timestamp = time.atDate(date);
        previousTime = time;

        if (!rawfile.startsWith(outputDir)) {
            throw new ParsingException("new.txt localfile (" + rawfile + ") outside output dir (" + outputDir + ")");
        }

        rawfile = rawfile.substring(outputDir.length());

        String fixedUrl = HtsUtil.fixupUrl(url);
        CacheEntry cacheEntry = cache == null ? null : cache.getEntry(fixedUrl);

        String filename = percentDecode(rawfile);
        Path file = dir.resolve(filename);
        if (!file.toAbsolutePath().startsWith(dir.toAbsolutePath())) {
            throw new IOException(file + " is outside of " + dir);
        }

        return new HttrackRecord(
                filename,
                timestamp,
                fixedUrl,
                mime,
                requestHeaders.get(fixedUrl),
                responseHeaders.get(fixedUrl),
                referrer,
                file,
                cacheEntry,
                status);
    }

    private void forEachByDebugLogs(RecordConsumer action) throws IOException {
        resetDateHeuristic();

        try (BufferedReader reader = Files.newBufferedReader(dir.resolve("logs/debug"), ISO_8859_1)) {
            for (;;) {
                String line = reader.readLine();
                if (line == null) break;

                Matcher m = DEBUG_RECORD_RE.matcher(line);
                if (!m.matches()) continue;

                LocalTime time = LocalTime.parse(m.group(1));
                String url = m.group(2);
                String file = m.group(3);

                String response = responseHeaders.get(HtsUtil.fixupUrl(url));
                int status;
                if (response != null) {
                    status = Integer.parseInt(response.split(" ")[1]);
                } else {
                    status = 200;
                }

                HttrackRecord record = buildRecord(time, url, file, null, null, status);
                action.accept(record);
            }
        }

        resetDateHeuristic();

        // TODO: redirects
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
