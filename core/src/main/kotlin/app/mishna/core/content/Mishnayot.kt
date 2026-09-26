package app.mishna.core.content

/** Index over all mishnayot in study order (Zeraim → Tahorot). */
object Mishnayot {
    val sedarim: List<Seder> get() = SEDARIM
    val total: Int get() = TOTAL_MISHNAYOT

    private class Entry(val seder: Seder, val tractate: Tractate, val first: Int)

    private val entries: List<Entry> = buildList {
        var index = 0
        for (seder in SEDARIM) for (t in seder.tractates) {
            add(Entry(seder, t, index))
            index += t.size
        }
    }

    fun ref(globalIndex: Int): MishnaRef {
        require(globalIndex in 0 until total) { "index $globalIndex out of range" }
        val e = entries.last { it.first <= globalIndex }
        var offset = globalIndex - e.first
        for ((p, size) in e.tractate.chapterSizes.withIndex()) {
            if (offset < size) return MishnaRef(globalIndex, e.seder.name, e.tractate.name, p + 1, offset + 1)
            offset -= size
        }
        error("unreachable")
    }

    fun indexOf(tractate: String, perek: Int, mishna: Int): Int {
        val e = entries.first { it.tractate.name == tractate }
        val sizes = e.tractate.chapterSizes
        require(perek in 1..sizes.size && mishna in 1..sizes[perek - 1]) { "no such mishna $tractate $perek:$mishna" }
        return e.first + sizes.take(perek - 1).sum() + mishna - 1
    }

    /**
     * Human-readable range, e.g. "ברכות א׳, א׳–ג׳", "ברכות א׳, ה׳ – ב׳, ב׳"
     * or "ברכות ט׳, ה׳ – פאה א׳, א׳". [count] must be ≥ 1.
     */
    fun describe(start: Int, count: Int): String {
        val a = ref(start)
        val b = ref(start + count - 1)
        val head = "${a.tractate} ${Hebrew.numeral(a.perek)}, ${Hebrew.numeral(a.mishna)}"
        return when {
            count == 1 -> head
            a.tractate != b.tractate -> "$head – ${b.tractate} ${Hebrew.numeral(b.perek)}, ${Hebrew.numeral(b.mishna)}"
            a.perek != b.perek -> "$head – ${Hebrew.numeral(b.perek)}, ${Hebrew.numeral(b.mishna)}"
            else -> "$head–${Hebrew.numeral(b.mishna)}"
        }
    }
}
