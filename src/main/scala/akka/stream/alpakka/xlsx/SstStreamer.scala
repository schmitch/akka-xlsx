package akka.stream.alpakka.xlsx

import java.io.InputStream
import java.util.zip.ZipFile

import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{ Characters, EndElement, ParseEvent, StartElement }
import akka.stream.scaladsl.{ Keep, Sink, Source, StreamConverters }

import scala.concurrent.Future

object SstStreamer {

  def readSst(zipFile: ZipFile)(implicit materializer: Materializer): Future[Map[Int, String]] = {
    readSst(zipFile, Sink.fold(Map.empty[Int, String])((v1, v2) => v1 + v2))
  }

  def readSst(
      zipFile: ZipFile,
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Future[Map[Int, String]] = {
    Option(zipFile.getEntry("xl/sharedStrings.xml")) match {
      case Some(entry) => read(zipFile.getInputStream(entry), mapSink)
      case None        => Source.empty[(Int, String)].toMat(mapSink)(Keep.right).run()
    }
  }

  private def read(
      inputStream: InputStream,
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer) = {
    StreamConverters
      .fromInputStream(() => inputStream)
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
      .toMat(mapSink)(Keep.right)
      .run()
  }

}
