package au.gov.nla.httrack2warc;

import au.gov.nla.httrack2warc.httrack.HtsCache;
import au.gov.nla.httrack2warc.httrack.HtsCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Httrack2Warc {
    private final static Logger log = LoggerFactory.getLogger(Httrack2Warc.class);
    private Path outputDirectory = Paths.get("");
    private Path alternateCacheDirectory = null;
    private long warcSizeTarget = 1024 * 1024 * 1024; // 1 GiB
    private String warcNamePattern = "crawl-%d.warc.gz";
    private ZoneId timezone = ZoneId.systemDefault();
    private MimeTypes mimeTypes = new MimeTypes();
    private StringBuilder extraWarcInfo = new StringBuilder();
    private Compression compression = Compression.GZIP;
    
    private Set<String> exclusions = new HashSet<>(Arrays.asList(
            "backblue.gif", "hts-log.txt", "fade.gif", "index.html", "hts-ioinfo.txt",
            "hts-cache/new.txt", "hts-cache/new.zip", "hts-cache/doit.log", "hts-cache/new.lst",
            "hts-cache/old.txt", "hts-cache/old.zip", "hts-cache/old.lst"));

    public void convert(Path sourceDirectory) throws IOException {
        log.debug("Starting WARC conversion. sourceDirectory = {} outputDirectory = {}", sourceDirectory, outputDirectory);
        HtsCache htsCache = HtsCache.load(sourceDirectory, alternateCacheDirectory);
        Instant launchInstant = htsCache.getLaunchTime().atZone(timezone).toInstant();

        String warcInfo = formatWarcInfo(htsCache);

        try (WarcWriter warc = new WarcWriter(outputDirectory.resolve(warcNamePattern).toString(), null)) {
            Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = sourceDirectory.relativize(file);
                    String encodedPath = encodePath(relativePath);
                    HtsCacheEntry htsCacheEntry = htsCache.get(encodedPath);
                    UUID responseRecordId = UUID.randomUUID();
                    long contentLength = Files.size(file);
                    String digest = sha1Digest(file);

                    // use the URL from the HTTrack log if available, otherwise use the path
                    String url;
                    if (htsCacheEntry != null && htsCacheEntry.getUrl() != null) {
                        url = htsCacheEntry.getUrl();
                    } else {
                        url = "http://" + encodedPath;
                    }

                    // use the date from the HTTrack log if available, fallback to crawl start time
                    LocalDateTime localDate;
                    if (htsCacheEntry != null && htsCacheEntry.getTimestamp() != null) {
                        localDate = htsCacheEntry.getTimestamp();
                    } else {
                        localDate = htsCache.getLaunchTime();
                    }
                    Instant date = localDate.atZone(timezone).toInstant();

                    // use content type if we have it, otherwise guess based on the file extension
                    String contentType;
                    if (htsCacheEntry != null && htsCacheEntry.getMime() != null) {
                        contentType = htsCacheEntry.getMime();
                    } else {
                        contentType = mimeTypes.forPath(file);
                        if (contentType == null) {
                            contentType = "application/octet-stream";
                        }
                    }

                    if (exclusions.contains(relativePath.toString().toLowerCase())) {
                        log.info(relativePath + " excluded");
                        return FileVisitResult.CONTINUE;
                    }

                    log.info("{} -> {}", relativePath, url);

                    // we only allow rotations at the start of each set of records to ensure they're always
                    // kept together in the same file
                    if (warc.rotateIfNecessary()) {
                        warc.writeWarcinfoRecord(UUID.randomUUID(), launchInstant, warcInfo);
                    }

                    if (htsCacheEntry != null && htsCacheEntry.getResponseHeader() != null) {
                        warc.writeResponseRecord(url, contentType, digest, responseRecordId, date, contentLength,
                                htsCacheEntry.getResponseHeader(), file);
                    } else {
                        warc.writeResourceRecord(url, contentType, digest, responseRecordId, date, contentLength, file);
                    }

                    if (htsCacheEntry != null && htsCacheEntry.getRequestHeader() != null) {
                        warc.writeRequestRecord(url, responseRecordId, date, htsCacheEntry.getRequestHeader());
                    }

                    if (htsCacheEntry != null && htsCacheEntry.getReferrer() != null) {
                        warc.writeMetadataRecord(url, responseRecordId, date, htsCacheEntry.getReferrer());
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        }

        log.debug("Finished WARC conversion.");
    }

    private String formatWarcInfo(HtsCache htsCache) {
        StringBuilder info = new StringBuilder(extraWarcInfo);

        if (htsCache.getHttrackVersion() != null) {
            info.append("software: HTTrack/").append(htsCache.getHttrackVersion()).append(" http://www.httrack.com/\r\n");
        }

        if (htsCache.getHttrackOptions() != null) {
            info.append("httrackOptions: ").append(htsCache.getHttrackOptions()).append("\r\n");
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

    private String sha1Digest(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] buffer = new byte[1024 * 1024];
        try (InputStream is = Files.newInputStream(file)) {
            for (;;) {
                int n = is.read(buffer);
                if (n < 0) break;
                digest.update(buffer, 0, n);
            }
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
