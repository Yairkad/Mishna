package app.mishna.core

import app.mishna.core.content.Marker
import app.mishna.core.content.Markers
import app.mishna.core.plan.ReviewDay
import app.mishna.core.plan.ReviewKey
import app.mishna.core.plan.ReviewStage
import app.mishna.core.plan.ReviewState
import app.mishna.core.plan.StudyPlan
import app.mishna.core.plan.reviewDay
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkersTest {
    private val mishna = "מֵאֵימָתַי קוֹרִין אֶת שְׁמַע בְּעַרְבִית. מִשָּׁעָה שֶׁהַכֹּהֲנִים נִכְנָסִים לֶאֱכֹל בִּתְרוּמָתָן, עַד סוֹף הָאַשְׁמוּרָה הָרִאשׁוֹנָה"

    @Test fun placesAfterDiburHamatchil() {
        val comm = listOf(
            "(א) <b>מֵאֵימָתַי כוּ'.</b> תַּנָּא אַקְּרָא קָאֵי",
            "(ב) דְּהַקְּרָא וְאַחַר יֹאכַל",
            "(ג) <b>עַד סוֹף הָאַשְׁמוּרָה.</b> שְׁלִישׁ הַלַּיְלָה",
        ).joinToString("\n")
        val markers = Markers.place(mishna, comm)
        val first = mishna.indexOf("מֵאֵימָתַי") + "מֵאֵימָתַי".length
        val last = mishna.indexOf("הָאַשְׁמוּרָה") + "הָאַשְׁמוּרָה".length
        assertEquals(listOf(Marker(first, "א-ב"), Marker(last, "ג")), markers)
    }

    @Test fun unknownWordsJoinPrevious() {
        val comm = "(א) <b>קוֹרִין</b> x\n(ב) <b>מִלָּה שֶׁאֵינָהּ</b> y"
        assertEquals(listOf("א-ב"), Markers.place(mishna, comm).map { it.label })
    }

    @Test fun reviewDayStatus() {
        val d0 = LocalDate.of(2026, 1, 1)
        val plan = StudyPlan.create(d0, 0, 3, today = d0.plusDays(3))
        val state = ReviewState(d0)
        assertEquals(ReviewDay.NONE, plan.reviewDay(state, d0, d0.plusDays(3)))
        assertEquals(ReviewDay.MISSED, plan.reviewDay(state, d0.plusDays(1), d0.plusDays(3)))
        assertEquals(ReviewDay.PENDING, plan.reviewDay(state, d0.plusDays(3), d0.plusDays(3)))
        val done = state.markReviewed(listOf(ReviewKey(d0, ReviewStage.DAY)), d0.plusDays(2))
        assertEquals(ReviewDay.DONE, plan.reviewDay(done, d0.plusDays(1), d0.plusDays(3)))
        // A review done on a day it was not due (carried over) still marks that day.
        val onlyCarried = state.markReviewed(listOf(ReviewKey(d0, ReviewStage.DAY)), d0.plusDays(4))
        val later = StudyPlan.create(d0, 0, 3, today = d0.plusDays(4), notStudied = setOf(d0.plusDays(1), d0.plusDays(2), d0.plusDays(3)))
        assertEquals(ReviewDay.DONE, later.reviewDay(onlyCarried, d0.plusDays(4), d0.plusDays(4)))
    }

    @Test fun lettersInBartenura() {
        val bart = "<b>מֵאֵימָתַי קוֹרִין</b>. מִשָּׁעָה שֶׁהַכֹּהֲנִים נִכְנָסִין. כֹּהֲנִים שֶׁנִּטְמְאוּ וְטָבְלוּ"
        val comm = "(א) <b>מֵאֵימָתַי כוּ'.</b> x\n(ב) <b>שֶׁנִּטְמְאוּ וְטָבְלוּ.</b> y\n(ג) z"
        val (inMishna, inBart) = Markers.placeBoth(mishna, bart, comm)
        assertEquals(listOf("א"), inMishna.map { it.label })
        assertEquals(listOf("ב-ג"), inBart.map { it.label })
        assertEquals(bart.indexOf("וְטָבְלוּ") + "וְטָבְלוּ".length, inBart.single().offset)
    }

    @Test fun leadingLetterWithoutPlaceJoinsNext() {
        val comm = "(י) בלי דיבור המתחיל\n(יא) עוד הערה\n(יב) <b>עַד סוֹף הָאַשְׁמוּרָה.</b> x"
        assertEquals(listOf("י-יב"), Markers.place(mishna, comm).map { it.label })
    }

    @Test fun noFutureReviewMarks() {
        val d0 = LocalDate.of(2026, 1, 1)
        val plan = StudyPlan.create(d0, 0, 3, today = d0.plusDays(1))
        assertEquals(ReviewDay.NONE, plan.reviewDay(ReviewState(d0), d0.plusDays(8), d0.plusDays(1)))
    }
}
