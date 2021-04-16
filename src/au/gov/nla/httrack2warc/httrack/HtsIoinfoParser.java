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

class HtsIoinfoParser implements Closeable {
    private static final Pattern HEADER_RE = Pattern.compile("(?:\\[\\d+] )?(request|response) for (.*):");
    private static final Pattern REQUEST_RE = Pattern.compile("(?:[A-Z]+) http://(.*) HTTP/1.[01]");
    private final BufferedReader reader;
    boolean request;
    String url;
    String header;
    int code;

    private HtsIoinfoParser(BufferedReader reader) {
        this.reader = reader;
    }

    HtsIoinfoParser(InputStream ioinfoStream) {
        this(new BufferedReader(new InputStreamReader(ioinfoStream, StandardCharsets.ISO_8859_1)));
    }

    public boolean parseRecord() throws IOException {
        String header;

        do {
            header = reader.readLine();
            if (header == null) return false;
        } while (header.isEmpty() || header.startsWith("---> QUIT"));

        StringBuilder buffer = new StringBuilder();
        String prefix;

        if (header.equals("Out:")) { // HTTrack 3.01 requests
            String requestLine = reader.readLine();
            Matcher matcher = REQUEST_RE.matcher(requestLine);
            if (!matcher.matches()) {
                throw new ParsingException("invalid request line: " + requestLine);
            }

            buffer.append(requestLine).append("\r\n");
            request = true;
            url = matcher.group(1);
            prefix = "";
        } else { // later versions
            Matcher matcher = HEADER_RE.matcher(header);
            if (!matcher.matches()) {
                throw new ParsingException("invalid header line: " + header);
            }
            request = matcher.group(1).equals("request");
            url = matcher.group(2);
            prefix = request ? "<<< " : ">>> ";
        }

        code = 0;

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new EOFException("missing trailer");
            } else if (line.isEmpty()) {
                break;
            } else if (line.startsWith("(Buffer) Status-Code=")) {
                code = Integer.parseInt(line.substring("(Buffer) Status-Code=".length()));
                prefix = "(buffer)>";
                buffer.append("HTTP/1.0 ").append(code).append(" OK\r\n");
            } else if (line.startsWith(prefix)) {
                line = line.substring(prefix.length());
                if (line.isEmpty()) break;
                buffer.append(line);
                buffer.append("\r\n");
            } else if (!request && line.startsWith("code=")) {
                code = Integer.parseInt(line.substring("code=".length()));
            } else {
                throw new ParsingException("invalid hts-ioinfo.txt header line: " + line);
            }
        }

        // include the trailing \r\n so we don't have to add it later
        buffer.append("\r\n");

        this.header = buffer.toString();

        return true;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
