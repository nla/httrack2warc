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

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

public class Httrack2Warc {
    private final static Logger log = LoggerFactory.getLogger(Httrack2Warc.class);
    private final static Set<String> exclusions = new HashSet<>(Arrays.asList(
            "backblue.gif",
            "cookies.txt",
            "external.gif",
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
            "index.html",
            "logs/info",
            "logs/err",
            "logs/gen",
            "logs/debug",
            "logs/warn"));
    private Path outputDirectory = Paths.get("");
    private Path alternateCacheDirectory = null;
    private long warcSizeTarget = 1024 * 1024 * 1024; // 1 GiB
    private String warcNamePattern = "crawl-%d.warc.gz";
    private ZoneId timezone = ZoneId.systemDefault();
    private MimeTypes mimeTypes = new MimeTypes();
    private StringBuilder extraWarcInfo = new StringBuilder();
    private Compression compression = Compression.GZIP;
    private String cdxName = null;
    private boolean strict = false;
    private boolean rewriteLinks = false;

    public void convert(Path sourceDirectory) throws IOException {
        log.debug("Starting WARC conversion. sourceDirectory = {} outputDirectory = {}", sourceDirectory, outputDirectory);

        CdxWriter cdxWriter = cdxName == null ? null : new CdxWriter(outputDirectory.resolve(cdxName));
        try (HttrackCrawl crawl = new HttrackCrawl(sourceDirectory);
             WarcWriter warc = new WarcWriter(outputDirectory.resolve(warcNamePattern).toString(), compression, cdxWriter)) {
            String warcInfo = formatWarcInfo(crawl);
            Instant launchInstant = crawl.getLaunchTime().atZone(timezone).toInstant();
            Set<String> processedFiles = new HashSet<>();
            LinkRewriter linkRewriter = rewriteLinks ? new LinkRewriter(crawl) : null;

            crawl.forEach(record -> {
                // XXX: skip missing files if they were an error message
                // this is a workaround until we can find a better way to handle cases like .delayed files and
                // images renamed to .html
                if (!strict && record.getStatus() > 399 && !record.exists()) {
                    log.warn("Missing file {} for {} URL {}", record.getFilename(), record.getStatus(), record.getUrl());
                    return;
                }

                if (!record.exists() && !record.isRedirect()) {
                    log.error("Missing file {} for {} URL {}", record.getFilename(), record.getStatus(), record.getUrl());
                }

                UUID responseRecordId = UUID.randomUUID();

                // use content type if we have it, otherwise guess based on the file extension
                String contentType = record.getMime();
                if (contentType == null) contentType = mimeTypes.forFilename(record.getFilename());
                if (contentType == null) contentType = "application/octet-stream";

                log.info("{}{} -> {}", record.getFilename(), record.hasCacheData() ? " (cache)" : "", record.getUrl());

                long contentLength = record.getSize();
                String digest = null;
                if (record.exists()) {
                    try (InputStream stream = record.openStream()) {
                        digest = sha1Digest(stream);
                    }
                }

                // we only allow rotations at the start of each set of records to ensure they're always
                // kept together in the same file
                if (warc.rotateIfNecessary()) {
                    warc.writeWarcinfoRecord(UUID.randomUUID(), launchInstant, warcInfo);
                }

                Instant warcDate = record.getTimestamp().atZone(timezone).toInstant();

                try (InputStream stream = record.openStream()) {
                    InputStream body;

                    if (linkRewriter != null && record.getFilename().endsWith(".html") && !record.hasCacheData()) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        linkRewriter.rewrite(stream, record.getFilename(), buffer);
                        byte[] data = buffer.toByteArray();
                        contentLength = data.length;
                        digest = sha1Digest(new ByteArrayInputStream(data));
                        body = new ByteArrayInputStream(data);
                    } else {
                        body = stream;
                    }

                    String responseHeader = record.getResponseHeader();
                    if (responseHeader != null) {
                        String truncated;
                        if (record.exists()) {
                            responseHeader = removeTransferEncodingHeader(responseHeader);
                            responseHeader = fixContentLength(responseHeader, contentLength);
                            truncated = null;
                        } else {
                            truncated = "unspecified";
                        }
                        warc.writeResponseRecord(record.getUrl(), contentType, digest, responseRecordId, warcDate, contentLength,
                                responseHeader, body, truncated);
                    } else {
                        warc.writeResourceRecord(record.getUrl(), contentType, digest, responseRecordId, warcDate, contentLength, body);
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

            if (cdxWriter != null) {
                cdxWriter.finish();
            }
        } finally {
            if (cdxWriter != null) {
                cdxWriter.close();
            }
        }

        log.debug("Finished WARC conversion.");
    }

    private static Pattern TRANSFER_ENCODING_RE = Pattern.compile("^\\s*Transfer-Encoding\\s*:.*\r\n", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static Pattern CONTENT_LENGTH_RE = Pattern.compile("^\\s*Content-Length\\s*:.*\r\n", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /**
     * Remove the Transfer-Encoding response header. We do this as we don't have the original encoded bytes and
     * tools attempting to read the WARC file we generate will expect to decode the payload according to the
     * Transfer-Encoding.
     */
    static String removeTransferEncodingHeader(String header) {
        return TRANSFER_ENCODING_RE.matcher(header).replaceAll("");
    }

    /**
     * We replace the Content-Length header as link rewriting (by either us or HTTrack) may have changed the
     * length of the body.
     */
    static String fixContentLength(String header, long length) {
        return CONTENT_LENGTH_RE.matcher(header).replaceAll("Content-Length: " +  length + "\r\n");
    }

    private String formatWarcInfo(HttrackCrawl crawl) {
        StringBuilder info = new StringBuilder(extraWarcInfo);

        if (crawl.getHttrackVersion() != null) {
            info.append("software: HTTrack/").append(crawl.getHttrackVersion()).append(" http://www.httrack.com/\r\n");
        }

        String selfVersion = getSelfVersion();
        if (selfVersion != null) {
            info.append("software: httrack2warc/").append(selfVersion).append(" https://github.com/nla/httrack2warc\r\n");
        } else {
            info.append("software: httrack2warc https://github.com/nla/httrack2warc\r\n");
        }

        if (crawl.getHttrackOptions() != null) {
            info.append("httrackOptions: ").append(crawl.getHttrackOptions()).append("\r\n");
        }

        return info.toString();
    }

    static String getSelfVersion() {
        URL resource = Httrack2Warc.class.getResource("/META-INF/maven/au.gov.nla/httrack2warc/pom.properties");
        if (resource == null) return null;
        try (InputStream stream = resource.openStream()) {
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("version");
        } catch (IOException e) {
            return null;
        }
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

    public void setCdxName(String cdxName) {
        this.cdxName = cdxName;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public void setRewriteLinks(boolean rewriteLinks) {
        this.rewriteLinks = rewriteLinks;
    }
}
