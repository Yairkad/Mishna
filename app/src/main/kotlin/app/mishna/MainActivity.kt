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
import app.mishna.ui.study.StudyScreen
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.MishnaTheme
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MishnaTheme { App() } }
    }
}

private enum class Tab(val label: String, val icon: Int) {
    STUDY("לימוד", R.drawable.ic_tab_study),
    HISTORY("היסטוריה", R.drawable.ic_tab_history),
    SETTINGS("הגדרות", R.drawable.ic_tab_settings),
}

@Composable
private fun App() {
    val c = LocalBook.current
    var tab by rememberSaveable { mutableStateOf(Tab.STUDY) }
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.STUDY -> StudyScreen()
                Tab.HISTORY -> ComingSoon("היסטוריה")
                Tab.SETTINGS -> ComingSoon("הגדרות")
            }
        }
        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp, modifier = Modifier.height(72.dp)) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = tab == t,
                    onClick = { tab = t },
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

@Composable
private fun ComingSoon(name: String) {
    val c = LocalBook.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name · בשלב הבא", color = c.muted, fontFamily = Serif, fontSize = 18.sp)
    }
}
