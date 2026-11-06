package com.invictus.abhyas.domain.engine

import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.QuestionType
import com.invictus.abhyas.domain.model.withoutAnswer
import com.invictus.abhyas.domain.pack.PayloadCodec
import java.security.MessageDigest

object Sha256 {
    fun hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/**
 * Identity of a question's *content* (type + normalised statement + options), independent of its
 * id, bank, metadata and answer key, so the same question is deduped across packages.
 */
object ContentHash {
    fun normalize(s: String): String = s.lowercase().replace(Regex("\\s+"), " ").trim()

    fun of(q: Question): String = of(q.type, q.statement, PayloadCodec.encode(q.payload.withoutAnswer()).toString())

    fun of(type: QuestionType, statement: String, payloadCanonical: String): String =
        Sha256.hex("${type.name}\n${normalize(statement)}\n$payloadCanonical".toByteArray(Charsets.UTF_8))
}
