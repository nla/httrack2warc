package au.gov.nla.httrack2warc;

import org.netpreserve.urlcanon.ByteString;
import org.netpreserve.urlcanon.Canonicalizer;
import org.netpreserve.urlcanon.ParsedUrl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses HTTrack hts-cache/new.txt files.
 */
public class HtsTxtParser implements Closeable {
    private static final Pattern RE = Pattern.compile("^(?<time>\\d\\d:\\d\\d:\\d\\d)\\t" +
            "(?<size>-?\\d+)/(?<remotesize>-?\\d+)\\t" +
            "(?<flags>[A-Z-]{6})\\t" +
            "(?<statuscode>\\d+)\\t" +
            "(?<status>\\w+)[ ]\\('(?<servermsg>[^']*)'\\)\\t" +
            "(?<mime>\\S+)\\t" +
            "(?<etag>\\S+)\\t" +
            "(?<url>.+)\\t" +
            "(?<localfile>[^\\t]*)\\t" +
            "\\(from[ ](?<via>.*)\\)$");
    private final BufferedReader reader;
    private final Matcher matcher = RE.matcher("");

    HtsTxtParser(BufferedReader reader) throws IOException {
        this.reader = reader;
        String header = reader.readLine();
        if (header == null) {
            throw new ParsingException("empty file");
        } else if (!header.equals("date\tsize'/'remotesize\tflags(request:Update,Range state:File response:Modified,Chunked,gZipped)\tstatuscode\tstatus ('servermsg')\tMIME\tEtag|Date\tURL\tlocalfile\t(from URL)")) {
            throw new ParsingException("invalid header line");
        }
    }

    HtsTxtParser(InputStream txtReader) throws IOException {
        this(new BufferedReader(new InputStreamReader(txtReader, StandardCharsets.ISO_8859_1)));
    }

    boolean readRecord() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return false;
        }
        matcher.reset(line);
        if (!matcher.matches()) {
            throw new ParsingException("invalid record: " + line);
        }
        return true;
    }

    LocalTime time() {
        return LocalTime.parse(matcher.group("time"));
    }

    String url() {
        return fixupUrl(matcher.group("url"));
    }

    private String fixupUrl(String raw) {
        ParsedUrl url = ParsedUrl.parseUrl(raw);
        Canonicalizer.WHATWG.canonicalize(url);

        // early versions of httrack wrote the URL without a scheme
        if (url.getScheme().isEmpty()) {
            url.setScheme(new ByteString("http"));
            url.setColonAfterScheme(new ByteString(":"));
            url.setSlashes(new ByteString("//"));
        }
        return url.toString();
    }

    String via() {
        String raw = matcher.group("via");
        if (raw.isEmpty()) {
            return null;
        }
        return fixupUrl(raw);
    }

    String mime() {
        return matcher.group("mime");
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public String localfile() {
        return matcher.group("localfile");
    }
}
