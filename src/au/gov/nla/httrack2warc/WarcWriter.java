package au.gov.nla.httrack2warc;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;

/**
 * Writes WARC files and the corresponding CDX files.
 */
class WarcWriter implements Closeable {
    private static final DateTimeFormatter WARC_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(UTC);
    private static final DateTimeFormatter ARC_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US).withZone(UTC);

    private static final long warcRotationSize = 1048576000;
    private final RotatingFile warcRotor;
    private final BufferedWriter cdxWriter;
    private final Path cdxPath, tmpCdxPath;
    boolean cdx11Format = true;

    public WarcWriter(String warcFilePattern, Path cdxPath) throws IOException {
        this.cdxPath = cdxPath;
        tmpCdxPath = Paths.get(cdxPath.toString() + ".tmp");
        this.warcRotor = new RotatingFile(warcFilePattern, warcRotationSize);
        this.cdxWriter = Files.newBufferedWriter(tmpCdxPath, UTF_8);
        cdxWriter.write(" CDX N b a m s k r M S V g\n");
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(tmpCdxPath);
    }

    public void finish() throws IOException {
        externalSort(tmpCdxPath, cdxPath);
    }

    private void externalSort(Path source, Path destination) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sort", source.toString());
        pb.environment().put("LC_ALL", "C");
        pb.environment().put("LANG", "C");
        pb.redirectOutput(destination.toFile());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        int exitValue = 0;
        try {
            exitValue = pb.start().waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (exitValue != 0) {
            throw new RuntimeException("sort returned non-zero exit value: " + exitValue);
        }
    }

    boolean rotateIfNecessary() throws IOException {
        return warcRotor.rotateIfNecessary();
    }

    void writeWarcinfoRecord(UUID uuid, Instant date, String warcInfo) throws IOException {
        byte[] body = warcInfo.getBytes(UTF_8);
        String header = "WARC/1.0\r\n" +
                "WARC-Type: warcinfo\r\n" +
                "WARC-Date: " + date + "\r\n" +
                "WARC-Record-ID: <urn:uuid:" + UUID.randomUUID() + ">\r\n" +
                "Content-Type: application/warc-fields\r\n" +
                "Content-Length:" + body.length + "\r\n" +
                "\r\n";
        writeRecord(header, out -> out.write(body));
    }

    void writeRequestRecord(String url, UUID responseUuid, Instant date, String requestHeader) throws IOException {
        byte[] body = requestHeader.getBytes(ISO_8859_1);
        String header = "WARC/1.0\r\n" +
                "WARC-Type: request\r\n" +
                "WARC-Target-URI: " + url + "\r\n" +
                "WARC-Date: " + date + "\r\n" +
                "WARC-Concurrent-To: <urn:uuid:" + responseUuid + ">\r\n" +
                "WARC-Record-ID: <urn:uuid:" + UUID.randomUUID() + ">\r\n" +
                "Content-Type: application/http;msgtype=request\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "\r\n";
        writeRecord(header, gzos -> gzos.write(body));
    }

    public void writeMetadataRecord(String url, UUID responseUuid, Instant date, String via) throws IOException {
        String metadata = "via: " + via + "\r\n";
        byte[] body = metadata.getBytes(ISO_8859_1);
        String header = "WARC/1.0\r\n" +
                "WARC-Type: metadata\r\n" +
                "WARC-Target-URI: " + url + "\r\n" +
                "WARC-Date: " + date + "\r\n" +
                "WARC-Concurrent-To: <urn:uuid:" + responseUuid + ">\r\n" +
                "WARC-Record-ID: <urn:uuid:" + UUID.randomUUID() + ">\r\n" +
                "Content-Type: application/warc-fields\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "\r\n";
        writeRecord(header, gzos -> gzos.write(body));
    }

    void writeResponseRecord(String url, String contentType, String digest, UUID uuid, Instant date,
                             long contentLength, String responseHeader, Path file) throws IOException {
        byte[] responseHeaderBytes = responseHeader.getBytes(ISO_8859_1);
        long blockLength = contentLength + responseHeaderBytes.length;
        String header = "WARC/1.0\r\n" +
                "WARC-Type: response\r\n" +
                "WARC-Target-URI: " + url + "\r\n" +
                "WARC-Date: " + WARC_DATE.format(date) + "\r\n" +
                "WARC-Payload-Digest: sha1:" + digest + "\r\n" +
                "WARC-Record-ID: <urn:uuid:" + uuid + ">\r\n" +
                "Content-Type: application/http; msgtype=response\r\n" +
                "Content-Length: " + blockLength + "\r\n" +
                "\r\n";
        RecordPosition recordPosition = writeRecord(header, gzos -> {
            gzos.write(responseHeaderBytes);
            Files.copy(file, gzos);
        });
        writeCdxLine(url, contentType, digest, date, recordPosition);
    }

    void writeResourceRecord(String url, String contentType, String digest, UUID uuid, Instant date,
                             long contentLength, Path body) throws IOException {
        String header = "WARC/1.0\r\n" +
                "WARC-Type: resource\r\n" +
                "WARC-Target-URI: " + url + "\r\n" +
                "WARC-Record-ID: <urn:uuid:" + uuid + ">\r\n" +
                "WARC-Date: " + WARC_DATE.format(date) + "\r\n" +
                "WARC-Block-Digest: sha1:" + digest + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "\r\n";
        RecordPosition recordPosition = writeRecord(header, gzos -> Files.copy(body, gzos));
        writeCdxLine(url, contentType, digest, date, recordPosition);
    }

    private void writeCdxLine(String url, String contentType, String digest, Instant date, RecordPosition recordPosition) throws IOException {
        String cdxLine;
        Path filename = recordPosition.file.getFileName();
        if (cdx11Format) {
            cdxLine = url + " " + ARC_DATE.format(date) + " " + url + " " + contentType + " 200 " +
                    digest + " - - " + recordPosition.length() + " " + recordPosition.start + " " + filename + "\n";
        } else {
            cdxLine = url + " " + ARC_DATE.format(date) + " " + url + " " + contentType + " 200 " +
                    digest + " - " + recordPosition.start + " " + filename + "\n";
        }
        System.out.print(cdxLine);
        cdxWriter.write(cdxLine);
    }

    RecordPosition writeRecord(String header, Body body) throws IOException {
        if (warcRotor.channel == null) {
            warcRotor.rotateIfNecessary();
        }
        long startOfRecord = warcRotor.channel.position();
        GZIPOutputStream gzos = new GZIPOutputStream(Channels.newOutputStream(warcRotor.channel));
        gzos.write(header.getBytes(UTF_8));
        body.writeTo(gzos);
        gzos.write("\r\n\r\n".getBytes(UTF_8));
        gzos.finish();
        long endOfRecord = warcRotor.channel.position();
        return new RecordPosition(warcRotor.currentFilePath, startOfRecord, endOfRecord);
    }

    static class RecordPosition {
        private final Path file;
        final long start;
        final long end;

        RecordPosition(Path file, long start, long end) {
            this.file = file;
            this.start = start;
            this.end = end;
        }

        long length() {
            return end - start;
        }
    }

    interface Body {
        void writeTo(OutputStream gzos) throws IOException;
    }
}
