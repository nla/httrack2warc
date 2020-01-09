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

package au.gov.nla.httrack2warc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;

public class CdxWriter implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(CdxWriter.class);
    private static final DateTimeFormatter ARC_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US).withZone(UTC);

    private final BufferedWriter writer;
    private final Path tmpCdxPath;
    private final Path cdxPath;
    boolean cdx11Format = true;

    CdxWriter(Path file) throws IOException {
        this.cdxPath = file;
        tmpCdxPath = Paths.get(file.toString() + ".tmp");
        writer = Files.newBufferedWriter(tmpCdxPath, UTF_8);
        writer.write(" CDX N b a m s k r M S V g\n");
    }

    public void finish() throws IOException {
        writer.close();
        externalSort(tmpCdxPath, cdxPath);
    }

    private void externalSort(Path source, Path destination) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sort", source.toString());
        pb.environment().put("LC_ALL", "C");
        pb.environment().put("LANG", "C");
        pb.redirectOutput(destination.toFile());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        int exitValue;
        try {
            exitValue = pb.start().waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (exitValue != 0) {
            throw new RuntimeException("sort returned non-zero exit value: " + exitValue);
        }
    }


    @Override
    public void close() throws IOException {
        writer.close();
        Files.deleteIfExists(tmpCdxPath);
    }

    void writeLine(String url, String contentType, int status, String digest, Instant date, WarcWriter.RecordPosition recordPosition, Path filename) throws IOException {
        String cdxLine;
        String digestField = digest != null ? digest : "-";
        if (cdx11Format) {
            cdxLine = url + " " + ARC_DATE.format(date) + " " + url + " " + contentType + " " + status + " " +
                    digestField + " - - " + recordPosition.length() + " " + recordPosition.start + " " + filename + "\n";
        } else {
            cdxLine = url + " " + ARC_DATE.format(date) + " " + url + " " + contentType + " " + status + " " +
                    digestField + " - " + recordPosition.start + " " + filename + "\n";
        }
        log.debug(cdxLine);
        writer.write(cdxLine);
    }
}
