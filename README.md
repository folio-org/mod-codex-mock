# mod-codex-mock
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



