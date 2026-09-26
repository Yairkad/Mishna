package app.mishna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mishna.core.state.ThemeMode
import app.mishna.core.time.JewishDays
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.zIndex
import app.mishna.ui.history.HistoryScreen
import app.mishna.ui.home.HomeScreen
import app.mishna.ui.settings.SettingsScreen
import app.mishna.ui.onboarding.OnboardingScreen
import app.mishna.ui.study.ReadingPrefs
import app.mishna.ui.study.StudyScreen
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.MishnaTheme
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = when (state.prefs.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MishnaTheme(dark) { App(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}

private enum class Tab(val label: String, val icon: Int) {
    STUDY("לימוד", R.drawable.ic_tab_study),
    HISTORY("היסטוריה", R.drawable.ic_tab_history),
    SETTINGS("הגדרות", R.drawable.ic_tab_settings),
}

@Composable
private fun App(vm: AppViewModel) {
    val c = LocalBook.current
    val state by vm.state.collectAsStateWithLifecycle()
    val today by vm.studyDate.collectAsStateWithLifecycle()
    val plan = state.plan
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }
    Box(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).zIndex(1f))
        if (plan == null) {
            OnboardingScreen(today) { name, place, p -> vm.finishOnboarding(name, place, p) }
            return@Box
        }
        var tab by rememberSaveable { mutableStateOf(Tab.STUDY) }
        // Opening the app shows the opening card; the study tab goes straight to the text.
        var showHome by rememberSaveable { mutableStateOf(true) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val day = plan.day(today)
                when {
                    tab == Tab.STUDY && (showHome || day == null || day.count == 0) -> HomeScreen(
                        name = state.name, plan = plan, today = today,
                        onStart = { showHome = false },
                        onMarkHoly = vm::markHolyDays,
                        onNewCycle = vm::startNewCycle,
                    )
                    tab == Tab.STUDY && day != null -> StudyScreen(
                        start = day.start, count = day.count,
                        dayNumber = plan.dayNumber(today),
                        hebrewDate = JewishDays.hebrewDate(today),
                        done = day.done,
                        prefs = ReadingPrefs(state.prefs.fontScale, state.prefs.lineSpacing.factor, state.prefs.showIkarTosafotYomTov),
                        keepScreenOn = state.prefs.keepScreenOn,
                        initialPage = if (state.readingDate == today) state.readingPage else 0,
                        splitRatio = state.prefs.splitRatio,
                        onSplitRatio = { r -> vm.updatePrefs { it.copy(splitRatio = r) } },
                        onPageChange = vm::setReadingPage,
                        onFinish = { vm.completeToday(); showHome = true; tab = Tab.HISTORY },
                        onShiftDay = vm::shiftDay,
                    )
                    tab == Tab.HISTORY -> HistoryScreen(plan, today)
                    else -> SettingsScreen(
                        state = state, today = today, versionName = BuildConfig.VERSION_NAME,
                        onPrefs = { change -> vm.updatePrefs(change) },
                        onPlace = vm::setPlace,
                        onPace = vm::changePace,
                        onMoveTo = vm::moveTo,
                        onExport = vm::exportBackup,
                        onImport = vm::importBackup,
                        onReset = vm::reset,
                    )
                }
            }
            NavigationBar(containerColor = c.surface, tonalElevation = 0.dp, modifier = Modifier.height(72.dp)) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (t == Tab.STUDY && tab == Tab.STUDY) showHome = !showHome
                            if (t == Tab.STUDY && tab != Tab.STUDY) showHome = false
                            tab = t
                        },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label, fontFamily = Sans, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accent, selectedTextColor = c.accent,
                            unselectedIconColor = c.muted, unselectedTextColor = c.muted, indicatorColor = c.soft,
                        ),
                    )
                }
            }
        }
    }
}
