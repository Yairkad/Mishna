package app.mishna

import android.app.Application
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun changePace(pace: Int) = updatePlan { it.changePace(pace, studyDate.value) }

    fun moveTo(index: Int) = updatePlan { it.moveTo(index, studyDate.value) }

    fun setPlace(place: Place) = viewModelScope.launch {
        store.update { it.copy(place = place) }
        refresh()
    }

    fun reset() = viewModelScope.launch { store.update { AppState() } }

    /** Writes the whole state as JSON to a file the user picked (SPEC §9). */
    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(state.value.encode().toByteArray())
                }
            }.isSuccess
        }
        _message.value = if (ok) "הגיבוי נשמר" else "שמירת הגיבוי נכשלה"
    }

    /** Replaces the whole state with a backup file. A file that is not a valid backup changes nothing. */
    fun importBackup(uri: Uri) = viewModelScope.launch {
        val restored = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                    AppState.decode(it.readBytes().decodeToString())
                }
            }.getOrNull()
        }
        if (restored?.plan == null) {
            _message.value = "הקובץ אינו גיבוי תקין של האפליקציה"
            return@launch
        }
        store.update { restored }
        refresh()
        _message.value = "הנתונים שוחזרו"
    }

    private val _message = MutableStateFlow<String?>(null)
    /** One-off messages shown as a snackbar. */
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    fun updatePrefs(change: (Prefs) -> Prefs) = viewModelScope.launch {
        store.update { it.copy(prefs = change(it.prefs)) }
    }
}
