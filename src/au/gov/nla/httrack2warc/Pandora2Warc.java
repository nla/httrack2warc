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

import au.gov.nla.httrack2warc.httrack.HttrackRecord;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pandora2Warc {
    private static final ZoneId PANDORA_TIMEZONE = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter PANDAS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US).withZone(PANDORA_TIMEZONE);

    private Path panaccessCacheKey = null;
    private Map<String, String> panaccessCacheValue;
    private Map<String, String> mimeTypes = loadMimes();

    private void decodeMimeLine(String line, Map<String, String> out) {
        String[] parts = line.replace("#.*", "").split("\\s+");
        String mime = parts[0];
        for (int i = 1; i < parts.length; i++) {
            String ext = parts[i];
            out.put(mime, ext);
        }
    }

    private Map<String, String> loadMimes() {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get("/etc/mime.types"))) {
            br.lines().forEach(line -> decodeMimeLine(line, map));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }

    private Pattern RE_FILES = Pattern.compile("\\s*<Files\\s*\"([^\"]+)\">\\s*");
    private Pattern RE_FORCE_TYPE = Pattern.compile("\\s*ForceType\\s+(\\S*)\\s*");

    private Map<String, String> readPanacces(Path f) throws IOException {
        if (!Files.exists(f)) {
            return Collections.emptyMap();
        }
        try (BufferedReader br = Files.newBufferedReader(f)) {
            String filename = null;
            Map<String, String> map = new HashMap<>();
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                Matcher m = RE_FILES.matcher(line);
                if (m.matches()) {
                    filename = m.group(1);
                }
                m = RE_FORCE_TYPE.matcher(line);
                if (m.matches() && filename != null) {
                    String type = m.group(1);
                    map.put(filename, type);
                }
            }
            return map;
        }
    }

    private Map<String,String> readPanaccessCache(Path f) throws IOException {
        if (!f.equals(panaccessCacheKey)) {
            panaccessCacheKey = f;
            panaccessCacheValue = readPanacces(f);
       }
       return panaccessCacheValue;
    }

    private String mimeForFile(Path f, HttrackRecord httrackRecord) throws IOException {
        String filename = f.getFileName().toString();
        Path panaccessMimeTypes = f.getParent().resolve(".panaccess-mime.types");
        String type = readPanaccessCache(panaccessMimeTypes).get(filename);
        if (type != null) {
            return type;
        }

        // try the HTTrack cache metadata if available
        if (httrackRecord != null) {
            return httrackRecord.getMime();
        }

        // if all else fails guess based on the file extension
        String[] parts = filename.split(",");
        String extension = parts[parts.length -1 ];
        return mimeTypes.getOrDefault(extension, "application/octet-stream");
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

    private String calculateSha1Digest(Path f) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] buffer = new byte[1024 * 1024];
        try (InputStream is = Files.newInputStream(f)) {
            for (;;) {
                int n = is.read(buffer);
                if (n < 0) break;
                digest.update(buffer, 0, n);
            }
        }
        return base32(digest.digest());
    }

    private static String encodePath(Path path) throws UnsupportedEncodingException {
        return URLEncoder.encode(path.toString(), "UTF-8")
                .replace("+", "%20")
                .replace("%2F", "/");
    }

    static LocalDateTime parseInstanceDate(String date) {
        if (!date.contains("-")) {
            date += "-0000";
        }
        return LocalDateTime.parse(date, PANDAS_DATE);
    }

    /*
    void convertInstance(Path instanceDir, Path preserveDir, Path destDir) throws IOException {
        String pi = instanceDir.getParent().getFileName().toString();
        String dateString = instanceDir.getFileName().toString();
        String basename = "nla.arc-" + pi + "-" + dateString;
        String warcFilePattern = destDir.resolve(basename + "-%d.warc.gz").toString();
        Path cdxFile = destDir.resolve(basename + ".cdx");

        HtsCache htsCache = HtsCache.load(instanceDir, preserveDir);
        LocalDateTime instanceDate = parseInstanceDate(instanceDir.getFileName().toString());
        LocalDateTime launchDateTime = htsCache.getLaunchTime() != null ? htsCache.getLaunchTime() : instanceDate;
        Instant launchInstant = launchDateTime.atZone(PANDORA_TIMEZONE).toInstant();
        String warcInfo = formatWarcInfo(pi, dateString, htsCache);

        try (WarcWriter warc = new WarcWriter(warcFilePattern, new CdxWriter(cdxFile))) {
            Path workingDir = instanceDir.getParent().getParent();

            Files.walkFileTree(instanceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.getFileName().startsWith(".panaccess")) {
                        Path panPath = workingDir.relativize(file); // "12345/20170203-0000/example.org/index.html"
                        String url = "http://pandora.nla.gov.au/pan/" + encodePath(panPath);

                        // sometimes we can get date metadata from HTTrack cache, but fallback to the instance date otherwise
                        Path path = instanceDir.relativize(file); // "example.org/index.html"
                        HttrackRecord httrackRecord = htsCache.get(encodePath(path));
                        LocalDateTime date;
                        if (httrackRecord != null && httrackRecord.getTimestamp() != null) {
                            date = httrackRecord.getTimestamp();
                        } else {
                            date = instanceDate;
                        }

                        String contentType = mimeForFile(file, httrackRecord);
                        String digest = calculateSha1Digest(file);
                        UUID responseUuid = UUID.randomUUID();
                        Instant instant = date.atZone(PANDORA_TIMEZONE).toInstant();
                        long contentLength = Files.size(file);

                        // we only allow rotations at the start of each set of records to ensure they're always
                        // kept together in the same file
                        if (warc.rotateIfNecessary()) {
                            warc.writeWarcinfoRecord(UUID.randomUUID(), launchInstant, warcInfo);
                        }

                        if (httrackRecord != null && httrackRecord.getResponseHeader() != null) {
                            warc.writeResponseRecord(url, contentType, digest, responseUuid, instant, contentLength,
                                    httrackRecord.getResponseHeader(), file);
                        } else {
                            warc.writeResourceRecord(url, contentType, digest, responseUuid, instant, contentLength, file);
                        }

                        if (httrackRecord != null && httrackRecord.getRequestHeader() != null) {
                            warc.writeRequestRecord(url, responseUuid, instant, httrackRecord.getRequestHeader());
                        }

                        if (httrackRecord != null && httrackRecord.getReferrer() != null) {
                            warc.writeMetadataRecord(url, responseUuid, instant, httrackRecord.getReferrer());
                        }

                    }
                    return FileVisitResult.CONTINUE;
                }


            });
            warc.finish();
        }

    }

    private String formatWarcInfo(String pi, String dateString, HtsCache htsCache) {
        StringBuilder info = new StringBuilder();
        info.append("isPartOf: http://nla.gov.au/nla.arc-").append(pi).append("-").append(dateString).append("\r\n");

        if (htsCache.getHttrackVersion() != null) {
            info.append("software: HTTrack/").append(htsCache.getHttrackVersion()).append(" http://www.httrack.com/\r\n");
        }

        if (htsCache.getHttrackOptions() != null) {
            info.append("httrackOptions: ").append(htsCache.getHttrackOptions()).append("\r\n");
        }

        return info.toString();
    }

    public static void main(String args[]) throws IOException {
        Path instanceDir;
        Path preserveDir;
        Path destDir;
        if (args.length == 2) {
            instanceDir = Paths.get(args[0]);
            preserveDir = null;
            destDir = Paths.get(args[1]);
        } else if (args.length == 3) {
            instanceDir = Paths.get(args[0]);
            preserveDir = Paths.get(args[1]);
            destDir = Paths.get(args[2]);
        } else {
            System.out.println("Usage: pandora2warc instanceDir [preserveDir] destDir");
            System.exit(1);
            return;
        }
        new Pandora2Warc().convertInstance(instanceDir, preserveDir, destDir);
    }
    */
}
