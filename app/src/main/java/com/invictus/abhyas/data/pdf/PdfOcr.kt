package com.invictus.abhyas.data.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.invictus.abhyas.domain.pdf.LinePos
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrResult(val text: String, val lines: List<LinePos>)

/** Fully offline OCR (ML Kit bundled Latin model). */
class PdfOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** [pxPerPoint] converts bitmap pixels back to PDF points for the line positions. */
    suspend fun recognize(bitmap: Bitmap, pxPerPoint: Float): OcrResult = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = ArrayList<LinePos>()
                for (block in result.textBlocks) for (line in block.lines) {
                    val top = line.boundingBox?.top ?: continue
                    lines += LinePos(line.text.trim().take(48), top / pxPerPoint)
                }
                val text = result.textBlocks.joinToString("\n") { it.text }
                if (cont.isActive) cont.resume(OcrResult(text, lines))
            }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    fun close() {
        runCatching { recognizer.close() }
    }
}
