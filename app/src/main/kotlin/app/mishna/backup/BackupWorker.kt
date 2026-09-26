package app.mishna.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mishna.data.StateStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Weekly backup (SPEC §9): writes the whole state to "mishna-backup.json" in the folder the
 * user picked. Without a folder it does nothing; Android's own backup still covers the app data.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = StateStore.get(applicationContext)
        val state = store.state.value
        val folder = state.prefs.backupFolder?.let(Uri::parse) ?: return Result.success()
        if (state.plan == null) return Result.success()
        return runCatching {
            val dir = DocumentFile.fromTreeUri(applicationContext, folder) ?: error("folder not available")
            val file = dir.findFile(FILE) ?: dir.createFile("application/json", FILE.removeSuffix(".json")) ?: error("cannot create file")
            applicationContext.contentResolver.openOutputStream(file.uri, "wt")!!.use { it.write(state.encode().toByteArray()) }
            store.update { it.copy(prefs = it.prefs.copy(lastBackup = LocalDate.now())) }
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        const val FILE = "mishna-backup.json"
        private const val WEEKLY = "weekly-backup"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                wm.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build())
            } else {
                wm.cancelUniqueWork(WEEKLY)
            }
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BackupWorker>().build())
        }
    }
}
