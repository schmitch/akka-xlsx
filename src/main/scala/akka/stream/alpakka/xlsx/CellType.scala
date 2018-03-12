package akka.stream.alpakka.xlsx

sealed trait CellType

object CellType {

  case object BLANK   extends CellType
  case object INLINE  extends CellType
  case object STRING  extends CellType
  case object FORMULA extends CellType
  case object BOOLEAN extends CellType
  case object ERROR   extends CellType
  case object NUMERIC extends CellType

  def parse(s: String): CellType = {
    s match {
      case "n"         => CellType.NUMERIC
      case "s"         => CellType.STRING
      case "inlineStr" => CellType.INLINE
      case "str"       => CellType.FORMULA
      case "b"         => CellType.BOOLEAN
      case "e"         => CellType.ERROR
      case _           => CellType.ERROR
    }
  }

}
