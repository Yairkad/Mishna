package app.mishna

import android.app.Application
import android.content.Intent
import android.net.Uri
import app.mishna.backup.BackupWorker
import app.mishna.update.Release
import app.mishna.update.UpdateStatus
import app.mishna.update.Updater
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mishna.core.plan.CompletionMode
import app.mishna.core.plan.Marking
import app.mishna.core.plan.ReviewKey
import app.mishna.core.plan.ReviewState
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

    /** Marks Shabbat/Yom Tov study after the fact, and optionally the reviews that were due. */
    fun markHolyDays(dates: List<LocalDate>, reviews: List<ReviewKey>) = viewModelScope.launch {
        store.update { s ->
            val plan = s.plan ?: return@update s
            s.copy(
                plan = Marking.markHolyDays(plan, studyDate.value, dates),
                review = s.review?.markReviewed(reviews, studyDate.value),
            )
        }
    }

    fun setReviewEnabled(on: Boolean) = viewModelScope.launch {
        store.update {
            val today = studyDate.value
            it.copy(review = it.review?.setEnabled(on, today) ?: if (on) ReviewState(today) else null)
        }
    }

    fun markReviewed(keys: List<ReviewKey>) = viewModelScope.launch {
        store.update { s -> s.copy(review = s.review?.markReviewed(keys, studyDate.value)) }
    }

    fun shiftDay(forward: Boolean) = updatePlan { it.shiftDay(forward, studyDate.value) }

    fun startNewCycle() = updatePlan { it.startNewCycle(studyDate.value) }

    fun setReviewPage(page: Int) = viewModelScope.launch {
        store.update { it.copy(reviewDate = studyDate.value, reviewPage = page) }
    }

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

    /** Keeps access to the chosen backup folder across restarts and runs a first backup there. */
    fun setBackupFolder(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        store.update { it.copy(prefs = it.prefs.copy(backupFolder = uri.toString())) }
        BackupWorker.runNow(app)
        _message.value = "התיקייה נשמרה. מגבה עכשיו."
    }

    fun backupNow() {
        BackupWorker.runNow(getApplication())
        _message.value = "מגבה…"
    }

    private val _update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val update: StateFlow<UpdateStatus> = _update

    fun checkForUpdate() = viewModelScope.launch {
        _update.value = UpdateStatus.Checking
        _update.value = runCatching { Updater.check() }.fold(
            { it?.let(UpdateStatus::Available) ?: UpdateStatus.UpToDate },
            { UpdateStatus.Failed(Updater.RELEASES_PAGE) },
        )
    }

    /** Downloads and opens the installer. Without install permission it opens that setting first. */
    fun installUpdate(release: Release) = viewModelScope.launch {
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            Updater.openInstallPermission(app)
            _message.value = "אשר התקנה מהאפליקציה, ואז לחץ שוב על הורד והתקן"
            return@launch
        }
        _update.value = UpdateStatus.Downloading(release, 0f)
        runCatching { Updater.download(app, release) { p -> _update.value = UpdateStatus.Downloading(release, p) } }
            .onSuccess { Updater.install(app, it); _update.value = UpdateStatus.Available(release) }
            .onFailure { _update.value = UpdateStatus.Failed(release.page) }
    }

    fun openReleasePage(url: String) = Updater.openPage(getApplication(), url)

    fun updatePrefs(change: (Prefs) -> Prefs) = viewModelScope.launch {
        store.update { it.copy(prefs = change(it.prefs)) }
    }
}
