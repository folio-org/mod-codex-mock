## 2.0.0 2018-01-09
* MODCXMOCK-14: Switch to providing the codex interface version 3.0, with a
  resultInfo, instead of the old resultCount.

## 1.0.3 2018-01-04

* MODCXMOCK-13. Hard-code all records to return "kb" in their "source" field.
  Also, add a command-line option `-Dsource=xxxx` to override the source on
  all returned records - unfortunately only after the search is done.

## 1.0.2 2018-01-04

* MODCXMOCK-12. Accept proper codex queries. Queries of `resourceType` get
  mapped to `type`, and there is special handling of isbn and issn queries,
  mapping `identifier /type=isbn = 123` into something like `(identifier=isbn
  and identifier="123"*)`. These mappings are quite crude, but seem to be
  sufficient for the mock module.

## 1.0.1 2017-12-18

* MODCXMOCK-10 Fix convert.pl to produce data according to the latest schema,
  and run.sh to use the newly introduced ModuleDescriptor-standalone.json.

## 1.0.0 2017-12-15

* MODCXMOCK-9 Update to RAML as of December 15, 2017. Test data updated a bit
  an codex interface is version 2.0.

## 0.1.2 2017-12-08

* More test data, naively converted from real MarcXml records
* When running in mockXXXX mode, overwrite the source with mockXXX

## 0.1.1 2017-12-01

* Different source fields in the test data records. Needed for UI development.

## 0.1.0 2017-12-01

* Uses RMB. Preloads six trivial records into the database at tenant init. These
can be queried with the usual CQL, and sorted etc. Possible to run multiple
instances in the same installation, and they return overlapping subsets of
the records.

## 0.0.1 2017-11-24

* Initial raw release, with the bare minimum of functionality. Two hard-coded
records that can be listed, or fetched by their id.
