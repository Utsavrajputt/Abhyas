package com.invictus.abhyas.domain.attempt

import com.invictus.abhyas.domain.engine.Scorer
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.Score
import com.invictus.abhyas.domain.model.Verdict
import com.invictus.abhyas.domain.model.hasAnswer

data class AttemptSummary(
    val perQuestion: List<Score>,
    val score: Double,
    /** Marks available from questions that can be scored (verified answers only). */
    val maxScore: Double,
    val correct: Int,
    val wrong: Int,
    val unattempted: Int,
    val unscored: Int,
) {
    val accuracy: Double get() = if (correct + wrong == 0) 0.0 else correct.toDouble() / (correct + wrong)
}

object SummaryCalculator {
    /** Questions carry their (possibly template-overridden) marks in the snapshot. */
    fun compute(items: List<Pair<Question, Response?>>): AttemptSummary {
        val scores = items.map { (q, r) -> Scorer.score(q, r) }
        return AttemptSummary(
            perQuestion = scores,
            score = scores.sumOf { it.marks },
            maxScore = maxScore(items.map { it.first }),
            correct = scores.count { it.verdict == Verdict.CORRECT },
            wrong = scores.count { it.verdict == Verdict.WRONG },
            unattempted = scores.count { it.verdict == Verdict.UNATTEMPTED },
            unscored = scores.count { it.verdict == Verdict.UNSCORED },
        )
    }

    fun maxScore(questions: List<Question>): Double =
        questions.filter { it.answerVerified && it.payload.hasAnswer() }.sumOf { it.marks }
}
