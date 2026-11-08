package com.invictus.abhyas.domain.ncert

import com.invictus.abhyas.domain.ai.AiSource
import com.invictus.abhyas.domain.engine.Sha256
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.pack.AbhyasJson
import com.invictus.abhyas.domain.pack.Built
import com.invictus.abhyas.domain.pack.QuestionDto
import com.invictus.abhyas.domain.pack.QuestionFactory
import com.invictus.abhyas.domain.pdf.AnswerLayout
import com.invictus.abhyas.domain.pdf.ExtractMethod
import com.invictus.abhyas.domain.pdf.PageText
import com.invictus.abhyas.domain.pdf.ParseOptions
import com.invictus.abhyas.domain.pdf.PdfParser

class ExerciseResult(val questions: List<Question>, val note: String?)

/**
 * Pulls the end-of-chapter "Exercises" out of a chapter's passages with the M2 parser. NCERT prints no
 * answers there, so every result is `answerVerified = false` (shown, never scored). Nothing is invented.
 */
object ExerciseExtractor {
    const val BANK_ID = "ncert-ex"
    private val HEADING = Regex("^exercises?\\b.{0,25}$", RegexOption.IGNORE_CASE)

    fun extract(passages: List<SourcePassage>, ctx: AiSource): ExerciseResult {
        val ordered = passages.sortedWith(compareBy({ it.page }, { it.idx }))
        val start = ordered.indexOfFirst { HEADING.matches(it.text.trim()) }
        if (start < 0) return ExerciseResult(emptyList(), "No \"Exercises\" section was found in this chapter.")
        val body = ordered.drop(start + 1)
        if (body.isEmpty()) return ExerciseResult(emptyList(), "The Exercises section is empty.")

        val pages = body.groupBy { it.page }.map { (page, ps) ->
            PageText(page = page, text = ps.joinToString("\n") { it.text }, method = ExtractMethod.TEXT)
        }.sortedBy { it.page }
        val parsed = PdfParser.parse(pages, ParseOptions(layout = AnswerLayout.INLINE, ncert = true))

        val out = ArrayList<Question>()
        val seen = HashSet<String>()
        for (d in parsed.drafts) {
            if (d.statement.length < 25) continue
            val ref = findRef(body, d.page, d.statement)
            val id = "$BANK_ID:" + Sha256.hex("${ctx.bookId}|${d.number}|${d.statement}".toByteArray(Charsets.UTF_8)).take(16)
            if (!seen.add(id)) continue
            val dto = QuestionDto(
                id = id,
                bankId = BANK_ID,
                type = d.typeName,
                statement = d.statement,
                payload = AbhyasJson.json.parseToJsonElement(d.payloadJson),
                explanation = d.explanation,
                subject = ctx.subject,
                classLevel = ctx.classLevel,
                chapter = ctx.chapter,
                source = "NCERT exercise ${d.number}",
                sourcePage = d.page,
                tags = listOf("ncert-exercise"),
                ncertRef = ref,
            )
            val built = QuestionFactory.create(dto, BANK_ID, id, QuestionFactory.Defaults(ctx.exam, ctx.subject, ctx.classLevel))
            if (built is Built.Ok) out += built.question
        }
        val note = when {
            out.isEmpty() -> "No questions could be read from the Exercises section."
            out.any { !it.answerVerified } -> "Exercises have no printed answers, so they are not scored."
            else -> null
        }
        return ExerciseResult(out, note)
    }

    private fun findRef(body: List<SourcePassage>, page: Int, statement: String): NcertRef? {
        val key = statement.lowercase().filter { it.isLetterOrDigit() }.take(40)
        if (key.length < 12) return null
        return body.firstOrNull { it.page == page && it.text.lowercase().filter { c -> c.isLetterOrDigit() }.contains(key) }?.ref()
    }
}
