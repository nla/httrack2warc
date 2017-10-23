package au.gov.nla.httrack2warc;

import java.io.IOException;
import java.io.OutputStream;

interface StreamWriter {
    void writeTo(OutputStream gzos) throws IOException;
}
