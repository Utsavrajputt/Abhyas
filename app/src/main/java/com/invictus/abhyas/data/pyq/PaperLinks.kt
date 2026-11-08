package com.invictus.abhyas.data.pyq

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * A paper (or ready-made question bank) the user pointed the app at. The app ships no sources:
 * the user adds links from wherever they are allowed to download from.
 * [type] is [TYPE_PDF] (a question paper PDF, reviewed in PDF Studio) or [TYPE_PACK] (an Abhyas
 * question-bank zip, installed directly).
 */
data class PaperLink(
    val id: String,
    val title: String,
    val year: Int?,
    val url: String,
    val type: String,
) {
    companion object {
        const val TYPE_PDF = "PDF"
        const val TYPE_PACK = "PACK"
    }
}

/** Hand-off from the Downloads screen to PDF Studio: a downloaded PDF waiting for its import settings. */
data class PendingPaper(val uri: Uri, val year: Int?, val title: String, val bankId: String)

object PaperLinkCodec {
    fun encode(list: List<PaperLink>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("year", it.year ?: 0)
                    .put("url", it.url)
                    .put("type", it.type),
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<PaperLink> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (!url.startsWith("https://")) return@mapNotNull null
                PaperLink(
                    id = o.optString("id").ifBlank { return@mapNotNull null },
                    title = o.optString("title").ifBlank { "Paper" },
                    year = o.optInt("year", 0).takeIf { it in 1990..2100 },
                    url = url,
                    type = if (o.optString("type") == PaperLink.TYPE_PACK) PaperLink.TYPE_PACK else PaperLink.TYPE_PDF,
                )
            }
        }.getOrDefault(emptyList())
    }
}
