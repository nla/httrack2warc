package au.gov.nla.httrack2warc.httrack;

import au.gov.nla.httrack2warc.ParsingException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class HtsCache {

    private final Map<String, HtsCacheEntry> entriesByPath;
    private final Map<String, HtsCacheEntry> entriesByUrl;
    private final String httrackVersion;
    private final LocalDateTime launchTime;
    private final String httrackOptions;

    HtsCache(InputStream logStream,
             InputStream doitStream,
             InputStream txtStream,
             InputStream ioinfoStream) throws IOException {

        // parse hts-log.txt
        HtsLogParser htsLog = new HtsLogParser(logStream);
        httrackVersion = htsLog.version;

        // parse doit.log
        HtsDoitParser doitLog = new HtsDoitParser(doitStream);
        launchTime = doitLog.crawlStartTime;
        String outputDir = doitLog.outputDir;
        httrackOptions = doitLog.commandLine;

        // parse new.txt
        entriesByPath = new HashMap<>();
        entriesByUrl = new HashMap<>();
        try (HtsTxtParser parser = new HtsTxtParser(txtStream)) {
            while (parser.readRecord()) {
                String localfile = parser.localfile();
                if (localfile.isEmpty()) {
                    continue; // skip 404 errors
                }

                LocalDateTime timestamp = parser.time().atDate(launchTime.toLocalDate());
                if (timestamp.isBefore(launchTime)) {
                    // try to handle crawls the cross over to the next day
                    // XXX: but we can't really handle those longer than 24 hours
                    timestamp = timestamp.plusDays(1);
                }

                if (!localfile.startsWith(outputDir)) {
                    throw new ParsingException("new.txt localfile (" + localfile + ") outside output dir (" + outputDir + ")");
                }
                localfile = localfile.substring(outputDir.length());
                HtsCacheEntry entry = entriesByPath.computeIfAbsent(localfile, HtsCacheEntry::new);
                entry.timestamp = timestamp;
                entry.mime = parser.mime();
                entry.url = parser.url();
                entry.via = parser.referrer();
                entriesByUrl.put(entry.url, entry);
            }
        }

        // parse hts-ioinfo.txt
        try (HtsIoinfoParser ioinfo = new HtsIoinfoParser(ioinfoStream)) {
            while (ioinfo.parseRecord()) {
                String url = HtsUtil.fixupUrl(ioinfo.url);
                HtsCacheEntry entry = entriesByUrl.computeIfAbsent(url, HtsCacheEntry::new);
                if (ioinfo.request) {
                    entry.requestHeader = ioinfo.header;
                } else {
                    entry.responseHeader = ioinfo.header;
                }
            }
        }
    }

    private static InputStream tryOpen(InputStream prev, Path path) throws IOException {
        if (prev != null) return prev;
        if (Files.exists(path)) return Files.newInputStream(path);
        return null;
    }

    public static HtsCache load(Path sourceDir, Path altCacheDir) throws IOException {
        InputStream htsLog = null;
        InputStream doit = null;
        InputStream txt = null;
        InputStream ioinfo = null;

        try {
            for (Path dir : new Path[]{sourceDir, altCacheDir}) {
                if (dir == null) continue;

                htsLog = tryOpen(htsLog, dir.resolve("hts-log.txt"));
                doit = tryOpen(doit, dir.resolve("hts-cache").resolve("doit.log"));
                txt = tryOpen(txt, dir.resolve("hts-cache").resolve("new.txt"));
                ioinfo = tryOpen(ioinfo, dir.resolve("hts-ioinfo.txt"));
            }

            return new HtsCache(htsLog, doit, txt, ioinfo);

        } finally {
            if (htsLog != null) htsLog.close();
            if (doit != null) doit.close();
            if (txt != null) txt.close();
            if (ioinfo != null) ioinfo.close();
        }
    }

    public HtsCacheEntry get(String path) {
        return entriesByPath.get(path);
    }

    public String getHttrackVersion() {
        return httrackVersion;
    }
    public LocalDateTime getLaunchTime() {
        return launchTime;
    }

    public String getHttrackOptions() {
        return httrackOptions;
    }
}
