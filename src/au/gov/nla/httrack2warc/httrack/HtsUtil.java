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
