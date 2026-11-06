package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.engine.Sha256
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionPayload
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.SingleChoicePayload

/** Synthetic placeholders only ("TEST ..."); never realistic exam content. */
object TestData {
    fun opts(n: Int = 4) = (1..n).map { Option("o$it", "TEST option $it") }

    fun question(
        payload: QuestionPayload,
        type: QuestionType = when (payload) {
            is SingleChoicePayload -> QuestionType.SINGLE_MCQ
            is MultiChoicePayload -> QuestionType.MULTI_MCQ
            is NumericalPayload -> QuestionType.NUMERICAL
            else -> QuestionType.SINGLE_MCQ
        },
        verified: Boolean = true,
        marks: Double = 4.0,
        negative: Double = 1.0,
    ) = Question(
        id = "test:1", bankId = "test", type = type, statement = "TEST statement",
        payload = payload, exam = Exam.NEET, subject = "Physics",
        marks = marks, negativeMarks = negative, answerVerified = verified,
    )

    fun manifest(
        questionsJson: String,
        count: Int,
        id: String = "test_bank",
        version: Int = 1,
        sha: String = Sha256.hex(questionsJson.toByteArray()),
    ) = """{"id":"$id","version":$version,"title":"TEST","exam":"NEET","class":11,"subject":"Physics","kind":"theory","sha256":"$sha","questionCount":$count}"""

    fun mcq(id: String, statement: String = "TEST statement $id", correct: String? = "a") =
        """{"id":"$id","type":"SINGLE_MCQ","statement":"$statement","payload":{"options":[{"id":"a","text":"TEST A"},{"id":"b","text":"TEST B"}]${if (correct != null) ",\"correctId\":\"$correct\"" else ""}}}"""
}
