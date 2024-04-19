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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class HtsLogParserTest {

    @Test
    public void test() throws IOException {
        try (HtsLogParser htsLog = new HtsLogParser(getClass().getResourceAsStream("test-hts-log.txt"))) {
            assertEquals("3.21-4", htsLog.version);
            assertEquals("http://www.industry.gov.au/acreagereleases/ar_home.html -pandora.nla.gov.au* -www.nla.gov.au/pandora*", htsLog.seedsAndFilters);
            assertEquals(LocalDateTime.parse("2003-04-03T01:00:14"), htsLog.launchTime);
            assertEquals("/pandas/working/13982/20030403/", htsLog.outputDir);
            assertEquals("http://www.industry.gov.au/acreagereleases/ar_home.html -O \"/pandas/working/13982/20030403\" -%HzZfI0A50000c6H1tx%xo0b1%sqZI0%I0%Hr50M1000000000E172800%PnK0L1p3Das0 -j -%A standard -%U pandora -#Z -#f -pandora.nla.gov.au* -www.nla.gov.au/pandora*", htsLog.commandLine);
        }
    }

    @Test
    public void testWinhttrack() throws IOException {
        String data = "HTTrack3.48-22+htsswf+htsjava launched on Mon, 05 Sep 2016 09:24:43 at http://example.com/ +*.png +*.gif +*.jpg +*.jpeg +*.css +*.js -ad.doubleclick.net/* -mime:application/foobar\r\r\n" +
                   "(winhttrack -qwC2%Pxs0b0u1%s%uN0%I0p3DaK0H0%kf2A25000%f#f -F  -%F \"<!-- Mirrored from %s%s by HTTrack Website Copier/3.x [XR&CO'2014], %s -->\" -%l \"en, *\" http://www.example.com/ -O1 \"C:\\My Web Sites\\Example Site\" +*.png +*.gif +*.jpg +*.jpeg +*.css +*.js -ad.doubleclick.net/* -mime:application/foobar )\r\r\n" +
                   "\r\r\n" +
                   "Information, Warnings and Errors reported for this mirror:\r\r\n";
        HtsLogParser parser = new HtsLogParser(new ByteArrayInputStream(data.getBytes(StandardCharsets.US_ASCII)));
        assertEquals("C:\\My Web Sites\\Example Site/", parser.outputDir);
    }
}