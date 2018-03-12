package akka.stream.alpakka.xlsx

import java.time.{ LocalDateTime, ZoneId }
import java.util.{ Calendar, Locale, TimeZone }

private[xlsx] object DateCell {

  val SECONDS_PER_MINUTE     = 60
  val MINUTES_PER_HOUR       = 60
  val HOURS_PER_DAY          = 24
  val SECONDS_PER_DAY: Int   = HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE
  val DAY_MILLISECONDS: Long = SECONDS_PER_DAY * 1000L

  def isDateFormat(format: Int): Option[String] = {
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

  def parse(
      date: BigDecimal,
      use1904windowing: Boolean = false,
      roundSeconds: Boolean = false,
      timeZone: TimeZone = TimeZone.getDefault,
      locale: Locale = Locale.getDefault,
      zoneId: ZoneId = ZoneId.systemDefault()
  ): LocalDateTime = {
    val doubleDate        = date.doubleValue()
    val wholeDays         = Math.floor(doubleDate).toInt
    val millisecondsInDay = ((doubleDate - wholeDays) * DAY_MILLISECONDS + 0.5).toInt
    val calendar          = Calendar.getInstance(TimeZone.getDefault, Locale.getDefault)

    val startYear = if (use1904windowing) 1904 else 1900
    val dayAdjust = if (use1904windowing) 1 else if (wholeDays < 61) 0 else -1
    calendar.set(startYear, 0, wholeDays + dayAdjust, 0, 0, 0)
    calendar.set(Calendar.MILLISECOND, millisecondsInDay)
    if (calendar.get(Calendar.MILLISECOND) == 0) {
      calendar.clear(Calendar.MILLISECOND)
    }
    if (roundSeconds) {
      calendar.add(Calendar.MILLISECOND, 500)
      calendar.clear(Calendar.MILLISECOND)
    }

    LocalDateTime.ofInstant(calendar.toInstant, ZoneId.systemDefault())
  }

}
