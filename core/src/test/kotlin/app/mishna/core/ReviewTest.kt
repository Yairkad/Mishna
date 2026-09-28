package app.mishna.core

import app.mishna.core.plan.CompletionMode
import app.mishna.core.plan.ReviewKey
import app.mishna.core.plan.ReviewStage
import app.mishna.core.plan.ReviewState
import app.mishna.core.plan.StudyPlan
import app.mishna.core.plan.dueReviews
import app.mishna.core.plan.grouped
import app.mishna.core.state.AppState
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewTest {
    private val d0 = LocalDate.of(2026, 1, 1)

    @Test fun dueDates() {
        assertEquals(d0.plusDays(1), ReviewKey(d0, ReviewStage.DAY).due)
        assertEquals(d0.plusDays(7), ReviewKey(d0, ReviewStage.WEEK).due)
        assertEquals(d0.plusDays(30), ReviewKey(d0, ReviewStage.MONTH).due)
        assertEquals(d0.plusDays(90), ReviewKey(d0, ReviewStage.MONTH3).due)
        assertEquals(d0.plusDays(365), ReviewKey(d0, ReviewStage.YEAR, 1).due)
        assertEquals(d0.plusDays(730), ReviewKey(d0, ReviewStage.YEAR, 2).due)
    }

    /** Studied every day from d0 with pace 5; review switched on at d0. */
    private fun plan(days: Long, pace: Int = 5) = StudyPlan.create(d0, 0, pace, today = d0.plusDays(days))

    @Test fun followsTheUsersPace() {
        val p = plan(1, pace = 5)
        val due = p.dueReviews(ReviewState(d0), d0.plusDays(1))
        assertEquals(1, due.size)
        assertEquals(0, due.single().start)
        assertEquals(5, due.single().count)
    }

    @Test fun afterAWeek() {
        val today = d0.plusDays(7)
        val state = ReviewState(d0)
        // Each day's DAY review is done, so only yesterday's and last week's remain.
        val doneDays = (1L..6L).map { ReviewKey(d0.plusDays(it - 1), ReviewStage.DAY) }
        val p = plan(7)
        val due = p.dueReviews(state.markReviewed(doneDays, d0), today)
        assertEquals(listOf(ReviewStage.WEEK, ReviewStage.DAY), due.map { it.key.stage })
        val groups = due.grouped()
        assertEquals(listOf("מאתמול", "משבוע שעבר"), groups.map { it.label })
    }

    @Test fun missedReviewCarriesOver() {
        val p = plan(3)
        val due = p.dueReviews(ReviewState(d0), d0.plusDays(3))
        // DAY reviews of d0, d0+1, d0+2 are all still due.
        assertEquals(3, due.size)
    }

    @Test fun enablingSkipsOldBacklog() {
        val p = StudyPlan.create(d0, 0, 3, today = d0.plusDays(100))
        val enabled = d0.plusDays(100)
        val due = p.dueReviews(ReviewState(enabled), enabled)
        assertTrue(due.all { it.key.due == enabled }, due.toString())
        // d0+99 → DAY, d0+93 → WEEK, d0+70 → MONTH, d0+10 → MONTH3.
        assertEquals(setOf(ReviewStage.DAY, ReviewStage.WEEK, ReviewStage.MONTH, ReviewStage.MONTH3), due.map { it.key.stage }.toSet())
    }

    @Test fun onlyStudiedDaysAreReviewed() {
        val p = StudyPlan.create(d0, 0, 3, today = d0.plusDays(2), notStudied = setOf(d0))
        val due = p.dueReviews(ReviewState(d0), d0.plusDays(2))
        assertEquals(listOf(d0.plusDays(1)), due.map { it.key.learned })
    }

    @Test fun markingAndSaving() {
        val p = plan(1).complete(d0.plusDays(1), CompletionMode.APP)
        var state = ReviewState(d0)
        val today = d0.plusDays(1)
        state = state.markReviewed(p.dueReviews(state, today).map { it.key }, today)
        assertTrue(p.dueReviews(state, today).isEmpty())
        assertEquals(1, state.doneOn(today).size)
        val app = AppState(plan = p, review = state)
        assertEquals(app, AppState.decode(app.encode()))
    }
}
