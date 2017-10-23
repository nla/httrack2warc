package au.gov.nla.httrack2warc;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

class RotatingFile implements Closeable {
    final String namePattern;
    final long rotationSize;
    SeekableByteChannel channel;
    int seq = 0;
    Path currentFilePath;

    RotatingFile(String namePattern, long rotationSize) {
        this.namePattern = namePattern;
        this.rotationSize = rotationSize;
    }

    boolean rotateIfNecessary() throws IOException {
        if (channel != null && channel.position() > rotationSize) {
            channel.close();
            channel = null;
        }
        if (channel == null) {
            currentFilePath = Paths.get(String.format(namePattern, seq));
            channel = Files.newByteChannel(currentFilePath, CREATE, WRITE);
            seq += 1;
            return true;
        }
        return false;
    }

    public void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }
}
