package app.mishna.core.plan

import java.time.LocalDate

data class Streaks(val current: Int, val best: Int)

data class Stats(
    val completedDays: Int,
    val learned: Int,
    val remaining: Int,
    val estimatedFinish: LocalDate?,
)

/**
 * Current streak counts back from [today]; if today is not done yet it counts from yesterday,
 * so the streak is not broken before the day ends.
 */
fun StudyPlan.streaks(today: LocalDate): Streaks {
    var best = 0
    var run = 0
    var prev: StudyDay? = null
    for (d in days.values.sortedBy { it.date }) {
        val continues = prev != null && prev.done && d.date == prev.date.plusDays(1)
        run = if (!d.done) 0 else if (continues) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    var current = 0
    var date = if (days[today]?.done == true) today else today.minusDays(1)
    while (days[date]?.done == true) {
        current++
        date = date.minusDays(1)
    }
    return Streaks(current, best)
}

fun StudyPlan.stats(today: LocalDate): Stats {
    val done = days.values.filter { it.done }
    val remaining = total - nextIndex
    val finish = if (remaining == 0) {
        null
    } else {
        val daysNeeded = (remaining + pace - 1) / pace
        val todayOpen = days[today]?.done != true
        today.plusDays((if (todayOpen) daysNeeded - 1 else daysNeeded).toLong())
    }
    return Stats(done.size, done.sumOf { it.count }, remaining, finish)
}
