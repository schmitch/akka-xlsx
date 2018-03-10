package example

import java.nio.file.Paths
import java.util.zip.ZipFile

import akka.Done
import akka.actor.ActorSystem
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.scaladsl.{ Sink, StreamConverters }
import akka.stream.{ ActorMaterializer, Materializer }

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

object Hello {

  private val debugSink = Sink.foreach[ParseEvent] {
    case StartDocument                       => println("Start Document")
    case EndDocument                         => println("End Document")
    case StartElement(name, attrs, _, _, _)  => println(s"Start Element: $name | $attrs")
    case EndElement(name)                    => println(s"End Element: $name")
    case ProcessingInstruction(target, data) => println(s"ProcessingInstruction: $target - $data")
    case Comment(text)                       => println(s"Comment: $text")
    case Characters(text)                    => println(s"Characters: $text")
    case CData(text)                         => println(s"CData: $text")
  }

  private def readSst(
      zipFile: ZipFile
  )(implicit materializer: Materializer, executionContext: ExecutionContext): Future[Map[Int, String]] = {
    Option(zipFile.getEntry("xl/sharedStrings.xml")) match {
      case Some(entry) =>
        StreamConverters
          .fromInputStream(() => zipFile.getInputStream(entry))
          .via(XmlParsing.parser)
          .statefulMapConcat[(Int, String)](() => {
            var count                              = 0
            var si                                 = false
            var lastContent: Option[StringBuilder] = None
            (data: ParseEvent) =>
              data match {
                case StartElement("si", _, _, _, _) =>
                  si = true
                  Nil
                case EndElement("si") =>
                  si = false
                  lastContent match {
                    case Some(builder) =>
                      val ret = (count, builder.toString())
                      lastContent = None
                      count += 1
                      ret :: Nil
                    case None =>
                      Nil
                  }
                case Characters(text) if si =>
                  lastContent match {
                    case Some(t) => t.append(text)
                    case None    => lastContent = Some(new StringBuilder().append(text))
                  }
                  Nil
                case _ => Nil
              }
          })
          .runFold(Map.empty[Int, String])((v1, v2) => v1 + v2)
      case None => Future.successful(Map.empty)
    }
  }

  def nillable(s: => Any): List[Row] = {
    s
    Nil
  }

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem   = ActorSystem()
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext       = actorSystem.dispatcher

    val sheetName = "sheet1"
    val path      = Paths.get(args(0))

    val zipFile = new ZipFile(path.toFile)
    val done = readSst(zipFile).flatMap { sst =>
      Option(zipFile.getEntry(s"xl/worksheets/$sheetName.xml")) match {
        case Some(entry) =>
          StreamConverters
            .fromInputStream(() => zipFile.getInputStream(entry))
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
                      val cell = buildCell(cellType, contentBuilder, sst, ref.getOrElse(CellReference(cellNum, rowNum)))
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
            .runForeach { row =>
              println(row)
            }

        case None =>
          println("No Entry found")
          Future.successful(Done)
      }

    }

    done.onComplete(t => { println(t); actorSystem.terminate() })
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

  /*
  def zipStream(path: Path, sheetName: String): Unit = {
    val zipInputStream = new ZipInputStream(Files.newInputStream(path))
    try {
      readZipEntry(zipInputStream, sheetName) match {
        case Some(entry) =>
        case None        =>
      }
    } finally {
      zipInputStream.close()
    }
  }

  @tailrec
  private def readZipEntry(inputStream: ZipInputStream, name: String): Option[ZipEntry] = {

    Option(inputStream.getNextEntry) match {
      case Some(entry) =>
        println(s"Entry: $entry")
        if (entry.getName == name) Some(entry)
        else readZipEntry(inputStream, name)
      case None => None
    }
  }
  */

}
