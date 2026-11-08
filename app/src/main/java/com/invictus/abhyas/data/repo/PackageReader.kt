package com.invictus.abhyas.data.repo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.invictus.abhyas.domain.pack.PackageFiles
import com.invictus.abhyas.domain.pack.PackageReadException
import com.invictus.abhyas.domain.pack.PackageZip
import java.io.File
import java.io.IOException

/** Reads a package from a SAF zip/folder or from app assets (debug demo bank). Blocking: call on IO. */
class PackageReader(private val context: Context) {

    /** [stagingDir] receives the `images/` folder found in the zip (so the importer can copy them after parsing). */
    fun readZip(uri: Uri, stagingDir: File? = null): PackageFiles {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw PackageReadException("Cannot open the selected file")
        return try {
            stream.use {
                val sink: ((String) -> java.io.OutputStream?)? = stagingDir?.let { dir ->
                    val images = File(dir, "images").apply { mkdirs() }
                    val open: (String) -> java.io.OutputStream? = { name -> File(images, name).outputStream() }
                    open
                }
                PackageZip.read(it, sink)
            }
        } catch (e: IOException) {
            throw PackageReadException("Not a valid zip file: ${e.message}")
        }
    }

    fun readFolder(treeUri: Uri): PackageFiles {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw PackageReadException("Cannot open the selected folder")
        val (manifest, questions) = findFiles(root, depth = 0)
        if (manifest == null) throw PackageReadException("manifest.json not found in the folder")
        if (questions == null) throw PackageReadException("questions.json not found in the folder")
        return PackageFiles(readDoc(manifest, "manifest.json"), readDoc(questions, "questions.json"))
    }

    fun readAsset(dir: String): PackageFiles = try {
        PackageFiles(
            context.assets.open("$dir/manifest.json").use { PackageZip.readBounded(it, "manifest.json") },
            context.assets.open("$dir/questions.json").use { PackageZip.readBounded(it, "questions.json") },
        )
    } catch (e: IOException) {
        throw PackageReadException("Asset package '$dir' not found")
    }

    fun assetPackages(root: String): List<String> =
        try { context.assets.list(root)?.toList().orEmpty() } catch (e: IOException) { emptyList() }

    private fun findFiles(dir: DocumentFile, depth: Int): Pair<DocumentFile?, DocumentFile?> {
        val children = dir.listFiles()
        var manifest = children.firstOrNull { it.isFile && it.name.equals("manifest.json", true) }
        var questions = children.firstOrNull { it.isFile && it.name.equals("questions.json", true) }
        if ((manifest == null || questions == null) && depth < 2) {
            for (sub in children.filter { it.isDirectory }) {
                val (m, q) = findFiles(sub, depth + 1)
                if (m != null && q != null) { manifest = m; questions = q; break }
            }
        }
        return manifest to questions
    }

    private fun readDoc(doc: DocumentFile, label: String): ByteArray {
        val stream = context.contentResolver.openInputStream(doc.uri)
            ?: throw PackageReadException("Cannot open $label")
        return stream.use { PackageZip.readBounded(it, label) }
    }
}
