package app.mishna

import android.app.Application
import app.mishna.backup.BackupWorker
import app.mishna.data.StateStore
import app.mishna.reminders.ReminderScheduler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MishnaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        val store = StateStore.get(this)
        val scope = MainScope()
        // Any change to the plan or the reminder settings moves the next alarm.
        scope.launch {
            store.state.map { Triple(it.plan, it.prefs.notifications, it.prefs.reminderTimes) }
                .distinctUntilChanged()
                .collect { ReminderScheduler.schedule(this@MishnaApp, store.state.value) }
        }
        scope.launch {
            store.state.map { it.prefs.weeklyBackup && it.plan != null }
                .distinctUntilChanged()
                .collect { BackupWorker.schedule(this@MishnaApp, enabled = it) }
        }
    }
}
