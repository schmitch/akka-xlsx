package example

import scala.collection.mutable

final case class Row(
    rowNum: Int,
    private val cellMap: mutable.TreeMap[Int, Cell]
) {

  def cells: List[Cell] = cellMap.values.toList


}
