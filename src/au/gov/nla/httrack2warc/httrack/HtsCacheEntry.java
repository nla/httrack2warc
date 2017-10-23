package au.gov.nla.httrack2warc.httrack;

import java.time.LocalDateTime;

public class HtsCacheEntry {
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

    public String getFilename() {
        return filename;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUrl() {
        return url;
    }

    public String getMime() {
        return mime;
    }

    public String getRequestHeader() {
        return requestHeader;
    }

    public String getResponseHeader() {
        return responseHeader;
    }

    public String getReferrer() {
        return via;
    }
}
