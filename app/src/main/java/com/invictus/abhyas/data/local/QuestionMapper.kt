package com.invictus.abhyas.data.local

import com.invictus.abhyas.domain.engine.ContentHash
import com.invictus.abhyas.domain.model.Difficulty
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.parseEnum
import com.invictus.abhyas.domain.pack.PayloadCodec

fun Question.toEntity(contentHash: String = ContentHash.of(this)): QuestionEntity = QuestionEntity(
    id = id,
    bankId = bankId,
    type = type.name,
    statement = statement,
    payloadJson = PayloadCodec.encode(payload).toString(),
    explanation = explanation,
    exam = exam.name,
    subject = subject,
    classLevel = classLevel,
    chapter = chapter,
    topic = topic,
    difficulty = difficulty?.name,
    year = year,
    source = source,
    sourcePage = sourcePage,
    tags = tags.joinToString(","),
    imagePath = imagePath,
    marks = marks,
    negativeMarks = negativeMarks,
    language = language,
    answerVerified = answerVerified,
    contentHash = contentHash,
)

/** Throws IllegalStateException on corrupt rows (should never happen: rows are written by [toEntity]). */
fun QuestionEntity.toQuestion(): Question {
    val t = parseEnum<QuestionType>(type) ?: error("Bad type '$type' in $id")
    return Question(
        id = id,
        bankId = bankId,
        type = t,
        statement = statement,
        payload = PayloadCodec.decodeString(t, payloadJson),
        explanation = explanation,
        exam = parseEnum<Exam>(exam) ?: error("Bad exam '$exam' in $id"),
        subject = subject,
        classLevel = classLevel,
        chapter = chapter,
        topic = topic,
        difficulty = parseEnum<Difficulty>(difficulty),
        year = year,
        source = source,
        sourcePage = sourcePage,
        tags = if (tags.isBlank()) emptyList() else tags.split(','),
        imagePath = imagePath,
        marks = marks,
        negativeMarks = negativeMarks,
        language = language,
        answerVerified = answerVerified,
    )
}
