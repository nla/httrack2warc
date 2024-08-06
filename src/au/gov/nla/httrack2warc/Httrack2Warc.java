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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.util.Locale.ROOT;

public class Httrack2Warc {
    private Logger log;
    private final static Set<String> ignoreFiles = new HashSet<>(Arrays.asList(
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
            "hts-cache/readme.txt",
            "hts-cache/winprofile.ini",
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
    private final List<Pattern> urlExclusions = new ArrayList<>();
    private String redirectFile;
    private String redirectPrefix;

    public void convert(Path source) throws IOException {
        if (log == null) {
            log = LoggerFactory.getLogger(Httrack2Warc.class);
        }
        String filename = source.getFileName().toString();
        if (!Files.isDirectory(source) && (filename.endsWith(".tar.gz") || filename.endsWith(".tgz"))) {
            convertTarball(source);
        } else {
            convertDirectory(source);
        }
    }

    private void convertTarball(Path crawldir) throws IOException {
        Path tmp = Files.createTempDirectory("httrack2warc");
        log.debug("Unpacking {} to {}", crawldir, tmp);
        try {
            try {
                int exitval = new ProcessBuilder("tar", "-C", tmp.toString(), "-zxf", crawldir.toAbsolutePath()
                        .toString()).inheritIO().start().waitFor();
                if (exitval != 0) {
                    throw new IOException("Unable to untar " + crawldir);
                }
            } finally {
                fixPermissions(tmp);
            }
            Optional<Path> cacheDir = Files.walk(tmp).filter(p -> p.getFileName().toString().equals("hts-cache") && Files.isDirectory(p)).findFirst();
            if (!cacheDir.isPresent()) throw new IOException("Unable to find hts-cache directory in archive");
            log.debug("Found httrack crawl under {}", cacheDir.get().getParent());
            convertDirectory(cacheDir.get().getParent());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            deleteRecursively(tmp);
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Unable to delete " + dir, e);
            }
        });
    }

    private void fixPermissions(Path dir) throws IOException {
        Set<PosixFilePermission> dirPerms = PosixFilePermissions.fromString("rwx------");
        Set<PosixFilePermission> filePerms = PosixFilePermissions.fromString("rw-------");
        Files.walk(dir).forEach(path -> {
            try {
                if (Files.isDirectory(path)) {
                    Files.setPosixFilePermissions(path, dirPerms);
                } else if (Files.isRegularFile(path)) {
                    Files.setPosixFilePermissions(path, filePerms);
                }
            } catch (IOException e) {
                log.warn("Unable to set permissions on " + path, e);
            }
        });
    }

    public void convertDirectory(Path sourceDirectory) throws IOException {
        log.debug("Starting WARC conversion. sourceDirectory = {} outputDirectory = {}", sourceDirectory, outputDirectory);

        try (CdxWriter cdxWriter = cdxName == null ? null : new CdxWriter(outputDirectory.resolve(cdxName));
             HttrackCrawl crawl = new HttrackCrawl(sourceDirectory);
             WarcWriter warc = new WarcWriter(outputDirectory.resolve(warcNamePattern).toString(), compression, cdxWriter);
             RedirectWriter redirectWriter = new RedirectWriter(redirectPrefix, redirectFile == null || redirectPrefix == null ? warc : new WarcWriter(outputDirectory.resolve(redirectFile).toString(), compression, cdxWriter))) {
            String warcInfo = formatWarcInfo(crawl);
            Instant launchInstant = crawl.getLaunchTime().atZone(timezone).toInstant();
            Set<String> processedFiles = new HashSet<>();
            LinkRewriter linkRewriter = rewriteLinks ? new LinkRewriter(crawl) : null;

            if (redirectWriter.warc != warc) {
                redirectWriter.warc.writeWarcinfoRecord(UUID.randomUUID(), launchInstant, warcInfo);
            }

            crawl.forEach(record -> {
                if (isUrlExcluded(record.getUrl())) {
                    log.info("Excluded {}", record.getUrl());
                    processedFiles.add(record.getFilename());
                    return;
                }

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

                long contentLength = record.getSize();
                String digest = null;
                if (record.exists()) {
                    try (InputStream stream = record.openStream()) {
                        digest = Digests.sha1(stream);
                    }
                }

                // we only allow rotations at the start of each set of records to ensure they're always
                // kept together in the same file
                if (warc.rotateIfNecessary()) {
                    warc.writeWarcinfoRecord(UUID.randomUUID(), launchInstant, warcInfo);
                }

                Instant warcDate = record.getTimestamp().atZone(timezone).toInstant();

                long linksRewritten = 0;
                try (InputStream stream = record.openStream()) {
                    InputStream body;

                    if (linkRewriter != null && record.getFilename() != null && record.getFilename().endsWith(".html") && !record.hasCacheData()) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        linksRewritten = linkRewriter.rewrite(stream, record.getFilename(), buffer);
                        byte[] data = buffer.toByteArray();
                        contentLength = data.length;
                        digest = Digests.sha1(new ByteArrayInputStream(data));
                        body = new ByteArrayInputStream(data);
                    } else {
                        body = stream;
                    }

                    String responseHeader = record.getResponseHeader();
                    if (responseHeader == null && record.getStatus() >= 300) {
                        // if there's no response header but an error status we fabricate a header to record it
                        // as that's the lesser evil than playback interpreting it incorrectly
                        responseHeader = "HTTP/1.0 " + record.getStatus() + " \r\nContent-Type: " + contentType + "\r\nServer: httrack2warc reconstructed header\r\n\r\n";
                    }
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

                log.info("{} {}{}{} -> {}", record.getTimestamp().format(ISO_LOCAL_DATE_TIME), record.getFilename(),
                        record.hasCacheData() ? " (cache)" : "", linksRewritten == 0 ? "" : " (" + linksRewritten + " links rewritten)", record.getUrl());

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

                redirectWriter.write(record, warcDate);

                processedFiles.add(record.getFilename());
            });

            Files.walk(sourceDirectory).forEach(path -> {
                String file = sourceDirectory.relativize(path).toString();
                if (processedFiles.contains(file) ||
                        ignoreFiles.contains(file) ||
                        Files.isDirectory(path) ||
                        file.toLowerCase(ROOT).endsWith(".readme")) {
                    return;
                }

                log.warn("Unprocessed extra file: {}", file);
            });

            if (cdxWriter != null) {
                cdxWriter.finish();
            }
        }

        log.debug("Finished WARC conversion.");
    }

    private boolean isUrlExcluded(String url) {
        return urlExclusions.stream().anyMatch(p -> p.matcher(url).matches());
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

    public void addExclusion(Pattern pattern) {
        urlExclusions.add(pattern);
    }

    public void setRedirectPrefix(String redirectPrefix) {
        this.redirectPrefix = redirectPrefix;
    }

    public void setRedirectFile(String redirectFile) {
        this.redirectFile = redirectFile;
    }
}
