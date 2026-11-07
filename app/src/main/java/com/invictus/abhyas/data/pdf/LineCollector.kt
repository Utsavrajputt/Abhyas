package com.invictus.abhyas.data.pdf

import com.invictus.abhyas.domain.ncert.TextLine
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * PdfBox hands text over word by word; this joins the words of each visual line and records the
 * line's baseline (points from the page top) and glyph height. Used by PDF Studio (figure crops)
 * and the NCERT book indexer (paragraphs).
 */
class LineCollector : PDFTextStripper() {
    val lines = ArrayList<TextLine>()
    private val cur = StringBuilder()
    private var curY = 0f
    private var curH = 0f

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (text.isNotBlank() && textPositions.isNotEmpty()) {
            if (cur.isEmpty()) {
                curY = textPositions.first().yDirAdj
                curH = textPositions.first().heightDir
            } else cur.append(' ')
            cur.append(text.trim())
        }
        super.writeString(text, textPositions)
    }

    override fun writeLineSeparator() {
        flush()
        super.writeLineSeparator()
    }

    override fun endPage(page: PDPage) {
        flush()
        super.endPage(page)
    }

    private fun flush() {
        if (cur.isNotEmpty()) {
            lines += TextLine(cur.toString(), curY, curH)
            cur.setLength(0)
        }
    }
}
