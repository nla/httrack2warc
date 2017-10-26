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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HtsLogParser implements Closeable {
    private static final Pattern HEADER_RE = Pattern.compile("HTTrack(?<version>[^ ]+) launched on " +
            "(?<date>\\w+, \\d\\d \\w+ \\d\\d\\d\\d \\d\\d:\\d\\d:\\d\\d) at " +
            "(?<seedsAndFilters>.*)");

    private final BufferedReader reader;
    String version;
    String launchDate;
    String seedsAndFilters;

    HtsLogParser(BufferedReader reader) throws IOException {
        this.reader = reader;
        readHeader();
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
        launchDate = matcher.group("date");
        seedsAndFilters = matcher.group("seedsAndFilters");
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
