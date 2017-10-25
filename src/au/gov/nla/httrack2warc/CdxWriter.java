package au.gov.nla.httrack2warc;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;

public class CdxWriter implements Closeable {
    private static final DateTimeFormatter ARC_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US).withZone(UTC);

    private final BufferedWriter writer;
    private final Path tmpCdxPath;
    private final Path cdxPath;
    boolean cdx11Format = true;

    CdxWriter(Path file) throws IOException {
        this.cdxPath = file;
        tmpCdxPath = Paths.get(file.toString() + ".tmp");
        writer = Files.newBufferedWriter(tmpCdxPath, UTF_8);
        writer.write(" CDX N b a m s k r M S V g\n");
    }

    public void finish() throws IOException {
        writer.close();
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


    @Override
    public void close() throws IOException {
        writer.close();
        Files.deleteIfExists(tmpCdxPath);
    }

    void writeLine(String url, String contentType, String digest, Instant date, WarcWriter.RecordPosition recordPosition, Path filename) throws IOException {
        String cdxLine;
        if (cdx11Format) {
            cdxLine = url + " " + ARC_DATE.format(date) + " " + url + " " + contentType + " 200 " +
                    digest + " - - " + recordPosition.length() + " " + recordPosition.start + " " + filename + "\n";
        } else {
            cdxLine = url + " " + ARC_DATE.format(date) + " " + url + " " + contentType + " 200 " +
                    digest + " - " + recordPosition.start + " " + filename + "\n";
        }
        System.out.print(cdxLine);
        writer.write(cdxLine);
    }
}
