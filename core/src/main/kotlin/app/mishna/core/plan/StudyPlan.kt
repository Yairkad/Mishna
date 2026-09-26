package app.mishna.core.plan

import app.mishna.core.content.Mishnayot
import app.mishna.core.state.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
enum class CompletionMode {
    /** Finished in the app after scrolling to the end. */
    APP,

    /** Shabbat/Yom Tov, marked after it ended ("studied from the book"). */
    BOOK,

    /** A day before the app was installed, confirmed during onboarding. */
    BACKFILL,
}

/** The mishnayot assigned to one study day. [count] is 0 only after the whole Mishna is finished. */
@Serializable
data class StudyDay(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val start: Int,
    val count: Int,
    val completed: CompletionMode? = null,
) {
    val done: Boolean get() = completed != null
}

/**
 * The whole learning state. Immutable: every operation returns a new plan.
 *
 * Rules (SPEC §2, §6): the study never skips. A missed day keeps its assignment and the
 * next day starts from the same mishna. [nextIndex] is the first mishna not yet studied.
 */
@Serializable
data class StudyPlan(
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    val pace: Int,
    val nextIndex: Int,
    val days: Map<@Serializable(with = LocalDateSerializer::class) LocalDate, StudyDay> = emptyMap(),
    val cycle: Int = 1,
    /** Not stored: always the size of the bundled content. */
    @Transient val total: Int = Mishnayot.total,
) {
    init {
        require(pace >= 1) { "pace must be ≥ 1" }
        require(nextIndex in 0..total)
    }

    val finished: Boolean get() = nextIndex >= total

    /** "Day 45": calendar days since the start date, counting missed days (1-based). */
    fun dayNumber(date: LocalDate): Int = ChronoUnit.DAYS.between(startDate, date).toInt() + 1

    private fun assignment(date: LocalDate, from: Int) =
        StudyDay(date, from, minOf(pace, total - from))

    /**
     * Makes sure every day from the last recorded day up to [today] has a record.
     * Days in between that were never opened are recorded as missed, with the same
     * assignment the next day gets.
     */
    fun open(today: LocalDate): StudyPlan {
        if (today in days) return this
        val last = days.keys.maxOrNull()
        val from = when {
            last == null -> startDate
            last >= today -> return this
            else -> last.plusDays(1)
        }
        val added = generateSequence(maxOf(from, startDate)) { it.plusDays(1) }
            .takeWhile { it <= today }
            .associateWith { assignment(it, nextIndex) }
        return copy(days = days + added)
    }

    fun day(date: LocalDate): StudyDay? = days[date]

    /**
     * Marks [date] as studied and advances [nextIndex]. The day's range is re-anchored at
     * [nextIndex] first, so marking consecutive Shabbat/Yom Tov days in order works.
     */
    fun complete(date: LocalDate, mode: CompletionMode): StudyPlan {
        val d = requireNotNull(days[date]) { "day $date not opened" }
        if (d.done) return this
        val anchored = assignment(date, nextIndex).copy(completed = mode)
        return copy(days = days + (date to anchored), nextIndex = anchored.start + anchored.count)
    }

    /**
     * Changes the pace. If [today] is not done yet its assignment changes now,
     * otherwise the new pace applies from tomorrow (SPEC §2).
     */
    fun changePace(newPace: Int, today: LocalDate): StudyPlan {
        val p = copy(pace = newPace)
        val d = days[today] ?: return p
        return if (d.done) p else p.copy(days = days + (today to p.assignment(today, d.start)))
    }

    /**
     * "I studied more / less outside the app": moves the position by one day's worth of
     * mishnayot. History is not changed; only today (if not done) and onward.
     */
    fun shiftDay(forward: Boolean, today: LocalDate): StudyPlan {
        val delta = if (forward) pace else -pace
        val d = days[today]
        return if (d != null && !d.done) {
            val start = (d.start + delta).coerceIn(0, total)
            copy(nextIndex = start, days = days + (today to assignment(today, start)))
        } else {
            copy(nextIndex = (nextIndex + delta).coerceIn(0, total))
        }
    }

    /**
     * What each of [dates] (after [today], in order) will cover if every day up to it is
     * studied. Used to show Shabbat and Yom Tov study in advance on Erev Shabbat.
     */
    fun preview(today: LocalDate, dates: List<LocalDate>): List<StudyDay> {
        val t = days[today]
        var from = if (t == null || t.done) nextIndex else t.start + t.count
        return dates.sorted().map { d ->
            val s = StudyDay(d, from, minOf(pace, total - from))
            from += s.count
            s
        }
    }

    /**
     * Continue from [index] (Settings → start point). History and streaks are kept;
     * today's assignment moves unless it is already done.
     */
    fun moveTo(index: Int, today: LocalDate): StudyPlan {
        val start = index.coerceIn(0, total)
        val d = days[today]
        return if (d != null && !d.done) {
            copy(nextIndex = start, days = days + (today to assignment(today, start)))
        } else {
            copy(nextIndex = start)
        }
    }

    /** Starts the Mishna again from Berakhot 1:1 after a finished cycle. History is kept. */
    fun startNewCycle(today: LocalDate): StudyPlan {
        check(finished)
        val p = copy(nextIndex = 0, cycle = cycle + 1)
        val d = days[today]
        return if (d != null && !d.done) p.copy(days = days + (today to p.assignment(today, 0))) else p
    }

    companion object {
        /**
         * Onboarding: a new plan starting at [startIndex] on [startDate]. When the start date is
         * in the past, every day before [today] is recorded; days in [notStudied] are missed,
         * all others count as studied (SPEC §3).
         */
        fun create(
            startDate: LocalDate,
            startIndex: Int,
            pace: Int,
            today: LocalDate,
            notStudied: Set<LocalDate> = emptySet(),
        ): StudyPlan {
            require(!startDate.isAfter(today)) { "start date must not be in the future" }
            var plan = StudyPlan(startDate, pace, startIndex)
            var date = startDate
            while (date.isBefore(today)) {
                plan = plan.open(date)
                if (date !in notStudied && !plan.finished) plan = plan.complete(date, CompletionMode.BACKFILL)
                date = date.plusDays(1)
            }
            return plan.open(today)
        }
    }
}
