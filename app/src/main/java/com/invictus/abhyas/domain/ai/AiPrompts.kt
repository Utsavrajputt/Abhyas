package com.invictus.abhyas.domain.ai

import com.invictus.abhyas.domain.engine.OptionShuffler
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.SingleChoicePayload

/** Builds prompts. Everything the model sees is the student's own textbook text; nothing else is sent. */
object AiPrompts {
    const val MAX_CONTEXT_CHARS = 6000
    const val MAX_QUESTION_CHARS = 600

    private const val BASE = "You are a patient tutor for Indian class 11-12 students preparing for NEET and JEE. " +
        "Use ONLY the passage you are given. If the passage does not contain what is needed, say so plainly instead of guessing. " +
        "Never invent page numbers, citations, formulas or facts. Write maths as plain text or LaTeX inside \$...\$. Be concise."

    fun clip(text: String, max: Int = MAX_CONTEXT_CHARS): String {
        val t = text.trim().replace(Regex("[ \\t]+"), " ")
        return if (t.length <= max) t else t.take(max).trimEnd() + " ..."
    }

    fun explain(passage: String): AiPrompt = AiPrompt(
        system = BASE,
        user = "Explain this textbook passage step by step. Define each key term the first time you use it, " +
            "then end with one line that says what to remember for the exam.\n\nPassage:\n${clip(passage)}",
    )

    fun simplify(passage: String): AiPrompt = AiPrompt(
        system = BASE,
        user = "Rewrite this passage in simple words a class 9 student would follow. Keep every fact and every term " +
            "that matters, and keep the technical term in brackets after its plain-language version.\n\nPassage:\n${clip(passage)}",
    )

    fun notes(passages: String): AiPrompt = AiPrompt(
        system = BASE,
        user = "Make revision notes from the text below: a short list of key points, then a list of definitions and " +
            "formulas that appear in the text. Do not add anything that is not in the text.\n\nText:\n${clip(passages)}",
    )

    fun ask(question: String, context: String): AiPrompt = AiPrompt(
        system = BASE,
        user = "Answer the student's question using only the passage. If the passage does not answer it, say " +
            "\"The passage does not cover this\" and stop.\n\nPassage:\n${clip(context)}\n\nQuestion: ${clip(question, MAX_QUESTION_CHARS)}",
    )

    /** Asks for assertion-reason questions as a strict JSON array (see [AiQuestionParser]). */
    fun assertionReason(passages: String, count: Int): AiPrompt = AiPrompt(
        system = BASE,
        json = true,
        user = "Write $count assertion-reason questions based ONLY on the text below. Reply with a JSON array and nothing else. " +
            "Each element: {\"assertion\": string, \"reason\": string, \"correctOption\": 1|2|3|4, \"explanation\": string}. " +
            "correctOption: 1 = both true and R explains A, 2 = both true but R does not explain A, 3 = A true and R false, 4 = A false and R true. " +
            "Make the options varied, every statement must be checkable from the text, and keep each statement under 40 words.\n\nText:\n${clip(passages)}",
    )

    /**
     * Explains a reviewed question: why the answer is right and why each other option is wrong. When the answer
     * key is not verified the model is told so and must say when it is unsure instead of sounding certain.
     */
    fun explainAnswer(q: Question, given: String, correct: String?, passage: String?): AiPrompt {
        val keyLine = if (q.answerVerified && correct != null) {
            "The verified correct answer is: $correct. Explain why it is correct and, for every other option, why it is wrong. " +
                "The student answered: $given. Say in one sentence what misunderstanding that answer suggests (skip this if they were right or did not answer)."
        } else {
            "The answer key for this question is NOT verified. Work it out from the passage if you can, and say clearly when you are unsure. " +
                "The student answered: $given."
        }
        val source = if (!passage.isNullOrBlank()) "Textbook passage:\n${clip(passage, 3000)}"
        else "No textbook passage is available for this question; use standard class 11-12 knowledge and say that you did."
        return AiPrompt(
            system = BASE,
            user = "$keyLine\n\n$source\n\nQuestion:\n${clip(questionText(q), 2500)}",
        )
    }

    /** The question as plain text, options labelled in the order the student saw them. */
    fun questionText(q: Question): String {
        fun labelled(options: List<Option>): String {
            val labels = OptionShuffler.labels(options)
            return options.joinToString("\n") { "(${labels[it.id]}) ${it.text}" }
        }
        return when (val p = q.payload) {
            is SingleChoicePayload -> q.statement + "\n" + labelled(p.options)
            is MultiChoicePayload -> q.statement + "\n" + labelled(p.options)
            is MatchPayload -> q.statement + "\n" + p.leftTitle + ": " + p.left.joinToString("; ") { "${it.id}. ${it.text}" } +
                "\n" + p.rightTitle + ": " + p.right.joinToString("; ") { "${it.id}. ${it.text}" } + "\n" + labelled(p.options)
            is AssertionReasonPayload -> q.statement + "\nAssertion (A): ${p.assertion}\nReason (R): ${p.reason}\n" +
                "(1) Both true and R explains A\n(2) Both true but R does not explain A\n(3) A true, R false\n(4) A false, R true"
            else -> q.statement
        }
    }

    /** A one-line request used by "Test connection" in Settings. */
    fun ping(): AiPrompt = AiPrompt(system = "Reply with the single word OK.", user = "Ping")
}
