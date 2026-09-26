package app.mishna.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import app.mishna.R

/** "Classic book" palette, DESIGN.md §1. */
@Immutable
data class BookColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val done: Color,
    val holy: Color,
    val miss: Color,
)

val LightBook = BookColors(
    bg = Color(0xFFF5EFE2), surface = Color(0xFFFBF7EE), ink = Color(0xFF2A2118), muted = Color(0xFF7A6B58),
    line = Color(0xFFE2D7C2), accent = Color(0xFF7A2E1F), onAccent = Color(0xFFFBF7EE), soft = Color(0xFFEFE4CF),
    done = Color(0xFF5C7A4A), holy = Color(0xFFB38A3C), miss = Color(0xFFC9B79A),
)

val NightBook = BookColors(
    bg = Color(0xFF141A24), surface = Color(0xFF1B2330), ink = Color(0xFFECE6D8), muted = Color(0xFF98A0AD),
    line = Color(0xFF2A3444), accent = Color(0xFFD9B36A), onAccent = Color(0xFF141A24), soft = Color(0xFF232D3C),
    done = Color(0xFF7FB08A), holy = Color(0xFFB69BE0), miss = Color(0xFF3A4454),
)

val LocalBook = staticCompositionLocalOf { LightBook }

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Serif = FontFamily(
    variable(R.font.frank_ruhl_libre, 400),
    variable(R.font.frank_ruhl_libre, 500),
    variable(R.font.frank_ruhl_libre, 700),
)

val Sans = FontFamily(
    variable(R.font.heebo, 400),
    variable(R.font.heebo, 500),
    variable(R.font.heebo, 700),
)

@Composable
fun MishnaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) NightBook else LightBook
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onBackground = c.ink, onSurface = c.ink)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onBackground = c.ink, onSurface = c.ink)
    }
    CompositionLocalProvider(LocalBook provides c, LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
