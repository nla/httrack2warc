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

package au.gov.nla.httrack2warc;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.zip.GZIPOutputStream;

public enum Compression {
    NONE {
        @Override
        public void writeMember(WritableByteChannel channel, StreamWriter streamWriter) throws IOException {
            streamWriter.writeTo(Channels.newOutputStream(channel));
        }
    },

    GZIP {
        @Override
        public void writeMember(WritableByteChannel channel, StreamWriter streamWriter) throws IOException {
            GZIPOutputStream stream = new GZIPOutputStream(Channels.newOutputStream(channel));
            streamWriter.writeTo(stream);
            stream.finish();
        }
    };

    public abstract void writeMember(WritableByteChannel channel, StreamWriter streamWriter) throws IOException;
}
