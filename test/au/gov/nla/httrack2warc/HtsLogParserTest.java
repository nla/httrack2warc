package au.gov.nla.httrack2warc;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class HtsLogParserTest {

    @Test
    public void test() throws IOException {
        try (HtsLogParser htsLog = new HtsLogParser(getClass().getResourceAsStream("test-hts-log.txt"))) {
            assertEquals("3.21-4", htsLog.version);
            assertEquals("http://www.industry.gov.au/acreagereleases/ar_home.html -pandora.nla.gov.au* -www.nla.gov.au/pandora*", htsLog.seedsAndFilters);
            assertEquals("Thu, 03 Apr 2003 01:00:14", htsLog.launchDate);
        }
    }
}