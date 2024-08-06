/*
 * Copyright (C) 2017 National Library of Australia
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

import au.gov.nla.httrack2warc.httrack.HttrackRecordTest;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netpreserve.jwarc.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class Httrack2WarcTest {
    @ClassRule
    public static TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void test() throws IOException {
        Path crawlPath = temp.newFolder().toPath();
        Path outdir = temp.newFolder().toPath();
        TestUtils.unzip(HttrackRecordTest.class.getResourceAsStream("testcrawl-3.49-2.zip"), crawlPath);

        Httrack2Warc httrack2Warc = new Httrack2Warc();
        httrack2Warc.addExclusion(Pattern.compile(".*/another"));
        httrack2Warc.setOutputDirectory(outdir);
        httrack2Warc.setRedirectPrefix("http://prefix.example.org/");
        httrack2Warc.convert(crawlPath);

        StringBuilder summary = new StringBuilder();
        try (WarcReader warcReader = new WarcReader(outdir.resolve("crawl-0.warc.gz"))) {
            for (WarcRecord warcRecord: warcReader) {
                String url = warcRecord instanceof WarcTargetRecord ? ((WarcTargetRecord) warcRecord).target() : null;
                summary.append(warcRecord.type()).append(" ").append(url).append("\n");

                // HTTrack generates bad http requests containing a fragment which jwarc strictly rejects, just skip them
                if (url != null && URI.create(url).getFragment() != null) continue;

                if (warcRecord instanceof WarcRequest) {
                    assertEquals(MessageVersion.HTTP_1_1, ((WarcRequest) warcRecord).http().version());
                } else if (warcRecord instanceof WarcResponse) {
                    assertEquals(MessageVersion.HTTP_1_1, ((WarcResponse) warcRecord).http().version());
                } else if (warcRecord instanceof Warcinfo) {
                    String payload = slurp(warcRecord.body().stream());
                    assertEquals("software: HTTrack/3.49-2 http://www.httrack.com/\r\n" +
                            "software: httrack2warc https://github.com/nla/httrack2warc\r\n" +
                            "httrackOptions: -%H http://test.example.org/\r\n", payload);
                }
            }
        }

        assertEquals("warcinfo null\n" +
                        "response http://test.example.org/\n" +
                        "request http://test.example.org/\n" +
                        "metadata http://test.example.org/\n" +
                        "response http://prefix.example.org/test.example.org/index.html\n" +
                        "response http://test.example.org/style.css\n" +
                        "request http://test.example.org/style.css\n" +
                        "metadata http://test.example.org/style.css\n" +
                        "response http://prefix.example.org/test.example.org/style.css\n" +
                        "response http://test.example.org/query.html?page=1&query=2&FOO=3&&BaR=4&&#anchor\n" +
                        "request http://test.example.org/query.html?page=1&query=2&FOO=3&&BaR=4&&#anchor\n" +
                        "metadata http://test.example.org/query.html?page=1&query=2&FOO=3&&BaR=4&&#anchor\n" +
                        "response http://prefix.example.org/test.example.org/query3b6f.html\n" +
                        "response http://test.example.org/redirect\n" +
                        "request http://test.example.org/redirect\n" +
                        "metadata http://test.example.org/redirect\n" +
                        "response http://prefix.example.org/test.example.org/redirect\n" +
                        "response http://test.example.org/page%20WITH%20%22special%22%20chars.html\n" +
                        "request http://test.example.org/page%20WITH%20%22special%22%20chars.html\n" +
                        "metadata http://test.example.org/page%20WITH%20%22special%22%20chars.html\n" +
                        "response http://prefix.example.org/test.example.org/page%20WITH%20_special_%20chars.html\n" +
                        "response http://test.example.org/image.gif\n" +
                        "request http://test.example.org/image.gif\n" +
                        "metadata http://test.example.org/image.gif\n" +
                        "response http://prefix.example.org/test.example.org/image.gif\n" +
                        "response http://test.example.org/image404.png\n" +
                        "request http://test.example.org/image404.png\n" +
                        "metadata http://test.example.org/image404.png\n" +
                        "response http://prefix.example.org/test.example.org/image404.png\n",
                summary.toString());
    }

    @Test
    public void removeTransferEncodingHeader() {
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 400\r\n" +
                "Transfer-Encoding: gzip\r\n" +
                "Content-Type: image/pants\r\n" +
                "TRANSFER-encoding: chunked, pizza\r\n\r\n";
        assertEquals("HTTP/1.1 200 OK\r\n" +
                        "Content-Length: 400\r\n" +
                        "Content-Type: image/pants\r\n" +
                        "\r\n",
                Httrack2Warc.removeTransferEncodingHeader(header));
    }

    private static String slurp(InputStream stream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (;;) {
            int n = stream.read(buf);
            if (n < 0) break;
            baos.write(buf, 0, n);
        }
        return baos.toString("UTF-8");
    }
}