package app.mishna.core.content

/** A reference letter shown inside the mishna text, e.g. "א" or "ב-ג", inserted after [offset]. */
data class Marker(val offset: Int, val label: String)

/**
 * Places Ikar Tosafot Yom Tov letters in the mishna text, like printed editions.
 *
 * Each comment starts with its letter, "(א) <b>dibur hamatchil</b> …". The marker goes after the
 * last word of the dibur hamatchil found in the mishna (vowels ignored; the longest matching
 * prefix wins, searching forward from the previous marker first). Comments with no dibur
 * hamatchil, or whose words are not in the mishna, usually continue the comment before them,
 * so their letter joins the previous marker.
 */
object Markers {
    private val vowels = Regex("[\\u0591-\\u05C7]")
    private val label = Regex("^\\(([\\u05D0-\\u05EA\"״']+)\\)")
    private val dibur = Regex("<b>(.*?)</b>")
    private val filler = setOf("כו", "וכו", "וגו", "וכולי")

    private fun normWord(w: String) = vowels.replace(w, "").filter { it in 'א'..'ת' }

    private class Word(val norm: String, val end: Int)

    private fun words(text: String): List<Word> =
        Regex("\\S+").findAll(text).map { Word(normWord(it.value), it.range.last + 1) }.filter { it.norm.isNotEmpty() }.toList()

    fun place(mishna: String, commentary: String): List<Marker> = placeBoth(mishna, "", commentary).first

    /**
     * Places every letter in the mishna or the Bartenura. Returns (mishna markers, Bartenura
     * markers); Bartenura offsets refer to the raw commentary text.
     *
     * - With a dibur hamatchil: after its longest matching run, in whichever text matches more
     *   words (at least two when it has two; ties go to the mishna).
     * - Without one: if at least two consecutive words from its opening appear in the Bartenura,
     *   it goes there (it usually explains the Bartenura).
     * - Otherwise it joins the previous letter, or the next one when it comes first, so no letter
     *   is left without a place.
     */
    fun placeBoth(mishna: String, bartenura: String, commentary: String): Pair<List<Marker>, List<Marker>> {
        val texts = listOf(words(mishna), words(bartenura))
        val from = intArrayOf(0, 0)
        val placed = mutableListOf<Triple<Int, Int, MutableList<String>>>() // (text, offset, letters)
        val waiting = mutableListOf<String>()
        for (comment in commentary.split('\n')) {
            val trimmed = comment.trim()
            val head = label.find(trimmed) ?: continue
            val letter = head.groupValues[1].replace("\"", "").replace("״", "")
            val dh = dibur.find(comment)?.groupValues?.get(1)?.let(::cleanWords).orEmpty()
            val hit: Pair<Int, Match>? = if (dh.isNotEmpty()) {
                val need = minOf(2, dh.size)
                val m = longest(texts[0], dh, from[0], prefixOnly = true)
                val b = longest(texts[1], dh, from[1], prefixOnly = true)
                when {
                    m != null && m.length >= need && (b == null || m.length >= b.length) -> 0 to m
                    b != null && b.length >= need -> 1 to b
                    m != null -> 0 to m
                    else -> null
                }
            } else {
                val opening = cleanWords(trimmed.substring(head.range.last + 1)).take(12)
                longest(texts[1], opening, from[1], prefixOnly = false)?.takeIf { it.length >= 2 }?.let { 1 to it }
            }
            if (hit == null) {
                placed.lastOrNull()?.third?.add(letter) ?: waiting.add(letter)
                continue
            }
            val (t, match) = hit
            from[t] = match.lastWord
            val same = placed.lastOrNull()?.takeIf { it.first == t && it.second == match.end }
            val letters = same?.third ?: mutableListOf<String>().also { placed.add(Triple(t, match.end, it)) }
            if (waiting.isNotEmpty()) { letters.addAll(0, waiting); waiting.clear() }
            letters.add(letter)
        }
        if (waiting.isNotEmpty()) placed.add(Triple(0, mishna.trimEnd().length, waiting))
        fun markers(t: Int) = placed.filter { it.first == t }.sortedBy { it.second }.map { Marker(it.second, join(it.third)) }
        return markers(0) to markers(1)
    }

    private class Match(val length: Int, val lastWord: Int, val end: Int)

    private fun cleanWords(s: String) = s.split(Regex("\\s+")).map(::normWord).filter { it.isNotEmpty() && it !in filler }

    /**
     * Longest run of [query] words found in [text]. With [prefixOnly] the run must start at the
     * query's first word (a dibur hamatchil); otherwise it may start at any of its words. Among
     * equal runs, the first one at or after word [from] wins (comments follow the text in order).
     */
    private fun longest(text: List<Word>, query: List<String>, from: Int, prefixOnly: Boolean): Match? {
        var best: Match? = null
        var bestScore = 0
        val starts = if (prefixOnly) 0..0 else query.indices
        for (s in starts) for (i in text.indices) {
            var k = 0
            while (s + k < query.size && i + k < text.size && text[i + k].norm == query[s + k]) k++
            if (k == 0) continue
            // Prefer longer runs, then runs at/after [from], then earlier ones.
            val score = k * 2 + if (i >= from) 1 else 0
            if (score > bestScore) {
                bestScore = score
                best = Match(k, i + k - 1, text[i + k - 1].end)
            }
        }
        return best
    }

    private fun join(letters: List<String>) = if (letters.size == 1) letters[0] else "${letters.first()}-${letters.last()}"
}
