package com.invictus.abhyas.domain.ncert

import com.invictus.abhyas.domain.ai.AiQuestionParser
import com.invictus.abhyas.domain.model.Question

/**
 * Questions made on the fly from NCERT text live only in an attempt snapshot until the student taps
 * "Save to bank". Saving moves them into one bank so bookmarks, the mistake notebook and retry work for them.
 */
object GeneratedBanks {
    const val PRACTICE_BANK = "ncert-practice"
    const val PRACTICE_TITLE = "NCERT practice (saved)"

    private val SOURCES = setOf(NcertQuestionGenerator.BANK_ID, ExerciseExtractor.BANK_ID, AiQuestionParser.BANK_ID)

    fun isGenerated(q: Question): Boolean = q.bankId in SOURCES

    /** Same question, filed under the saved-practice bank. The id stays, so attempt history still lines up. */
    fun forSaving(q: Question): Question = q.copy(bankId = PRACTICE_BANK)
}
