package akka.stream.alpakka.xlsx

import java.io.FileNotFoundException
import java.util.zip.{ ZipEntry, ZipFile }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{ Characters, EndElement, ParseEvent, StartElement }
import akka.stream.scaladsl.{ Sink, Source, StreamConverters }

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try

object XlsxParsing {

  def fromZipFile(file: ZipFile, sheetId: Int)(implicit materializer: Materializer): Source[Row, NotUsed] = {
    fromZipFile(
      file,
      sheetId,
      Sink.fold[Map[Int, String], (Int, String)](Map.empty[Int, String])((v1, v2) => v1 + v2)
    )
  }

  def fromZipFile(file: ZipFile, sheetName: String)(implicit materializer: Materializer): Source[Row, NotUsed] = {
    fromZipFile(
      file,
      sheetName,
      Sink.fold[Map[Int, String], (Int, String)](Map.empty[Int, String])((v1, v2) => v1 + v2)
    )
  }

  def fromZipFile(
      file: ZipFile,
      sheetId: Int,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Source[Row, NotUsed] = {
    read(file, SheetType.Id(sheetId), sstSink)
  }

  def fromZipFile(
      file: ZipFile,
      sheetName: String,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Source[Row, NotUsed] = {
    read(file, SheetType.Name(sheetName), sstSink)
  }

  private def nillable(s: => Any): List[Row] = {
    s
    Nil
  }

  private def buildCell(
      typ: Option[CellType],
      value: Option[java.lang.StringBuilder],
      numFmtId: Option[Int],
      workbook: Workbook,
      ref: CellReference
  ): Cell = {
    // the default cell type is always NUMERIC
    typ.getOrElse(CellType.NUMERIC) match {
      case CellType.BLANK   => Cell.Blank(ref)
      case CellType.INLINE  => Cell.parseInline(value, ref)
      case CellType.STRING  => Cell.parseString(value, workbook.sst, ref)
      case CellType.FORMULA => Cell.parseFormula(value, ref)
      case CellType.BOOLEAN => Cell.parseBoolean(value, ref)
      case CellType.ERROR   => Cell.Error(new Exception("cell type is invalid"), ref)
      case CellType.NUMERIC => Cell.parseNumeric(value, numFmtId.flatMap(id => workbook.styles.get(id)), ref)
    }
  }

  private def getSheetStream(file: ZipFile, typ: SheetType, workbook: Workbook) = {
    val io = typ match {
      case SheetType.Name(name) =>
        workbook.sheets.get(name).flatMap(v => Option(file.getEntry(s"xl/worksheets/sheet$v.xml")))
      case SheetType.Id(id) =>
        Option(file.getEntry(s"xl/worksheets/sheet$id.xml"))
    }
    io match {
      case Some(entry) => file.getInputStream(entry)
      case None        => throw new FileNotFoundException(s"workbook sheet $typ could not be found")
    }
  }

  private def read(
      file: ZipFile,
      typ: SheetType,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer) = {
    val sstSource   = Source.fromFuture(SstStreamer.readSst(file, sstSink))
    val styleSource = Source.fromFuture(StyleStreamer.readStyles(file))

    Source
      .fromFuture(SstStreamer.readSst(file, sstSink))
      .flatMapConcat(sst => Source.fromFuture(StyleStreamer.readStyles(file)).map((sst, _)))
      .flatMapConcat {
        case (sst, styles) =>
          Source.fromFuture(WorkbookStreamer.readWorkbook(file)).map(sheets => Workbook(sst, sheets, styles))
      }
      .flatMapConcat { workbook =>
        StreamConverters
          .fromInputStream(() => getSheetStream(file, typ, workbook))
          .via(XmlParsing.parser)
          .statefulMapConcat[Row](() => {
            var insideRow: Boolean                              = false
            var insideCol: Boolean                              = false
            var insideValue: Boolean                            = false
            var cellType: Option[CellType]                      = None
            var contentBuilder: Option[java.lang.StringBuilder] = None
            var cellList: mutable.TreeMap[Int, Cell]            = mutable.TreeMap.empty
            var rowNum                                          = 1
            var cellNum                                         = 1
            var ref: Option[CellReference]                      = None
            var numFmtId: Option[Int]                           = None

            (data: ParseEvent) =>
              data match {
                case StartElement("row", _, _, _, _) => nillable(insideRow = true)
                case StartElement("c", attrs, _, _, _) if insideRow =>
                  nillable({
                    ref = CellReference.parseRef(attrs)
                    contentBuilder = Some(new java.lang.StringBuilder())
                    numFmtId = attrs.find(_.name == "s").flatMap(a => Try(Integer.parseInt(a.value)).toOption)
                    cellType = attrs.find(_.name == "t").map(a => CellType.parse(a.value))
                    insideCol = true
                  })
                case StartElement("v", _, _, _, _) if insideCol =>
                  nillable({ insideValue = true })
                case Characters(text) if insideValue =>
                  nillable(contentBuilder.foreach(_.append(text)))
                case EndElement("v") if insideValue =>
                  nillable({ insideValue = false })
                case EndElement("c") if insideCol =>
                  nillable({
                    val simpleRef = ref.getOrElse(CellReference("", cellNum, rowNum))
                    val cell      = buildCell(cellType, contentBuilder, numFmtId, workbook, simpleRef)
                    cellList += (simpleRef.colNum -> cell)
                    numFmtId = None
                    ref = None
                    cellNum += 1
                    insideCol = false
                    cellType = None
                    contentBuilder = None
                  })
                case EndElement("row") if insideRow =>
                  val ret = new Row(rowNum, cellList)
                  rowNum += 1
                  cellNum = 1
                  cellList = mutable.TreeMap.empty
                  insideRow = false
                  ret :: Nil
                case _ => Nil // ignore unused stuff
              }
          })
      }
  }

}
