package app.mishna.core

import app.mishna.core.plan.CompletionMode.BOOK
import app.mishna.core.plan.Marking
import app.mishna.core.plan.StudyPlan
import app.mishna.core.time.DayType
import app.mishna.core.time.JewishDays
import app.mishna.core.time.Place
import app.mishna.core.time.Reminders
import app.mishna.core.time.StudyClock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeTest {
    private val il = ZoneId.of("Asia/Jerusalem")

    // 5787: Rosh Hashana Sat–Sun 12–13.9.2026, Yom Kippur Mon 21.9, Sukkot Sat 26.9.
    private val thu = LocalDate.of(2026, 9, 17)
    private val fri = LocalDate.of(2026, 9, 18)
    private val sat = LocalDate.of(2026, 9, 19)

    @Test fun beforeSunriseIsPreviousStudyDay() {
        val early = ZonedDateTime.of(fri, LocalTime.of(4, 0), il)
        val late = ZonedDateTime.of(fri, LocalTime.of(8, 0), il)
        assertEquals(thu, StudyClock.studyDate(early, Place.JERUSALEM))
        assertEquals(fri, StudyClock.studyDate(late, Place.JERUSALEM))
        assertEquals(thu, StudyClock.studyDate(ZonedDateTime.of(fri, LocalTime.of(0, 30), il), Place.JERUSALEM))
    }

    @Test fun dayTypes() {
        assertEquals(DayType.WEEKDAY, JewishDays.type(thu))
        assertEquals(DayType.EREV, JewishDays.type(fri))
        assertEquals(DayType.HOLY, JewishDays.type(sat))
        assertEquals(DayType.EREV, JewishDays.type(LocalDate.of(2026, 9, 20))) // Erev Yom Kippur
        assertEquals(DayType.HOLY, JewishDays.type(LocalDate.of(2026, 9, 21)))
    }

    @Test fun roshHashanaChain() {
        val rh1 = LocalDate.of(2026, 9, 12)
        val rh2 = LocalDate.of(2026, 9, 13)
        assertEquals(listOf(rh1, rh2), JewishDays.holyDaysAfter(rh1.minusDays(1)))
        assertEquals(listOf(rh1, rh2), JewishDays.holyChainEndingAt(rh2))
        assertTrue(Reminders.forDate(rh1).isEmpty())
        assertEquals(LocalTime.of(21, 30), Reminders.forDate(rh2).single().time)
    }

    @Test fun reminders() {
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)), Reminders.forDate(thu).map { it.time })
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(14, 0)), Reminders.forDate(fri).map { it.time })
    }

    @Test fun markRoshHashanaAfterwards() {
        val rh1 = LocalDate.of(2026, 9, 12)
        val rh2 = LocalDate.of(2026, 9, 13)
        val p = StudyPlan.create(rh1, 0, 3, today = rh1).open(rh2)
        val marked = Marking.markHolyDays(p, rh2, listOf(rh1, rh2))
        assertEquals(BOOK, marked.day(rh1)!!.completed)
        assertEquals(3, marked.day(rh2)!!.start)
        assertEquals(6, marked.nextIndex)
        assertEquals(listOf(thu), Marking.markableDates(thu))
    }

    @Test fun hebrewDate() {
        assertTrue(JewishDays.hebrewDate(LocalDate.of(2026, 9, 24)).startsWith("י״ג"))
    }
}
