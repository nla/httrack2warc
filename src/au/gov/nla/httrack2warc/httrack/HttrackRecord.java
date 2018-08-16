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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.io.InputStream;

public class HttrackRecord {
    private final String filename;
    final LocalDateTime timestamp;
    final String url;
    final String mime;
    final String requestHeader;
    final String responseHeader;
    private final String referrer;
    private final CacheEntry cacheEntry;
    private final Path path;
    private final int status;

    HttrackRecord(String filename, LocalDateTime timestamp, String url, String mime, String requestHeader,
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
        } else {
            return Files.newInputStream(path);
        }
    }

    public long getSize() throws IOException {
        if (hasCacheData()) {
            return cacheEntry.getSize();
        } else {
            return Files.size(path);
        }
    }

    public boolean exists() throws IOException {
        return hasCacheData() || Files.exists(path);
    }

    public int getStatus() {
        return status;
    }

    public boolean hasCacheData() {
        return cacheEntry != null && cacheEntry.hasData();
    }
}
