# mod-codex-mock

Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Codex mock module - for testing and development

This module provides a very minimal implementation of the codex interface. It
has a small number of hard coded records, that it can list, and provide by the
id.

The module loads a hard-coded list of instances into its database at tenant-init
time. These can be queried and sorted according to the usual RMB conventions.

By default the ModuleDescriptor has the `"interfaceType"` set to `"multiple"`,
so it will be loaded in a multiple mode. This means you have to use an extra
header to make calls to it.

There is a script `run.sh` that loads the module into Okapi, enables it, and
shows what it can do. It isn't all that much, but it shows that something works.
This script uses the ModuleDescriptor-standalone.json to load the module, so
that it can run in stand-alone, without the `"interfaceType": "multiple"`.

### Test data
There is a shell script called `makedata.sh` under the scripts directory. It
takes two marcXML files and converts them into format that can be used as test
data for the module. The records get various 'source' fields, namely "local",
"kb", or "mock", as is needed by the UI development. Their IDs are in the 7777
series.

### Multiple instances
It is possible to enable the module several times for a tenant, by using the
ModuleDescriptors -one and -two that are provided. These have the `InterfaceType`
set to `multiple`. The corresponding DeploymentDescriptors have a `-Dmock=1111`
or `-dmock=2222` on their command line. This causes the module to filter out only
records that contain that string as a part of their ids.  The test data contains
records with one, the other, or both of these strings.

This facility is intended for testing and developing the mod-codex-mux module,
which needs to merge data from multiple sources.

There is a script `run-multi.sh` that demonstrates how this works.

## Additional information

### Other documentation

Other [modules](http://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](http://dev.folio.org/)

### Issue tracker

See project [MODCXMOCK](https://issues.folio.org/browse/MODCXMOCK)
at the [FOLIO issue tracker](http://dev.folio.org/community/guide-issues).

