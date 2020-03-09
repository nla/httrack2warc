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

package au.gov.nla.httrack2warc.httrack;

import org.netpreserve.urlcanon.Canonicalizer;
import org.netpreserve.urlcanon.ParsedUrl;

import java.util.regex.Pattern;

public class HtsUtil {
    private static Pattern PROTOCOL = Pattern.compile("^https?://");

    public static String fixupUrl(String raw) {
        ParsedUrl url = ParsedUrl.parseUrl(raw);

        // early versions of httrack wrote the URL without a scheme
        if (url.getScheme().isEmpty()) {
            url = ParsedUrl.parseUrl("http://" + raw);
        }

        Canonicalizer.WHATWG.canonicalize(url);

        // httrack incorrectly makes requests including the fragment. Should we fix clear them?
        //url.setHashSign(ByteString.EMPTY);
        //url.setFragment(ByteString.EMPTY);

        return url.toString();
    }

    public static String stripProtocol(String url) {
        return PROTOCOL.matcher(url).replaceFirst("");
    }
}
