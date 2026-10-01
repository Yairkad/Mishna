package app.mishna.ui.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mishna.core.content.Hebrew
import app.mishna.core.content.Marker
import app.mishna.core.content.Markers
import app.mishna.core.content.Mishnayot
import app.mishna.data.CommentarySource
import app.mishna.data.ContentDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A run of mishnayot to show; [label] marks review sections ("חזרה · משבוע שעבר"). */
data class StudySection(val start: Int, val count: Int, val label: String? = null)

data class MishnaPage(
    val title: String,
    val text: String,
    val bartenura: String?,
    val ikarTosafotYomTov: String?,
    val label: String? = null,
    /** Ikar Tosafot Yom Tov letters to show inside the mishna text. */
    val markers: List<Marker> = emptyList(),
)

data class StudyUi(
    val sections: List<StudySection> = emptyList(),
    val seder: String = "",
    val heading: String = "",
    val pages: List<MishnaPage> = emptyList(),
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ContentDatabase.get(app).dao()
    private val _ui = MutableStateFlow(StudyUi())
    val ui: StateFlow<StudyUi> = _ui

    private var loaded: List<StudySection>? = null

    fun load(sections: List<StudySection>) {
        if (loaded == sections) return
        loaded = sections
        viewModelScope.launch { fill(sections) }
    }

    private suspend fun fill(sections: List<StudySection>) {
        val pages = sections.flatMap { sec ->
            val rows = dao.mishnayot(sec.start, sec.count)
            val comms = dao.commentaries(sec.start, sec.count).groupBy { it.globalIndex }
            rows.map { m ->
                val ref = Mishnayot.ref(m.globalIndex)
                val c = comms[m.globalIndex].orEmpty()
                MishnaPage(
                    title = "${ref.tractate} פרק ${Hebrew.numeral(ref.perek)} · משנה ${Hebrew.numeral(ref.mishna)}",
                    text = m.text,
                    bartenura = c.firstOrNull { it.source == CommentarySource.BARTENURA }?.text,
                    ikarTosafotYomTov = c.firstOrNull { it.source == CommentarySource.IKAR_TOSAFOT_YOM_TOV }?.text,
                    label = sec.label,
                    markers = c.firstOrNull { it.source == CommentarySource.IKAR_TOSAFOT_YOM_TOV }?.text
                        ?.let { Markers.place(m.text, it) }.orEmpty(),
                )
            }
        }
        val first = sections.firstOrNull()?.let { Mishnayot.ref(it.start) }
        _ui.value = StudyUi(
            sections = sections,
            seder = first?.let { "סדר ${it.seder}" }.orEmpty(),
            heading = sections.joinToString(" · ") { Mishnayot.describe(it.start, it.count) },
            pages = pages,
        )
    }
}
