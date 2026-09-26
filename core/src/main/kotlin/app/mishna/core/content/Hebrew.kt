package app.mishna.core.content

object Hebrew {
    private val ones = arrayOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val tens = arrayOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val hundreds = arrayOf("", "ק", "ר", "ש", "ת")

    /** 1..499 as Hebrew letters with geresh/gershayim: 1 → "א׳", 15 → "ט״ו", 30 → "ל׳". */
    fun numeral(n: Int): String {
        require(n in 1..499)
        val letters = buildString {
            append(hundreds[n / 100])
            when (val rest = n % 100) {
                15 -> append("טו")
                16 -> append("טז")
                else -> append(tens[rest / 10]).append(ones[rest % 10])
            }
        }
        return if (letters.length == 1) "$letters׳" else letters.dropLast(1) + "״" + letters.last()
    }
}
