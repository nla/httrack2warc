package au.gov.nla.httrack2warc.httrack;

import java.io.Closeable;

public interface Cache extends Closeable {
    CacheEntry getEntry(String url);
}
