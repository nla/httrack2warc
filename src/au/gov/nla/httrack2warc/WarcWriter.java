/*
 * Copyright (c) 2017 National Library of Australia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.gov.nla.httrack2warc;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;

/**
 * Writes WARC files and the corresponding CDX files.
 */
class WarcWriter implements Closeable {
    private static final DateTimeFormatter WARC_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(UTC);

    private static final long warcRotationSize = 1048576000;
    private final RotatingFile warcRotor;
    private final CdxWriter cdxWriter;
    private final Compression compression = Compression.GZIP;

    public WarcWriter(String warcFilePattern, CdxWriter cdxWriter) throws IOException {
        this.warcRotor = new RotatingFile(warcFilePattern, warcRotationSize);
        this.cdxWriter = cdxWriter;
    }

    public void finish() throws IOException {
        if (cdxWriter != null) cdxWriter.finish();
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
                             long contentLength, String responseHeader, InputStream body) throws IOException {
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
            copyStream(body, gzos);
        });
        if (cdxWriter != null) {
            Path filename = recordPosition.file.getFileName();
            cdxWriter.writeLine(url, contentType, digest, date, recordPosition, filename);
        }
    }

    void writeResourceRecord(String url, String contentType, String digest, UUID uuid, Instant date,
                             long contentLength, InputStream body) throws IOException {
        String header = "WARC/1.0\r\n" +
                "WARC-Type: resource\r\n" +
                "WARC-Target-URI: " + url + "\r\n" +
                "WARC-Record-ID: <urn:uuid:" + uuid + ">\r\n" +
                "WARC-Date: " + WARC_DATE.format(date) + "\r\n" +
                "WARC-Block-Digest: sha1:" + digest + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "\r\n";
        RecordPosition recordPosition = writeRecord(header, gzos -> copyStream(body, gzos));
        if (cdxWriter != null) {
            Path filename = recordPosition.file.getFileName();
            cdxWriter.writeLine(url, contentType, digest, date, recordPosition, filename);
        }
    }

    RecordPosition writeRecord(String header, StreamWriter body) throws IOException {
        if (warcRotor.channel == null) {
            warcRotor.rotateIfNecessary();
        }
        long startOfRecord = warcRotor.channel.position();

        compression.writeMember(warcRotor.channel, stream -> {
            stream.write(header.getBytes(UTF_8));
            body.writeTo(stream);
            stream.write("\r\n".getBytes(UTF_8));
        });

        long endOfRecord = warcRotor.channel.position();
        return new RecordPosition(warcRotor.currentFilePath, startOfRecord, endOfRecord);
    }

    @Override
    public void close() throws IOException {
        warcRotor.close();
        if (cdxWriter != null) {
            cdxWriter.close();
        }
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

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];
        for (;;) {
            int n = is.read(buffer);
            if (n < 0) break;
            os.write(buffer, 0, n);
        }
    }

}
