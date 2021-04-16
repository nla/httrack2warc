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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttrackRecord {
    private static final Logger log = LoggerFactory.getLogger(HttrackRecord.class);

    private String filename;
    final LocalDateTime timestamp;
    final String url;
    final String mime;
    final String requestHeader;
    final String responseHeader;
    private final String referrer;
    private final CacheEntry cacheEntry;
    private Path path;
    private final int status;

    public HttrackRecord(String filename, LocalDateTime timestamp, String url, String mime, String requestHeader,
                         String responseHeader, String referrer, Path path, CacheEntry cacheEntry, int status) {
        this.filename = filename;
        this.timestamp = timestamp;
        this.url = url;
        this.mime = mime;
        this.requestHeader = requestHeader;
        this.responseHeader = responseHeader;
        this.referrer = referrer;
        this.path = path;
        this.cacheEntry = cacheEntry;
        this.status = status;
        fixupDelayedPath();
    }

    public String getFilename() {
        return filename;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUrl() {
        return url;
    }

    public String getMime() {
        return mime;
    }

    public String getRequestHeader() {
        return requestHeader;
    }

    public String getResponseHeader() {
        return responseHeader;
    }

    public String getReferrer() {
        return referrer;
    }

    public InputStream openStream() throws IOException {
        if (hasCacheData()) {
            return cacheEntry.openStream();
        } else if (path != null && Files.exists(path)) {
            return Files.newInputStream(path);
        } else {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    public long getSize() throws IOException {
        if (hasCacheData()) {
            return cacheEntry.getSize();
        } else if (path != null && Files.exists(path)) {
            return Files.size(path);
        } else {
            return 0;
        }
    }

    public boolean exists() {
        return hasCacheData() || path != null && Files.exists(path);
    }

    private static Pattern RE_DELAYED = Pattern.compile("\\.([a-z0-9]+)\\.delayed$");

    /**
     * Httrack sometimes logs 404 errors with a filename ending in .delayed. The actual file seems to be present as
     * a .html though so use that instead if present.
     */
    private void fixupDelayedPath() {
        if (path == null || hasCacheData() || !path.toString().endsWith(".delayed") || Files.exists(path)) return;
        Matcher m = RE_DELAYED.matcher(path.toString());
        if (!m.find()) return;
        String hash = m.group(1);
        if (hash.length() > 4) {
            hash = hash.substring(hash.length() - 4);
        }
        String extension = mime.startsWith("text/html") ? ".html" : url.replaceFirst(".*\\.", ".");
        Path fixedPath = Paths.get(m.replaceFirst(hash + extension));
        if (!Files.exists(fixedPath)) { // try the bare version too
            fixedPath = Paths.get(m.replaceFirst(extension));
        }
        if (!Files.exists(fixedPath)) {
            return;
        }
        String fixedFilename = Paths.get(filename).getParent().resolve(fixedPath.getFileName()).toString();
        log.debug("Fixed path {} to {}", path, fixedPath);
        log.debug("Fixed filename {} to {}", path, fixedFilename);
        path = fixedPath;
        filename = fixedFilename;
    }

    public int getStatus() {
        return status;
    }

    public boolean hasCacheData() {
        return cacheEntry != null && cacheEntry.hasData();
    }

    public boolean isRedirect() {
        return getStatus() >= 300 && getStatus() <= 399;
    }
}
