package com.invictus.abhyas.domain.pack

import com.invictus.abhyas.domain.engine.ContentHash
import com.invictus.abhyas.domain.engine.Sha256
import com.invictus.abhyas.domain.model.BankKind
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.parseEnum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class ParsedQuestion(val question: Question, val contentHash: String)

data class QuestionError(val index: Int, val id: String?, val messages: List<String>)

sealed interface PackageParseResult {
    data class Success(
        val manifest: BankManifest,
        val exam: Exam,
        val kind: BankKind,
        val questions: List<ParsedQuestion>,
        val errors: List<QuestionError>,
        val duplicatesInPackage: Int,
    ) : PackageParseResult

    /** Package-level problem: nothing can be imported. */
    data class Failure(val reason: String) : PackageParseResult
}

/**
 * Pure (no Android/Room) package reader: verifies manifest + checksum, validates every question
 * independently so one bad entry never blocks the rest, and dedupes by content hash.
 */
object PackageParser {
    private val ID_REGEX = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun parse(manifestBytes: ByteArray, questionsBytes: ByteArray): PackageParseResult {
        val json = AbhyasJson.json

        val manifest = try {
            json.decodeFromString(BankManifest.serializer(), manifestBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return PackageParseResult.Failure("manifest.json is invalid: ${e.message?.lineSequence()?.firstOrNull()}")
        }

        if (!ID_REGEX.matches(manifest.id)) return PackageParseResult.Failure("Bank id '${manifest.id}' is invalid")
        if (manifest.version < 1) return PackageParseResult.Failure("Bank version must be >= 1")
        if (manifest.formatVersion != 1) {
            return PackageParseResult.Failure("Unsupported package formatVersion ${manifest.formatVersion}")
        }
        val exam = parseEnum<Exam>(manifest.exam)
            ?: return PackageParseResult.Failure("Unknown exam '${manifest.exam}'")
        val kind = parseEnum<BankKind>(manifest.kind)
            ?: return PackageParseResult.Failure("Unknown kind '${manifest.kind}'")
        if (manifest.subject.isBlank()) return PackageParseResult.Failure("Manifest subject is blank")
        if (manifest.classLevel != null && manifest.classLevel != 11 && manifest.classLevel != 12) {
            return PackageParseResult.Failure("Manifest class must be 11 or 12")
        }

        val actual = Sha256.hex(questionsBytes)
        if (!actual.equals(manifest.sha256.trim(), ignoreCase = true)) {
            return PackageParseResult.Failure("Checksum mismatch: questions.json is corrupted or was modified")
        }

        val array = try {
            json.parseToJsonElement(questionsBytes.toString(Charsets.UTF_8)) as? JsonArray
                ?: return PackageParseResult.Failure("questions.json must be a JSON array")
        } catch (e: Exception) {
            return PackageParseResult.Failure("questions.json is invalid: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (array.isEmpty()) return PackageParseResult.Failure("Package contains no questions")
        if (array.size != manifest.questionCount) {
            return PackageParseResult.Failure(
                "Manifest says ${manifest.questionCount} questions but questions.json has ${array.size}",
            )
        }

        val defaults = QuestionFactory.Defaults(exam = exam, subject = manifest.subject, classLevel = manifest.classLevel)
        val parsed = ArrayList<ParsedQuestion>(array.size)
        val errors = ArrayList<QuestionError>()
        val seenIds = HashSet<String>()
        val seenHashes = HashSet<String>()
        var duplicates = 0

        array.forEachIndexed { index, element ->
            val rawId = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
            val dto = try {
                json.decodeFromJsonElement(QuestionDto.serializer(), element.jsonObject)
            } catch (e: Exception) {
                errors += QuestionError(index, rawId, listOf("malformed entry: ${e.message?.lineSequence()?.firstOrNull()}"))
                return@forEachIndexed
            }
            if (dto.id.isBlank()) {
                errors += QuestionError(index, null, listOf("id is blank"))
                return@forEachIndexed
            }
            if (!seenIds.add(dto.id)) {
                errors += QuestionError(index, dto.id, listOf("duplicate id in package"))
                return@forEachIndexed
            }
            when (val built = QuestionFactory.create(dto, manifest.id, "${manifest.id}:${dto.id}", defaults)) {
                is Built.Invalid -> errors += QuestionError(index, dto.id, built.errors)
                is Built.Ok -> {
                    val hash = ContentHash.of(built.question)
                    if (seenHashes.add(hash)) parsed += ParsedQuestion(built.question, hash) else duplicates++
                }
            }
        }

        return PackageParseResult.Success(manifest, exam, kind, parsed, errors, duplicates)
    }
}
