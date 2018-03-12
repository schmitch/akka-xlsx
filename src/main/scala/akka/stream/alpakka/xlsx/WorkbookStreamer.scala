package akka.stream.alpakka.xlsx

import java.io.InputStream
import java.util.zip.ZipFile

import akka.stream.Materializer
import akka.stream.alpakka.xml.{ EndElement, ParseEvent, StartElement }
import akka.stream.alpakka.xml.javadsl.XmlParsing
import akka.stream.scaladsl.StreamConverters

import scala.concurrent.Future
import scala.util.Try

object WorkbookStreamer {

  def readWorkbook(sst: Map[Int, String], file: ZipFile)(implicit materializer: Materializer): Future[Workbook] = {
    Option(file.getEntry("xl/workbook.xml")) match {
      case Some(entry) => read(sst, file.getInputStream(entry))
      case None        => Future.failed(new Exception("invalid xlsx file"))
    }
  }

  private def read(
      sst: Map[Int, String],
      inputStream: InputStream
  )(implicit materializer: Materializer): Future[Workbook] = {
    StreamConverters
      .fromInputStream(() => inputStream)
      .via(XmlParsing.parser)
      .statefulMapConcat[(String, Int)](() => {
        var insideSheets: Boolean = false
        (data: ParseEvent) =>
          data match {
            case StartElement("sheets", _, _, _, _) =>
              insideSheets = true
              Nil
            case StartElement("sheet", attrs, _, _, _) if insideSheets =>
              val nameValue = attrs.find(_.name == "name").map(_.value)
              val idValue   = attrs.find(_.name == "sheetId").flatMap(a => Try(Integer.parseInt(a.value)).toOption)
              val sheet = for {
                name <- nameValue
                id   <- idValue
              } yield (name, id)
              sheet match {
                case Some(s) => s :: Nil
                case None    => Nil
              }
            case EndElement("sheet") => // ignored since we can get everything from attrs?
              Nil
            case EndElement("sheets") =>
              insideSheets = false
              Nil
            case _ => Nil
          }
      })
      .runFold(Workbook(sst, Map.empty))((v1, v2) => v1.copy(sheets = v1.sheets + v2))
  }

}
