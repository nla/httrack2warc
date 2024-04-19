package au.gov.nla.httrack2warc.httrack;

import junit.framework.TestCase;

public class HtsUtilTest extends TestCase {
    public void testPercentEncode() {
        assertEquals("a%20b%20%00%20%22c%22", HtsUtil.percentEncode("a b \0 \"c\""));
    }
}