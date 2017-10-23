package au.gov.nla.httrack2warc;

import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class HtsCacheEntryTest {

    @Test
    public void test() throws IOException {
        HtsCache cache = new HtsCache(
                getClass().getResourceAsStream("test-hts-log.txt"),
                getClass().getResourceAsStream("test-doit2.log"),
                getClass().getResourceAsStream("test-hts-new2.txt"),
                getClass().getResourceAsStream("test-hts-ioinfo.txt"));

        assertEquals("3.21-4", cache.getHttrackVersion());
        assertEquals(LocalDateTime.parse("2003-04-03T01:00:14"), cache.getLaunchTime());

        HtsCacheEntry entry = cache.get("www.industry.gov.au/acreagereleases/Images/help_on.jpg");

        assertEquals(LocalDateTime.parse("2003-04-03T01:00:17"), entry.timestamp);
        assertEquals("http://www.industry.gov.au/acreagereleases/Images/help_on.jpg", entry.url);
        assertEquals("image/jpeg", entry.mime);
        assertEquals("GET /acreagereleases/Images/help_on.jpg HTTP/1.1\r\n" +
                "Referer: http://www.industry.gov.au/acreagereleases/ar_home.html\r\n" +
                "Cookie: $Version=1; WEBTRENDS_ID=192.199.45.6-984156736.29554984; $Path=/\r\n" +
                "Connection: close\r\n" +
                "Host: www.industry.gov.au\r\n" +
                "User-Agent: Mozilla/4.5 (compatible; HTTrack 3.0x; Windows 98)\r\n" +
                "Accept: image/gif, image/x-xbitmap, image/jpeg, image/pjpeg, image/svg+xml, */*\r\n" +
                "Accept-Language: en, *\r\n" +
                "Accept-Charset: iso-8859-1, *\r\n" +
                "Accept-Encoding: gzip, deflate, compress, identity\r\n\r\n", entry.requestHeader);
        assertEquals("HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 4500\r\n" +
                "Date: Wed, 02 Apr 2003 14:57:52 GMT\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Server: Microsoft-IIS/5.0\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Last-Modified: Sat, 22 Mar 2003 01:04:33 GMT\r\n" +
                "ETag: \"17a1350ff0c21:8ad\"\r\n\r\n", entry.responseHeader);
    }

}