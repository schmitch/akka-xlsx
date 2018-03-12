package akka.stream.alpakka.xlsx

sealed trait SheetType

private[xlsx] object SheetType {

  case class Name(name: String) extends SheetType
  case class Id(id: Int)        extends SheetType

}
