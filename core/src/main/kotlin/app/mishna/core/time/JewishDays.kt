package app.mishna.core.time

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.LocalDate
import java.time.LocalTime

enum class DayType {
    WEEKDAY,

    /** Friday or the day before Yom Tov (and not itself holy). */
    EREV,

    /** Shabbat or Yom Tov. */
    HOLY,
}

/** Israel calendar (one day of Yom Tov), SPEC §4. */
object JewishDays {
    private fun cal(date: LocalDate) = JewishCalendar(date).apply { inIsrael = true }

    fun isHoly(date: LocalDate): Boolean = cal(date).isAssurBemelacha

    fun type(date: LocalDate): DayType = when {
        isHoly(date) -> DayType.HOLY
        isHoly(date.plusDays(1)) -> DayType.EREV
        else -> DayType.WEEKDAY
    }

    /** The upcoming run of holy days right after [date], e.g. [Shabbat] or [Yom Tov, Shabbat]. */
    fun holyDaysAfter(date: LocalDate): List<LocalDate> =
        generateSequence(date.plusDays(1)) { it.plusDays(1) }.takeWhile { isHoly(it) }.toList()

    /** The run of holy days ending at [date] (inclusive), oldest first. Empty if [date] is not holy. */
    fun holyChainEndingAt(date: LocalDate): List<LocalDate> =
        generateSequence(date) { it.minusDays(1) }.takeWhile { isHoly(it) }.toList().reversed()

    /** Full Hebrew date, e.g. "י״ג תשרי תשפ״ז". */
    fun hebrewDate(date: LocalDate): String {
        val f = HebrewDateFormatter().apply {
            isHebrewFormat = true
            isUseGershGershayim = true
        }
        return f.format(cal(date))
    }
}

data class Reminder(val time: LocalTime, val onlyIfNotDone: Boolean)

data class ReminderTimes(
    val weekdayFirst: LocalTime = LocalTime.of(9, 0),
    val weekdaySecond: LocalTime = LocalTime.of(21, 0),
    val erevFirst: LocalTime = LocalTime.of(9, 0),
    val erevSecond: LocalTime = LocalTime.of(14, 0),
    val afterHoly: LocalTime = LocalTime.of(21, 30),
)

/** SPEC §7. */
object Reminders {
    fun forDate(date: LocalDate, t: ReminderTimes = ReminderTimes()): List<Reminder> = when (JewishDays.type(date)) {
        DayType.WEEKDAY -> listOf(Reminder(t.weekdayFirst, false), Reminder(t.weekdaySecond, true))
        DayType.EREV -> listOf(Reminder(t.erevFirst, false), Reminder(t.erevSecond, true))
        // Only after the last holy day of a run (e.g. Yom Tov followed by Shabbat).
        DayType.HOLY -> if (JewishDays.isHoly(date.plusDays(1))) emptyList() else listOf(Reminder(t.afterHoly, true))
    }
}
