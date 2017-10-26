package au.gov.nla.httrack2warc.httrack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class HttrackRecord {
    private final String filename;
    final LocalDateTime timestamp;
    final String url;
    final String mime;
    final String requestHeader;
    final String responseHeader;
    private final String referrer;
    private final CacheEntry cacheEntry;
    private final Path path;

    HttrackRecord(String filename, LocalDateTime timestamp, String url, String mime, String requestHeader, String responseHeader, String referrer, Path path, CacheEntry cacheEntry) {
        this.filename = filename;
        this.timestamp = timestamp;
        this.url = url;
        this.mime = mime;
        this.requestHeader = requestHeader;
        this.responseHeader = responseHeader;
        this.referrer = referrer;
        this.path = path;
        this.cacheEntry = cacheEntry;
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
        return referrer;
    }

    public InputStream openStream() throws IOException {
        if (cacheEntry.hasData()) {
            return cacheEntry.openStream();
        } else {
            return Files.newInputStream(path);
        }
    }

    public long getSize() throws IOException {
        if (cacheEntry.getSize() > 0) {
            return cacheEntry.getSize();
        } else {
            return Files.size(path);
        }
    }
}
