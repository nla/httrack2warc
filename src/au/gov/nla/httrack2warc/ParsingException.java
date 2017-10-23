package au.gov.nla.httrack2warc;

import java.io.IOException;

public class ParsingException extends IOException {
    public ParsingException(String msg) {
        super(msg);
    }
}
