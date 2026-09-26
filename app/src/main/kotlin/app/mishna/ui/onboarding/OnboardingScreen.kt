package app.mishna.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mishna.core.content.Hebrew
import app.mishna.core.content.Mishnayot
import app.mishna.core.plan.StudyPlan
import app.mishna.core.time.JewishDays
import app.mishna.core.time.Place
import app.mishna.ui.common.GhostButton
import app.mishna.ui.common.Picker
import app.mishna.ui.common.StartPicker
import app.mishna.ui.common.Stepper
import app.mishna.ui.common.PrimaryButton
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val STEPS = 5

/** First run, 5 steps (DESIGN.md §3.1). */
@Composable
fun OnboardingScreen(today: LocalDate, onDone: (name: String, place: Place, plan: StudyPlan) -> Unit) {
    val c = LocalBook.current
    var step by rememberSaveable { mutableIntStateOf(1) }
    var name by rememberSaveable { mutableStateOf("") }
    var pace by rememberSaveable { mutableIntStateOf(3) }
    var startIndex by rememberSaveable { mutableIntStateOf(0) }
    var startDate by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val notStudied = remember { mutableStateListOf<Long>() }
    var place by remember { mutableStateOf(Place.JERUSALEM) }

    Column(Modifier.fillMaxSize().background(c.bg).padding(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 22.dp)) {
            repeat(STEPS) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < step) c.accent else c.line))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                1 -> {
                    Title("ברוך הבא", "לימוד משנה יומי, רציף, מברכות ועד עוקצין.")
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text("איך לקרוא לך?", fontFamily = Sans) },
                        textStyle = TextStyle(fontFamily = Sans, fontSize = 16.sp, color = c.ink),
                        colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                }
                2 -> {
                    Title("כמה משניות ביום?", "אפשר לשנות אחר כך בהגדרות המתקדמות.")
                    Stepper(pace, onChange = { pace = it.coerceIn(1, 30) })
                    val years = (Mishnayot.total - startIndex).toDouble() / pace / 365
                    Text("סיום ששת הסדרים בעוד כ-${"%.1f".format(years)} שנים", color = c.muted, fontFamily = Sans,
                        fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                3 -> {
                    Title("מאיפה מתחילים?", "ברירת המחדל: תחילת הש״ס.")
                    StartPicker(startIndex) { startIndex = it }
                }
                4 -> {
                    Title("מתי התחלת?", "אם כבר התחלת ללמוד לפני האפליקציה, בחר את התאריך שבו התחלת.")
                    StartDateStep(
                        today = today,
                        startDate = LocalDate.ofEpochDay(startDate),
                        onStartDate = { startDate = it.toEpochDay(); notStudied.clear() },
                        startIndex = startIndex, pace = pace, notStudied = notStudied,
                    )
                }
                5 -> {
                    Title("מיקום והתראות", "המיקום משמש לחישוב הזריחה, שבה מתחלף יום הלימוד.")
                    LocationStep(place) { place = it }
                    if (Build.VERSION.SDK_INT >= 33) NotificationStep()
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
            if (step > 1) GhostButton("חזרה", Modifier.width(100.dp)) { step-- }
            PrimaryButton(if (step == STEPS) "בוא נתחיל" else "המשך", Modifier.weight(1f)) {
                if (step < STEPS) {
                    step++
                } else {
                    val plan = StudyPlan.create(
                        startDate = LocalDate.ofEpochDay(startDate), startIndex = startIndex, pace = pace, today = today,
                        notStudied = notStudied.map { LocalDate.ofEpochDay(it) }.toSet(),
                    )
                    onDone(name.trim(), place, plan)
                }
            }
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    val c = LocalBook.current
    Text(title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = c.ink)
    Text(sub, fontFamily = Sans, fontSize = 14.sp, color = c.muted, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
}

@Composable
private fun fieldColors() = LocalBook.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.line, focusedLabelColor = c.accent,
        unfocusedLabelColor = c.muted, cursorColor = c.accent, focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateStep(
    today: LocalDate,
    startDate: LocalDate,
    onStartDate: (LocalDate) -> Unit,
    startIndex: Int,
    pace: Int,
    notStudied: MutableList<Long>,
) {
    val c = LocalBook.current
    var picking by remember { mutableStateOf(false) }
    val past = startDate.isBefore(today)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
        Choice("היום", !past, Modifier.weight(1f)) { onStartDate(today) }
        Choice("תאריך בעבר", past, Modifier.weight(1f)) { picking = true }
    }
    if (past) {
        Text("תאריך התחלה: ${JewishDays.hebrewDate(startDate)}", fontFamily = Sans, fontSize = 15.sp, color = c.ink,
            modifier = Modifier.clickable { picking = true }.padding(vertical = 6.dp))
        Text("סמן אילו ימים למדת בפועל", fontFamily = Sans, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 8.dp))
        // Preview the backfill with the current choices, oldest first.
        val plan = StudyPlan.create(startDate, startIndex, pace, today, notStudied.map { LocalDate.ofEpochDay(it) }.toSet())
        generateSequence(startDate) { it.plusDays(1) }.takeWhile { it.isBefore(today) }.forEach { d ->
            val day = plan.day(d)
            val studied = d.toEpochDay() !in notStudied
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (studied) notStudied.add(d.toEpochDay()) else notStudied.remove(d.toEpochDay())
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(studied, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                Text(JewishDays.hebrewDate(d).substringBeforeLast(' '), fontFamily = Sans, fontSize = 14.sp, color = c.ink,
                    modifier = Modifier.padding(start = 8.dp).weight(1f))
                if (day != null && day.count > 0) Text(Mishnayot.describe(day.start, day.count), fontFamily = Serif, fontSize = 14.sp, color = c.muted)
            }
        }
        Text("יום שלא סומן לא מקדם את הלימוד. הלימוד של היום מתחיל מהמקום שבו נעצרת.",
            fontFamily = Sans, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 10.dp))
    }
    if (picking) {
        val zoneMillis = { d: LocalDate -> d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = zoneMillis(if (past) startDate else today.minusDays(7)),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= zoneMillis(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onStartDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("אישור", fontFamily = Sans, color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("ביטול", fontFamily = Sans, color = c.muted) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalBook.current
    Text(
        label, fontFamily = Sans, fontSize = 15.sp, color = c.ink, textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(c.surface)
            .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp),
    )
}

@Composable
private fun LocationStep(place: Place, onPlace: (Place) -> Unit) {
    val c = LocalBook.current
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) {
            lastLocation(context)?.also(onPlace)?.let { "✓ המיקום נקבע" } ?: "לא נמצא מיקום. בחר עיר מהרשימה."
        } else {
            "בלי הרשאת מיקום. בחר עיר מהרשימה."
        }
    }
    GhostButton("השתמש במיקום שלי", Modifier.fillMaxWidth()) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            status = lastLocation(context)?.also(onPlace)?.let { "✓ המיקום נקבע" } ?: "לא נמצא מיקום. בחר עיר מהרשימה."
        } else {
            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    status?.let { Text(it, fontFamily = Sans, fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(16.dp))
    val options = if (place in Place.CITIES) Place.CITIES else listOf(place) + Place.CITIES
    Picker("עיר (משמשת גם כשאין מיקום)", options, place, { it.name }, onPlace)
}

@Composable
private fun NotificationStep() {
    val c = LocalBook.current
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Spacer(Modifier.height(20.dp))
    Text("תזכורת בבוקר, ותזכורת נוספת בערב אם הלימוד לא הושלם.", fontFamily = Sans, fontSize = 14.sp, color = c.muted,
        modifier = Modifier.padding(bottom = 10.dp))
    GhostButton(if (granted) "✓ התראות מאופשרות" else "אפשר התראות", Modifier.fillMaxWidth(), enabled = !granted) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@SuppressLint("MissingPermission")
private fun lastLocation(context: Context): Place? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time } ?: return null
    return Place("המיקום שלי", loc.latitude, loc.longitude, loc.altitude)
}
