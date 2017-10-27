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

import au.gov.nla.httrack2warc.TestUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import java.io.InputStream;

public class HttrackRecordTest {

    @ClassRule
    public static TemporaryFolder temp = new TemporaryFolder();
    private static Path crawlPath;

    @BeforeClass
    public static void setUp() throws IOException {
        crawlPath = temp.newFolder().toPath();
        TestUtils.unzip(HttrackRecordTest.class.getResourceAsStream("testcrawl-3.49-2.zip"), crawlPath);
    }

    @Test
    public void test() throws IOException {
        HttrackCrawl crawl = new HttrackCrawl(crawlPath);

        assertEquals("3.49-2", crawl.getHttrackVersion());
        assertEquals(LocalDateTime.parse("2017-10-25T18:41:47"), crawl.getLaunchTime());

        List<HttrackRecord> recordList = new ArrayList<>();
        crawl.forEach(recordList::add);

        HttrackRecord entry = recordList.get(0);
        assertEquals(LocalDateTime.parse("2017-10-25T18:41:48"), entry.timestamp);
        assertEquals("http://test.example.org/", entry.url);
        assertEquals("text/html", entry.mime);
        assertEquals("GET / HTTP/1.1\r\n" +
                "Connection: keep-alive\r\n" +
                "Host: test.example.org\r\n" +
                "User-Agent: Mozilla/4.5 (compatible; HTTrack 3.0x; Windows 98)\r\n" +
                "Accept: text/html,image/png,image/jpeg,image/pjpeg,image/x-xbitmap,image/svg+xml,image/gif;q=0.9,*/*;q=0.1\r\n" +
                "Accept-Language: en, *\r\n" +
                "Accept-Encoding: gzip, identity;q=0.9\r\n" +
                "\r\n", entry.requestHeader);
        assertEquals("HTTP/1.1 200 OK\r\n" +
                "Server: nginx/1.12.1\r\n" +
                "Date: Wed, 25 Oct 2017 09:41:48 GMT\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: 219\r\n" +
                "Last-Modified: Wed, 25 Oct 2017 09:41:34 GMT\r\n" +
                "Connection: keep-alive\r\n" +
                "ETag: \"59f05c4e-db\"\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "\r\n", entry.responseHeader);
    }

}