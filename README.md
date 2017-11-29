# mod-codex-mock

Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Codex mock module - for testing and development

This module provides a very minimal implementation of the codex interface. It
has a small number of hard coded records, that it can list, and provide by the
id.

At the moment there is no query processing, no filtering, no paging, and no
sorting. Some of these may be implemented soon.

There is a script `run.sh` that loads the module into Okapi, enables it, and
shows what it can do. It isn't all that much, but it shows that something works.

The module makes no use of any database, and does not provide any tenant init
interface to initialize anything. It does not depend on any other modules.

## Additional information

### Other documentation

Other [modules](http://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](http://dev.folio.org/)

### Issue tracker

See project [MODCXMOCK](https://issues.folio.org/browse/MODCXMOCK)
at the [FOLIO issue tracker](http://dev.folio.org/community/guide-issues).

