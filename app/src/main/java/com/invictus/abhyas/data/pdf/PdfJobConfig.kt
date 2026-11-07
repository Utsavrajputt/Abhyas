package com.invictus.abhyas.data.pdf

import com.invictus.abhyas.domain.pack.AbhyasJson
import kotlinx.serialization.Serializable

/** User choices made when a PDF is added (stored as JSON on the job). */
@Serializable
data class PdfJobConfig(
    val exam: String = "NEET",
    val classLevel: Int? = null,
    val subject: String = "Physics",
    val chapter: String? = null,
    val kind: String = "PYQS",
    val layout: String = "AUTO",
    val ncert: Boolean = false,
    val year: Int? = null,
    val bankId: String = "",
    val title: String = "",
    val allowOcr: Boolean = true,
) {
    fun encode(): String = AbhyasJson.json.encodeToString(serializer(), this)

    companion object {
        fun decode(s: String): PdfJobConfig =
            runCatching { AbhyasJson.json.decodeFromString(serializer(), s) }.getOrDefault(PdfJobConfig())
    }
}
