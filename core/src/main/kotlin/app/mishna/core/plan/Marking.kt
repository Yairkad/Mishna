package app.mishna.core.plan

import app.mishna.core.time.JewishDays
import java.time.LocalDate

/** Which days may be marked as studied right now (SPEC §6: no marking after a day ended). */
object Marking {
    /**
     * On a weekday only the current study day. On Shabbat/Yom Tov (including the night after,
     * until sunrise) the whole run of holy days ending today, marked "from the book".
     */
    fun markableDates(studyDate: LocalDate): List<LocalDate> =
        JewishDays.holyChainEndingAt(studyDate).ifEmpty { listOf(studyDate) }

    /** Marks every date in [dates] (oldest first) that is allowed today. */
    fun markHolyDays(plan: StudyPlan, studyDate: LocalDate, dates: List<LocalDate>): StudyPlan {
        val allowed = markableDates(studyDate).toSet()
        return dates.sorted().filter { it in allowed }.fold(plan.open(studyDate)) { p, d ->
            p.complete(d, CompletionMode.BOOK)
        }
    }
}
