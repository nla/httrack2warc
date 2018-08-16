package au.gov.nla.httrack2warc;

import au.gov.nla.httrack2warc.httrack.HtsUtil;
import au.gov.nla.httrack2warc.httrack.HttrackCrawl;
import net.htmlparser.jericho.*;
import org.netpreserve.urlcanon.ByteString;
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
            String httrackUrl = "file:///" + record.getFilename();
            urlMap.put(httrackUrl, record.getUrl());
        });
    }

    public static void main(String[] args) throws IOException {
        HttrackCrawl crawl = new HttrackCrawl(Paths.get("/tmp/p/22209/20011019"));
        File file = new File("/tmp/a/22209/20011019/www.ronboswell.com/main.html");

        LinkRewriter rewriter = new LinkRewriter(crawl);
        rewriter.rewrite(new FileInputStream(file), "www.ronboswell.com/main.html", System.out);
    }

    void rewrite(InputStream stream, String filename, OutputStream out) throws IOException {
        URI baseUrl = URI.create("file:///" + filename);
        Source source = new Source(stream);
        OutputDocument outputDocument = new OutputDocument(source);

        for (StartTag tag : source.getAllStartTags()) {
            for (Attribute attr : tag.getURIAttributes()) {
                if (!attr.hasValue()) continue;
                String value = attr.getValue();

                value = value.replace("<br>", "");

                URI url = baseUrl.resolve(value);

                String fragment = url.getRawFragment();

                ParsedUrl parsed = ParsedUrl.parseUrl(url.toString());
                Canonicalizer.WHATWG.canonicalize(parsed);
                parsed.setQuery(ByteString.EMPTY);
                parsed.setQuestionMark(ByteString.EMPTY);
                parsed.setHashSign(ByteString.EMPTY);
                parsed.setFragment(ByteString.EMPTY);

                String original;
                if (parsed.toString().equals("file:///external.html") && url.getRawQuery() != null && url.getRawQuery().startsWith("link=")) {
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

        outputDocument.writeTo(new OutputStreamWriter(out, source.getEncoding()));
    }

}
