package au.gov.nla.httrack2warc.httrack;

import au.gov.nla.httrack2warc.ParsingException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtsLogParser implements Closeable {
    private static final Pattern HEADER_RE = Pattern.compile("HTTrack(?<version>[^ ]+) launched on " +
            "(?<date>\\w+, \\d\\d \\w+ \\d\\d\\d\\d \\d\\d:\\d\\d:\\d\\d) at " +
            "(?<seedsAndFilters>.*)");

    private final BufferedReader reader;
    String version;
    String launchDate;
    String seedsAndFilters;

    HtsLogParser(BufferedReader reader) throws IOException {
        this.reader = reader;
        readHeader();
    }

    HtsLogParser(InputStream stream) throws IOException {
        this(new BufferedReader(new InputStreamReader(stream, StandardCharsets.ISO_8859_1)));
    }

    private void readHeader() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new ParsingException("missing header line");
        }
        Matcher matcher = HEADER_RE.matcher(line);
        if (!matcher.matches()) {
            throw new ParsingException("invalid hts-log.txt header: " + line);
        }
        version = matcher.group("version");
        launchDate = matcher.group("date");
        seedsAndFilters = matcher.group("seedsAndFilters");
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
