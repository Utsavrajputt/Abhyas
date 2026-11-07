package com.invictus.abhyas.domain.engine

import kotlin.random.Random

data class RandomPick(
    val ids: List<String>,
    val requested: Int,
    /** How many picked ids had to come from the "recently attempted" set. */
    val fromRecent: Int,
) {
    /** > 0 when fewer questions were available than requested. */
    val shortfall: Int get() = (requested - ids.size).coerceAtLeast(0)
}

/**
 * Picks question ids for an attempt: no duplicates, fresh questions first, recently attempted
 * ones only as a fallback (or never, when [allowRecentFallback] is false), and returns fewer
 * than requested instead of failing when the pool is small. Filters are applied by the DB
 * before this runs; option shuffling is [OptionShuffler]'s job.
 */
object Randomizer {
    fun pick(
        candidates: List<String>,
        count: Int,
        recent: Set<String> = emptySet(),
        allowRecentFallback: Boolean = true,
        random: Random = Random.Default,
    ): RandomPick {
        if (count <= 0) return RandomPick(emptyList(), count.coerceAtLeast(0), 0)
        val unique = candidates.distinct()
        val (stale, fresh) = unique.partition { it in recent }
        val chosen = fresh.shuffled(random).take(count).toMutableList()
        var fromRecent = 0
        if (chosen.size < count && allowRecentFallback) {
            val extra = stale.shuffled(random).take(count - chosen.size)
            fromRecent = extra.size
            chosen += extra
        }
        return RandomPick(chosen.shuffled(random), count, fromRecent)
    }
}
