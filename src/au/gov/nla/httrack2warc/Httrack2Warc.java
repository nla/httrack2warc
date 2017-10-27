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

import au.gov.nla.httrack2warc.httrack.HttrackCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Httrack2Warc {
    private final static Logger log = LoggerFactory.getLogger(Httrack2Warc.class);
    private final static Set<String> exclusions = new HashSet<>(Arrays.asList(
            "backblue.gif",
            "cookies.txt",
            "external.html",
            "fade.gif",
            "hts-cache/doit.log",
            "hts-cache/new.dat",
            "hts-cache/new.lst",
            "hts-cache/new.ndx",
            "hts-cache/new.txt",
            "hts-cache/new.zip",
            "hts-cache/old.dat",
            "hts-cache/old.lst",
            "hts-cache/old.ndx",
            "hts-cache/old.txt",
            "hts-cache/old.zip",
            "hts-err.txt",
            "hts-ioinfo.txt",
            "hts-log.txt",
            "hts-stats.txt",
            "index.html"));
    private Path outputDirectory = Paths.get("");
    private Path alternateCacheDirectory = null;
    private long warcSizeTarget = 1024 * 1024 * 1024; // 1 GiB
    private String warcNamePattern = "crawl-%d.warc.gz";
    private ZoneId timezone = ZoneId.systemDefault();
    private MimeTypes mimeTypes = new MimeTypes();
    private StringBuilder extraWarcInfo = new StringBuilder();
    private Compression compression = Compression.GZIP;

    public void convert(Path sourceDirectory) throws IOException {
        log.debug("Starting WARC conversion. sourceDirectory = {} outputDirectory = {}", sourceDirectory, outputDirectory);

        try (WarcWriter warc = new WarcWriter(outputDirectory.resolve(warcNamePattern).toString(), compression, null)) {
            HttrackCrawl crawl = new HttrackCrawl(sourceDirectory);
            String warcInfo = formatWarcInfo(crawl);
            Instant launchInstant = crawl.getLaunchTime().atZone(timezone).toInstant();
            Set<String> processedFiles = new HashSet<>();

            crawl.forEach(record -> {
                UUID responseRecordId = UUID.randomUUID();

                // use content type if we have it, otherwise guess based on the file extension
                String contentType = record.getMime();
                if (contentType == null) contentType = mimeTypes.forFilename(record.getFilename());
                if (contentType == null) contentType = "application/octet-stream";

                log.info("{} -> {}", record.getFilename(), record.getUrl());

                long contentLength = record.getSize();
                String digest;
                try (InputStream stream = record.openStream()) {
                    digest = sha1Digest(stream);
                }

                // we only allow rotations at the start of each set of records to ensure they're always
                // kept together in the same file
                if (warc.rotateIfNecessary()) {
                    warc.writeWarcinfoRecord(UUID.randomUUID(), launchInstant, warcInfo);
                }

                Instant warcDate = record.getTimestamp().atZone(timezone).toInstant();

                try (InputStream stream = record.openStream()) {
                    if (record.getResponseHeader() != null) {
                        warc.writeResponseRecord(record.getUrl(), contentType, digest, responseRecordId, warcDate, contentLength,
                                record.getResponseHeader(), stream);
                    } else {
                        warc.writeResourceRecord(record.getUrl(), contentType, digest, responseRecordId, warcDate, contentLength, stream);
                    }
                }

                if (record.getRequestHeader() != null) {
                    warc.writeRequestRecord(record.getUrl(), responseRecordId, warcDate, record.getRequestHeader());
                }

                // build metadata record
                StringBuilder metadata = new StringBuilder();
                if (record.getReferrer() != null) {
                    metadata.append("via: ").append(record.getReferrer()).append("\r\n");
                }
                if (record.getFilename() != null) {
                    metadata.append("httrackFile: ").append(record.getFilename()).append("\r\n");
                }
                if (metadata.length() > 0) {
                    warc.writeMetadataRecord(record.getUrl(), responseRecordId, warcDate, metadata.toString());
                }

                processedFiles.add(record.getFilename());
            });

            Files.walk(sourceDirectory).forEach(path -> {
                String file = sourceDirectory.relativize(path).toString();
                if (processedFiles.contains(file) ||
                        exclusions.contains(file) ||
                        Files.isDirectory(path)) {
                    return;
                }

                log.warn("Unprocessed extra file: {}", file);
            });
        }

        log.debug("Finished WARC conversion.");
    }

    private String formatWarcInfo(HttrackCrawl crawl) {
        StringBuilder info = new StringBuilder(extraWarcInfo);

        if (crawl.getHttrackVersion() != null) {
            info.append("software: HTTrack/").append(crawl.getHttrackVersion()).append(" http://www.httrack.com/\r\n");
        }

        if (crawl.getHttrackOptions() != null) {
            info.append("httrackOptions: ").append(crawl.getHttrackOptions()).append("\r\n");
        }

        return info.toString();
    }

    private static String encodePath(Path path) throws UnsupportedEncodingException {
        return URLEncoder.encode(path.toString(), "UTF-8")
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static String base32(byte[] data) {
        if (data.length % 5 != 0) {
            throw new IllegalArgumentException("Padding not implemented, data.length must be multiple of 5");
        }
        StringBuilder out = new StringBuilder(data.length / 5 * 8);

        // process 40 bits at a time
        for (int i = 0; i < data.length; i += 5) {
            long buf = 0;

            // read 5 bytes
            for (int j = 0; j < 5; j++) {
                buf <<= 8;
                buf += data[i + j] & 0xff;
            }

            // write 8 base32 characters
            for (int j = 0; j < 8; j++) {
                out.append(BASE32_ALPHABET.charAt((int)((buf >> ((7-j) * 5)) & 31)));
            }
        }
        return out.toString();
    }

    private String sha1Digest(InputStream stream) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] buffer = new byte[1024 * 1024];
        for (; ; ) {
            int n = stream.read(buffer);
            if (n < 0) break;
            digest.update(buffer, 0, n);
        }
        return base32(digest.digest());
    }

    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public void setWarcSizeTarget(long bytes) {
        this.warcSizeTarget = bytes;
    }

    public long getWarcSizeTarget() {
        return warcSizeTarget;
    }

    public Path getAlternateCacheDirectory() {
        return alternateCacheDirectory;
    }

    public void setAlternateCacheDirectory(Path alternateCacheDirectory) {
        this.alternateCacheDirectory = alternateCacheDirectory;
    }

    public String getWarcNamePattern() {
        return warcNamePattern;
    }

    public void setWarcNamePattern(String warcNamePattern) {
        this.warcNamePattern = warcNamePattern;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }

    public void addWarcInfoLine(String line) {
        extraWarcInfo.append(line).append("\r\n");
    }

    public Compression getCompression() {
        return compression;
    }

    public void setCompression(Compression compression) {
        this.compression = compression;
    }
}
