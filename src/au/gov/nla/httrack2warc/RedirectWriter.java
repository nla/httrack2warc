package au.gov.nla.httrack2warc;

import au.gov.nla.httrack2warc.httrack.HttrackRecord;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes synthetic redirect records from the HTTrack URL to the original live url.
 */
public class RedirectWriter implements Closeable {
    private final String prefix;
    final WarcWriter warc;

    public RedirectWriter(String prefix, WarcWriter warc) {
        this.prefix = prefix;
        this.warc = warc;
    }

    public void write(HttrackRecord record, Instant warcDate) throws IOException {
        // build synthetic redirect record
        if (prefix != null && record.getFilename() != null) {
            String httrackUrl = prefix + record.getFilename();
            byte[] body = new byte[0];
            String header = "HTTP/1.1 301 Moved Permanently\r\n" +
                    "Location: " + record.getUrl() + "\r\n" +
                    "Server: httrack2warc synthetic redirect\r\n" +
                    "Content-Length: " + body.length + "\r\n" +
                    "\r\n";
            warc.writeResponseRecord(httrackUrl, null,
                    Digests.sha1(new ByteArrayInputStream(body)), UUID.randomUUID(), warcDate, body.length,
                    header, new ByteArrayInputStream(body), null);
        }
    }

    @Override
    public void close() throws IOException {
        warc.close();
    }
}
