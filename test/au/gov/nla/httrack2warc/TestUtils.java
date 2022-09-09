/*
 * Copyright (C) 2017 National Library of Australia
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TestUtils {
    public static void unzip(InputStream stream, Path dest) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(stream)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path path = dest.resolve(entry.getName());
                if (!path.normalize().startsWith(dest.normalize())) {
                    throw new IOException("Bad zip entry");
                }
                if (entry.isDirectory()) {
                    Files.createDirectory(path);
                } else {
                    Files.copy(zip, path);
                }
            }
        }
    }
}
