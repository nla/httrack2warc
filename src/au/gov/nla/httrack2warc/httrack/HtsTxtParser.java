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
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses HTTrack hts-cache/new.txt files.
 */
class HtsTxtParser implements Closeable {
    private static final Pattern RE = Pattern.compile("^(?<time>\\d\\d:\\d\\d:\\d\\d)\\t" +
            "(?<size>-?\\d+)/(?<remotesize>-?\\d+)\\t" +
            "(?<flags>[A-Z-]{6})\\t" +
            "(?<statuscode>-?\\d+)\\t" +
            "(?<status>\\w+)[ ](error )?\\('(?<servermsg>[^']*)'\\)\\t" +
            "(?<mime>\\S*)\\t" +
            "(?<etag>\\S*)\\t" +
            "(?<url>.+)\\t" +
            "(?<localfile>[^\\t]*)\\t" +
            "\\(from[ ](?<via>.*)\\)$");
    private final BufferedReader reader;
    private final Matcher matcher = RE.matcher("");

    HtsTxtParser(BufferedReader reader) throws IOException {
        this.reader = reader;
        String header = reader.readLine();
        if (header == null) {
            throw new ParsingException("empty file");
        } else if (!header.equals("date\tsize'/'remotesize\tflags(request:Update,Range state:File response:Modified,Chunked,gZipped)\tstatuscode\tstatus ('servermsg')\tMIME\tEtag|Date\tURL\tlocalfile\t(from URL)")) {
            throw new ParsingException("invalid header line");
        }
    }

    HtsTxtParser(InputStream txtReader) throws IOException {
        this(new BufferedReader(new InputStreamReader(txtReader, StandardCharsets.ISO_8859_1)));
    }

    boolean readRecord() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return false;
        }
        matcher.reset(line);
        if (!matcher.matches()) {
            throw new ParsingException("invalid record: " + line);
        }
        return true;
    }

    public LocalTime time() {
        return LocalTime.parse(matcher.group("time"));
    }

    public String url() {
        return matcher.group("url");
    }

    public String referrer() {
        String raw = matcher.group("via");
        if (raw.isEmpty()) {
            return null;
        }
        return HtsUtil.fixupUrl(raw);
    }

    public String mime() {
        return matcher.group("mime");
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public String localfile() {
        return matcher.group("localfile");
    }

    public int status() {
        return Integer.parseInt(matcher.group("statuscode"));
    }
}
