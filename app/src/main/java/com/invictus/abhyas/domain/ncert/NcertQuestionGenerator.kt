package com.invictus.abhyas.domain.ncert

import com.invictus.abhyas.domain.ai.AiSource
import com.invictus.abhyas.domain.engine.Sha256
import com.invictus.abhyas.domain.model.Difficulty
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.TrueFalsePayload
import com.invictus.abhyas.domain.validation.QuestionValidator
import kotlin.random.Random

enum class GenKind(val label: String) {
    FILL_BLANK("Fill in the blanks"),
    TRUE_FALSE("True / false"),
    EXERCISE("Book exercises"),
    ASSERTION_REASON("Assertion-reason (AI)"),
}

class GenResult(val questions: List<Question>, val notes: List<String>)

/**
 * Builds practice questions from the book's own sentences with plain rules (no AI). The answer is the
 * book text itself, so these are `answerVerified = true`; false statements are made by swapping one word
 * for its opposite or one defined term for another, and the original sentence is shown in the explanation.
 */
object NcertQuestionGenerator {
    const val BANK_ID = "ncert-gen"
    private val BLANK_MARK = "_____"

    fun fillBlanks(passages: List<SourcePassage>, ctx: AiSource, max: Int, random: Random): List<Question> {
        val terms = TermExtractor.extract(passages).shuffled(random)
        val out = ArrayList<Question>()
        for (t in terms) {
            if (out.size >= max) break
            if (BAD_START.containsMatchIn(t.sentence)) continue
            val stem = blank(t.sentence, t.term) ?: continue
            val answers = listOf(t.term).distinct()
            out += make(
                ctx, t.source, QuestionType.FILL_BLANK, stem,
                FillBlankPayload(acceptedAnswers = answers, caseSensitive = false),
                explanation = "Answer: ${t.term}. NCERT page ${t.source.page}: \"${t.sentence}\"",
            ) ?: continue
        }
        return out
    }

    fun trueFalse(passages: List<SourcePassage>, ctx: AiSource, max: Int, random: Random): List<Question> {
        val terms = TermExtractor.extract(passages)
        val termPool = terms.map { it.term }
        val trues = ArrayList<Question>()
        val falses = ArrayList<Question>()
        val sentences = passages.flatMap { p -> SentenceSplitter.split(p.text).filter(::usable).map { it to p } }.shuffled(random)
        for ((s, p) in sentences) {
            if (trues.size >= max && falses.size >= max) break
            // Keep the two sides level: try a false version only while false items are not ahead.
            val tryFalse = falses.size < max && (falses.size <= trues.size || trues.size >= max)
            val wrong = if (tryFalse) falsify(s, termPool, random) else null
            if (wrong != null) {
                make(ctx, p, QuestionType.TRUE_FALSE, wrong, TrueFalsePayload(false), "False. NCERT page ${p.page} says: \"$s\"")
                    ?.let { falses += it }
            } else if (trues.size < max) {
                make(ctx, p, QuestionType.TRUE_FALSE, s, TrueFalsePayload(true), "True. NCERT page ${p.page}.")
                    ?.let { trues += it }
            }
        }
        // Alternate so the set is balanced even when one side runs short.
        val out = ArrayList<Question>()
        var i = 0
        while (out.size < max && (i < trues.size || i < falses.size)) {
            if (i < trues.size && out.size < max) out += trues[i]
            if (i < falses.size && out.size < max) out += falses[i]
            i++
        }
        return out
    }

    /** Dispatches by kind. [GenKind.EXERCISE] and [GenKind.ASSERTION_REASON] are handled elsewhere. */
    fun generate(passages: List<SourcePassage>, ctx: AiSource, kinds: Set<GenKind>, count: Int, random: Random = Random.Default): GenResult {
        val rule = kinds.filter { it == GenKind.FILL_BLANK || it == GenKind.TRUE_FALSE }
        val notes = ArrayList<String>()
        if (rule.isEmpty()) return GenResult(emptyList(), notes)
        val share = (count + rule.size - 1) / rule.size
        val pools = rule.associateWith { kind ->
            when (kind) {
                GenKind.FILL_BLANK -> fillBlanks(passages, ctx, share * 2, random)
                else -> trueFalse(passages, ctx, share * 2, random)
            }
        }
        val picked = ArrayList<Question>()
        val used = HashSet<String>()
        for ((kind, qs) in pools) {
            val take = qs.take(share)
            if (take.size < share) notes += "Only ${take.size} ${kind.label.lowercase()} items could be made from this text."
            take.forEach { if (used.add(it.id)) picked += it }
        }
        // Top up from whichever pool still has spare items.
        for (q in pools.values.flatten()) {
            if (picked.size >= count) break
            if (used.add(q.id)) picked += q
        }
        return GenResult(picked.shuffled(random).take(count), notes)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun make(
        ctx: AiSource,
        source: SourcePassage,
        type: QuestionType,
        statement: String,
        payload: com.invictus.abhyas.domain.model.QuestionPayload,
        explanation: String,
    ): Question? {
        val id = "$BANK_ID:" + Sha256.hex("${source.bookId}|$type|$statement".toByteArray(Charsets.UTF_8)).take(16)
        val q = Question(
            id = id,
            bankId = BANK_ID,
            type = type,
            statement = statement,
            payload = payload,
            explanation = explanation,
            exam = ctx.exam,
            subject = ctx.subject,
            classLevel = ctx.classLevel,
            chapter = ctx.chapter ?: source.chapter,
            difficulty = Difficulty.EASY,
            source = "NCERT (made from the book text by rules)",
            sourcePage = source.page,
            tags = listOf("ncert-generated", "rule-based"),
            marks = 4.0,
            negativeMarks = 1.0,
            answerVerified = true,
            ncertRef = source.ref(),
        )
        return if (QuestionValidator.validate(q).isEmpty()) q else null
    }

    /** [sentence] with the single occurrence of [term] replaced by a blank; null if it is not a clean fit. */
    fun blank(sentence: String, term: String): String? {
        val first = sentence.indexOf(term, ignoreCase = true)
        if (first < 0) return null
        if (sentence.indexOf(term, first + 1, ignoreCase = true) >= 0) return null
        val before = sentence.getOrNull(first - 1)
        val after = sentence.getOrNull(first + term.length)
        if ((before != null && before.isLetterOrDigit()) || (after != null && after.isLetterOrDigit())) return null
        val stem = sentence.substring(0, first) + BLANK_MARK + sentence.substring(first + term.length)
        // A blank this close to the start leaves no clue to answer from.
        return stem.takeIf { first >= 12 || stem.length - BLANK_MARK.length >= 60 }
    }

    private val BAD_START = Regex(
        "^(this|these|those|that|it|its|they|their|such|thus|hence|therefore|however|also|moreover|similarly|here|there|he|she|we|you|" +
            "in this|in the above|as|but|and|so|for example|for instance|note|consider|let|suppose|figure|fig|table|activity|example|solution|exercise)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val BAD_ANYWHERE = Regex("\\bfig(?:ure)?s?\\b|\\btable\\b|\\(see|\\babove\\b|\\bbelow\\b|\\bfollowing\\b|\\beq(?:uation)?s?\\.?\\s*\\(|\\(\\d+\\.\\d+\\)|\\?", RegexOption.IGNORE_CASE)

    /** A sentence that stands on its own and reads as a plain statement of fact. */
    fun usable(s: String): Boolean {
        if (s.length !in 50..220 || !s.endsWith(".")) return false
        if (!s.first().isUpperCase()) return false
        if (BAD_START.containsMatchIn(s) || BAD_ANYWHERE.containsMatchIn(s)) return false
        val letters = s.count { it.isLetter() }
        return letters.toDouble() / s.length >= 0.75
    }

    private val OPPOSITES = listOf(
        "increase" to "decrease", "increases" to "decreases", "increased" to "decreased", "increasing" to "decreasing",
        "higher" to "lower", "greater" to "smaller", "more" to "less", "larger" to "smaller",
        "positive" to "negative", "absorbed" to "released", "absorbs" to "releases", "absorb" to "release",
        "directly" to "inversely", "parallel" to "perpendicular", "maximum" to "minimum",
        "clockwise" to "anticlockwise", "faster" to "slower", "upward" to "downward",
        "attractive" to "repulsive", "endothermic" to "exothermic", "convex" to "concave",
        "converging" to "diverging", "real" to "virtual", "gains" to "loses", "gain" to "lose",
        "oxidation" to "reduction", "cathode" to "anode", "left" to "right",
    )
    private val NEGATION = Regex("\\b(not|no|never|neither|nor|cannot|can't|unless|without|none)\\b", RegexOption.IGNORE_CASE)

    /** A false version of [s], or null when no safe change exists. */
    fun falsify(s: String, termPool: List<String>, random: Random): String? {
        if (NEGATION.containsMatchIn(s)) return null
        val shuffled = OPPOSITES.shuffled(random)
        for ((a, b) in shuffled) {
            for ((from, to) in listOf(a to b, b to a)) {
                val m = Regex("\\b${Regex.escape(from)}\\b", RegexOption.IGNORE_CASE).find(s) ?: continue
                // Both words present means the sentence contrasts them; swapping could keep it true.
                if (Regex("\\b${Regex.escape(to)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(s)) continue
                val repl = if (m.value.first().isUpperCase()) to.replaceFirstChar { it.uppercase() } else to
                return s.replaceRange(m.range, repl)
            }
        }
        val term = TermExtractor.termIn(s)
        if (term != null) {
            val other = termPool.filter { !it.equals(term, true) && !s.contains(it, true) && !term.contains(it, true) && !it.contains(term, true) }
                .let { if (it.isEmpty()) null else it[random.nextInt(it.size)] }
            if (other != null) {
                val i = s.indexOf(term, ignoreCase = true)
                if (i >= 0) return s.replaceRange(i, i + term.length, other)
            }
        }
        return null
    }
}
