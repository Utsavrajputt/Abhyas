package com.invictus.abhyas.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.min

/** Renders PDF pages to bitmaps (OCR input and figure crops) with the platform PdfRenderer. */
class PdfPageRenderer(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)

    val pageCount: Int get() = renderer.pageCount

    class Rendered(val bitmap: Bitmap, val pxPerPoint: Float)

    /** [page] is 1-based. Width is capped so very large pages cannot exhaust memory. */
    @Synchronized
    fun render(page: Int, targetWidthPx: Int): Rendered {
        renderer.openPage(page - 1).use { p ->
            val scale = min(targetWidthPx.toFloat() / p.width, 3.5f).coerceAtLeast(1f)
            val w = (p.width * scale).toInt().coerceAtLeast(1)
            val h = (p.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return Rendered(bmp, scale)
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
