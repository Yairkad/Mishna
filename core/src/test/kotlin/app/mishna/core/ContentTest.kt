package app.mishna.core

import app.mishna.core.content.Hebrew
import app.mishna.core.content.Mishnayot
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentTest {
    @Test fun total() = assertEquals(4181, Mishnayot.total)

    @Test fun firstAndLast() {
        assertEquals("ברכות", Mishnayot.ref(0).tractate)
        val last = Mishnayot.ref(Mishnayot.total - 1)
        assertEquals("עוקצין", last.tractate)
        assertEquals(3 to 12, last.perek to last.mishna)
    }

    @Test fun roundTrip() {
        for (i in 0 until Mishnayot.total step 37) {
            val r = Mishnayot.ref(i)
            assertEquals(i, Mishnayot.indexOf(r.tractate, r.perek, r.mishna))
        }
    }

    @Test fun peahStartsAfterBerakhot() = assertEquals(57, Mishnayot.indexOf("פאה", 1, 1))

    @Test fun numerals() {
        assertEquals("א׳", Hebrew.numeral(1))
        assertEquals("ט״ו", Hebrew.numeral(15))
        assertEquals("ט״ז", Hebrew.numeral(16))
        assertEquals("ל׳", Hebrew.numeral(30))
        assertEquals("כ״ד", Hebrew.numeral(24))
    }

    @Test fun describe() {
        assertEquals("ברכות א׳, א׳–ג׳", Mishnayot.describe(0, 3))
        assertEquals("ברכות א׳, ה׳ – ב׳, ב׳", Mishnayot.describe(4, 3))
        assertEquals("ברכות ט׳, ג׳ – פאה א׳, א׳", Mishnayot.describe(54, 4))
        assertEquals("ברכות א׳, א׳", Mishnayot.describe(0, 1))
    }
}
