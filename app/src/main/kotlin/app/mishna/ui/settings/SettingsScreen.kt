package app.mishna.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mishna.core.content.Mishnayot
import app.mishna.core.state.AppState
import app.mishna.core.state.LineSpacing
import app.mishna.core.state.Prefs
import app.mishna.core.state.ThemeMode
import app.mishna.core.time.Place
import app.mishna.ui.common.Picker
import app.mishna.ui.common.StartPicker
import app.mishna.ui.common.Stepper
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif
import java.time.LocalDate
import java.time.LocalTime
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.mishna.core.time.JewishDays
import app.mishna.core.time.ReminderTimes
import app.mishna.update.Release
import app.mishna.update.UpdateStatus

private val DANGER = Color(0xFFB3261E)

/** Advanced actions that need the two-step confirmation (SPEC §10). */
private enum class Advanced(val title: String, val warning: String) {
    PACE("שינוי קצב לימוד", "פעולה זו משנה את תוכנית הלימוד שלך."),
    POSITION("שינוי נקודת הלימוד", "הלימוד ימשיך מהמשנה שתבחר. ההיסטוריה והרצף נשמרים."),
    RESTORE("שחזור מגיבוי", "כל הנתונים הנוכחיים יוחלפו בנתונים מקובץ הגיבוי."),
    RESET("איפוס מלא", "כל הנתונים יימחקו והאפליקציה תחזור להפעלה הראשונה."),
}

@Composable
fun SettingsScreen(
    state: AppState,
    today: LocalDate,
    versionName: String,
    onPrefs: ((Prefs) -> Prefs) -> Unit,
    onPlace: (Place) -> Unit,
    onPace: (Int) -> Unit,
    onMoveTo: (Int) -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onReset: () -> Unit,
    onBackupFolder: (android.net.Uri) -> Unit,
    onBackupNow: () -> Unit,
    onReviewEnabled: (Boolean) -> Unit,
    update: UpdateStatus,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: (Release) -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val c = LocalBook.current
    val p = state.prefs
    val plan = state.plan ?: return
    var advanced by remember { mutableStateOf<Advanced?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onBackupFolder)
    }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Section("תצוגה")
        Item("גודל גופן") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onPrefs { it.copy(fontScale = (it.fontScale - .1f).coerceAtLeast(.8f)) } }) { Text("א−", color = c.ink, fontFamily = Serif) }
                Text("${(p.fontScale * 100).toInt()}%", fontFamily = Sans, fontSize = 13.sp, color = c.muted)
                TextButton(onClick = { onPrefs { it.copy(fontScale = (it.fontScale + .1f).coerceAtMost(1.5f)) } }) { Text("א+", color = c.ink, fontFamily = Serif, fontSize = 18.sp) }
            }
        }
        Item("רווח בין שורות") {
            Segmented(listOf(LineSpacing.COMPACT to "צפוף", LineSpacing.NORMAL to "רגיל", LineSpacing.WIDE to "מרווח"), p.lineSpacing) { v ->
                onPrefs { it.copy(lineSpacing = v) }
            }
        }
        Item("עיקר תוספות יום טוב") { Toggle(p.showIkarTosafotYomTov) { v -> onPrefs { it.copy(showIkarTosafotYomTov = v) } } }
        Item("מסך דלוק בזמן הלימוד") { Toggle(p.keepScreenOn) { v -> onPrefs { it.copy(keepScreenOn = v) } } }
        Item("מצב תצוגה") {
            Segmented(listOf(ThemeMode.SYSTEM to "מערכת", ThemeMode.LIGHT to "בהיר", ThemeMode.DARK to "כהה"), p.theme) { v ->
                onPrefs { it.copy(theme = v) }
            }
        }

        Section("חזרה")
        Item("תוכנית חזרה") { Toggle(state.review?.enabled == true, onReviewEnabled) }
        Text("חוזרים על כל יום לימוד למחרת, שבוע אחרי, חודש אחרי ו-3 חודשים אחרי (כל פעם מהחזרה הקודמת), ואז כל שנה ביום השנה. חזרה שלא בוצעה נשארת עד שמבצעים אותה.",
            fontFamily = Sans, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(vertical = 6.dp))

        Section("התראות")
        Item("תזכורות") {
            Toggle(p.notifications) { v ->
                if (v && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                onPrefs { it.copy(notifications = v) }
            }
        }
        if (p.notifications) {
            val t = p.reminderTimes
            TimeItem("ראשון–חמישי · תזכורת ראשונה", t.weekdayFirst) { v -> onPrefs { it.copy(reminderTimes = it.reminderTimes.copy(weekdayFirst = v)) } }
            TimeItem("ראשון–חמישי · אם לא הושלם", t.weekdaySecond) { v -> onPrefs { it.copy(reminderTimes = it.reminderTimes.copy(weekdaySecond = v)) } }
            TimeItem("שישי וערב חג · תזכורת ראשונה", t.erevFirst) { v -> onPrefs { it.copy(reminderTimes = it.reminderTimes.copy(erevFirst = v)) } }
            TimeItem("שישי וערב חג · אם לא הושלם", t.erevSecond) { v -> onPrefs { it.copy(reminderTimes = it.reminderTimes.copy(erevSecond = v)) } }
            TimeItem("מוצאי שבת וחג · אם לא סומן", t.afterHoly) { v -> onPrefs { it.copy(reminderTimes = it.reminderTimes.copy(afterHoly = v)) } }
            if (t != ReminderTimes()) {
                Item("איפוס לשעות ברירת המחדל", accent = true, onClick = { onPrefs { it.copy(reminderTimes = ReminderTimes()) } }) {}
            }
        }

        Section("גיבוי")
        Item("גיבוי שבועי אוטומטי") { Toggle(p.weeklyBackup) { v -> onPrefs { it.copy(weeklyBackup = v) } } }
        Item("תיקיית גיבוי", onClick = { folderPicker.launch(null) }) {
            Text(if (p.backupFolder == null) "בחר תיקייה" else "נבחרה ✓", fontFamily = Sans, fontSize = 13.sp, color = c.muted)
        }
        if (p.backupFolder != null) {
            Item("גבה עכשיו", accent = true, onClick = onBackupNow) {
                Text(p.lastBackup?.let { "אחרון: ${JewishDays.hebrewDate(it)}" } ?: "טרם בוצע", fontFamily = Sans, fontSize = 12.sp, color = c.muted)
            }
        } else {
            Text("בלי תיקייה, הנתונים נשמרים רק בגיבוי של Android (אם הוא מופעל במכשיר).",
                fontFamily = Sans, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(vertical = 6.dp))
        }

        Section("כללי")
        val places = if (state.place in Place.CITIES) Place.CITIES else listOf(state.place) + Place.CITIES
        Picker("עיר לזמני היום", places, state.place, { it.name }, onPlace, Modifier.padding(top = 8.dp))
        Item("אודות", onClick = { about = true }) { Text("גרסה $versionName", fontFamily = Sans, fontSize = 13.sp, color = c.muted) }
        when (update) {
            UpdateStatus.Idle -> Item("בדוק עדכון", accent = true, onClick = onCheckUpdate) {}
            UpdateStatus.Checking -> Item("בודק…") {}
            UpdateStatus.UpToDate -> Item("הגרסה עדכנית ✓", onClick = onCheckUpdate) {}
            is UpdateStatus.Available -> Item("עדכון זמין · הורד והתקן", accent = true, onClick = { onInstallUpdate(update.release) }) {
                Text(update.release.name, fontFamily = Sans, fontSize = 13.sp, color = c.muted)
            }
            is UpdateStatus.Downloading -> Item("מוריד עדכון…") {
                Text("${(update.progress * 100).toInt()}%", fontFamily = Sans, fontSize = 13.sp, color = c.muted)
            }
            is UpdateStatus.Failed -> Item("לא הצלחתי לבדוק · פתח ב-GitHub", accent = true, onClick = { onOpenPage(update.page) }) {}
        }

        Section("מתקדם · דורש אישור")
        Item("קצב לימוד", accent = true, onClick = { advanced = Advanced.PACE }) {
            Text("${plan.pace} משניות ביום", fontFamily = Sans, fontSize = 13.sp, color = c.muted)
        }
        Item("נקודת הלימוד", accent = true, onClick = { advanced = Advanced.POSITION }) {
            val next = plan.day(today)?.takeIf { !it.done }?.start ?: plan.nextIndex
            if (next < plan.total) Text(Mishnayot.describe(next, 1), fontFamily = Serif, fontSize = 14.sp, color = c.muted)
        }
        Item("גיבוי ידני לקובץ", accent = true, onClick = { exporter.launch("mishna-backup-$today.json") }) {
            Text("JSON", fontFamily = Sans, fontSize = 13.sp, color = c.muted)
        }
        Item("שחזור מגיבוי", accent = true, onClick = { advanced = Advanced.RESTORE }) {}
        Item("איפוס מלא", color = DANGER, onClick = { advanced = Advanced.RESET }) {}
    }

    advanced?.let { a ->
        var pace by remember(a) { mutableIntStateOf(plan.pace) }
        var position by remember(a) { mutableIntStateOf(plan.day(today)?.takeIf { !it.done }?.start ?: plan.nextIndex.coerceAtMost(plan.total - 1)) }
        val close = { advanced = null; confirmed = false }
        AlertDialog(
            onDismissRequest = close,
            containerColor = c.bg,
            title = {
                Column {
                    Text("שלב ${if (confirmed) 2 else 1} מתוך 2", fontFamily = Sans, fontSize = 12.sp, color = c.muted)
                    Text(a.title, fontFamily = Serif, fontWeight = FontWeight.Bold, color = c.ink)
                }
            },
            text = {
                Column {
                    if (!confirmed) {
                        Text(a.warning, fontFamily = Sans, color = c.muted)
                    } else when (a) {
                        Advanced.PACE -> {
                            Stepper(pace) { pace = it.coerceIn(1, 30) }
                            Text(
                                if (plan.day(today)?.done == true) "השינוי יחול ממחר." else "הלימוד של היום עדיין לא סומן, ולכן השינוי חל כבר היום.",
                                fontFamily = Sans, fontSize = 13.sp, color = c.muted,
                            )
                        }
                        Advanced.POSITION -> StartPicker(position) { position = it }
                        Advanced.RESTORE -> Text("בחר את קובץ הגיבוי.", fontFamily = Sans, color = c.muted)
                        Advanced.RESET -> Text("אי אפשר לבטל פעולה זו.", fontFamily = Sans, color = DANGER)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!confirmed) {
                        confirmed = true
                    } else {
                        when (a) {
                            Advanced.PACE -> onPace(pace)
                            Advanced.POSITION -> onMoveTo(position)
                            Advanced.RESTORE -> importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                            Advanced.RESET -> onReset()
                        }
                        close()
                    }
                }) {
                    Text(
                        when {
                            !confirmed -> "המשך"
                            a == Advanced.RESET -> "מחק הכל"
                            a == Advanced.RESTORE -> "בחירת קובץ"
                            else -> "אישור סופי"
                        },
                        color = if (confirmed && a == Advanced.RESET) DANGER else c.accent, fontFamily = Sans, fontWeight = FontWeight.Medium,
                    )
                }
            },
            dismissButton = { TextButton(onClick = close) { Text("ביטול", color = c.muted, fontFamily = Sans) } },
        )
    }

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            containerColor = c.bg,
            title = { Text("משנה יומית", fontFamily = Serif, fontWeight = FontWeight.Bold, color = c.ink) },
            text = {
                Text(
                    "גרסה $versionName\n\nטקסט המשנה, פירוש ברטנורא ועיקר תוספות יום טוב: מהדורת תורת אמת, דרך הייצוא הפתוח של ספריא (Sefaria).\n\nזמני הזריחה והלוח העברי: KosherJava Zmanim.",
                    fontFamily = Sans, color = c.ink, fontSize = 14.sp,
                )
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("סגירה", color = c.accent, fontFamily = Sans) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeItem(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    val c = LocalBook.current
    var open by remember { mutableStateOf(false) }
    Item(label, onClick = { open = true }) {
        Text("%02d:%02d".format(time.hour, time.minute), fontFamily = Sans, fontSize = 15.sp, color = c.accent)
    }
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = c.bg,
            title = { Text(label, fontFamily = Sans, fontSize = 15.sp, color = c.ink) },
            text = {
                // The clock face reads left-to-right.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state) }
            },
            confirmButton = {
                TextButton(onClick = { onChange(LocalTime.of(state.hour, state.minute)); open = false }) {
                    Text("אישור", color = c.accent, fontFamily = Sans)
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("ביטול", color = c.muted, fontFamily = Sans) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontFamily = Sans, fontSize = 12.sp, color = LocalBook.current.muted, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Item(
    label: String,
    accent: Boolean = false,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val c = LocalBook.current
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontFamily = Sans, fontSize = 15.sp, color = color ?: if (accent) c.accent else c.ink, modifier = Modifier.weight(1f))
            trailing()
        }
        HorizontalDivider(color = c.line)
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalBook.current
    Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent, uncheckedTrackColor = c.line))
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = LocalBook.current
    Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, c.line, RoundedCornerShape(10.dp))) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label, fontFamily = Sans, fontSize = 12.sp, color = if (on) c.onAccent else c.ink,
                modifier = Modifier.background(if (on) c.accent else c.surface).clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
