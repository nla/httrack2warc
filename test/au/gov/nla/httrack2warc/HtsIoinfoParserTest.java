package au.gov.nla.httrack2warc;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class HtsIoinfoParserTest {

    @Test
    public void test() throws IOException {
        try (HtsIoinfoParser ioinfo = new HtsIoinfoParser(getClass().getResourceAsStream("test-hts-ioinfo.txt"))) {
            assertTrue(ioinfo.parseRecord());
            assertTrue(ioinfo.request);
            assertEquals("www.industry.gov.au/acreagereleases/ar_home.html", ioinfo.filename);
            assertEquals("GET /acreagereleases/ar_home.html HTTP/1.1\r\n" +
                    "Connection: close\r\n" +
                    "Host: www.industry.gov.au\r\n" +
                    "User-Agent: Mozilla/4.5 (compatible; HTTrack 3.0x; Windows 98)\r\n" +
                    "Accept: image/gif, image/x-xbitmap, image/jpeg, image/pjpeg, image/svg+xml, */*\r\n" +
                    "Accept-Language: en, *\r\n" +
                    "Accept-Charset: iso-8859-1, *\r\n" +
                    "Accept-Encoding: gzip, deflate, compress, identity\r\n", ioinfo.header);

            assertTrue(ioinfo.parseRecord());
            assertFalse(ioinfo.request);
            assertEquals("www.industry.gov.au/acreagereleases/ar_home.html", ioinfo.filename);
            assertEquals("HTTP/1.1 200 OK\r\n" +
                    "Connection: close\r\n" +
                    "Content-Length: 6256\r\n" +
                    "Date: Wed, 02 Apr 2003 14:57:49 GMT\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Set-Cookie: WEBTRENDS_ID=192.199.45.6-984156736.29554984; expires=Fri, 31-Dec-2010 00:00:00 GMT; path=/\r\n" +
                    "Server: Microsoft-IIS/5.0\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Last-Modified: Mon, 24 Mar 2003 04:02:30 GMT\r\n" +
                    "ETag: \"5af18630baf1c21:8ad\"\r\n", ioinfo.header);

            while (ioinfo.parseRecord()) {
                assertNotNull(ioinfo.filename);
            }
        }
    }

}