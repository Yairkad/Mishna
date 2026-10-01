package app.mishna.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import app.mishna.R
import app.mishna.core.content.Marker
import app.mishna.core.content.Mishnayot
import androidx.compose.ui.text.style.BaselineShift
import app.mishna.ui.common.GhostButton
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif
import kotlinx.coroutines.launch

data class ReadingPrefs(val fontScale: Float = 1f, val lineHeight: Float = 1.85f, val showIkarTosafotYomTov: Boolean = true)

private const val MIN_SPLIT = 0.2f
private const val MAX_SPLIT = 0.8f

/**
 * The study screen (DESIGN.md §3.3): mishna on top, commentary below, a draggable divider
 * between them, and one slim navigation row at the bottom.
 */
@Composable
fun StudyScreen(
    sections: List<StudySection>,
    dayNumber: Int,
    hebrewDate: String,
    done: Boolean,
    prefs: ReadingPrefs,
    keepScreenOn: Boolean,
    initialPage: Int,
    splitRatio: Float,
    onSplitRatio: (Float) -> Unit,
    onPageChange: (Int) -> Unit,
    onFinish: () -> Unit,
    onShiftDay: ((forward: Boolean) -> Unit)?,
    finishLabel: String = "סיימתי את הלימוד היום",
    summaryTitle: String = "סיימת את הלימוד להיום",
    vm: StudyViewModel = viewModel(),
) {
    LaunchedEffect(sections) { vm.load(sections) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = LocalBook.current
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    if (ui.pages.isEmpty() || ui.sections != sections) {
        Box(Modifier.fillMaxSize().background(c.bg))
        return
    }
    key(sections) {
        val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, ui.pages.lastIndex)) { ui.pages.size }
        val scope = rememberCoroutineScope()
        var bottomReached by rememberSaveable { mutableStateOf(false) }
        var ratio by remember { mutableFloatStateOf(splitRatio.coerceIn(MIN_SPLIT, MAX_SPLIT)) }
        var commentaryTab by rememberSaveable { mutableIntStateOf(0) }
        var summary by remember { mutableStateOf(false) }
        var shiftSheet by remember { mutableStateOf(false) }
        var confirmShift by remember { mutableStateOf<Boolean?>(null) }
        val last = pager.currentPage == ui.pages.lastIndex
        LaunchedEffect(pager.currentPage) { onPageChange(pager.currentPage) }

        Column(Modifier.fillMaxSize().background(c.bg)) {
            // Header
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val label = ui.pages[pager.currentPage].label
                        Text(label ?: "${ui.seder} · יום $dayNumber · $hebrewDate", color = if (label != null) c.accent else c.muted, fontSize = 12.sp, fontFamily = Sans)
                        Text(ui.pages[pager.currentPage].title, color = c.ink, fontSize = 19.sp, fontFamily = Serif, fontWeight = FontWeight.Bold)
                    }
                    if (onShiftDay != null) TextButton(onClick = { shiftSheet = true }) { Text("⋯", color = c.muted, fontSize = 22.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
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
                SplitPage(
                    page = ui.pages[page],
                    prefs = prefs,
                    ratio = ratio,
                    onRatio = { ratio = it },
                    onRatioDone = { onSplitRatio(ratio) },
                    tab = commentaryTab,
                    onTab = { commentaryTab = it },
                    isCurrent = page == pager.currentPage,
                    onMishnaEnd = { if (page == ui.pages.lastIndex) bottomReached = true },
                )
            }

            SlimFooter(
                page = pager.currentPage,
                pages = ui.pages.size,
                done = done,
                canFinish = bottomReached,
                onPrev = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                finishLabel = finishLabel,
                onFinish = { summary = true },
            )
        }

        if (summary) {
            AlertDialog(
                onDismissRequest = { summary = false },
                containerColor = c.bg,
                title = { Text(summaryTitle, fontFamily = Serif, fontWeight = FontWeight.Bold, color = c.ink) },
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
                confirmButton = { TextButton(onClick = { confirmShift = null; onShiftDay?.invoke(forward) }) { Text("אישור", color = c.accent, fontFamily = Sans) } },
                dismissButton = { TextButton(onClick = { confirmShift = null }) { Text("ביטול", color = c.muted, fontFamily = Sans) } },
            )
        }
    }
}

@Composable
private fun SplitPage(
    page: MishnaPage,
    prefs: ReadingPrefs,
    ratio: Float,
    onRatio: (Float) -> Unit,
    onRatioDone: () -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
    isCurrent: Boolean,
    onMishnaEnd: () -> Unit,
) {
    val c = LocalBook.current
    val density = LocalDensity.current
    val commentaries = buildList {
        page.bartenura?.let { add("ברטנורא" to it) }
        if (prefs.showIkarTosafotYomTov) page.ikarTosafotYomTov?.let { add("עיקר תוספות יום טוב" to it) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val totalPx = with(density) { maxHeight.toPx() }
        val drag = rememberDraggableState { delta -> onRatio((ratio + delta / totalPx).coerceIn(MIN_SPLIT, MAX_SPLIT)) }
        Column(Modifier.fillMaxSize()) {
            // Mishna: finishing needs only this pane scrolled to its end.
            val scroll = rememberScrollState()
            val atEnd by remember { derivedStateOf { scroll.value >= scroll.maxValue - 8 } }
            // Only the page on screen counts; the pager may compose the next page early.
            LaunchedEffect(atEnd, isCurrent) { if (atEnd && isCurrent) onMishnaEnd() }
            Box(Modifier.fillMaxWidth().weight(ratio).verticalScroll(scroll)) {
                Text(
                    if (prefs.showIkarTosafotYomTov) withMarkers(page.text, page.markers, c.accent) else AnnotatedString(page.text),
                    color = c.ink,
                    style = TextStyle(fontFamily = Serif, fontSize = (21 * prefs.fontScale).sp, lineHeight = prefs.lineHeight.em),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }

            // Divider: drag to resize.
            Box(
                Modifier.fillMaxWidth().height(20.dp).background(c.bg)
                    .draggable(drag, Orientation.Vertical, onDragStopped = { onRatioDone() }),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line).align(Alignment.TopCenter))
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.muted.copy(alpha = .5f)))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line).align(Alignment.BottomCenter))
            }

            // Commentary
            Column(Modifier.fillMaxWidth().weight(1f - ratio).background(c.soft.copy(alpha = .45f))) {
                if (commentaries.isEmpty()) {
                    Text("אין פירוש למשנה זו", color = c.muted, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.padding(18.dp))
                } else {
                    val current = tab.coerceIn(0, commentaries.lastIndex)
                    Row(Modifier.padding(start = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        commentaries.forEachIndexed { i, (name, _) ->
                            val on = i == current
                            Text(
                                name, fontFamily = Sans, fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                                color = if (on) c.accent else c.muted,
                                modifier = Modifier.clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                    .background(if (on) c.bg else c.bg.copy(alpha = 0f))
                                    .clickable { onTab(i) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Box(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
                        val raw = commentaries[current].second
                        val marked = if (prefs.showIkarTosafotYomTov && raw == page.bartenura) withMarkerTokens(raw, page.bartenuraMarkers) else raw
                        Text(
                            boldMarkup(marked, c.accent),
                            color = c.ink,
                            style = TextStyle(fontFamily = Serif, fontSize = (16 * prefs.fontScale).sp, lineHeight = (prefs.lineHeight - .1f).em),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlimFooter(
    page: Int,
    pages: Int,
    done: Boolean,
    canFinish: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    finishLabel: String,
    onFinish: () -> Unit,
) {
    val c = LocalBook.current
    val last = page == pages - 1
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // In RTL the first item sits on the right: "previous" points right, "next" points left.
        ArrowButton(R.drawable.ic_arrow_right, "המשנה הקודמת", enabled = page > 0, onClick = onPrev)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when {
                done -> FooterButton("הלימוד הושלם ✓", enabled = false) {}
                last -> FooterButton(if (canFinish) finishLabel else "גלול עד סוף המשנה לסיום", enabled = canFinish, onClick = onFinish)
                else -> Text("משנה ${page + 1} מתוך $pages", color = c.muted, fontFamily = Sans, fontSize = 14.sp)
            }
        }
        ArrowButton(R.drawable.ic_arrow_left, "המשנה הבאה", enabled = !last, onClick = onNext)
    }
}

@Composable
private fun ArrowButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalBook.current
    Box(
        Modifier.size(width = 48.dp, height = 44.dp).alpha(if (enabled) 1f else .3f)
            .clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), contentDescription = label, tint = c.ink, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun FooterButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalBook.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent, disabledContainerColor = c.soft, disabledContentColor = c.muted),
    ) { Text(text, fontFamily = Sans, fontSize = if (enabled) 15.sp else 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

/** Inserts the Ikar Tosafot Yom Tov letters, small and raised, like a printed Mishna. */
private fun withMarkers(text: String, markers: List<Marker>, color: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var at = 0
        for (m in markers.sortedBy { it.offset }) {
            append(text.substring(at, m.offset))
            withStyle(SpanStyle(color = color, fontSize = 0.6.em, baselineShift = BaselineShift(0.35f), fontFamily = Sans)) {
                append("(${m.label})")
            }
            at = m.offset
        }
        append(text.substring(at))
    }

private const val MARK_OPEN = '\u0001'
private const val MARK_CLOSE = '\u0002'

/** Puts marker letters into raw commentary text as MARK_OPEN label MARK_CLOSE, for [boldMarkup] to style. */
private fun withMarkerTokens(raw: String, markers: List<Marker>): String = buildString {
    var at = 0
    for (m in markers.sortedBy { it.offset }) {
        append(raw, at, m.offset)
        append(MARK_OPEN).append(m.label).append(MARK_CLOSE)
        at = m.offset
    }
    append(raw, at, raw.length)
}

/**
 * Commentary text keeps only <b>…</b> (the dibur hamatchil) and newlines between comments;
 * marker tokens become small raised letters in [markColor].
 */
private fun boldMarkup(s: String, markColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val token = Regex("<b>|</b>|$MARK_OPEN([^$MARK_CLOSE]*)$MARK_CLOSE|\n")
    var bold = false
    var at = 0
    fun text(t: String) = if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) } else append(t)
    for (m in token.findAll(s)) {
        text(s.substring(at, m.range.first))
        when (m.value) {
            "<b>" -> bold = true
            "</b>" -> bold = false
            "\n" -> append("\n\n")
            else -> withStyle(SpanStyle(color = markColor, fontSize = 0.6.em, baselineShift = BaselineShift(0.35f), fontFamily = Sans)) {
                append("(${m.groupValues[1]})")
            }
        }
        at = m.range.last + 1
    }
    text(s.substring(at))
}
