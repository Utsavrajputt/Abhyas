package com.invictus.abhyas.domain.engine

import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.SingleChoicePayload
import kotlin.random.Random

/**
 * Shuffles options independently per attempt. Options carry stable ids, so the correct answer
 * is remapped for free: only the displayed order (and therefore the A/B/C/D label) changes.
 */
object OptionShuffler {

    // "All of the above", "None of these", "Both ... and ...", "Neither ..."
    private val PINNED_LAST = Regex(
        """(?i)\b(all|none|any)\s+(one\s+)?of\s+(the\s+)?(above|these|them|following)\b|\b(both|neither)\b""",
    )

    // "(a) and (b)", "1 & 3", "A, B" style: refers to other options by position.
    private val POSITIONAL = Regex("""(?i)^\s*\(?[a-d1-4]\)?\s*(and|&|,)\s*\(?[a-d1-4]\)?\s*$""")

    fun shuffle(q: Question, random: Random = Random.Default): Question = when (val p = q.payload) {
        is SingleChoicePayload -> q.copy(payload = p.copy(options = shuffleOptions(p.options, random)))
        is MultiChoicePayload -> q.copy(payload = p.copy(options = shuffleOptions(p.options, random)))
        is MatchPayload -> q.copy(payload = p.copy(options = shuffleOptions(p.options, random)))
        else -> q
    }

    fun shuffleOptions(options: List<Option>, random: Random): List<Option> {
        if (options.size < 2) return options
        // Options that point at other options by position make any reorder wrong: keep as authored.
        if (options.any { POSITIONAL.matches(it.text) }) return options
        val (pinned, movable) = options.partition { PINNED_LAST.containsMatchIn(it.text) }
        return movable.shuffled(random) + pinned
    }

    /** id -> "A", "B", ... by current display position. */
    fun labels(options: List<Option>): Map<String, String> =
        options.mapIndexed { i, o -> o.id to ('A' + i).toString() }.toMap()
}
