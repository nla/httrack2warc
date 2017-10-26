package au.gov.nla.httrack2warc.httrack;

import java.io.IOException;
import java.io.InputStream;

public interface CacheEntry {
    long getSize() throws IOException;

    InputStream openStream() throws IOException;

    boolean hasData();
}
