package com.invictus.abhyas.domain.analytics

import com.invictus.abhyas.domain.engine.OptionShuffler
import com.invictus.abhyas.domain.model.AssertionReasonOptions
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload

/** Human-readable answers for the review screen, using the option order the student actually saw. */
object AnswerFormatter {

    /** Null when the question has no answer key. */
    fun correct(q: Question): String? = when (val p = q.payload) {
        is SingleChoicePayload -> p.correctId?.let { choice(p.options, setOf(it)) }
        is MultiChoicePayload -> if (p.correctIds.isEmpty()) null else choice(p.options, p.correctIds.toSet())
        is MatchPayload -> p.correctId?.let { choice(p.options, setOf(it)) }
        is AssertionReasonPayload -> p.correctOption?.let { "($it) " + AssertionReasonOptions.ALL.getOrNull(it - 1)?.text.orEmpty() }
        is TrueFalsePayload -> p.answer?.let { if (it) "True" else "False" }
        is NumericalPayload -> p.answer?.let { num(it) + if (p.tolerance > 0) " (+/- ${num(p.tolerance)})" else "" }
        is FillBlankPayload -> if (p.acceptedAnswers.isEmpty()) null else p.acceptedAnswers.joinToString(" / ")
    }

    fun given(q: Question, r: Response?): String {
        if (r == null) return "Not attempted"
        return when (r) {
            is Response.Choice -> when (val p = q.payload) {
                is SingleChoicePayload -> choice(p.options, r.ids)
                is MultiChoicePayload -> choice(p.options, r.ids)
                is MatchPayload -> choice(p.options, r.ids)
                is AssertionReasonPayload ->
                    r.ids.sorted().joinToString("; ") { id -> "($id) " + (AssertionReasonOptions.ALL.firstOrNull { it.id == id }?.text.orEmpty()) }
                else -> r.ids.joinToString()
            }
            is Response.Number -> num(r.value)
            is Response.Bool -> if (r.value) "True" else "False"
            is Response.Text -> r.value
        }.ifBlank { "Not attempted" }
    }

    private fun choice(options: List<Option>, ids: Set<String>): String {
        val labels = OptionShuffler.labels(options)
        return options.filter { it.id in ids }.joinToString("; ") { "${labels[it.id]}. ${it.text}" }
    }

    private fun num(v: Double): String = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
}
