package app.mishna.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mishna.core.state.AppState
import app.mishna.core.time.Reminders
import java.time.LocalDateTime
import java.time.ZoneId

/** Keeps exactly one alarm set: the next reminder (SPEC §7). */
object ReminderScheduler {
    const val CHANNEL = "daily"
    const val EXTRA_DATE = "date"
    const val EXTRA_AFTER_HOLY = "afterHoly"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "תזכורות לימוד", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, state: AppState, after: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val plan = state.plan
        val next = if (plan == null || !state.prefs.notifications) {
            null
        } else {
            Reminders.next(after, { plan.day(it)?.done == true }, state.prefs.reminderTimes)
        }
        val intent = Intent(context, ReminderReceiver::class.java)
        if (next == null) {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let(alarms::cancel)
            return
        }
        intent.putExtra(EXTRA_DATE, next.date.toString()).putExtra(EXTRA_AFTER_HOLY, next.afterHoly)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}
