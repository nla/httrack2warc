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

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
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
            channel = Files.newByteChannel(currentFilePath, CREATE, WRITE, TRUNCATE_EXISTING);
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
