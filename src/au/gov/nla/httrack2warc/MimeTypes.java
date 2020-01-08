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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MimeTypes {
    private final Map<String, String> typeForExtensionMap;

    public MimeTypes() {
        try (InputStream stream = getClass().getResourceAsStream("mime.types")) {
            this.typeForExtensionMap = parse(stream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to mime.types from classpath", e);
        }
    }

    public MimeTypes(InputStream stream) throws IOException {
        typeForExtensionMap = parse(stream);
    }

    public String forExtension(String extension) {
        return typeForExtensionMap.get(extension);
    }

    public String forPath(Path path) {
        String filename = path.getFileName().toString();
        return forFilename(filename);
    }


    public String forFilename(String filename) {
        if (filename == null) return null;
        int i = filename.lastIndexOf(".");
        if (i < 0) {
            return null;
        }
        return forExtension(filename.substring(i + 1));
    }

    private static Map<String,String> parse(InputStream stream) throws IOException {
        return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    private static Map<String,String> parse(BufferedReader reader) throws IOException {

        Map<String,String> map = new HashMap<>();

        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            line = line.replaceFirst("#.*", "");
            String[] fields = line.split("\\s+");

            for (int i = 1; i < fields.length; i++) {
                map.put(fields[i], fields[0]);
            }
        }

        return map;
    }
}
