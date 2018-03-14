package akka.stream.alpakka.xlsx

import java.time.LocalDateTime

import scala.util.{ Failure, Success, Try }

sealed abstract class Cell(ref: CellReference)

object Cell {

  final case class Blank(ref: CellReference)                                 extends Cell(ref)
  final case class Text(value: String, ref: CellReference)                   extends Cell(ref)
  final case class Formula(value: Cell, formula: String, ref: CellReference) extends Cell(ref)
  final case class Bool(value: Boolean, ref: CellReference)                  extends Cell(ref)
  final case class Numeric(value: BigDecimal, ref: CellReference)            extends Cell(ref)
  final case class Date(value: LocalDateTime, ref: CellReference)            extends Cell(ref)
  final case class Error(t: Throwable, ref: CellReference)                   extends Cell(ref)

  private def optionBuilderString(
      value: Option[String],
      ref: CellReference
  )(call: String => Cell): Cell = {
    value match {
      case Some(v) if v.nonEmpty => call(v)
      case _                     => Cell.Blank(ref)
    }
  }

  private[xlsx] def parseNumeric(
      value: Option[String],
      numFmtId: Option[Int],
      ref: CellReference
  ): Cell = {
    optionBuilderString(value, ref) { data =>
      Try(BigDecimal(data)) match {
        case Success(v) =>
          if (DateParser.isInternalDateFormat(numFmtId.getOrElse(0)).nonEmpty) Cell.Date(DateCell.parse(v), ref)
          else Cell.Numeric(v, ref)
        case Failure(t) => Cell.Error(t, ref)
      }
    }
  }

  private[xlsx] def parseInline(value: Option[String], ref: CellReference): Cell = {
    value.map(v => Cell.Text(v, ref)).getOrElse(Cell.Blank(ref))
  }

  private[xlsx] def parseString(
      value: Option[String],
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

  private[xlsx] def parseBoolean(value: Option[String], ref: CellReference): Cell = {
    optionBuilderString(value, ref) { data =>
      Try(java.lang.Boolean.parseBoolean(data)) match {
        case Success(b) => Cell.Bool(b, ref)
        case Failure(t) => Cell.Error(t, ref)
      }
    }
  }

  private[xlsx] def parseFormula(value: Option[String], ref: CellReference): Cell = {
    value.map(v => Cell.Text(v, ref)).getOrElse(Cell.Blank(ref))
  }

}
