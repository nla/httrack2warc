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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class HttrackCrawl {
    private final Path dir;

    private String httrackVersion;
    private LocalDateTime launchTime;
    private String httrackOptions;
    private String outputDir;
    private final Map<String,String> requestHeaders = new HashMap<>();
    private final Map<String,String> responseHeaders = new HashMap<>();

    public HttrackCrawl(Path dir) throws IOException {
        this.dir = dir;

        parseHtsLog();
        parseDoitLog();
        parseIoinfo();
    }

    private void parseIoinfo() throws IOException {
        try (HtsIoinfoParser ioinfo = new HtsIoinfoParser(Files.newInputStream(dir.resolve("hts-ioinfo.txt")))) {
            while (ioinfo.parseRecord()) {
                String url = HtsUtil.fixupUrl(ioinfo.url);
                Map<String, String> map = ioinfo.request ? requestHeaders : responseHeaders;
                map.put(url, ioinfo.header);
            }
        }
    }

    private void parseHtsLog() throws IOException {
        try (HtsLogParser htsLog = new HtsLogParser(Files.newInputStream(dir.resolve("hts-log.txt")))) {
            httrackVersion = htsLog.version;
        }
    }

    private void parseDoitLog() throws IOException {
        try (InputStream stream = Files.newInputStream(dir.resolve("hts-cache/doit.log"))) {
            HtsDoitParser doitLog = new HtsDoitParser(stream);
            launchTime = doitLog.crawlStartTime;
            outputDir = doitLog.outputDir;
            httrackOptions = doitLog.commandLine;
        }
    }

    public void forEach(RecordConsumer action) throws IOException {
        LocalTime previousTime = null;
        LocalDate date = launchTime.toLocalDate();

        try (HtsTxtParser parser = new HtsTxtParser(Files.newInputStream(dir.resolve("hts-cache/new.txt")));
             Cache cache = openCache()) {
            while (parser.readRecord()) {
                String rawfile = parser.localfile();
                if (rawfile.isEmpty()) {
                    continue; // skip 404 errors
                }

                LocalTime time = parser.time();
                if (previousTime != null && time.isBefore(previousTime)) {
                    // if we go backwards in time, assume we've wrapped around to the next day
                    date = date.plusDays(1);
                }
                LocalDateTime timestamp = parser.time().atDate(date);
                previousTime = time;

                if (!rawfile.startsWith(outputDir)) {
                    throw new ParsingException("new.txt localfile (" + rawfile + ") outside output dir (" + outputDir + ")");
                }

                rawfile = rawfile.substring(outputDir.length());

                String url = parser.url();

                CacheEntry cacheEntry = cache.getEntry(url);
                if (cacheEntry == null) {
                    throw new IOException("no cache entry: " + url);
                }

                String filename = percentDecode(rawfile);
                Path file = dir.resolve(filename);
                if (!file.toAbsolutePath().startsWith(dir.toAbsolutePath())) {
                    throw new IOException(file + " is outside of " + dir);
                }

                HttrackRecord record = new HttrackRecord(
                        filename,
                        timestamp,
                        HtsUtil.fixupUrl(url),
                        parser.mime(),
                        requestHeaders.get(url),
                        responseHeaders.get(url),
                        parser.referrer(),
                        file,
                        cacheEntry);
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
        } else {
            return new NdxCache(dir);
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

    public interface RecordConsumer {
        void accept(HttrackRecord record) throws IOException;
    }

}
