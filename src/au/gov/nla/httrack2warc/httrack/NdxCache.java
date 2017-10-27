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

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

class NdxCache implements Cache {

    private final Map<String,Long> entries = new HashMap<>();
    private final Path datFile;

    NdxCache(Path dir) throws IOException {
        datFile = dir.resolve("hts-cache/new.dat");
        try (DataInputStream stream = new DataInputStream(new BufferedInputStream(Files.newInputStream(dir.resolve("hts-cache/new.ndx"))))) {
            String version = readString(stream);
            if (!version.startsWith("CACHE-1.")) {
                throw new IOException("Unsupported cache version: " + version);
            }

            String lastModified = readString(stream);

            for (;;) {
                String hostAndPath = readString(stream);
                if (hostAndPath == null) break;
                long position = Long.parseLong(stream.readLine());
                int i = hostAndPath.indexOf('\n');
                String host = hostAndPath.substring(0, i);
                String path = hostAndPath.substring(i + 1, hostAndPath.length() - 1);
                String url = host + path;
                entries.put(url, position);
            }
        }
    }

    /**
     * Reads a string of the form: length '\n' string
     */
    String readString(DataInputStream stream) throws IOException {
        String line = stream.readLine();
        if (line == null) return null;

        int len = Integer.parseInt(line);
        byte[] buffer = new byte[len];
        stream.readFully(buffer);
        return new String(buffer, StandardCharsets.ISO_8859_1);
    }

    @Override
    public CacheEntry getEntry(String url) {
        Long position = entries.get(url);
        if (position == null) return null;
        return new Entry(position);
    }

    private class Entry implements CacheEntry {
        boolean parsed = false;
        private final long position;
        private long dataLen;
        private long headerLen;

        public Entry(long position) {
            this.position = position;
        }

        @Override
        public long getSize() throws IOException {
            parseDatHeader();
            return dataLen;
        }

        private void parseDatHeader() throws IOException {
            if (parsed) return;
            try (SeekableByteChannel channel = Files.newByteChannel(datFile)) {
                channel.position(Math.abs(position));
                CountingStream countingStream = new CountingStream(new BufferedInputStream(Channels.newInputStream(channel)), Long.MAX_VALUE);
                DataInputStream stream = new DataInputStream(countingStream);
                String status = readString(stream);
                String size = readString(stream);
                String msg = readString(stream);
                String contentType = readString(stream);
                String lastModified = readString(stream);
                String etag = readString(stream);

                for (;;) {
                    String line = readString(stream);
                    if (line.equals("HTS")) break;
                    if (line.equals("SD")) {
                        readString(stream); // supplementary data
                    }
                }

                dataLen = Long.parseLong(readString(stream));
                headerLen = countingStream.count;
                parsed = true;
            }
        }

        @Override
        public InputStream openStream() throws IOException {
            parseDatHeader();
            SeekableByteChannel channel = Files.newByteChannel(datFile);
            channel.position(Math.abs(position) + headerLen);
            return new CountingStream(Channels.newInputStream(channel), dataLen);
        }

        @Override
        public boolean hasData() {
            return position >= 0;
        }
    }
    @Override
    public void close() throws IOException {

    }

    static class CountingStream extends FilterInputStream {
        long count = 0;
        long limit;

        protected CountingStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (count >= limit) return -1;
            int n = in.read();
            if (n >= 0) {
                count++;
            }
            return n;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (count + len > limit) {
                len = (int)(limit - count);
                if (len == 0) {
                    return -1;
                }
            }
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }

        @Override
        public long skip(long len) throws IOException {
            if (count + len > limit) {
                len = (int)(limit - count);
            }
            long skipped = super.skip(len);
            if (skipped > 0) {
                count += skipped;
            }
            return skipped;
        }
    }
}
