package example

import akka.stream.alpakka.xml.ParseEvent

case class StatefulRow(index: Int, events: Seq[ParseEvent])
