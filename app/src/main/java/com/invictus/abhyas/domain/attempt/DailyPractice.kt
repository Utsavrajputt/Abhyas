package com.invictus.abhyas.domain.attempt

import java.time.LocalDate
import kotlin.random.Random

/**
 * The daily set: [count] random questions, the same all day and new tomorrow. A day counts for the
 * streak when its attempt (titled by date) has been submitted, so no extra storage is needed.
 */
object DailyPractice {
    const val DEFAULT_COUNT = 30
    private const val PREFIX = "Daily practice - "

    fun title(day: LocalDate): String = PREFIX + day

    fun dayOf(title: String): LocalDate? =
        if (title.startsWith(PREFIX)) runCatching { LocalDate.parse(title.removePrefix(PREFIX)) }.getOrNull() else null

    /** Stable for [day]: sorting first makes the pick independent of query order. */
    fun pick(ids: List<String>, day: LocalDate, count: Int): List<String> =
        ids.sorted().shuffled(Random(day.toEpochDay())).take(count)

    /** Consecutive finished days ending today (or yesterday while today is still open). */
    fun streak(done: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in done) today else today.minusDays(1)
        var n = 0
        while (d in done) { n++; d = d.minusDays(1) }
        return n
    }
}
