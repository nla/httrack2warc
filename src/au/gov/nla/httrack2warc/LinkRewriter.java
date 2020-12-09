package au.gov.nla.httrack2warc;

import au.gov.nla.httrack2warc.httrack.HtsUtil;
import au.gov.nla.httrack2warc.httrack.HttrackCrawl;
import net.htmlparser.jericho.*;
import org.netpreserve.urlcanon.Canonicalizer;
import org.netpreserve.urlcanon.ParsedUrl;

import java.io.*;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LinkRewriter {
    private Map<String, String> urlMap = new HashMap<>();

    LinkRewriter(HttrackCrawl crawl) throws IOException {
        crawl.forEach(record -> {
            ParsedUrl httrackUrl = ParsedUrl.parseUrl("http://httrack/" + record.getFilename());
            Canonicalizer.SEMANTIC.canonicalize(httrackUrl);
            urlMap.put(httrackUrl.toString(), record.getUrl());
        });
    }

    public static void main(String[] args) throws IOException {
        HttrackCrawl crawl = new HttrackCrawl(Paths.get("/tmp/p/22209/20011019"));
        File file = new File("/tmp/a/22209/20011019/www.ronboswell.com/main.html");

        LinkRewriter rewriter = new LinkRewriter(crawl);
        rewriter.rewrite(new FileInputStream(file), "www.ronboswell.com/main.html", System.out);
    }

    void rewrite(InputStream stream, String filename, OutputStream out) throws IOException {
        URI baseUrl = URI.create("http://httrack/" + filename);
        Source source = new Source(stream);
        OutputDocument outputDocument = new OutputDocument(source);

        for (StartTag tag : source.getAllStartTags()) {
            for (Attribute attr : tag.getURIAttributes()) {
                if (!attr.hasValue()) continue;
                String value = attr.getValue();

                URI url;
                try {
                    url = baseUrl.resolve(value);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                String fragment = url.getRawFragment();

                ParsedUrl parsed = ParsedUrl.parseUrl(url.toString());
                Canonicalizer.SEMANTIC.canonicalize(parsed);
                parsed.setQuery("");
                parsed.setQuestionMark("");

                String original;
                if (parsed.toString().equals("http://httrack/external.html") && url.getRawQuery() != null && url.getRawQuery().startsWith("link=")) {
                    original = HtsUtil.fixupUrl(url.getRawQuery().substring("link=".length()));
                } else {
                    original = urlMap.get(parsed.toString());
                }

                if (original == null) {
                    continue;
                }

                if (fragment != null) {
                    original += "#" + fragment;
                }

                String replacement = "\"" + CharacterReference.encode(original, true) + "\"";
                outputDocument.replace(attr.getValueSegmentIncludingQuotes(), replacement);
            }
        }

        String encoding = source.getEncoding();
        if (encoding == null) encoding = "iso-8859-1"; // seems to be what jericho defaults to for reading
        outputDocument.writeTo(new OutputStreamWriter(out, encoding));
    }

}
