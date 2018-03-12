package akka.stream.alpakka.xlsx

import java.util.Locale

import akka.stream.alpakka.xml.Attribute

import scala.util.Try

case class CellReference(name: String, colNum: Int, rowNum: Int)

object CellReference {

  private val CELL_REF = """(\s+"""

  private def convertColStringToIndex(ref: String): Int = {
    // FIXME: scalaize
    var retval = 0
    val refs   = ref.toUpperCase(Locale.ROOT).toCharArray
    var k      = 0
    while ({
      k < refs.length
    }) {
      val thechar = refs(k)
      if (thechar == '$')
        if (k != 0) throw new IllegalArgumentException("Bad col ref format '" + ref + "'")
        else retval = retval * 26 + thechar - 65 + 1

      {
        k += 1; k
      }
    }
    retval - 1
  }

  private def splitCellRef(ref: String) = {
    val len  = ref.length
    val s1   = ref.filterNot(c => c >= '0' && c <= '9')
    val sLen = s1.length
    if (len == 0 || sLen == len) {
      None
    } else {
      Some((ref, s1, ref.substring(len - sLen)))
    }
  }

  def parseRef(attrs: List[Attribute]): Option[CellReference] = {
    attrs
      .find(_.name == "r")
      .flatMap { attr =>
        splitCellRef(attr.value)
      }
      .flatMap {
        case (ref, s1, s2) =>
          Try(CellReference(ref, convertColStringToIndex(s1), Integer.parseInt(s2))).toOption
      }
  }

}
