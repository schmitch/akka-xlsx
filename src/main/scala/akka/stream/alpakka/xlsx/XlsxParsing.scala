package akka.stream.alpakka.xlsx

import java.util.zip.{ ZipEntry, ZipFile }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{ Characters, EndElement, ParseEvent, StartElement }
import akka.stream.scaladsl.{ Sink, Source, StreamConverters }

import scala.collection.mutable
import scala.concurrent.Future

object XlsxParsing {

  def fromZipFile(file: ZipFile, sheetName: String)(implicit materializer: Materializer): Source[Row, NotUsed] = {
    fromZipFile(
      file,
      sheetName,
      Sink.fold(Map.empty[Int, String])((v1, v2) => v1 + v2)
    )
  }

  def fromZipFile(
      file: ZipFile,
      sheetName: String,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Source[Row, NotUsed] = {
    Option(file.getEntry(s"xl/worksheets/$sheetName.xml")) match {
      case Some(entry) => read(file, entry, sstSink)
      case None        => Source.failed(new Exception("no valid worksheet found"))
    }
  }

  private def nillable(s: => Any): List[Row] = {
    s
    Nil
  }

  private def buildCell(
      typ: Option[CellType],
      value: Option[java.lang.StringBuilder],
      sst: Map[Int, String],
      ref: CellReference
  ): Cell = {
    // the default cell type is always NUMERIC
    typ.getOrElse(CellType.NUMERIC) match {
      case CellType.BLANK   => Cell.Blank(ref)
      case CellType.INLINE  => Cell.parseInline(value, ref)
      case CellType.STRING  => Cell.parseString(value, sst, ref)
      case CellType.FORMULA => Cell.parseFormula(value, ref)
      case CellType.BOOLEAN => Cell.parseBoolean(value, ref)
      case CellType.ERROR   => Cell.Error(new Exception("cell type is invalid"), ref)
      case CellType.NUMERIC => Cell.parseNumeric(value, ref)
    }
  }

  private def read(
      file: ZipFile,
      entry: ZipEntry,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer) = {

    Source.fromFuture(SstStreamer.readSst(file, sstSink)).flatMapConcat { sstMap =>
      StreamConverters
        .fromInputStream(() => file.getInputStream(entry))
        .via(XmlParsing.parser)
        .statefulMapConcat[Row](() => {
          var insideRow: Boolean                              = false
          var insideCol: Boolean                              = false
          var insideValue: Boolean                            = false
          var cellType: Option[CellType]                      = None
          var contentBuilder: Option[java.lang.StringBuilder] = None
          var cellList: mutable.TreeMap[Int, Cell]            = mutable.TreeMap.empty
          var rowNum                                          = 1
          var cellNum                                         = 0
          var ref: Option[CellReference]                      = None

          (data: ParseEvent) =>
            data match {
              case StartElement("row", _, _, _, _) => nillable(insideRow = true)
              case StartElement("c", attrs, _, _, _) if insideRow =>
                nillable({
                  ref = CellReference.parseRef(attrs)
                  contentBuilder = Some(new java.lang.StringBuilder())
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
                  val cell =
                    buildCell(cellType, contentBuilder, sstMap, ref.getOrElse(CellReference("", cellNum, rowNum)))
                  ref = None
                  cellList += (cellNum -> cell)
                  cellNum += 1
                  insideCol = false
                  cellType = None
                  contentBuilder = None
                })
              case EndElement("row") if insideRow =>
                val ret = Row(rowNum, cellList)
                rowNum += 1
                cellNum = 0
                cellList = mutable.TreeMap.empty
                insideRow = false
                ret :: Nil
              case _ => Nil // ignore unused stuff
            }
        })
    }
  }

}
