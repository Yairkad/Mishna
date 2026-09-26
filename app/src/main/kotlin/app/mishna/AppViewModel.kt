package app.mishna

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mishna.core.plan.CompletionMode
import app.mishna.core.plan.Marking
import app.mishna.core.plan.StudyPlan
import app.mishna.core.state.AppState
import app.mishna.core.state.Prefs
import app.mishna.core.time.Place
import app.mishna.core.time.StudyClock
import app.mishna.data.StateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StateStore.get(app)
    val state: StateFlow<AppState> = store.state

    private val _studyDate = MutableStateFlow(StudyClock.studyDate(ZonedDateTime.now(), state.value.place))
    /** The current study day (changes at sunrise, not midnight). */
    val studyDate: StateFlow<LocalDate> = _studyDate

    /** Called when the app comes to the foreground: picks up a new study day. */
    fun refresh() {
        val date = StudyClock.studyDate(ZonedDateTime.now(), state.value.place)
        _studyDate.value = date
        updatePlan { it.open(date) }
    }

    private fun updatePlan(change: (StudyPlan) -> StudyPlan) = viewModelScope.launch {
        store.update { s -> s.plan?.let { s.copy(plan = change(it)) } ?: s }
    }

    fun finishOnboarding(name: String, place: Place, plan: StudyPlan) = viewModelScope.launch {
        store.update { it.copy(name = name, place = place, plan = plan) }
        refresh()
    }

    fun completeToday() = updatePlan { it.complete(studyDate.value, CompletionMode.APP) }

    fun markHolyDays(dates: List<LocalDate>) = updatePlan { Marking.markHolyDays(it, studyDate.value, dates) }

    fun shiftDay(forward: Boolean) = updatePlan { it.shiftDay(forward, studyDate.value) }

    fun startNewCycle() = updatePlan { it.startNewCycle(studyDate.value) }

    fun setReadingPage(page: Int) = viewModelScope.launch {
        store.update { it.copy(readingDate = studyDate.value, readingPage = page) }
    }

    fun updatePrefs(change: (Prefs) -> Prefs) = viewModelScope.launch {
        store.update { it.copy(prefs = change(it.prefs)) }
    }
}
