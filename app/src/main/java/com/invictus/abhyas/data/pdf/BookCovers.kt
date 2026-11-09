package com.invictus.abhyas.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** First-page thumbnails of the books on the device, rendered once and cached as small JPEGs. */
object BookCovers {
    suspend fun load(context: Context, bookId: String, pdfPath: String): Bitmap? = withContext(Dispatchers.IO) {
        val pdf = File(pdfPath)
        if (!pdf.isFile) return@withContext null
        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val cached = File(dir, "$bookId.jpg")
        if (cached.isFile && cached.lastModified() >= pdf.lastModified()) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { return@withContext it }
        }
        try {
            PdfPageRenderer(pdf).use { r ->
                if (r.pageCount < 1) return@withContext null
                val bmp = r.render(1, 420).bitmap
                FileOutputStream(cached).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }
}
