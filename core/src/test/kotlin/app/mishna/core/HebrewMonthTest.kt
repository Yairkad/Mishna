package app.mishna.core

import app.mishna.core.plan.CompletionMode
import app.mishna.core.plan.StudyPlan
import app.mishna.core.time.HebrewMonth
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HebrewMonthTest {
    @Test fun tishrei5787() {
        val m = HebrewMonth.of(LocalDate.of(2026, 9, 25))
        assertEquals(LocalDate.of(2026, 9, 12), m.firstDay) // 1 Tishrei = Rosh Hashana
        assertEquals(30, m.length)
        assertTrue(m.title.startsWith("תשרי"), m.title)
        assertEquals("י״ג", HebrewMonth.dayLabel(LocalDate.of(2026, 9, 24)))
    }

    @Test fun navigation() {
        val m = HebrewMonth.of(LocalDate.of(2026, 9, 25))
        assertEquals(LocalDate.of(2026, 10, 12), m.next().firstDay)
        assertTrue(m.previous().title.startsWith("אלול"), m.previous().title)
        assertEquals(m, m.next().previous())
    }

    @Test fun moveTo() {
        val d0 = LocalDate.of(2026, 9, 1)
        val p = StudyPlan.create(d0, 0, 3, d0).moveTo(57, d0)
        assertEquals(57, p.day(d0)!!.start)
        val done = StudyPlan.create(d0, 0, 3, d0).complete(d0, CompletionMode.APP).moveTo(57, d0)
        assertEquals(0, done.day(d0)!!.start)
        assertEquals(57, done.nextIndex)
    }
}
