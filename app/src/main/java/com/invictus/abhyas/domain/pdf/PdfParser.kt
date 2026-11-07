package com.invictus.abhyas.domain.pdf

import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.QuestionPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import com.invictus.abhyas.domain.model.hasAnswer
import com.invictus.abhyas.domain.pack.PayloadCodec

/**
 * Deterministic text -> draft questions pipeline (no AI). Pure Kotlin; works on the cleaned text of
 * every page, so digital and OCR pages are handled the same way.
 */
object PdfParser {

    private val INLINE_ANS = Regex(
        "^\\[?\\s*(?:ans(?:wer)?|correct\\s+(?:answer|option|choice))\\s*(?:[.:\\-\\u2013]|\\s)\\s*(.+?)\\]?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val INLINE_ANS_END = Regex("\\[\\s*ans(?:wer)?\\.?\\s*[:\\-]?\\s*([^\\]]{1,30})\\]\\s*$", RegexOption.IGNORE_CASE)
    private val ANS_PUNCT = Regex("^\\[?\\s*(?:ans(?:wer)?|correct\\s+\\w+)\\s*[.:\\-\\u2013]", RegexOption.IGNORE_CASE)
    private val ANS_TOKEN = Regex("^[(\\[]?[A-Ea-e1-5][)\\]]?(?:\\s*(?:,|&|and)?\\s*[(\\[]?[A-Ea-e][)\\]]?)*$")
    private val SOL_MARK = Regex("^(?:sol(?:ution)?|explanation|hint)\\b\\.?\\s*:?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val SOL_INLINE = Regex("\\s+(?:sol(?:ution)?\\.?|explanation)\\b.*$", RegexOption.IGNORE_CASE)

    private val MULTI_HINT = Regex(
        "one or more|more than one|multiple correct|more than one option|select all|correct (?:options|statements|answers?) (?:is|are)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TF_HINT = Regex("true\\s*(?:or|/)\\s*false|state whether .*true", RegexOption.IGNORE_CASE)
    private val BLANK = Regex("_{3,}|\\u2026{2,}|\\.{5,}")
    private val INTEGER_HINT = Regex("integer|numerical value|non-negative|nearest integer|answer is", RegexOption.IGNORE_CASE)
    private val FIGURE_HINT = Regex("\\bfigure\\b|\\bfig\\.|diagram|as shown|shown (?:below|above|in)|graph|circuit|the (?:given )?curve", RegexOption.IGNORE_CASE)
    private val AR_SPLIT = Regex(
        "assertion\\s*(?:\\(?\\s*a\\s*\\)?)?\\s*[:\\-\\u2013.]\\s*(.+?)\\s*reason\\s*(?:\\(?\\s*r\\s*\\)?)?\\s*[:\\-\\u2013.]\\s*(.+)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val AR_SHORT = Regex("\\bA\\s*[:\\u2013-]\\s*(.+?)\\s*\\bR\\s*[:\\u2013-]\\s*(.+)", RegexOption.DOT_MATCHES_ALL)
    private val MATCH_HINT = Regex("(?:list|column)[- ]?i\\b.*(?:list|column)[- ]?ii", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun parse(pages: List<PageText>, options: ParseOptions = ParseOptions()): ParseOutput {
        val notes = ArrayList<String>()
        val cleaned = TextCleaner.clean(pages)
        val lines = cleaned.flatMap { (page, ls) -> ls.map { Ln(page, it) } }
        if (lines.isEmpty()) return ParseOutput(emptyList(), 0, listOf("No text found in the PDF"))
        val ocrPages = pages.filter { it.method == ExtractMethod.OCR }.map { it.page }.toSet()
        val imagePages = pages.filter { it.hasImages }.map { it.page }.toSet()

        // ---- locate answer key / solutions sections -------------------------------------------
        var keyIdx = -1
        var key: Map<String, String> = emptyMap()
        if (options.layout != AnswerLayout.INLINE) {
            for (h in lines.indices.reversed()) {
                if (!AnswerKeyParser.isHeading(lines[h].text)) continue
                val end = (h + 1 until lines.size).firstOrNull { AnswerKeyParser.isSolutionsHeading(lines[it].text) } ?: lines.size
                val entries = AnswerKeyParser.parse(lines.subList(h + 1, end).map { it.text })
                if (entries.size >= 3) { keyIdx = h; key = entries; break }
            }
            if (keyIdx < 0 && options.layout == AnswerLayout.KEY_AT_END) notes += "No answer key section found"
        }
        val solStart = (maxOf(keyIdx, 0) until lines.size).firstOrNull { AnswerKeyParser.isSolutionsHeading(lines[it].text) }
        val questionEnd = when {
            keyIdx >= 0 -> keyIdx
            solStart != null -> solStart
            else -> lines.size
        }
        val solutions: Map<String, String> = if (solStart != null && options.layout != AnswerLayout.INLINE) {
            QuestionSplitter.split(lines.subList(solStart + 1, lines.size), options.ncert)
                .associate { it.label to it.lines.joinToString(" ").trim() }
        } else emptyMap()

        // ---- split + build -------------------------------------------------------------------
        val blocks = QuestionSplitter.split(lines.subList(0, questionEnd), options.ncert)
        if (blocks.isEmpty()) notes += "No numbered questions detected"
        val drafts = blocks.mapIndexed { i, b ->
            build(i, b, key, solutions, options, b.page in ocrPages, b.page in imagePages)
        }
        if (key.isNotEmpty()) notes += "Answer key: ${key.size} entries"
        return ParseOutput(drafts, key.size, notes)
    }

    private fun build(
        position: Int,
        block: QBlock,
        key: Map<String, String>,
        solutions: Map<String, String>,
        opts: ParseOptions,
        ocr: Boolean,
        pageHasImages: Boolean,
    ): ParsedDraft {
        val issues = ArrayList<String>()
        var confidence = 1.0

        // Inline answer + explanation
        val body = ArrayList<String>()
        var inlineRaw: String? = null
        var explanation: String? = solutions[block.label]
        val solLines = ArrayList<String>()
        var inSol = false
        for ((idx, l) in block.lines.withIndex()) {
            if (!inSol) {
                val sol = SOL_MARK.matchEntire(l)
                if (sol != null) { inSol = true; if (sol.groupValues[1].isNotBlank()) solLines += sol.groupValues[1]; continue }
                val ans = INLINE_ANS.matchEntire(l)
                if (ans != null && opts.layout != AnswerLayout.KEY_AT_END) {
                    var raw = ans.groupValues[1].replace(SOL_INLINE, "").trim()
                    if (raw.isEmpty()) raw = block.lines.getOrNull(idx + 1)?.trim().orEmpty()
                    if (raw.isNotEmpty() && plausibleAnswer(raw, ANS_PUNCT.containsMatchIn(l))) {
                        inlineRaw = raw
                        if (SOL_INLINE.containsMatchIn(ans.groupValues[1])) {
                            solLines += SOL_INLINE.find(ans.groupValues[1])!!.value.replace(Regex("^\\s*\\S+\\.?\\s*:?"), "").trim()
                            inSol = true
                        }
                        continue
                    }
                }
                body += l
            } else solLines += l
        }
        if (inlineRaw == null) {
            // "... [Ans. (3)]" at the end of the last body line
            val last = body.lastOrNull()
            val m = last?.let { INLINE_ANS_END.find(it) }
            if (m != null && opts.layout != AnswerLayout.KEY_AT_END) {
                inlineRaw = m.groupValues[1].trim()
                body[body.lastIndex] = last!!.substring(0, m.range.first).trim()
            }
        }
        if (solLines.isNotEmpty()) explanation = solLines.joinToString(" ").replace(Regex("\\s+"), " ").trim()

        val keyRaw = key[block.label]
        val rawAnswer = inlineRaw ?: keyRaw
        if (inlineRaw != null && keyRaw != null && normalizeAnswer(inlineRaw) != normalizeAnswer(keyRaw)) {
            issues += "Inline answer ($inlineRaw) differs from the key ($keyRaw)"
            confidence -= 0.3
        }

        val split = OptionParser.parse(body)
        val full = body.joinToString("\n")
        val stemLower = split.stem.lowercase()
        val hasOptions = split.options.size >= 2
        var statement = if (MATCH_HINT.containsMatchIn(full)) split.stemRaw.lines().joinToString("\n") { it.trim() } else split.stem
        val payload: QuestionPayload
        val type: QuestionType

        val ar = if (stemLower.contains("assertion") && stemLower.contains("reason")) arParts(split.stem) else null
        when {
            ar != null -> {
                type = QuestionType.ASSERTION_REASON
                statement = ar.first.ifBlank { "Assertion (A) and Reason (R) are given below." }
                val code = arAnswer(rawAnswer, split.options, issues)
                if (!hasOptions) { issues += "Standard assertion-reason option order assumed"; confidence -= 0.15 }
                payload = AssertionReasonPayload(ar.second, ar.third, code)
            }
            hasOptions -> {
                val opt = split.options.map { Option(it.first, it.second) }
                val ids = AnswerInterpreter.optionIds(rawAnswer, opt.size)
                if (rawAnswer != null && ids.isEmpty()) issues += "Answer '$rawAnswer' does not match the options"
                if (MULTI_HINT.containsMatchIn(split.stem)) {
                    type = QuestionType.MULTI_MCQ
                    payload = MultiChoicePayload(opt, ids)
                } else {
                    type = QuestionType.SINGLE_MCQ
                    if (ids.size > 1) issues += "Key lists several options for a single-answer question"
                    payload = SingleChoicePayload(opt, if (ids.size == 1) ids.first() else null)
                }
                if (opt.size != 4) { confidence -= 0.1; issues += "${opt.size} options found" }
            }
            TF_HINT.containsMatchIn(split.stem) -> {
                type = QuestionType.TRUE_FALSE
                payload = TrueFalsePayload(AnswerInterpreter.bool(rawAnswer))
            }
            BLANK.containsMatchIn(split.stem) && AnswerInterpreter.number(rawAnswer) == null -> {
                type = QuestionType.FILL_BLANK
                payload = FillBlankPayload(blankAnswers(rawAnswer))
            }
            AnswerInterpreter.number(rawAnswer) != null || (INTEGER_HINT.containsMatchIn(split.stem) && !opts.ncert) -> {
                type = QuestionType.NUMERICAL
                val n = AnswerInterpreter.number(rawAnswer)
                if (rawAnswer != null && n == null) issues += "Numerical answer '$rawAnswer' is not a plain number"
                payload = NumericalPayload(
                    answer = n,
                    tolerance = AnswerInterpreter.toleranceFor(split.stem),
                    integerOnly = n != null && AnswerInterpreter.isInteger(n) && Regex("integer", RegexOption.IGNORE_CASE).containsMatchIn(split.stem),
                )
            }
            else -> {
                type = QuestionType.FILL_BLANK
                payload = FillBlankPayload(blankAnswers(rawAnswer))
                issues += "No options found: descriptive question, add accepted answers or skip it"
                confidence -= 0.45
            }
        }

        if (!payload.hasAnswer()) {
            issues += "No answer found"
            confidence -= 0.35
        }
        if (statement.length < 12) { issues += "Very short statement"; confidence -= 0.3 }
        if (statement.length > 1500) { issues += "Very long statement (merged questions?)"; confidence -= 0.2 }
        if (ocr) { issues += "Read by OCR: check the text"; confidence -= 0.15 }
        if (block.gapBefore) { issues += "Numbering jumps before this question"; confidence -= 0.1 }
        val figure = pageHasImages || FIGURE_HINT.containsMatchIn(statement)
        if (figure) { issues += "May need a figure"; confidence -= 0.05 }

        return ParsedDraft(
            position = position,
            number = block.label,
            page = block.page,
            typeName = type.name,
            statement = statement,
            payloadJson = PayloadCodec.encode(payload).toString(),
            explanation = explanation?.takeIf { it.isNotBlank() },
            confidence = confidence.coerceIn(0.0, 1.0),
            issues = issues,
            hasFigureHint = figure,
        )
    }

    /** Tokens/numbers/true-false always; short free text only when the marker had punctuation ("Ans. ..."). */
    private fun plausibleAnswer(raw: String, hadPunct: Boolean): Boolean =
        ANS_TOKEN.matches(raw) || AnswerInterpreter.number(raw) != null || AnswerInterpreter.bool(raw) != null ||
            (hadPunct && raw.length <= 30 && raw.split(' ').size <= 4)

    private fun blankAnswers(raw: String?): List<String> =
        raw?.split('/', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun normalizeAnswer(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun arParts(stem: String): Triple<String, String, String>? {
        val m = AR_SPLIT.find(stem) ?: AR_SHORT.find(stem) ?: return null
        val prefix = stem.substring(0, m.range.first).replace(Regex("[\\s:\\-]+$"), "").trim()
        val a = m.groupValues[1].trim()
        val r = m.groupValues[2].trim()
        return if (a.isNotEmpty() && r.isNotEmpty()) Triple(prefix, a, r) else null
    }

    /** Answer 1..4 in standard order, verified against the option wording when options are present. */
    private fun arAnswer(raw: String?, options: List<Pair<String, String>>, issues: MutableList<String>): Int? {
        if (raw == null) return null
        val pos = AnswerInterpreter.singleId(raw, maxOf(options.size, 4))
        if (pos == null) { issues += "Answer '$raw' not understood"; return null }
        val ord = OptionParser.ordinal(pos) ?: return null
        if (options.size < 4) return ord
        val codes = options.map { AnswerInterpreter.arCode(it.second) }
        if (codes.any { it == null } || codes.toSet().size != codes.size) {
            issues += "Option wording not recognised: answer left unverified"
            return null
        }
        return codes[ord - 1]
    }
}
