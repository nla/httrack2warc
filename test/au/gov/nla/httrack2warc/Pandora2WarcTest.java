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