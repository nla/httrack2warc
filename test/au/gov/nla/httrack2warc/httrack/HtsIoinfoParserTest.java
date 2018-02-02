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

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class HtsIoinfoParserTest {

    @Test
    public void test() throws IOException {
        try (HtsIoinfoParser ioinfo = new HtsIoinfoParser(getClass().getResourceAsStream("test-hts-ioinfo.txt"))) {
            assertTrue(ioinfo.parseRecord());
            assertTrue(ioinfo.request);
            assertEquals("www.industry.gov.au/acreagereleases/ar_home.html", ioinfo.url);
            assertEquals("GET /acreagereleases/ar_home.html HTTP/1.1\r\n" +
                    "Connection: close\r\n" +
                    "Host: www.industry.gov.au\r\n" +
                    "User-Agent: Mozilla/4.5 (compatible; HTTrack 3.0x; Windows 98)\r\n" +
                    "Accept: image/gif, image/x-xbitmap, image/jpeg, image/pjpeg, image/svg+xml, */*\r\n" +
                    "Accept-Language: en, *\r\n" +
                    "Accept-Charset: iso-8859-1, *\r\n" +
                    "Accept-Encoding: gzip, deflate, compress, identity\r\n\r\n", ioinfo.header);

            assertTrue(ioinfo.parseRecord());
            assertFalse(ioinfo.request);
            assertEquals("www.industry.gov.au/acreagereleases/ar_home.html", ioinfo.url);
            assertEquals("HTTP/1.1 200 OK\r\n" +
                    "Connection: close\r\n" +
                    "Content-Length: 6256\r\n" +
                    "Date: Wed, 02 Apr 2003 14:57:49 GMT\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Set-Cookie: WEBTRENDS_ID=192.199.45.6-984156736.29554984; expires=Fri, 31-Dec-2010 00:00:00 GMT; path=/\r\n" +
                    "Server: Microsoft-IIS/5.0\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Last-Modified: Mon, 24 Mar 2003 04:02:30 GMT\r\n" +
                    "ETag: \"5af18630baf1c21:8ad\"\r\n\r\n", ioinfo.header);

            assertTrue(ioinfo.parseRecord());
            assertTrue(ioinfo.request);
            assertEquals("test.example.org/page WITH \"special\" chars.html", ioinfo.url);
            assertEquals("GET /page%20WITH%20%22special%22%20chars.html HTTP/1.1\r\n" +
                    "Referer: http://test.example.org/\r\n" +
                    "Connection: keep-alive\r\n" +
                    "Host: test.example.org\r\n" +
                    "User-Agent: Mozilla/4.5 (compatible; HTTrack 3.0x; Windows 98)\r\n" +
                    "Accept: text/html,image/png,image/jpeg,image/pjpeg,image/x-xbitmap,image/svg+xml,image/gif;q=0.9,*/*;q=0.1\r\n" +
                    "Accept-Language: en, *\r\n" +
                    "Accept-Encoding: gzip, identity;q=0.9\r\n" +
                    "\r\n", ioinfo.header);

            while (ioinfo.parseRecord()) {
                assertNotNull(ioinfo.url);
            }
        }
    }

    @Test
    public void test301() throws IOException {
        try (HtsIoinfoParser ioinfo = new HtsIoinfoParser(getClass().getResourceAsStream("test-hts-ioinfo-3.01.txt"))) {
            assertTrue(ioinfo.parseRecord());
            assertTrue(ioinfo.request);
            assertEquals("www.artistsfootsteps.com/", ioinfo.url);
            assertEquals("GET http://www.artistsfootsteps.com/ HTTP/1.1\r\n" +
                    "Connection: close\r\n" +
                    "Host: www.artistsfootsteps.com\r\n" +
                    "User-Agent: Mozilla/4.5 (compatible; HTTrack 3.0x; Windows 98)\r\n" +
                    "Accept: image/gif, image/x-xbitmap, image/jpeg, image/pjpeg, */*\r\n" +
                    "Accept-Language: en, *\r\n" +
                    "Accept-Charset: iso-8859-1, *\r\n" +
                    "Accept-Encoding: identity\r\n" +
                    "\r\n", ioinfo.header);

            assertTrue(ioinfo.parseRecord());
            assertFalse(ioinfo.request);
            assertEquals("www.artistsfootsteps.com/", ioinfo.url);
            assertEquals("HTTP/1.0 200 OK\r\n" +
                    "Date: Fri, 01 Jun 2001 00:15:52 GMT\r\n" +
                    "Age: 450072\r\n" +
                    "Server: Microsoft-IIS/4.0\r\n" +
                    "Content-Location: http://www.artistsfootsteps.com/index.html\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Last-Modified: Wed, 07 Mar 2001 01:34:34 GMT\r\n" +
                    "Content-Length: 990\r\n" +
                    "Etag: \"0316fc3a6a6c01:44dbc\"\r\n" +
                    "Via: 1.1 proxy.cache.telstra.net (NetCache 4.1R6)\r\n" +
                    "\r\n", ioinfo.header);
            while (ioinfo.parseRecord()) {
                assertNotNull(ioinfo.url);
            }
        }
    }
}