package com.invictus.abhyas.data.pdf

import android.content.Context
import com.invictus.abhyas.domain.pdf.LinePos
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

class RawPage(val text: String, val lines: List<LinePos>, val height: Float, val hasImages: Boolean)

/** Digital-text extraction with PdfBox-Android, one page at a time (temp-file backed, low memory). */
class PdfTextExtractor(context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** Opens the document. The caller must close it (also removes PdfBox temp files). */
    fun open(file: File): PDDocument = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())

    private class PosStripper : PDFTextStripper() {
        val lines = ArrayList<LinePos>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isNotBlank() && textPositions.isNotEmpty()) {
                lines += LinePos(text.trim().take(48), textPositions.first().yDirAdj)
            }
            super.writeString(text, textPositions)
        }
    }

    /** [page] is 1-based. */
    fun extractPage(doc: PDDocument, page: Int): RawPage {
        val stripper = PosStripper()
        stripper.sortByPosition = true
        stripper.startPage = page
        stripper.endPage = page
        val text = stripper.getText(doc)
        val pdPage = doc.getPage(page - 1)
        return RawPage(text, stripper.lines, pdPage.mediaBox.height, hasLargeImage(pdPage))
    }

    private fun hasLargeImage(page: com.tom_roush.pdfbox.pdmodel.PDPage): Boolean = try {
        val res = page.resources
        res != null && res.xObjectNames.any { name ->
            res.isImageXObject(name) && (res.getXObject(name) as? PDImageXObject)?.let { it.width >= 150 && it.height >= 100 } == true
        }
    } catch (e: Exception) {
        false
    }
}
