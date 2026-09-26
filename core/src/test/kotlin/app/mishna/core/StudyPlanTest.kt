package app.mishna.core

import app.mishna.core.plan.CompletionMode.APP
import app.mishna.core.plan.CompletionMode.BACKFILL
import app.mishna.core.plan.StudyPlan
import app.mishna.core.plan.stats
import app.mishna.core.plan.streaks
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyPlanTest {
    private val d0 = LocalDate.of(2026, 9, 1)
    private fun day(n: Long) = d0.plusDays(n)
    private fun fresh() = StudyPlan.create(d0, 0, 3, today = d0)

    @Test fun firstDay() {
        val p = fresh()
        assertEquals(0, p.day(d0)!!.start)
        assertEquals(3, p.day(d0)!!.count)
        assertEquals(1, p.dayNumber(d0))
    }

    @Test fun completingAdvances() {
        val p = fresh().complete(d0, APP).open(day(1))
        assertEquals(3, p.day(day(1))!!.start)
        assertEquals(3, p.nextIndex)
    }

    @Test fun missedDayContinuesWithoutSkipping() {
        val p = fresh().open(day(2))
        assertFalse(p.day(d0)!!.done)
        assertEquals(0, p.day(day(1))!!.start)
        assertEquals(0, p.day(day(2))!!.start)
        assertEquals(3, p.dayNumber(day(2)))
    }

    @Test fun crossesTractateBoundary() {
        // Berakhot has 57 mishnayot; start with 2 left.
        val p = StudyPlan.create(d0, 55, 3, d0)
        assertEquals(55, p.day(d0)!!.start)
        assertEquals(3, p.day(d0)!!.count)
        assertEquals(58, p.complete(d0, APP).nextIndex)
    }

    @Test fun paceChangeBeforeDoneAppliesToday() {
        val p = fresh().changePace(5, d0)
        assertEquals(5, p.day(d0)!!.count)
    }

    @Test fun paceChangeAfterDoneAppliesTomorrow() {
        val p = fresh().complete(d0, APP).changePace(5, d0)
        assertEquals(3, p.day(d0)!!.count)
        assertEquals(5, p.open(day(1)).day(day(1))!!.count)
    }

    @Test fun shiftForwardAndBack() {
        val p = fresh().shiftDay(forward = true, today = d0)
        assertEquals(3, p.day(d0)!!.start)
        assertEquals(0, p.shiftDay(forward = false, today = d0).day(d0)!!.start)
        assertEquals(0, fresh().shiftDay(forward = false, today = d0).day(d0)!!.start)
    }

    @Test fun shiftAfterDoneMovesTomorrow() {
        val p = fresh().complete(d0, APP).shiftDay(forward = true, today = d0)
        assertEquals(0, p.day(d0)!!.start)
        assertEquals(6, p.open(day(1)).day(day(1))!!.start)
    }

    @Test fun backfillFromPastStartDate() {
        val p = StudyPlan.create(d0, 0, 3, today = day(5), notStudied = setOf(day(2)))
        // Days 0,1,3,4 studied (4 days × 3), day 2 missed.
        assertEquals(12, p.nextIndex)
        assertEquals(12, p.day(day(5))!!.start)
        assertEquals(BACKFILL, p.day(day(1))!!.completed)
        assertFalse(p.day(day(2))!!.done)
        assertEquals(6, p.day(day(3))!!.start)
    }

    @Test fun streaks() {
        var p = StudyPlan.create(d0, 0, 3, today = day(6), notStudied = setOf(day(2)))
        // done: 0,1 | missed 2 | done 3,4,5 | today 6 open
        assertEquals(3, p.streaks(day(6)).current)
        assertEquals(3, p.streaks(day(6)).best)
        p = p.complete(day(6), APP)
        assertEquals(4, p.streaks(day(6)).current)
        assertEquals(0, p.open(day(8)).streaks(day(8)).current)
    }

    @Test fun statsAndFinish() {
        val p = fresh().complete(d0, APP)
        val s = p.stats(d0)
        assertEquals(1, s.completedDays)
        assertEquals(3, s.learned)
        assertEquals(4189, s.remaining)
        // ⌈4189 / 3⌉ = 1397 more days starting tomorrow.
        assertEquals(d0.plusDays(1397), s.estimatedFinish)
    }

    @Test fun finishAndNewCycle() {
        val last = StudyPlan.create(d0, 4190, 3, d0)
        assertEquals(2, last.day(d0)!!.count)
        val done = last.complete(d0, APP)
        assertTrue(done.finished)
        val again = done.open(day(1)).startNewCycle(day(1))
        assertEquals(2, again.cycle)
        assertEquals(0, again.day(day(1))!!.start)
        assertEquals(3, again.day(day(1))!!.count)
    }
}
