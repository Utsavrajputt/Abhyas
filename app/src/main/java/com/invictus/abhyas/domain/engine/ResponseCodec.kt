package com.invictus.abhyas.domain.engine

import com.invictus.abhyas.domain.model.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Compact JSON form of a [Response] for the attempt_questions table. */
object ResponseCodec {
    fun encode(r: Response): String = when (r) {
        is Response.Choice -> buildJsonObject {
            put("k", "choice")
            put("ids", JsonArray(r.ids.sorted().map { JsonPrimitive(it) }))
        }
        is Response.Number -> buildJsonObject { put("k", "number"); put("v", r.value) }
        is Response.Bool -> buildJsonObject { put("k", "bool"); put("v", r.value) }
        is Response.Text -> buildJsonObject { put("k", "text"); put("v", r.value) }
    }.toString()

    fun decode(json: String?): Response? {
        if (json.isNullOrBlank()) return null
        return try {
            val o: JsonObject = Json.parseToJsonElement(json).jsonObject
            when (o["k"]?.jsonPrimitive?.contentOrNull) {
                "choice" -> Response.Choice(o["ids"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
                "number" -> Response.Number(o["v"]!!.jsonPrimitive.double)
                "bool" -> Response.Bool(o["v"]!!.jsonPrimitive.boolean)
                "text" -> Response.Text(o["v"]!!.jsonPrimitive.content)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
