package app.mishna.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mishna.core.content.Mishnayot
import app.mishna.core.plan.CompletionMode
import app.mishna.core.plan.ReviewState
import app.mishna.core.plan.StudyPlan
import app.mishna.core.plan.dueReviews
import app.mishna.core.plan.stats
import app.mishna.core.plan.streaks
import app.mishna.core.time.HebrewMonth
import app.mishna.core.time.JewishDays
import app.mishna.ui.common.BookCard
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif
import java.time.LocalDate

private val WEEKDAYS = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש")

/** History: streaks, stats and a Hebrew-month calendar (DESIGN.md §3.5). */
@Composable
fun HistoryScreen(plan: StudyPlan, today: LocalDate, review: ReviewState? = null) {
    val c = LocalBook.current
    val streaks = plan.streaks(today)
    val stats = plan.stats(today)
    var month by remember { mutableStateOf(HebrewMonth.of(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${streaks.current}", "רצף נוכחי (ימים)", Modifier.weight(1f))
            StatTile("${streaks.best}", "שיא רצף", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${stats.learned}", "משניות שנלמדו", Modifier.weight(1f))
            StatTile("${stats.remaining}", "משניות שנותרו", Modifier.weight(1f))
        }
        if (review != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${review.done.size}", "חזרות שבוצעו", Modifier.weight(1f))
                if (review.enabled) StatTile("${plan.dueReviews(review, today).sumOf { it.count }}", "משניות לחזרה היום", Modifier.weight(1f))
            }
        }

        BookCard(Modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.previous() }) { Text("›", fontSize = 22.sp, color = c.muted) }
                Text(month.title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.ink,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                TextButton(onClick = { month = month.next() }) { Text("‹", fontSize = 22.sp, color = c.muted) }
            }
            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEach { Text(it, fontSize = 11.sp, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            // Sunday = column 0.
            val lead = month.firstDay.dayOfWeek.value % 7
            val cells: List<LocalDate?> = List(lead) { null } + month.days
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { d ->
                        val reviewed = d != null && review != null && review.doneOn(d).isNotEmpty()
                        DayCell(d, plan, today, reviewed, Modifier.weight(1f)) { selected = d }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.done, "בוצע")
                Legend(c.holy, "שבת/חג (מהספר)")
                Legend(null, "לא הושלם")
                if (review != null && review.done.isNotEmpty()) Legend(c.accent, "• חזרה")
            }
        }

        Column {
            InfoRow("תאריך סיום משוער", stats.estimatedFinish?.let { JewishDays.hebrewDate(it) } ?: "הסתיים")
            HorizontalDivider(color = c.line)
            InfoRow("ימי לימוד שהושלמו", "${stats.completedDays}")
            if (plan.cycle > 1) {
                HorizontalDivider(color = c.line)
                InfoRow("מחזור", "${plan.cycle}")
            }
        }
    }

    selected?.let { d ->
        val day = plan.day(d)
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = c.bg,
            title = { Text(JewishDays.hebrewDate(d), fontFamily = Serif, fontWeight = FontWeight.Bold, color = c.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (day == null || day.count == 0) {
                        Text("אין לימוד ביום זה", fontFamily = Sans, color = c.muted)
                    } else {
                        Text(Mishnayot.describe(day.start, day.count), fontFamily = Serif, fontSize = 17.sp, color = c.ink)
                        Text("יום ${plan.dayNumber(d)} בתוכנית · ${day.count} משניות", fontFamily = Sans, color = c.muted, fontSize = 13.sp)
                        Text(status(day.completed, d, today), fontFamily = Sans, color = c.ink, fontSize = 14.sp)
                    }
                    review?.doneOn(d)?.takeIf { it.isNotEmpty() }?.let { keys ->
                        Text("חזרה: " + keys.joinToString(" · ") { k ->
                            plan.day(k.learned)?.let { Mishnayot.describe(it.start, it.count) } ?: k.label
                        }, fontFamily = Sans, color = c.muted, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("סגירה", color = c.accent, fontFamily = Sans) } },
        )
    }
}

private fun status(mode: CompletionMode?, d: LocalDate, today: LocalDate) = when (mode) {
    CompletionMode.APP -> "בוצע"
    CompletionMode.BOOK -> "בוצע · סומן אחרי שבת/חג"
    CompletionMode.BACKFILL -> "בוצע · לפני האפליקציה"
    null -> if (d == today) "טרם הושלם" else "לא הושלם"
}

@Composable
private fun DayCell(d: LocalDate?, plan: StudyPlan, today: LocalDate, reviewed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalBook.current
    if (d == null) {
        Spacer(modifier)
        return
    }
    val day = plan.day(d)
    val fill: Color? = when (day?.completed) {
        CompletionMode.BOOK -> c.holy
        CompletionMode.APP, CompletionMode.BACKFILL -> c.done
        null -> null
    }
    val missed = day != null && !day.done && d.isBefore(today)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.aspectRatio(1f).clip(shape)
            .background(fill ?: Color.Transparent)
            .border(
                width = if (d == today) 2.dp else if (missed) 1.5.dp else 0.dp,
                color = if (d == today) c.accent else if (missed) c.miss else Color.Transparent,
                shape = shape,
            )
            .clickable(enabled = day != null || reviewed, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(HebrewMonth.dayLabel(d), fontFamily = Serif, fontSize = 14.sp,
                color = when { fill != null -> c.onAccent; d.isAfter(today) -> c.muted; else -> c.ink })
            Text("${d.dayOfMonth}", fontFamily = Sans, fontSize = 9.sp, color = if (fill != null) c.onAccent.copy(alpha = .8f) else c.muted)
            if (reviewed) Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (fill != null) c.onAccent else c.accent))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    val c = LocalBook.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, fontFamily = Serif, fontSize = 26.sp, color = c.ink)
        Text(label, fontFamily = Sans, fontSize = 12.sp, color = c.muted)
    }
}

@Composable
private fun Legend(color: Color?, label: String) {
    val c = LocalBook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color ?: Color.Transparent)
            .border(if (color == null) 1.5.dp else 0.dp, c.miss, RoundedCornerShape(3.dp)))
        Text(label, fontFamily = Sans, fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalBook.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Sans, fontSize = 14.sp, color = c.ink, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Serif, fontSize = 16.sp, color = c.ink)
    }
}
