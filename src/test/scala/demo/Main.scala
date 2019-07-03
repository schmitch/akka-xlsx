package demo

import java.nio.file.Paths
import java.util.zip.ZipFile

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.scaladsl.{ Sink, StreamConverters }
import akka.stream.alpakka.xlsx._

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.StdIn

object Main {

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

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem   = ActorSystem()
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext       = actorSystem.dispatcher

    val sheetId = 1

    val path    = Paths.get(args(0))
    val zipFile = new ZipFile(path.toFile)

    val done = XlsxParsing.fromZipFile(zipFile, sheetId).runForeach { row =>
      if (row.rowNum > 1) {
        val sb = new mutable.StringBuilder()
        row.cells.zipWithIndex.foreach {
          case (Cell.Date(value, _), index)               => sb.append(s"$index: $value ")
          case (Cell.Numeric(value, ref), index)          => sb.append(s"$index: $value ")
          case (Cell.Formula(value, formula, ref), index) => sb.append(s"$index: $value ")
          case (Cell.Text(value, _), index)               => sb.append(s"$index: $value ")
          case _                                 =>
        }
        println(sb.toString())
        // StdIn.readLine()
      }
    }

    done.onComplete(t => { println(t); actorSystem.terminate() })
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
