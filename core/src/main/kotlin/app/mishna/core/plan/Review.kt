package app.mishna.core.plan

import app.mishna.core.state.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Spaced review stages, as in Shas Chabura: each gap counts from the previous review —
 * the next day, a week later, 30 days later, 90 days later — then every year on the
 * anniversary. [days] is the offset from the day studied.
 */
@Serializable
enum class ReviewStage(val days: Int) { DAY(1), WEEK(1 + 7), MONTH(1 + 7 + 30), MONTH3(1 + 7 + 30 + 90), YEAR(365) }

/** One review of the day [learned] studied. [year] counts the yearly reviews (1, 2, …); 0 for the others. */
@Serializable
data class ReviewKey(
    @Serializable(with = LocalDateSerializer::class) val learned: LocalDate,
    val stage: ReviewStage,
    val year: Int = 0,
) {
    val due: LocalDate
        get() = learned.plusDays(if (stage == ReviewStage.YEAR) 365L * year else stage.days.toLong())

    val label: String
        get() = when (stage) {
            ReviewStage.DAY -> "מאתמול"
            ReviewStage.WEEK -> "משבוע שעבר"
            ReviewStage.MONTH -> "מלפני חודש"
            ReviewStage.MONTH3 -> "מלפני 3 חודשים"
            ReviewStage.YEAR -> if (year == 1) "מלפני שנה" else "מלפני $year שנים"
        }
}

@Serializable
data class DoneReview(val key: ReviewKey, @Serializable(with = LocalDateSerializer::class) val on: LocalDate)

/** The review plan (SPEC §12). Only done reviews are stored; what is due is computed from the study days. */
@Serializable
data class ReviewState(
    @Serializable(with = LocalDateSerializer::class) val enabledFrom: LocalDate,
    val done: List<DoneReview> = emptyList(),
    /** Switched off in Settings; the history in [done] is kept. */
    val enabled: Boolean = true,
) {
    private val doneKeys: Set<ReviewKey> by lazy { done.mapTo(HashSet()) { it.key } }

    fun isDone(key: ReviewKey) = key in doneKeys

    fun markReviewed(keys: Collection<ReviewKey>, on: LocalDate): ReviewState =
        copy(done = done + keys.filterNot(::isDone).distinct().map { DoneReview(it, on) })

    /**
     * Switching back on starts from [today] again, so the days it was off do not come back as a
     * backlog; reviews already done stay done.
     */
    fun setEnabled(on: Boolean, today: LocalDate): ReviewState = when {
        on == enabled -> this
        on -> copy(enabled = true, enabledFrom = today)
        else -> copy(enabled = false)
    }

    /** Reviews finished on [date]. */
    fun doneOn(date: LocalDate): List<ReviewKey> = done.filter { it.on == date }.map { it.key }
}

/** A review with the mishnayot it covers: exactly what was studied on the day it reviews. */
data class ReviewItem(val key: ReviewKey, val start: Int, val count: Int)

/** Reviews of one stage shown together, e.g. "משבוע שעבר". */
data class ReviewGroup(val label: String, val items: List<ReviewItem>) {
    val count: Int get() = items.sumOf { it.count }
}

/**
 * Every review due on or before [date] and not yet done. Missed reviews stay until done.
 * Reviews due before the plan was switched on are skipped, so turning it on does not create a backlog.
 */
fun StudyPlan.dueReviews(state: ReviewState, date: LocalDate): List<ReviewItem> =
    if (!state.enabled) emptyList() else days.values.asSequence()
        .filter { it.done && it.count > 0 }
        .flatMap { day -> keysUpTo(day.date, date).map { ReviewItem(it, day.start, day.count) } }
        .filter { !it.key.due.isBefore(state.enabledFrom) && !state.isDone(it.key) }
        .sortedWith(compareBy({ it.key.due }, { it.key.learned }))
        .toList()

private fun keysUpTo(learned: LocalDate, date: LocalDate): Sequence<ReviewKey> = sequence {
    for (stage in ReviewStage.entries) {
        if (stage == ReviewStage.YEAR) {
            var year = 1
            while (!learned.plusDays(365L * year).isAfter(date)) yield(ReviewKey(learned, stage, year++))
        } else if (!learned.plusDays(stage.days.toLong()).isAfter(date)) {
            yield(ReviewKey(learned, stage))
        }
    }
}

/** Groups by stage in a fixed order: yesterday, last week, a month, three months, then years. */
fun List<ReviewItem>.grouped(): List<ReviewGroup> =
    groupBy { it.key.stage to it.key.year }
        .toSortedMap(compareBy({ it.first.ordinal }, { it.second }))
        .map { (_, items) -> ReviewGroup(items.first().key.label, items.sortedBy { it.start }) }
