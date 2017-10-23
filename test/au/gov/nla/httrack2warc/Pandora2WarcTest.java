package au.gov.nla.httrack2warc;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class Pandora2WarcTest {
    @Test
    public void base32() throws Exception {
        assertEquals("MZXW6YTBMZXW6YTC", Pandora2Warc.base32("foobafoobb".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void parseInstanceDate() {
        assertEquals(LocalDateTime.parse("2003-01-01T00:00"), Pandora2Warc.parseInstanceDate("20030101"));
        assertEquals(LocalDateTime.parse("2003-01-01T00:00"), Pandora2Warc.parseInstanceDate("20030101-0000"));
    }

}