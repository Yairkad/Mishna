package app.mishna.core

import app.mishna.core.state.AppState
import app.mishna.core.state.Prefs
import app.mishna.core.time.ReminderTimes
import app.mishna.core.time.Reminders
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemindersTest {
    private val thu = LocalDate.of(2026, 9, 17)
    private val fri = LocalDate.of(2026, 9, 18)
    private val sat = LocalDate.of(2026, 9, 19)

    @Test fun morningThenEvening() {
        val none = { _: LocalDate -> false }
        assertEquals(thu.atTime(9, 0), Reminders.next(thu.atTime(7, 0), none)!!.at)
        assertEquals(thu.atTime(21, 0), Reminders.next(thu.atTime(10, 0), none)!!.at)
        assertEquals(fri.atTime(9, 0), Reminders.next(thu.atTime(22, 0), none)!!.at)
    }

    @Test fun doneDaySkipped() {
        val next = Reminders.next(thu.atTime(10, 0), { it == thu })!!
        assertEquals(fri.atTime(9, 0), next.at)
    }

    @Test fun fridayAndShabbat() {
        val none = { _: LocalDate -> false }
        assertEquals(fri.atTime(14, 0), Reminders.next(fri.atTime(10, 0), none)!!.at)
        val motzash = Reminders.next(fri.atTime(15, 0), none)!!
        assertEquals(sat.atTime(21, 30), motzash.at)
        assertTrue(motzash.afterHoly)
    }

    @Test fun customTimes() {
        val t = ReminderTimes(weekdayFirst = LocalTime.of(6, 30))
        assertEquals(thu.atTime(6, 30), Reminders.next(thu.atTime(5, 0), { false }, t)!!.at)
        val state = AppState(prefs = Prefs(reminderTimes = t, lastBackup = thu))
        assertEquals(state, AppState.decode(state.encode()))
    }

    @Test fun oldStateWithoutNewFieldsLoads() {
        val old = """{"version":1,"name":"x","prefs":{"fontScale":1.2}}"""
        assertEquals(ReminderTimes(), AppState.decode(old).prefs.reminderTimes)
    }

    @Suppress("unused") private fun at(d: LocalDate, h: Int) = LocalDateTime.of(d, LocalTime.of(h, 0))
}
