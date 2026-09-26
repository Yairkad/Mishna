package app.mishna.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mishna.core.content.Hebrew
import app.mishna.core.content.Mishnayot
import app.mishna.ui.theme.LocalBook
import app.mishna.ui.theme.Sans
import app.mishna.ui.theme.Serif

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit) {
    val c = LocalBook.current
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        RoundButton("−") { onChange(value - 1) }
        Text("$value", fontFamily = Serif, fontSize = 54.sp, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.width(100.dp))
        RoundButton("+") { onChange(value + 1) }
    }
}

@Composable
fun RoundButton(label: String, onClick: () -> Unit) {
    val c = LocalBook.current
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 24.sp, color = c.ink, fontFamily = Sans) }
}

@Composable
fun <T> Picker(label: String, items: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBook.current
    var open by remember { mutableStateOf(false) }
    Column(modifier.padding(bottom = 12.dp)) {
        Text(label, color = c.muted, fontFamily = Sans, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Text(
                text(selected), fontFamily = Sans, fontSize = 16.sp, color = c.ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
                    .border(1.dp, c.line, RoundedCornerShape(12.dp)).clickable { open = true }.padding(12.dp),
            )
            DropdownMenu(open, onDismissRequest = { open = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(text(item), fontFamily = Sans) }, onClick = { onSelect(item); open = false })
                }
            }
        }
    }
}

@Composable
fun StartPicker(index: Int, onChange: (Int) -> Unit) {
    val ref = Mishnayot.ref(index)
    val seder = Mishnayot.sedarim.first { it.name == ref.seder }
    val tractate = seder.tractates.first { it.name == ref.tractate }
    Picker("סדר", Mishnayot.sedarim, seder, { it.name }, { onChange(Mishnayot.indexOf(it.tractates.first().name, 1, 1)) })
    Picker("מסכת", seder.tractates, tractate, { it.name }, { onChange(Mishnayot.indexOf(it.name, 1, 1)) })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Picker("פרק", (1..tractate.chapterSizes.size).toList(), ref.perek, { Hebrew.numeral(it) },
            { onChange(Mishnayot.indexOf(tractate.name, it, 1)) }, Modifier.weight(1f))
        Picker("משנה", (1..tractate.chapterSizes[ref.perek - 1]).toList(), ref.mishna, { Hebrew.numeral(it) },
            { onChange(Mishnayot.indexOf(tractate.name, ref.perek, it)) }, Modifier.weight(1f))
    }
}

