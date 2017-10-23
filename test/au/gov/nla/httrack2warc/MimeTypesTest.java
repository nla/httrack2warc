package au.gov.nla.httrack2warc;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class MimeTypesTest {

    @Test
    public void test() throws IOException {
        MimeTypes mimeTypes = new MimeTypes();
        assertEquals("image/jpeg", mimeTypes.forExtension("jpg"));
        assertEquals("image/jpeg", mimeTypes.forExtension(("jpeg")));

        assertEquals("image/jpeg", mimeTypes.forPath(Paths.get("foo.bar.jpg")));
        assertEquals("image/jpeg", mimeTypes.forPath((Paths.get("foo.zip.jpeg"))));
    }
}