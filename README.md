# Alpakka XLSX Reader

**NOT FOR PRODUCTION USE, YET**

Currently this project provides a yet incomplete way of reading Excel files.
It will **never** support evaluating Formulas, since that would mean that all Rows need to be
in memory. However you can save the Formulas inside a database and evaluate them within your datastore.

Things to do:

* [ ] Better XLSX file format detection
* [ ] Maybe: MathML support
* [ ] Maybe if possible: ZipInputStream Source (currently it should be possible to read a sst and sheet from a stream)

