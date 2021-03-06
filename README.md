# httrack2warc

Converts HTTrack crawls to WARC files.

Status: Working on many crawls but needs more testing on corner cases. We're not using it in production yet.

This tool works by reading the HTTrack cache directory (hts-cache) and any available log files to reconstruct an
approximation of the original requests and responses. This process is not perfect as not all the necessary information
is always available. Some of the information that is available is only present in debug log messages that were never
intended for machine consumption. Please see the list of known issues and limitations below.

## Usage

Download the [latest release jar](https://github.com/nla/httrack2warc/releases)
and run it under Java 8 or later.

```
Usage:
  httrack2warc [OPTIONS...] -o outdir crawldir

Options:
  --cdx FILENAME               Write a CDX index file for the generated WARCs.
  -C, --compression none|gzip  Type of compression to use (default: gzip).
  -x, --exclude REGEX          Exclude URLs matching a regular expression.
  -h, --help                   Show this screen.
  -n, --name PATTERN           WARC name pattern (default: crawl-%d.warc.gz).
  -o, --outdir DIR             Directory to write output (default: current working directory).
  -q, --quiet                  Decrease logging verbosity.
  --redirect-file PATTERN      Direct synthetic redirects to a separate set of WARC files.
  --redirect-prefix URLPREFIX  Generates synthetic redirects from HTTrack-rewritten URLs to original URLs.
  --rewrite-links              When the unmodified HTML is unavailable attempt to rewrite links to undo HTTrack's URL mangling. (experimental)
  -s, --size BYTES             WARC size target (default: 1GB).
  --strict                     Abort on issues normally considered a warning.
  -Z, --timezone ZONEID        Timezone of HTTrack logs (default: Australia/Sydney).
  -I, --warcinfo 'KEY: VALUE'  Add extra lines to warcinfo record.
  -v, --verbose                Increase logging verbosity.
```

### Example

Conduct a crawl into a temporary directory (/tmp/crawl) using HTTrack:

    $ httrack -O /tmp/crawl http://www.example.org/
    Mirror launched on Mon, 08 Jan 2018 13:50:40 by HTTrack Website Copier/3.49-2 [XR&CO'2014]
    mirroring http://www.example.org/ with the wizard help..
    Done.www.example.org/ (1270 bytes) - OK
    Thanks for using HTTrack!

Run httrack2warc over the output to produce a WARC file. By default the output file will be named `crawl-0.warc.gz`.

    $ java -jar httrack2warc-shaded-0.2.0.jar /tmp/crawl
    Httrack2Warc - www.example.org/index.html -> http://www.example.org/

Replay the ingested WARC files using a replay tool like [pywb](https://github.com/ikreymer/pywb):

    $ pip install --user pywb
    $ PATH="$PATH:$HOME/.local/bin"
    $ wb-manager init test
    $ wb-manager add test crawl-*.warc.gz
    [INFO]: Copied crawl-0.warc.gz to collections/test/archive
    $ wayback
    [INFO]: Starting pywb Wayback Web Archive Replay on port 8080
    # Open in browser: http://localhost:8080/test/*/example.org/

### Synthetic redirects

When migrating from a HTTrack-based archive to a WARC-based one you may have the problem of breaking existing links
which used the HTTrack manipulated filenames. To assist with this httrack2warc can synthesize redirects records from a
HTTrack path to the reconstructed original live URL.

For example suppose you have the following situation:
  
    Original URL: http://example.com/index.php?id=16
    HTTrack URL: http://httrack/arc/2016/example.com/indexd455f.html

Then setting this option:

    --redirect-prefix http://httrack/arc/2016/

Will generate a redirect like:

    http://httrack/arc/2016/example.com/indexd455f.html -> http://example.com/index.php?id=16
    
You can then put a webserver rule on http://httrack/ that simply redirects all requests into your new WARC-based
archive.

You can configure synthentic redirects to be written to a separate set of WARC files using this option:

    --redirect-file crawl-redirects-%d.warc.gz

## Known issues and limitations

### HTTP headers

By default HTTrack does not record HTTP headers. If the --debug-headers option is specified however the file
hts-ioinfo.txt will be produced containing a log of the request and response headers.

When headers are available httrack2warc produces WARC records of type request and response. When headers are unavailable
only WARC resource records are produced.

The `Transfer-Encoding` header is always stripped as the encoded bytes of the message are not recorded by HTTrack.

### Redirects and error codes

Currently without hts-ioinfo.txt and an entry in the cache zip (newer versions of HTTrack), non-200 status code 
responses are converted to resource records and the status code is lost. See issue #3. 

### IP addresses and DNS records

HTTrack does not record DNS records or the IP addresses of hostnames therefore httrack2warc cannot produce
WARC-IP-Address or DNS records.

### HTTrack version compatiblity

Some testing has been done against crawls generated by the following versions: 3.01, 3.21-4, 3.49-2. Not all combinations
of options have been tested.

### Link rewriting

For cases when the original HTML is unavailable there is an experimental ``--rewrite-links`` option which will modify
the HTML changing links from filenames to absolute URLs. This feature somewhat primitive and does not currently 
attempt to rewrite URLs inside CSS or JavaScript.

## Compilation

Install Java JDK 8 (or later) and [Maven](https://maven.apache.org/).  On Fedora Linux:

    dnf install java-1.8.0-openjdk-devel maven

Then compile using Maven from the top-level of this repository:

     cd httrack2warc
     mvn package

This will produce an executable jar file which you can run like so:

    java -jar target/httrack2warc-*-shaded.jar --help

## License

Copyright (C) 2017-2020 National Library of Australia

Licensed under the [Apache License, Version 2.0](LICENSE).

## Similar Projects

* [HTTrack2Arc](https://github.com/arquivo/httrack2arc)
* [Netarchive Suite migrations](https://sbforge.org/sonar/drilldown/measures/1?metric=lines&rids%5B%5D=16)
* [warc-tools httrack2warc.sh](https://code.google.com/archive/p/warc-tools/source/default/source?page=6)
