package com.invictus.abhyas.data.repo

import android.content.Context
import com.invictus.abhyas.domain.pack.PackageZip
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Installs the previous-year papers that ship inside the app (the zip files in assets/packs) the first time they are seen, and
 * again only when the app carries a newer version of one. A paper the student deletes stays deleted: what was
 * installed is remembered by id and version, not looked up in the database.
 */
class BundledBanks(private val context: Context, private val importer: BankImporter) {
    private val prefs = context.getSharedPreferences("bundled_banks", Context.MODE_PRIVATE)

    suspend fun installAll() = withContext(Dispatchers.IO) {
        val names = try { context.assets.list(DIR)?.filter { it.endsWith(".zip") }.orEmpty().sorted() } catch (e: Exception) { emptyList() }
        for (name in names) {
            try {
                install(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A bad bundled file must never stop the app: it is simply not installed.
            }
        }
    }

    private suspend fun install(name: String) {
        val (id, version) = peekManifest(name) ?: return
        if (prefs.getInt(id, 0) >= version) return
        val stage = File(context.cacheDir, "bundle_${System.nanoTime()}")
        try {
            val files = context.assets.open("$DIR/$name").use { input ->
                val images = File(stage, "images").apply { mkdirs() }
                PackageZip.read(input) { image -> File(images, image).outputStream() }
            }
            when (importer.install(files.manifest, files.questions, stage)) {
                is ImportResult.Success, is ImportResult.AlreadyInstalled -> prefs.edit().putInt(id, version).apply()
                is ImportResult.Failure -> Unit
            }
        } finally {
            stage.deleteRecursively()
        }
    }

    /** id and version from the zip's manifest.json (written first by tools/pack_bank.py), without reading the rest. */
    private fun peekManifest(name: String): Pair<String, Int>? =
        context.assets.open("$DIR/$name").use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.substringAfterLast('/') == "manifest.json") {
                        val obj = Json.parseToJsonElement(PackageZip.readBounded(zip, "manifest.json").decodeToString()).jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content ?: return null
                        return id to (obj["version"]?.jsonPrimitive?.intOrNull ?: 1)
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }

    private companion object {
        const val DIR = "packs"
    }
}
