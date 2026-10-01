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
    }
}
