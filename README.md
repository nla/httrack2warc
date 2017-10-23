# httrack2warc
Converts HTTrack crawls to WARC files

Status: Not fully functional or tested yet.

## Usage

```
Convert HTTrack web crawls to WARC files

Usage:
  httrack2warc [OPTIONS...] -o outdir crawldir

Options:
  -h, --help                   Show this screen.
  -o, --outdir DIR             Directory to write output (default: current working directory).
  -s, --size BYTES             WARC size target (default: 1GB).
  -n, --name PATTERN           WARC name pattern (default: crawl-%d.warc.gz).
  -Z, --timezone ZONEID        Timezone of HTTrack logs (default: ?).
  -I, --warcinfo 'KEY: VALUE'  Add extra lines to warcinfo record.
  -C, --compression none|gzip  Type of compression to use (default: gzip).
```

## Compiling

To be written.

## License

Copyright (C) 2017 National Library of Australia

Licensed under the [Apache License, Version 2.0](LICENSE).

## Similar Projects

* [HTTrack2Arc](https://github.com/arquivo/httrack2arc)
* [Netarchive Suite migrations](https://sbforge.org/sonar/drilldown/measures/1?metric=lines&rids%5B%5D=16)
* [warc-tools httrack2warc.sh](https://code.google.com/archive/p/warc-tools/source/default/source?page=6)