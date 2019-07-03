package akka.stream.alpakka.xlsx

import java.io.FileNotFoundException
import java.util.zip.ZipFile

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
      value: Option[String],
      lastFormula: Option[String],
      numFmtId: Option[Int],
      workbook: Workbook,
      ref: CellReference
  ): Cell = {
    def parseValue = {
      // the default cell type is always NUMERIC
      typ.getOrElse(CellType.NUMERIC) match {
        case CellType.BLANK                     => Cell.Blank(ref)
        case CellType.INLINE | CellType.FORMULA => Cell.parseInline(value, ref)
        case CellType.STRING                    => Cell.parseString(value, workbook.sst, ref)
        case CellType.BOOLEAN                   => Cell.parseBoolean(value, ref)
        case CellType.ERROR                     => Cell.Error(new Exception("cell type is invalid"), ref)
        case CellType.NUMERIC                   => Cell.parseNumeric(value, numFmtId.flatMap(id => workbook.styles.get(id)), ref)
      }
    }
    lastFormula match {
      case Some(formula) => Cell.Formula(parseValue, formula, ref)
      case None          => parseValue
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
            var insideRow: Boolean                   = false
            var insideCol: Boolean                   = false
            var insideValue: Boolean                 = false
            var insideFormula: Boolean               = false
            var cellType: Option[CellType]           = None
            var lastContent: Option[String]          = None
            var lastFormula: Option[String]          = None
            var cellList: mutable.TreeMap[Int, Cell] = mutable.TreeMap.empty
            var rowNum                               = 1
            var cellNum                              = 1
            var ref: Option[CellReference]           = None
            var numFmtId: Option[Int]                = None

            (data: ParseEvent) =>
              data match {
                case StartElement("row", _, _, _, _) => nillable({ insideRow = true })
                case StartElement("c", attrs, _, _, _) if insideRow =>
                  nillable({
                    ref = CellReference.parseRef(attrs)
                    numFmtId = attrs.find(_.name == "s").flatMap(a => Try(Integer.parseInt(a.value)).toOption)
                    cellType = attrs.find(_.name == "t").map(a => CellType.parse(a.value))
                    insideCol = true
                  })
                case StartElement("v", _, _, _, _) if insideCol =>
                  nillable({ insideValue = true })
                case StartElement("f", _, _, _, _) if insideCol =>
                  nillable({ insideFormula = true })
                case Characters(text) if insideValue =>
                  nillable({ lastContent = Some(lastContent.map(_ + text).getOrElse(text)) })
                case Characters(text) if insideFormula =>
                  nillable({
                    lastFormula = Some(lastFormula.map(_ + text).getOrElse(text))
                  })
                case EndElement("v") if insideValue =>
                  nillable({ insideValue = false })
                case EndElement("f") if insideFormula =>
                  nillable({ insideFormula = false })
                case EndElement("c") if insideCol =>
                  nillable({
                    val simpleRef = ref.getOrElse(CellReference("", cellNum, rowNum))
                    val cell      = buildCell(cellType, lastContent, lastFormula, numFmtId, workbook, simpleRef)
                    cellList += (simpleRef.colNum -> cell)
                    numFmtId = None
                    ref = None
                    cellNum += 1
                    insideCol = false
                    cellType = None
                    lastContent = None
                    lastFormula = None
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
