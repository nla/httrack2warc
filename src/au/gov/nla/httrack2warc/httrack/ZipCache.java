package au.gov.nla.httrack2warc.httrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ZipCache implements Cache {
    private final ZipFile zipFile;

    public ZipCache(Path zipPath) throws IOException {
        this.zipFile = new ZipFile(zipPath.toFile());
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    @Override
    public CacheEntry getEntry(String url) {
        return new Entry(zipFile.getEntry(url));
    }

    private class Entry implements CacheEntry {
        private final ZipEntry entry;

        Entry(ZipEntry entry) {
            this.entry = entry;
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
