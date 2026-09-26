package app.mishna.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import app.mishna.core.content.Mishnayot
import app.mishna.ui.common.GhostButton
import app.mishna.ui.common.PrimaryButton
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif
import kotlinx.coroutines.launch

data class ReadingPrefs(val fontScale: Float = 1f, val lineHeight: Float = 1.85f, val showIkarTosafotYomTov: Boolean = true)

/** The study screen (DESIGN.md §3.3). */
@Composable
fun StudyScreen(
    start: Int,
    count: Int,
    dayNumber: Int,
    hebrewDate: String,
    done: Boolean,
    prefs: ReadingPrefs,
    keepScreenOn: Boolean,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    onFinish: () -> Unit,
    onShiftDay: (forward: Boolean) -> Unit,
    vm: StudyViewModel = viewModel(),
) {
    LaunchedEffect(start, count) { vm.load(start, count) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = LocalBook.current
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    if (ui.pages.isEmpty() || ui.heading != Mishnayot.describe(start, count)) {
        Box(Modifier.fillMaxSize().background(c.bg))
        return
    }
    key(start, count) {
        val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, ui.pages.lastIndex)) { ui.pages.size }
        val scope = rememberCoroutineScope()
        var bottomReached by rememberSaveable { mutableStateOf(false) }
        var summary by remember { mutableStateOf(false) }
        var shiftSheet by remember { mutableStateOf(false) }
        var confirmShift by remember { mutableStateOf<Boolean?>(null) }
        val last = pager.currentPage == ui.pages.lastIndex
        LaunchedEffect(pager.currentPage) { onPageChange(pager.currentPage) }

        Column(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${ui.seder} · יום $dayNumber · $hebrewDate", color = c.muted, fontSize = 12.sp, fontFamily = Sans)
                        Text(ui.pages[pager.currentPage].title, color = c.ink, fontSize = 19.sp, fontFamily = Serif, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { shiftSheet = true }) { Text("⋯", color = c.muted, fontSize = 22.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    ui.pages.indices.forEach { i ->
                        val color = when {
                            i == pager.currentPage -> c.accent
                            i < pager.currentPage -> c.muted
                            else -> c.line
                        }
                        Box(Modifier.width(24.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val scroll = rememberScrollState()
                val atBottom by remember { derivedStateOf { scroll.value >= scroll.maxValue - 8 } }
                LaunchedEffect(atBottom) { if (page == ui.pages.lastIndex && atBottom) bottomReached = true }
                MishnaPageView(ui.pages[page], prefs, Modifier.verticalScroll(scroll))
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            if (last && !bottomReached && !done) {
                Text("גלול עד סוף המשנה כדי לסיים", color = c.muted, fontSize = 11.sp, fontFamily = Sans,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PagerButton("→  המשנה הקודמת", enabled = pager.currentPage > 0, modifier = Modifier.weight(1f)) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                    }
                    Text("${pager.currentPage + 1} / ${ui.pages.size}", color = c.muted, fontFamily = Serif, fontSize = 15.sp)
                    PagerButton("המשנה הבאה  ←", enabled = !last, modifier = Modifier.weight(1f)) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
                PrimaryButton(if (done) "הלימוד הושלם ✓" else "סיימתי את הלימוד היום", enabled = last && bottomReached && !done) { summary = true }
            }
        }

        if (summary) {
            AlertDialog(
                onDismissRequest = { summary = false },
                containerColor = c.bg,
                title = { Text("סיימת את הלימוד להיום", fontFamily = Serif, fontWeight = FontWeight.Bold, color = c.ink) },
                text = { Text("${ui.heading}\n${ui.pages.size} משניות", fontFamily = Serif, color = c.muted, fontSize = 16.sp) },
                confirmButton = { TextButton(onClick = { summary = false; onFinish() }) { Text("אישור", color = c.accent, fontFamily = Sans) } },
            )
        }
        if (shiftSheet) {
            AlertDialog(
                onDismissRequest = { shiftSheet = false },
                containerColor = c.bg,
                title = { Text("תיקון מיקום בלימוד", fontFamily = Serif, fontWeight = FontWeight.Bold, color = c.ink) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("לשימוש כשלמדת יותר או פחות מחוץ לאפליקציה. ההיסטוריה לא משתנה.", fontFamily = Sans, color = c.muted, fontSize = 14.sp)
                        GhostButton("למדתי יותר · יום קדימה", Modifier.fillMaxWidth()) { shiftSheet = false; confirmShift = true }
                        GhostButton("למדתי פחות · יום אחורה", Modifier.fillMaxWidth()) { shiftSheet = false; confirmShift = false }
                    }
                },
                confirmButton = { TextButton(onClick = { shiftSheet = false }) { Text("סגירה", color = c.muted, fontFamily = Sans) } },
            )
        }
        confirmShift?.let { forward ->
            AlertDialog(
                onDismissRequest = { confirmShift = null },
                containerColor = c.bg,
                title = { Text(if (forward) "להזיז יום קדימה?" else "להזיז יום אחורה?", fontFamily = Serif, fontWeight = FontWeight.Bold, color = c.ink) },
                text = { Text(if (done) "השינוי יחול מהלימוד של מחר." else "הלימוד של היום ישתנה בהתאם.", fontFamily = Sans, color = c.muted) },
                confirmButton = { TextButton(onClick = { confirmShift = null; onShiftDay(forward) }) { Text("אישור", color = c.accent, fontFamily = Sans) } },
                dismissButton = { TextButton(onClick = { confirmShift = null }) { Text("ביטול", color = c.muted, fontFamily = Sans) } },
            )
        }
    }
}

@Composable
private fun PagerButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalBook.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = c.ink, disabledContainerColor = c.surface.copy(alpha = .35f), disabledContentColor = c.muted.copy(alpha = .5f)),
    ) { Text(label, fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
private fun MishnaPageView(page: MishnaPage, prefs: ReadingPrefs, modifier: Modifier) {
    val c = LocalBook.current
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(
            page.text,
            color = c.ink,
            style = TextStyle(fontFamily = Serif, fontSize = (21 * prefs.fontScale).sp, lineHeight = prefs.lineHeight.em),
        )
        Spacer(Modifier.height(18.dp))
        page.bartenura?.let { Commentary("ברטנורא", it, prefs, initiallyOpen = true) }
        if (prefs.showIkarTosafotYomTov) page.ikarTosafotYomTov?.let { Commentary("עיקר תוספות יום טוב", it, prefs, initiallyOpen = false) }
    }
}

@Composable
private fun Commentary(name: String, body: String, prefs: ReadingPrefs, initiallyOpen: Boolean) {
    val c = LocalBook.current
    var open by rememberSaveable(name) { mutableStateOf(initiallyOpen) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(c.soft.copy(alpha = .6f)),
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(name, color = c.ink, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (open) "–" else "+", color = c.muted, fontFamily = Sans)
        }
        AnimatedVisibility(open) {
            Text(
                boldMarkup(body),
                color = c.ink,
                style = TextStyle(fontFamily = Serif, fontSize = (16 * prefs.fontScale).sp, lineHeight = (prefs.lineHeight - .1f).em),
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            )
        }
    }
}

/** Commentary text keeps only <b>…</b> (the dibur hamatchil) and newlines between comments. */
private fun boldMarkup(s: String): AnnotatedString = buildAnnotatedString {
    var rest = s.replace("\n", "\n\n")
    while (true) {
        val open = rest.indexOf("<b>")
        if (open < 0) { append(rest); break }
        append(rest.substring(0, open))
        val close = rest.indexOf("</b>", open)
        if (close < 0) { append(rest.substring(open + 3)); break }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(open + 3, close)) }
        rest = rest.substring(close + 4)
    }
}
