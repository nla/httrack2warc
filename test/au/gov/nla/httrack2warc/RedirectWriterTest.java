package au.gov.nla.httrack2warc;

import au.gov.nla.httrack2warc.httrack.HttrackRecord;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class RedirectWriterTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void write() throws IOException {
        String warcFile = folder.newFolder().toString() + "/test.warc";
        Instant warcDate = Instant.parse("2020-12-01T04:01:25Z");
        try (RedirectWriter redirectWriter = new RedirectWriter("http://example.com/crawl/", new WarcWriter(warcFile, Compression.NONE, null))) {
            redirectWriter.write(new HttrackRecord("index.html", LocalDateTime.now(), "http://example.org/",
                    "text/html", "", "", "",
                    folder.newFile("index.html").toPath(), null, 200), warcDate);
        }
        String data = new String(Files.readAllBytes(Paths.get(warcFile)), StandardCharsets.ISO_8859_1);
        data = data.replaceFirst("<urn:uuid:[^>]*>", "<urn:uuid:...>");
        assertEquals("WARC/1.0\r\n" +
                "WARC-Type: response\r\n" +
                "WARC-Target-URI: http://example.com/crawl/index.html\r\n" +
                "WARC-Date: 2020-12-01T04:01:25Z\r\n" +
                "WARC-Payload-Digest: sha1:3I42H3S6NNFQ2MSVX7XZKYAYSCX5QBYJ\r\n" +
                "WARC-Record-ID: <urn:uuid:...>\r\n" +
                "Content-Type: application/http; msgtype=response\r\n" +
                "Content-Length: 125\r\n" +
                "\r\n" +
                "HTTP/1.1 301 Moved Permanently\r\n" +
                "Location: http://example.org/\r\n" +
                "Server: httrack2warc synthetic redirect\r\n" +
                "Content-Length: 0\r\n\r\n\r\n\r\n", data);
    }
}