package akka.stream.alpakka.xlsx

import scala.collection.mutable

final class Row(
    rowNum: Int,
    private val cellMap: mutable.TreeMap[Int, Cell]
) {

  def lastCellNum: Option[Int] = {
    cellMap.lastOption.map(_._1)
  }

  def getSize: Int = cellMap.size

  def getCell(cellNum: Int): Option[Cell] = {
    cellMap.get(cellNum)
  }

  def simpleCells: Iterable[Cell] = cellMap.values

  lazy val cells: Seq[Cell] = {
    lastCellNum.map { max =>
      for (index <- 1 to max) yield {
        cellMap.getOrElse(index, Cell.Blank(CellReference.generateRef(rowNum, index)))
      }
    }.getOrElse(Nil)
  }

  override def toString: String = s"Row($rowNum, $cells)"

}
