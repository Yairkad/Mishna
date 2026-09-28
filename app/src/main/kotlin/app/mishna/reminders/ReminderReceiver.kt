package app.mishna.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mishna.MainActivity
import app.mishna.R
import app.mishna.core.content.Mishnayot
import app.mishna.core.plan.dueReviews
import app.mishna.data.StateStore
import java.time.LocalDate
import java.time.LocalDateTime

/** Fires at reminder time: shows the notification if the day is still open, then sets the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = StateStore.get(context).state.value
        val plan = state.plan
        val date = intent.getStringExtra(ReminderScheduler.EXTRA_DATE)?.let(LocalDate::parse)
        if (plan != null && date != null && state.prefs.notifications) {
            val day = plan.open(date).day(date)
            if (day != null && !day.done && day.count > 0) {
                val afterHoly = intent.getBooleanExtra(ReminderScheduler.EXTRA_AFTER_HOLY, false)
                val text = if (afterHoly) {
                    "האם למדת מהספר? אפשר לסמן עד הזריחה."
                } else {
                    "הלימוד של היום: ${Mishnayot.describe(day.start, day.count)}"
                }
                val reviewCount = state.review?.let { plan.dueReviews(it, date).sumOf { r -> r.count } } ?: 0
                val full = if (reviewCount > 0) "$text\n+ חזרה: $reviewCount משניות" else text
                notify(context, if (afterHoly) "סימון לימוד השבת" else "משנה יומית", full)
            }
        }
        ReminderScheduler.schedule(context, state, after = LocalDateTime.now().plusSeconds(30))
    }

    private fun notify(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_study)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}

/** After reboot, an app update or a clock change, alarms must be set again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.schedule(context, StateStore.get(context).state.value)
    }
}
