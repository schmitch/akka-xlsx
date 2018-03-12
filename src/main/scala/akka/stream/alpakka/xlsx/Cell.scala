package akka.stream.alpakka.xlsx

import scala.util.{ Failure, Success, Try }

sealed abstract class Cell(ref: CellReference)

object Cell {

  final case class Blank(ref: CellReference)                      extends Cell(ref)
  final case class Text(value: String, ref: CellReference)        extends Cell(ref)
  final case class Formula(value: String, ref: CellReference)     extends Cell(ref)
  final case class Bool(value: Boolean, ref: CellReference)       extends Cell(ref)
  final case class Numeric(value: BigDecimal, ref: CellReference) extends Cell(ref)
  final case class Error(t: Throwable, ref: CellReference)        extends Cell(ref)

  private def optionBuilderString(
      value: Option[java.lang.StringBuilder],
      ref: CellReference
  )(call: String => Cell): Cell = {
    value.map(_.toString()) match {
      case Some(v) if v.nonEmpty => call(v)
      case _                     => Cell.Blank(ref)
    }
  }

  private[xlsx] def parseNumeric(value: Option[java.lang.StringBuilder], ref: CellReference): Cell = {
    optionBuilderString(value, ref) { data =>
      Try(BigDecimal(data)) match {
        case Success(v) => Cell.Numeric(v, ref)
        case Failure(t) => Cell.Error(t, ref)
      }
    }
  }

  private[xlsx] def parseInline(value: Option[java.lang.StringBuilder], ref: CellReference): Cell = {
    value.map(v => Cell.Text(v.toString, ref)).getOrElse(Cell.Blank(ref))
  }

  private[xlsx] def parseString(
      value: Option[java.lang.StringBuilder],
      sst: Map[Int, String],
      ref: CellReference
  ): Cell = {
    optionBuilderString(value, ref) { data =>
      Try(sst(Integer.parseInt(data))) match {
        case Success(v) => Cell.Text(v, ref)
        case Failure(t) => Cell.Error(t, ref)
      }
    }
  }

  private[xlsx] def parseBoolean(value: Option[java.lang.StringBuilder], ref: CellReference): Cell = {
    optionBuilderString(value, ref) { data =>
      Try(java.lang.Boolean.parseBoolean(data)) match {
        case Success(b) => Cell.Bool(b, ref)
        case Failure(t) => Cell.Error(t, ref)
      }
    }
  }

  private[xlsx] def parseFormula(value: Option[java.lang.StringBuilder], ref: CellReference): Cell = {
    value.map(v => Cell.Text(v.toString, ref)).getOrElse(Cell.Blank(ref))
  }

}
