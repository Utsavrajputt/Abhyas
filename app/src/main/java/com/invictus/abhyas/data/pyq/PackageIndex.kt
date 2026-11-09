package com.invictus.abhyas.data.pyq

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a downloadable package is: a question paper / bank zip, or an NCERT-style book PDF. */
enum class PackageType { PAPER, BOOK }

/** One downloadable package listed in an `index.json` (any source: NCERT, PYQ or "Other links"). */
data class PackageEntry(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val url: String,
    /** Hex SHA-256 of the file. Optional for papers, required for books. */
    val sha256: String?,
    val type: PackageType,
    val subject: String?,
    val coaching: String?,
    /** Upper-case kind: PYQ, THEORY, ... (null = unknown). */
    val kind: String?,
    val year: Int?,
    val questions: Int,
    val classLevel: Int?,
    val exam: String?,
    val sizeBytes: Long?,
)

class ParsedIndex(val name: String?, val entries: List<PackageEntry>, val errors: List<String>)

/**
 * Reads an index.json. Accepts the new `{"name":"..","packages":[...]}` format and the older
 * `packs` (previous-year papers) and `books` (NCERT books) arrays, so existing indexes keep working.
 * Invalid entries are skipped and reported, never fatal. Pure JVM, unit-testable.
 */
object PackageIndexParser {
    private val ID = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val SHA = Regex("^[0-9a-fA-F]{64}$")

    fun parse(bytes: ByteArray): ParsedIndex {
        val root = try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return ParsedIndex(null, emptyList(), listOf("The link did not return a valid index.json"))
        }
        val obj = root as? JsonObject ?: return ParsedIndex(null, emptyList(), listOf("index.json must be a JSON object"))
        val entries = ArrayList<PackageEntry>()
        val errors = ArrayList<String>()
        val seen = HashSet<String>()
        var found = false
        for (key in listOf("packages", "packs", "books")) {
            val arr = obj[key] as? JsonArray ?: continue
            found = true
            val defaultType = if (key == "books") PackageType.BOOK else PackageType.PAPER
            arr.forEachIndexed { i, el ->
                val o = el as? JsonObject
                if (o == null) { errors += "Entry #${i + 1}: not an object"; return@forEachIndexed }
                when (val r = entry(o, defaultType)) {
                    is Result.Ok -> { if (seen.add(r.entry.id)) entries.add(r.entry) }
                    is Result.Bad -> { errors.add("Entry #${i + 1}${o.str("id")?.let { " ($it)" } ?: ""}: ${r.why}") }
                }
                Unit
            }
        }
        if (!found) errors += "index.json has no \"packages\" list"
        return ParsedIndex(obj.str("name") ?: obj.str("title"), entries, errors)
    }

    private sealed interface Result {
        class Ok(val entry: PackageEntry) : Result
        class Bad(val why: String) : Result
    }

    private fun entry(o: JsonObject, defaultType: PackageType): Result {
        val id = o.str("id")
        if (id == null || !ID.matches(id)) return Result.Bad("bad id")
        val url = o.str("url")
        if (url == null || !url.startsWith("https://")) return Result.Bad("url must start with https://")
        val sha = o.str("sha256")
        if (sha != null && !SHA.matches(sha)) return Result.Bad("sha256 must be 64 hex characters")
        val type = when (o.str("type")?.lowercase()) {
            "book" -> PackageType.BOOK
            "paper", "pack", "bank", "zip" -> PackageType.PAPER
            else -> defaultType
        }
        if (type == PackageType.BOOK && sha == null) return Result.Bad("books need a sha256")
        if (type == PackageType.BOOK && o.str("subject") == null) return Result.Bad("books need a subject")
        val year = o.num("year")?.toInt()?.takeIf { it in 1990..2100 }
        val kind = when (val k = o.str("kind")?.uppercase()?.replace(" ", "_")) {
            "PYQ", "PYQS" -> "PYQ"
            else -> k
        }
        return Result.Ok(
            PackageEntry(
                id = id,
                name = o.str("name") ?: o.str("title") ?: id,
                description = o.str("description") ?: o.str("note") ?: "",
                version = o.version(),
                url = url,
                sha256 = sha?.lowercase(),
                type = type,
                subject = o.str("subject"),
                coaching = o.str("coaching"),
                kind = kind,
                year = year,
                questions = (o.num("questions") ?: o.num("questionCount") ?: 0L).toInt().coerceAtLeast(0),
                classLevel = o.num("class")?.toInt(),
                exam = o.str("exam"),
                sizeBytes = o.num("sizeBytes"),
            ),
        )
    }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }

    private fun JsonObject.num(k: String): Long? = (this[k] as? JsonPrimitive)?.content?.trim()?.toLongOrNull()

    private fun JsonObject.version(): Int =
        (this["version"] as? JsonPrimitive)?.content?.trim()?.substringBefore('.')?.toIntOrNull()?.coerceAtLeast(1) ?: 1
}

/** A source the user added in Settings > NCERT & data > Other links. [name] is filled from the link's index.json. */
@Serializable
data class IndexLink(val id: String, val name: String, val url: String)

/** Extra facts about an installed paper that the question-bank manifest does not carry (from the index.json). */
@Serializable
data class PaperMeta(
    val name: String? = null,
    val coaching: String? = null,
    val subject: String? = null,
    val kind: String? = null,
    val year: Int? = null,
)

object SourceCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val linksSerializer = ListSerializer(IndexLink.serializer())
    private val metaSerializer = MapSerializer(String.serializer(), PaperMeta.serializer())

    fun encodeLinks(list: List<IndexLink>): String = json.encodeToString(linksSerializer, list)

    fun decodeLinks(s: String?): List<IndexLink> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(linksSerializer, s) }.getOrDefault(emptyList())
            .filter { it.url.startsWith("https://") }
    }

    fun encodeMeta(map: Map<String, PaperMeta>): String = json.encodeToString(metaSerializer, map)

    fun decodeMeta(s: String?): Map<String, PaperMeta> {
        if (s.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(metaSerializer, s) }.getOrDefault(emptyMap())
    }
}

/** Turns the GitHub page address a person copies from the browser into the raw file address the app can read. */
object GitHubLinks {
    private val BLOB = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$")
    private val REPO = Regex("^https://github\\.com/([^/]+)/([^/?#]+?)(?:\\.git)?/?$")

    fun normalize(input: String): String {
        val u = input.trim()
        BLOB.matchEntire(u)?.let { return "https://raw.githubusercontent.com/${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}/${it.groupValues[4]}" }
        REPO.matchEntire(u)?.let { return "https://raw.githubusercontent.com/${it.groupValues[1]}/${it.groupValues[2]}/HEAD/index.json" }
        return u
    }
}
