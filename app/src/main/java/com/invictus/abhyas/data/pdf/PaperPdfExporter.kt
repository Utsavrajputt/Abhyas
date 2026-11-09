package com.invictus.abhyas.data.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import com.invictus.abhyas.domain.model.AssertionReasonPayload
import com.invictus.abhyas.domain.model.FillBlankPayload
import com.invictus.abhyas.domain.model.MatchPayload
import com.invictus.abhyas.domain.model.MultiChoicePayload
import com.invictus.abhyas.domain.model.NumericalPayload
import com.invictus.abhyas.domain.model.Option
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.model.TrueFalsePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Writes a question paper as a printable A4 PDF (questions first, answer key at the end) into the
 * public Downloads folder. Uses only the platform [PdfDocument], so it works offline with no extra library.
 */
class PaperPdfExporter(private val context: Context) {
    data class Saved(val fileName: String, val where: String)

    suspend fun export(title: String, questions: List<Question>): Saved = withContext(Dispatchers.IO) {
        if (questions.isEmpty()) throw IOException("This paper has no questions")
        val doc = PdfDocument()
        try {
            render(doc, title, questions)
            save(doc, fileNameFor(title))
        } finally {
            doc.close()
        }
    }

    private fun render(doc: PdfDocument, title: String, questions: List<Question>) {
        val w = Writer(doc)
        val heading = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val sub = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF555555.toInt() }
        val body = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val bold = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        val section = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD }

        w.text(title, heading, gap = 2f)
        w.text("${questions.size} questions - exported from Abhyas", sub, gap = 14f)

        questions.forEachIndexed { i, q ->
            w.text("Q${i + 1}. ${clean(q.statement)}", bold, gap = 3f)
            if (q.imagePath != null) w.text("[Figure: see the question in the app]", sub, indent = 14f, gap = 3f)
            when (val p = q.payload) {
                is SingleChoicePayload -> options(w, body, p.options)
                is MultiChoicePayload -> options(w, body, p.options)
                is AssertionReasonPayload -> {
                    w.text("Assertion: ${clean(p.assertion)}", body, indent = 14f, gap = 2f)
                    w.text("Reason: ${clean(p.reason)}", body, indent = 14f, gap = 3f)
                    options(w, body, com.invictus.abhyas.domain.model.AssertionReasonOptions.ALL)
                }
                is MatchPayload -> {
                    w.text(p.leftTitle, bold, indent = 14f, gap = 1f)
                    p.left.forEach { w.text("${it.id}. ${clean(it.text)}", body, indent = 24f, gap = 1f) }
                    w.text(p.rightTitle, bold, indent = 14f, gap = 1f)
                    p.right.forEach { w.text("${it.id}. ${clean(it.text)}", body, indent = 24f, gap = 1f) }
                    options(w, body, p.options)
                }
                is NumericalPayload -> w.text("Answer: ____________${p.unit?.let { " $it" } ?: ""}", body, indent = 14f)
                is TrueFalsePayload -> w.text("True / False", body, indent = 14f)
                is FillBlankPayload -> w.text("Answer: ____________", body, indent = 14f)
            }
            w.gap(8f)
        }

        w.newPage()
        w.text("Answer key", section, gap = 8f)
        questions.chunked(5).forEachIndexed { row, chunk ->
            val line = chunk.mapIndexed { j, q -> "${row * 5 + j + 1}. ${answerText(q)}" }.joinToString("      ")
            w.text(line, body, gap = 4f)
        }
        w.finish()
    }

    private fun options(w: Writer, paint: TextPaint, list: List<Option>) {
        list.forEachIndexed { i, o -> w.text("(${letter(i)}) ${clean(o.text)}", paint, indent = 14f, gap = 2f) }
    }

    private fun answerText(q: Question): String = when (val p = q.payload) {
        is SingleChoicePayload -> p.correctId?.let { id -> p.options.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::letter) } ?: "-"
        is MultiChoicePayload -> p.correctIds.mapNotNull { id -> p.options.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::letter) }
            .joinToString(",").ifEmpty { "-" }
        is NumericalPayload -> p.answer?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "-"
        is TrueFalsePayload -> p.answer?.let { if (it) "True" else "False" } ?: "-"
        is AssertionReasonPayload -> p.correctOption?.let { letter(it - 1) } ?: "-"
        is MatchPayload -> p.correctId?.let { id -> p.options.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::letter) } ?: "-"
        is FillBlankPayload -> p.acceptedAnswers.firstOrNull() ?: "-"
    }

    private fun letter(i: Int): String = if (i in 0..25) ('A' + i).toString() else "?"

    private fun clean(s: String): String = s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "").trim()

    private fun fileNameFor(title: String): String {
        val slug = title.replace(Regex("[^A-Za-z0-9 _-]+"), " ").trim().replace(Regex("\\s+"), "_").take(60).ifEmpty { "paper" }
        return "$slug.pdf"
    }

    private fun save(doc: PdfDocument, name: String): Saved {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the file in Downloads")
            try {
                (resolver.openOutputStream(uri) ?: throw IOException("Could not open the file in Downloads")).use { doc.writeTo(it) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return Saved(name, "Downloads")
        }
        // Android 8 and 9: no write permission for the public folder, so use the app's own Downloads folder.
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use { doc.writeTo(it) }
        return Saved(name, file.absolutePath)
    }

    /** Minimal A4 text flow: wraps blocks with [StaticLayout] and starts a new page when one does not fit. */
    private class Writer(private val doc: PdfDocument) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 44f
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = margin

        fun newPage() {
            finish()
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p
            canvas = p.canvas
            y = margin
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
            canvas = null
        }

        fun gap(h: Float) { y += h }

        fun text(t: CharSequence, paint: TextPaint, indent: Float = 0f, gap: Float = 4f) {
            val width = (pageW - 2 * margin - indent).toInt().coerceAtLeast(50)
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, width).setLineSpacing(0f, 1.1f).build()
            if (canvas == null) newPage()
            if (y + layout.height > pageH - margin) newPage()
            val c = canvas ?: return
            c.save()
            c.translate(margin + indent, y)
            layout.draw(c)
            c.restore()
            y += layout.height + gap
        }
    }
}
