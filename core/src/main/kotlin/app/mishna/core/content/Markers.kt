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
     * Like [place], and a comment whose dibur hamatchil is not in the mishna is looked up in the
     * Bartenura (Tosafot Yom Tov often comments on his words). Returns (mishna markers, Bartenura markers);
     * Bartenura offsets refer to the raw commentary text.
     */
    fun placeBoth(mishna: String, bartenura: String, commentary: String): Pair<List<Marker>, List<Marker>> {
        val texts = listOf(words(mishna), words(bartenura))
        val from = intArrayOf(0, 0)
        // (which text, offset, letters)
        val result = mutableListOf<Triple<Int, Int, MutableList<String>>>()
        for (comment in commentary.split('\n')) {
            val letter = label.find(comment.trim())?.groupValues?.get(1)?.replace("\"", "")?.replace("״", "") ?: continue
            val dh = dibur.find(comment)?.groupValues?.get(1)
                ?.split(Regex("\\s+"))?.map(::normWord)?.filter { it.isNotEmpty() && it !in filler }.orEmpty()
            var hit: Pair<Int, Pair<Int, Int>>? = null
            if (dh.isNotEmpty()) {
                for (t in texts.indices) {
                    val at = find(texts[t], dh, from[t]) ?: find(texts[t], dh, 0)
                    if (at != null) { hit = t to at; break }
                }
            }
            if (hit == null) {
                result.lastOrNull()?.third?.add(letter)
                continue
            }
            val (t, at) = hit
            from[t] = at.first
            val same = result.lastOrNull()?.takeIf { it.first == t && it.second == at.second }
            if (same != null) same.third.add(letter) else result.add(Triple(t, at.second, mutableListOf(letter)))
        }
        fun markers(t: Int) = result.filter { it.first == t }.map { Marker(it.second, join(it.third)) }
        return markers(0) to markers(1)
    }

    /** Longest prefix of [dh] found in [text] from word [from]: (index of last matched word, char offset after it). */
    private fun find(text: List<Word>, dh: List<String>, from: Int): Pair<Int, Int>? {
        for (k in dh.size downTo 1) {
            val needle = dh.subList(0, k)
            for (i in from..text.size - k) {
                if ((0 until k).all { text[i + it].norm == needle[it] }) return (i + k - 1) to text[i + k - 1].end
            }
        }
        return null
    }

    private fun join(letters: List<String>) = if (letters.size == 1) letters[0] else "${letters.first()}-${letters.last()}"
}
