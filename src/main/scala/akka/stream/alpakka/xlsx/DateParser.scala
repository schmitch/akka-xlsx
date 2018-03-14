package akka.stream.alpakka.xlsx

import java.util.regex.Pattern

import scala.annotation.tailrec

object DateParser {

  // FIXME: scala regex?
  private val datePtrn1  = Pattern.compile("^\\[\\$\\-.*?\\]")
  private val datePtrn2  = Pattern.compile("^\\[[a-zA-Z]+\\]")
  private val datePtrn3a = Pattern.compile("[yYmMdDhHsS]")
  private val datePtrn3b = Pattern.compile("^[\\[\\]yYmMdDhHsS\\-T/年月日,. :\"\\\\]+0*[ampAMP/]*$")
  private val datePtrn4  = Pattern.compile("^\\[([hH]+|[mM]+|[sS]+)\\]")
  private val datePtrn5  = Pattern.compile("^\\[DBNum(1|2|3)\\]")

  def isInternalDateFormat(format: Int): Option[String] = {
    // Ecma Office Open XML Part 1 Page 1777 - SpreadsheetML Reference
    format match {
      case 14 => Some("mm-dd-yy")
      case 15 => Some("d-mmm-yy")
      case 16 => Some("d-mmm")
      case 17 => Some("mmm-yy")
      case 18 => Some("h:mm AM/PM")
      case 19 => Some("h:mm:ss AM/PM")
      case 20 => Some("h:mm")
      case 21 => Some("h:mm:ss")
      case 22 => Some("m/d/yy h:mm")
      case 45 => Some("mm:ss")
      case 46 => Some("[h]:mm:ss")
      case 47 => Some("mmss.0")
      case _  => None
    }
  }

  @tailrec
  private def getSeperatorString(index: Int, fs: String, fsLength: Int, s: String = ""): String = {
    if (index >= fsLength) {
      s
    } else {
      val c                 = fs.charAt(index)
      val indexSmallerAsLen = index < fsLength - 1
      val (newIndex, maybeChar) = {
        val nc = if (indexSmallerAsLen) Some(fs.charAt(index + 1)) else None
        if (indexSmallerAsLen && c == '\\') {
          nc match {
            case Some(' ' | ',' | '-' | '.' | '\\') => (index + 1, None)
            case _                                  => (index + 1, Some(c))
          }
        } else if (indexSmallerAsLen && c == ';' && nc.contains('@')) {
          (index + 2, None)
        } else {
          (index + 1, Some(c))
        }
      }

      val newString = maybeChar match {
        case Some(newChar) => s + newChar
        case None          => s
      }

      getSeperatorString(newIndex, fs, fsLength, newString)
    }
  }

  // FIXME: make me more scalaish and maybe chache result values, like it is done in apache-poi
  private def isInternalADateFormat(formatString: String): Boolean = {
    val len = formatString.length
    val fs  = getSeperatorString(0, formatString, len)
    if (datePtrn4.matcher(fs).matches()) {
      true
    } else {
      val fs1   = datePtrn5.matcher(fs).replaceAll("")
      val fs2   = datePtrn1.matcher(fs1).replaceAll("")
      val fs3   = datePtrn2.matcher(fs2).replaceAll("")
      val index = fs.indexOf(59)
      val fs4 = {
        if (0 < index && index < fs3.length - 1) fs3.substring(0, index)
        else fs3
      }

      if (!datePtrn3a.matcher(fs4).find()) {
        false
      } else {
        datePtrn3b.matcher(fs4).matches()
      }
    }
  }

  def isADateFormat(formatIndex: Int, formatString: Option[String]): Boolean = {
    if (isInternalDateFormat(formatIndex).nonEmpty) {
      true
    } else {
      formatString match {
        case Some(format) => isInternalADateFormat(format)
        case None => false
      }
    }
  }

}
