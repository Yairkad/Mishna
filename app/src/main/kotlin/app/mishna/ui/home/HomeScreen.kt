package app.mishna.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mishna.core.content.Mishnayot
import app.mishna.core.plan.Marking
import app.mishna.core.plan.ReviewItem
import app.mishna.core.plan.ReviewKey
import app.mishna.core.plan.grouped
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mishna.ui.common.GhostButton
import app.mishna.core.plan.StudyPlan
import app.mishna.core.plan.streaks
import app.mishna.core.time.DayType
import app.mishna.core.time.JewishDays
import app.mishna.ui.common.BookCard
import app.mishna.ui.common.Eyebrow
import app.mishna.ui.common.PrimaryButton
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif
import java.time.LocalDate
import java.time.LocalTime

/** The opening card (DESIGN.md §3.2). */
@Composable
fun HomeScreen(
    name: String,
    plan: StudyPlan,
    today: LocalDate,
    onStart: () -> Unit,
    onMarkHoly: (dates: List<LocalDate>, reviews: List<ReviewKey>) -> Unit,
    onNewCycle: () -> Unit,
    /** Due reviews, or null when the review plan is off. */
    reviews: List<ReviewItem>? = null,
    /** Reviews that fall on the coming Shabbat/Yom Tov (shown on Erev Shabbat). */
    reviewsAhead: List<ReviewItem> = emptyList(),
    reviewsDoneToday: Int = 0,
    onStartReview: () -> Unit = {},
) {
    val c = LocalBook.current
    val day = plan.day(today)
    val streak = plan.streaks(today).current
    val holyChain = Marking.markableDates(today).filter { JewishDays.isHoly(it) }
    val unmarkedHoly = holyChain.filter { plan.day(it)?.done == false }

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column {
            Text("${greeting()}${if (name.isNotBlank()) ", $name" else ""}", color = c.muted, fontFamily = Sans, fontSize = 15.sp)
            Text(JewishDays.hebrewDate(today), color = c.ink, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            if (streak > 0) Text("רצף נוכחי: $streak ימים", color = c.muted, fontFamily = Sans, fontSize = 13.sp)
        }

        when {
            plan.finished && day?.done != false -> BookCard {
                Eyebrow("סיום ששת סדרי משנה")
                Text("הדרן עלך ששה סדרי משנה", fontFamily = Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.ink,
                    modifier = Modifier.padding(vertical = 8.dp))
                Text("סיימת את מחזור ${plan.cycle}. ההיסטוריה נשמרת.", fontFamily = Sans, fontSize = 14.sp, color = c.muted)
                Spacer(Modifier.height(14.dp))
                PrimaryButton("התחל מחזור חדש", onClick = onNewCycle)
            }

            unmarkedHoly.isNotEmpty() -> HolyMarkCard(plan, unmarkedHoly, reviews.orEmpty(), onMarkHoly)

            JewishDays.type(today) == DayType.EREV && day != null -> BookCard {
                Eyebrow("ערב ${if (today.dayOfWeek.value == 5) "שבת" else "חג"} · הלימוד לימים הקרובים")
                Text("בשבת ובחג לומדים מהספר. במוצאי היום תתבקש לסמן מה נלמד.",
                    fontFamily = Sans, fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(vertical = 8.dp))
                AssignmentRow("היום", Mishnayot.describe(day.start, day.count), done = day.done)
                plan.preview(today, JewishDays.holyDaysAfter(today)).filter { it.count > 0 }.forEach {
                    HorizontalDivider(color = c.line)
                    AssignmentRow(JewishDays.hebrewDate(it.date).substringBeforeLast(' '), Mishnayot.describe(it.start, it.count), tag = "מהספר")
                }
            }

            day != null && day.count > 0 -> BookCard {
                if (day.done) {
                    Text("סיימת את הלימוד להיום ✓", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.done,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                Eyebrow("הלימוד של היום · יום ${plan.dayNumber(today)} בתוכנית")
                val ref = Mishnayot.ref(day.start)
                Text(Mishnayot.describe(day.start, day.count), fontFamily = Serif, fontSize = 26.sp, lineHeight = 34.sp,
                    color = c.ink, modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${day.count} משניות", color = c.muted, fontFamily = Sans, fontSize = 13.sp)
                    Text("סדר ${ref.seder}", color = c.muted, fontFamily = Sans, fontSize = 13.sp)
                }
            }
        }

        val showStudy = day != null && day.count > 0 && !(plan.finished && day.done)
        if (showStudy && day?.done == false) PrimaryButton("התחל ללמוד", onClick = onStart)

        if (reviews != null && unmarkedHoly.isEmpty()) {
            ReviewCard(reviews, reviewsAhead, reviewsDoneToday, primary = day?.done == true, onStart = onStartReview)
        }

        if (showStudy && day?.done == true) GhostButton("למד שוב את הלימוד של היום", Modifier.fillMaxWidth(), onClick = onStart)
    }
}

/** "Review for today" (SPEC §12): one row per stage, then a button that opens the review. */
@Composable
private fun ReviewCard(due: List<ReviewItem>, ahead: List<ReviewItem>, doneToday: Int, primary: Boolean, onStart: () -> Unit) {
    val c = LocalBook.current
    if (due.isEmpty() && ahead.isEmpty()) {
        if (doneToday > 0) Text("החזרה של היום הושלמה ✓", color = c.done, fontFamily = Sans, fontSize = 14.sp)
        return
    }
    BookCard {
        if (due.isNotEmpty()) {
            Eyebrow("חזרה להיום · ${due.sumOf { it.count }} משניות")
            due.grouped().forEachIndexed { i, g ->
                if (i > 0) HorizontalDivider(color = c.line)
                AssignmentRow(g.label, g.items.joinToString(" · ") { Mishnayot.describe(it.start, it.count) })
            }
            Spacer(Modifier.height(10.dp))
            if (primary) PrimaryButton("התחל חזרה", onClick = onStart) else GhostButton("התחל חזרה", Modifier.fillMaxWidth(), onClick = onStart)
        }
        if (ahead.isNotEmpty()) {
            Eyebrow("חזרה בשבת ובחג (מהספר) · ${ahead.sumOf { it.count }} משניות", Modifier.padding(top = if (due.isEmpty()) 0.dp else 14.dp))
            ahead.grouped().forEach { g ->
                AssignmentRow(g.label, g.items.joinToString(" · ") { Mishnayot.describe(it.start, it.count) }, tag = "מהספר")
            }
        }
    }
}

@Composable
private fun HolyMarkCard(
    plan: StudyPlan,
    dates: List<LocalDate>,
    reviews: List<ReviewItem>,
    onMark: (List<LocalDate>, List<ReviewKey>) -> Unit,
) {
    val c = LocalBook.current
    val checked = remember(dates) { mutableStateListOf(*dates.toTypedArray()) }
    var reviewChecked by remember(reviews) { mutableStateOf(true) }
    // Marking re-anchors each day at the first unstudied mishna, in date order.
    val preview = buildMap {
        var from = plan.nextIndex
        for (d in dates.sorted()) {
            val n = minOf(plan.pace, plan.total - from)
            put(d, from to n)
            from += n
        }
    }
    BookCard {
        Eyebrow("מוצאי ${if (dates.size == 1 && dates.single().dayOfWeek.value == 6) "שבת" else "שבת/חג"} · עד הזריחה")
        Text("האם למדת מהספר?", fontFamily = Serif, fontSize = 21.sp, color = c.ink, modifier = Modifier.padding(vertical = 6.dp))
        dates.forEach { d ->
            val p = preview[d]
            Row(
                Modifier.fillMaxWidth().clickable { if (d in checked) checked.remove(d) else checked.add(d) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(d in checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                Text(JewishDays.hebrewDate(d).substringBeforeLast(' '), fontFamily = Sans, fontSize = 14.sp, color = c.ink,
                    modifier = Modifier.padding(start = 8.dp).weight(1f))
                if (p != null && p.second > 0) Text(Mishnayot.describe(p.first, p.second), fontFamily = Serif, fontSize = 15.sp, color = c.muted)
            }
        }
        if (reviews.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clickable { reviewChecked = !reviewChecked }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(reviewChecked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                Text("גם החזרה", fontFamily = Sans, fontSize = 14.sp, color = c.ink, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Text("${reviews.sumOf { it.count }} משניות", fontFamily = Sans, fontSize = 13.sp, color = c.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("סמן כבוצע", enabled = checked.isNotEmpty() || (reviewChecked && reviews.isNotEmpty())) {
            onMark(checked.toList(), if (reviewChecked) reviews.map { it.key } else emptyList())
        }
    }
}

@Composable
private fun AssignmentRow(label: String, ref: String, done: Boolean = false, tag: String? = null) {
    val c = LocalBook.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Sans, fontSize = 14.sp, color = c.ink)
        if (tag != null) Tag(tag, c.soft, c.muted, Modifier.padding(start = 6.dp))
        if (done) Tag("✓", c.done, c.onAccent, Modifier.padding(start = 6.dp))
        Spacer(Modifier.weight(1f))
        Text(ref, fontFamily = Serif, fontSize = 16.sp, color = c.ink)
    }
}

@Composable
private fun Tag(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(text, color = fg, fontFamily = Sans, fontSize = 11.sp,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "בוקר טוב"
    in 12..16 -> "צהריים טובים"
    in 17..21 -> "ערב טוב"
    else -> "לילה טוב"
}
