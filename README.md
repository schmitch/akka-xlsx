# Alpakka XLSX Reader

**NOT FOR PRODUCTION USE, YET**

Currently this project provides a yet incomplete way of reading Excel files.
It will **never** support evaluating Formulas, since that would mean that all Rows need to be
in memory. However you can save the Formulas inside a database and evaluate them within your datastore.

Things to do:

* [ ] Correctly handle Blank Cells inside 
      i.e. `<row r="3" spans="1:3" x14ac:dyDescent="0.2"><c r="A3" s="1"><v>43435</v></c><c r="C3" t="s"><v>3</v></c></row>`
      should be represented as TreeMap(0 -> Cell.Number, 1 -> Cell.Blank, 2 -> Cell.String) 
* [ ] Maybe: MathML support
* [ ] Maybe if possible: ZipInputStream Source (currently it should be possible to read a sst and sheet from a stream)

