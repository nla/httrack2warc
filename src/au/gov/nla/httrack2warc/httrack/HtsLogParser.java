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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HtsLogParser implements Closeable {
    private static final Pattern HEADER_RE = Pattern.compile("HTTrack(?<version>[^ ]+) launched on " +
            "(?<date>\\w+, \\d\\d \\w+ \\d\\d\\d\\d \\d\\d:\\d\\d:\\d\\d) at " +
            "(?<seedsAndFilters>.*)");
    private static final Pattern CMDLINE_RE = Pattern.compile("\\(.*-O ?(?:\"([^\"]*)\"|([^ ]*)) .*");

    private final BufferedReader reader;
    String version;
    LocalDateTime launchTime;
    String seedsAndFilters;
    String outputDir;
    String commandLine;

    HtsLogParser(BufferedReader reader) throws IOException {
        this.reader = reader;
        readHeader();
        readCmdLine();
    }

    HtsLogParser(InputStream stream) throws IOException {
        this(new BufferedReader(new InputStreamReader(stream, StandardCharsets.ISO_8859_1)));
    }

    private void readHeader() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new ParsingException("missing header line");
        }
        Matcher matcher = HEADER_RE.matcher(line);
        if (!matcher.matches()) {
            throw new ParsingException("invalid hts-log.txt header: " + line);
        }
        version = matcher.group("version");
        launchTime = LocalDateTime.parse(matcher.group("date"), HtsDoitParser.HTS_LOCAL_DATE);
        seedsAndFilters = matcher.group("seedsAndFilters");
    }

    private void readCmdLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return;
        }
        commandLine = line.substring(1, line.length() - 1).trim().split(" ", 2)[1];
        Matcher matcher = CMDLINE_RE.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        outputDir = matcher.group(1);
        if (outputDir == null) {
            outputDir = matcher.group(2);
        }
        if (!outputDir.endsWith("/")) {
            outputDir += "/";
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
