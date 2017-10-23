package au.gov.nla.httrack2warc;

import java.time.LocalDateTime;

class HtsCacheEntry {
    final String filename;
    LocalDateTime timestamp;
    String url;
    String mime;
    String requestHeader;
    String responseHeader;
    String via;

    public HtsCacheEntry(String filename) {
        this.filename = filename;
    }
}
