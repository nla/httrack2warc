package au.gov.nla.httrack2warc.httrack;

import au.gov.nla.httrack2warc.ParsingException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HtsDoitParser {
    private static final Pattern DOIT_CMDLINE_RE = Pattern.compile(".*-O ?(?:\"([^\"]*)\"|([^ ]*)) .*");
    private static final Pattern DOIT_TS_RE = Pattern.compile("File generated automatically on (.*), do NOT edit");
    private static final DateTimeFormatter HTS_LOCAL_DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss", Locale.US);

    String outputDir;
    LocalDateTime crawlStartTime;
    String commandLine;

    HtsDoitParser(BufferedReader doitReader) throws IOException {
        commandLine = doitReader.readLine();
        if (commandLine == null) {
            throw new ParsingException("doit.log is empty");
        }
        Matcher m = DOIT_CMDLINE_RE.matcher(commandLine);
        if (m.matches()) {
            outputDir = m.group(1);
            if (outputDir == null) {
                outputDir = m.group(2);
            }
            if (!outputDir.endsWith("/")) {
                outputDir += "/";
            }
        } else {
            outputDir = "";
        }

        String timestampLine = doitReader.readLine();
        Matcher m2 = DOIT_TS_RE.matcher(timestampLine);
        if (!m2.matches()) {
            throw new ParsingException("doit.log invalid, missing 'File generated' line " + timestampLine);
        }
        crawlStartTime = LocalDateTime.parse(m2.group(1), HTS_LOCAL_DATE);
    }

    HtsDoitParser(InputStream doitStream) throws IOException {
        this(new BufferedReader(new InputStreamReader(doitStream, StandardCharsets.ISO_8859_1)));
    }

}
