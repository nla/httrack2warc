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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Parser for the zip-based cache format used in HTTrack 3.31 and earlier.
 * The format is described at https://www.httrack.com/html/cache.html
 */
class ZipCache implements Cache {
    private final ZipFile zipFile;
    private final Map<String,ZipEntry> entries = new HashMap<>();

    public ZipCache(Path zipPath) throws IOException {
        this.zipFile = new ZipFile(zipPath.toFile());
        Enumeration<? extends ZipEntry> e = zipFile.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = e.nextElement();
            String url = entry.getName();
            url = HtsUtil.fixupUrl(url);
            entries.put(url, entry);
        }
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    @Override
    public CacheEntry getEntry(String url) {
        ZipEntry entry = entries.get(url); // zipFile.getEntry(url);
        return entry == null ? null : new Entry(entry);
    }

    private class Entry implements CacheEntry {
        private final ZipEntry entry;

        Entry(ZipEntry entry) {
            this.entry = Objects.requireNonNull(entry);
        }

        @Override
        public long getSize() {
            return entry.getSize();
        }

        @Override
        public InputStream openStream() throws IOException {
            return zipFile.getInputStream(entry);
        }

        @Override
        public boolean hasData() {
            return getSize() > 0;
        }
    }
}
