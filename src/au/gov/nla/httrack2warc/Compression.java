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
