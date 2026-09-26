package app.mishna.core.time

import app.mishna.core.content.Hebrew
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.LocalDate

/** A Hebrew calendar month, for the history calendar (DESIGN.md §3.5). */
data class HebrewMonth(val firstDay: LocalDate, val length: Int, val title: String) {
    val days: List<LocalDate> get() = (0 until length).map { firstDay.plusDays(it.toLong()) }

    fun next(): HebrewMonth = of(firstDay.plusDays(length.toLong()))
    fun previous(): HebrewMonth = of(firstDay.minusDays(1))
    operator fun contains(date: LocalDate) = !date.isBefore(firstDay) && date.isBefore(firstDay.plusDays(length.toLong()))

    companion object {
        private val formatter = HebrewDateFormatter().apply {
            isHebrewFormat = true
            isUseGershGershayim = true
        }

        fun of(date: LocalDate): HebrewMonth {
            val cal = JewishCalendar(date)
            val day = cal.jewishDayOfMonth
            val first = date.minusDays((day - 1).toLong())
            val title = "${formatter.formatMonth(cal)} ${formatter.formatHebrewNumber(cal.jewishYear % 1000)}"
            return HebrewMonth(first, cal.daysInJewishMonth, title)
        }

        /** Day of month as Hebrew letters, e.g. "י״ג". */
        fun dayLabel(date: LocalDate): String = Hebrew.numeral(JewishCalendar(date).jewishDayOfMonth)
    }
}
