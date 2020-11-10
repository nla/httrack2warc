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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.time.LocalTime;

import static org.junit.Assert.*;

public class HtsTxtParserTest {
    @Test
    public void testModern() throws Exception {
        try (HtsTxtParser parser = new HtsTxtParser(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("test-hts-new.txt"))))) {
            assertTrue(parser.readRecord());
            assertEquals(LocalTime.of(16, 24, 26), parser.time());
            assertEquals("http://www-test.nla.gov.au/xinq/", parser.url());
            assertNull(parser.referrer());
            assertEquals("text/html", parser.mime());
            assertEquals("/home/aosborne/tmp/pandas/working/1/20170725-1623/www-test.nla.gov.au/xinq/index.html", parser.localfile());

            while (parser.readRecord()) {
                assertTrue(parser.url().startsWith("http://"));
            }
        }
    }

    @Test
    public void testEarly() throws Exception {
        try (HtsTxtParser parser = new HtsTxtParser(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("test-hts-new2.txt"))))) {
            assertTrue(parser.readRecord());
            assertEquals(LocalTime.of(1, 0, 14), parser.time());
            assertEquals("www.industry.gov.au/acreagereleases/ar_home.html", parser.url());
            assertNull(parser.referrer());
            assertEquals("text/html", parser.mime());
            assertEquals("/pandas/working/13982/20030403/www.industry.gov.au/acreagereleases/ar_home.html", parser.localfile());

            while (parser.readRecord()) {
                // just try to touch everything
                assertNotNull(parser.url());
            }
        }
    }

    @Test
    public void test303() throws IOException {
        try (HtsTxtParser parser = new HtsTxtParser(new BufferedReader(new StringReader("date\tsize'/'remotesize\tflags(request:Update,Range state:File response:Modified,Chunked,gZipped)\tstatuscode\tstatus ('servermsg')\tMIME\tEtag|Date\tURL\tlocalfile\t(from URL)\n15:55:04\t0/0\t---M--\t303\terror ('')\t\t\thttp://www.antisf.com.au/component/weblinks/weblink/9-aussie-worldcon-2010?Itemid=89\t/pandoraworking/working/10063/20140108-1525/www.antisf.com.au/component/weblinks/weblink/9-aussie-worldcon-2010.80cd8f2.delayed\t(from http://www.antisf.com.au/the-stories/star-light-star-bright/42-content/newsflashes)")))) {
            while (parser.readRecord()) {
                // just try to touch everything
                assertNotNull(parser.url());
            }
        }
    }
}