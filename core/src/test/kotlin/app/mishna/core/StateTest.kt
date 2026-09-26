package app.mishna.core

import app.mishna.core.plan.CompletionMode
import app.mishna.core.plan.StudyPlan
import app.mishna.core.state.AppState
import app.mishna.core.time.Place
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StateTest {
    @Test fun roundTrip() {
        val d0 = LocalDate.of(2026, 9, 1)
        val plan = StudyPlan.create(d0, 0, 3, today = d0.plusDays(3), notStudied = setOf(d0.plusDays(1)))
            .complete(d0.plusDays(3), CompletionMode.APP)
        val state = AppState(name = "יאיר", place = Place.BNEI_BRAK, plan = plan)
        assertEquals(state, AppState.decode(state.encode()))
    }

    @Test fun preview() {
        val d0 = LocalDate.of(2026, 9, 18)
        val plan = StudyPlan.create(d0, 0, 3, d0)
        val ahead = plan.preview(d0, listOf(d0.plusDays(1), d0.plusDays(2)))
        assertEquals(listOf(3, 6), ahead.map { it.start })
        val afterDone = plan.complete(d0, CompletionMode.APP).preview(d0, listOf(d0.plusDays(1)))
        assertEquals(3, afterDone.single().start)
    }
}
