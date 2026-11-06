package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.TestData.question
import com.invictus.abhyas.domain.engine.OptionShuffler
import com.invictus.abhyas.domain.engine.Scorer
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.Verdict
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionShufflerTest {
    private val options = (1..6).map { Option("o$it", "TEST option $it") }
    private val q = question(SingleChoicePayload(options, "o4"))

    private fun ids(q: com.invictus.abhyas.domain.model.Question) =
        (q.payload as SingleChoicePayload).options.map { it.id }

    @Test fun keepsSameOptionsAndCorrectIdStillScores() {
        repeat(20) { seed ->
            val s = OptionShuffler.shuffle(q, Random(seed))
            assertEquals(options.map { it.id }.toSet(), ids(s).toSet())
            assertEquals(Verdict.CORRECT, Scorer.score(s, Response.Choice(setOf("o4"))).verdict)
        }
    }

    @Test fun deterministicPerSeedAndActuallyShuffles() {
        assertEquals(ids(OptionShuffler.shuffle(q, Random(7))), ids(OptionShuffler.shuffle(q, Random(7))))
        assertTrue((0..20).any { ids(OptionShuffler.shuffle(q, Random(it))) != ids(q) })
    }

    @Test fun allOfTheAboveStaysLast() {
        val opts = listOf(
            Option("a", "TEST one"), Option("b", "All of the above"),
            Option("c", "TEST two"), Option("d", "TEST three"),
        )
        repeat(20) { seed ->
            val out = OptionShuffler.shuffleOptions(opts, Random(seed))
            assertEquals("b", out.last().id)
        }
    }

    @Test fun positionalOptionsAreNotReordered() {
        val opts = listOf(Option("a", "(a) and (b)"), Option("b", "(b) and (c)"), Option("c", "TEST x"))
        assertEquals(opts, OptionShuffler.shuffleOptions(opts, Random(1)))
    }

    @Test fun labelsFollowDisplayPosition() {
        val s = OptionShuffler.shuffle(q, Random(3))
        val labels = OptionShuffler.labels((s.payload as SingleChoicePayload).options)
        val firstId = ids(s).first()
        assertEquals("A", labels[firstId])
        assertNotEquals(labels["o1"], null)
    }
}
