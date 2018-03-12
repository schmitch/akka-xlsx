package akka.stream.alpakka.xlsx

case class Workbook(sst: Map[Int, String], sheets: Map[String, Int], styles: Map[Int, Int])
