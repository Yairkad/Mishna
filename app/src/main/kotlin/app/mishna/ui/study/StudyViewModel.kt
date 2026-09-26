package app.mishna.ui.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mishna.core.content.Hebrew
import app.mishna.core.content.Mishnayot
import app.mishna.data.CommentarySource
import app.mishna.data.ContentDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MishnaPage(
    val title: String,
    val text: String,
    val bartenura: String?,
    val ikarTosafotYomTov: String?,
)

data class StudyUi(
    val seder: String = "",
    val heading: String = "",
    val pages: List<MishnaPage> = emptyList(),
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ContentDatabase.get(app).dao()
    private val _ui = MutableStateFlow(StudyUi())
    val ui: StateFlow<StudyUi> = _ui

    // Until the plan is stored (next stage), show the first day of the default plan.
    fun load(start: Int = 0, count: Int = 3) = viewModelScope.launch {
        val rows = dao.mishnayot(start, count)
        val comms = dao.commentaries(start, count).groupBy { it.globalIndex }
        val first = Mishnayot.ref(start)
        _ui.value = StudyUi(
            seder = "סדר ${first.seder}",
            heading = Mishnayot.describe(start, count),
            pages = rows.map { m ->
                val ref = Mishnayot.ref(m.globalIndex)
                val c = comms[m.globalIndex].orEmpty()
                MishnaPage(
                    title = "${ref.tractate} פרק ${Hebrew.numeral(ref.perek)} · משנה ${Hebrew.numeral(ref.mishna)}",
                    text = m.text,
                    bartenura = c.firstOrNull { it.source == CommentarySource.BARTENURA }?.text,
                    ikarTosafotYomTov = c.firstOrNull { it.source == CommentarySource.IKAR_TOSAFOT_YOM_TOV }?.text,
                )
            },
        )
    }
}
