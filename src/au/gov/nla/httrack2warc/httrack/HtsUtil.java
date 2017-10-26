package au.gov.nla.httrack2warc.httrack;

import org.netpreserve.urlcanon.ByteString;
import org.netpreserve.urlcanon.Canonicalizer;
import org.netpreserve.urlcanon.ParsedUrl;

class HtsUtil {
    static String fixupUrl(String raw) {
        ParsedUrl url = ParsedUrl.parseUrl(raw);
        Canonicalizer.WHATWG.canonicalize(url);

        // early versions of httrack wrote the URL without a scheme
        if (url.getScheme().isEmpty()) {
            url.setScheme(new ByteString("http"));
            url.setColonAfterScheme(new ByteString(":"));
            url.setSlashes(new ByteString("//"));
        }

        // httrack incorrectly makes requests including the fragment. Should we fix clear them?
        //url.setHashSign(ByteString.EMPTY);
        //url.setFragment(ByteString.EMPTY);

        return url.toString();
    }
}
