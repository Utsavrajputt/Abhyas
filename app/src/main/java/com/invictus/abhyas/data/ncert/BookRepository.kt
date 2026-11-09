package com.invictus.abhyas.data.ncert

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.local.BookBookmarkEntity
import com.invictus.abhyas.data.local.BookChapterEntity
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.data.local.PassageEntity
import com.invictus.abhyas.data.local.ReadCount
import com.invictus.abhyas.data.local.ReadPageEntity
import com.invictus.abhyas.data.local.SavedPassageEntity
import com.invictus.abhyas.data.pdf.LineCollector
import com.invictus.abhyas.data.pdf.PdfPageRenderer
import com.invictus.abhyas.data.pdf.PdfTextExtractor
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.ncert.BookChapter
import com.invictus.abhyas.domain.ncert.BookLayout
import com.invictus.abhyas.domain.ncert.BookIndexParser
import com.invictus.abhyas.domain.ncert.BookZipPlan
import com.invictus.abhyas.domain.ncert.ChapterTitles
import com.invictus.abhyas.domain.ncert.ChapterGuess
import com.invictus.abhyas.domain.ncert.IndexEntry
import com.invictus.abhyas.domain.ncert.IndexParseResult
import com.invictus.abhyas.domain.ncert.MatchConfidence
import com.invictus.abhyas.domain.ncert.MatchPassage
import com.invictus.abhyas.domain.ncert.NcertRef
import com.invictus.abhyas.domain.ncert.ParagraphSplitter
import com.invictus.abhyas.domain.ncert.PartInfo
import com.invictus.abhyas.domain.ncert.PassageIndex
import com.invictus.abhyas.domain.ncert.PassageMatch
import com.invictus.abhyas.domain.ncert.PassageMatcher
import com.invictus.abhyas.domain.ncert.PrintedPages
import com.invictus.abhyas.domain.ncert.SectionKind
import com.invictus.abhyas.domain.ncert.ZipPart
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * NCERT Connect: books on the device (imported or downloaded from a hosted index), their text index
 * (paragraphs with stable hashes), chapters, bookmarks, saved passages, reading progress, and the
 * resolver that turns a question's [NcertRef] into a verified location.
 */
class BookRepository(
    private val context: Context,
    private val db: AbhyasDatabase,
    private val extractor: PdfTextExtractor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Outcome { DONE, STOPPED, FAILED, RETRY }

    sealed interface AddResult {
        data class Added(val id: String) : AddResult
        data class Existing(val id: String) : AddResult
        data class Failed(val message: String) : AddResult
    }

    /**
     * A located passage. [page] is the page of the stored PDF (what the reader opens); [pageLabel] is the
     * number printed in the textbook. [likely] marks a text-search match that is not certain.
     */
    class Resolved(
        val book: BookEntity,
        val page: Int,
        val paragraph: Int,
        val chapter: String?,
        val text: String,
        val pageLabel: String = page.toString(),
        val likely: Boolean = false,
    )

    private val dao get() = db.bookDao()
    private val root = File(context.filesDir, "books")
    private val layouts = ConcurrentHashMap<String, BookLayout>()
    private val matchIndexes = ConcurrentHashMap<String, PassageIndex>()

    fun observeBooks(): Flow<List<BookEntity>> = dao.observeBooks()
    fun observeReadCounts(): Flow<List<ReadCount>> = dao.observeReadCounts()
    fun observeBook(id: String) = dao.observeBook(id)
    fun observeChapters(id: String) = dao.observeChapters(id)
    fun observeBookmarks(id: String) = dao.observeBookmarks(id)
    fun observeSaved(id: String) = dao.observeSaved(id)
    fun observeRead(id: String) = dao.observeRead(id)
    suspend fun book(id: String) = dao.getBook(id)
    suspend fun passages(id: String, page: Int) = dao.passagesOnPage(id, page)
    suspend fun passagesInRange(id: String, from: Int, to: Int) = dao.passagesInRange(id, from, to)
    suspend fun chapters(id: String) = dao.chapters(id)

    // ------------------------------------------------------------------------------------------
    // Adding books
    // ------------------------------------------------------------------------------------------

    suspend fun addLocal(uri: Uri, title: String?, classLevel: Int?, subject: String): AddResult = withContext(Dispatchers.IO) {
        root.mkdirs()
        val tmp = File(root, "incoming_${clock()}.pdf")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext AddResult.Failed("Cannot open the selected file")
            val md = MessageDigest.getInstance("SHA-256")
            DigestInputStream(input, md).use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }
            val checksum = md.digest().joinToString("") { "%02x".format(it) }
            dao.byChecksum(checksum)?.let { tmp.delete(); return@withContext AddResult.Existing(it.id) }
            val head = ByteArray(1024)
            val n = tmp.inputStream().use { it.read(head) }
            if (n <= 0 || !String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF")) {
                tmp.delete()
                return@withContext AddResult.Failed("This file is not a PDF")
            }
            val pages = try {
                PdfPageRenderer(tmp).use { it.pageCount }
            } catch (e: Exception) {
                tmp.delete()
                return@withContext AddResult.Failed("The PDF is password-protected or damaged")
            }
            val id = "local_${checksum.take(12)}"
            val dir = File(root, id).apply { mkdirs() }
            val dest = File(dir, "book.pdf")
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            val name = title?.takeIf { it.isNotBlank() } ?: displayName(uri)?.substringBeforeLast('.') ?: "Book"
            dao.insertBook(
                BookEntity(
                    id = id, title = name, classLevel = classLevel, subject = subject, localPath = dest.absolutePath,
                    pageCount = pages, checksum = checksum, origin = "LOCAL", sourceUrl = null, status = BookStatus.INDEXING,
                    indexedPages = 0, message = null, lastPage = 1, addedAt = clock(),
                ),
            )
            enqueueIndex(id, replace = false)
            AddResult.Added(id)
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: IOException) {
            tmp.delete()
            AddResult.Failed("Could not read the file: ${e.message}")
        }
    }

    /**
     * Merges downloaded chapter PDFs into one book (kept in app storage) and queues the text indexer,
     * so a book fetched from the official NCERT site reads, bookmarks and links questions exactly
     * like an imported one.
     */
    suspend fun addMerged(
        id: String,
        title: String,
        classLevel: Int?,
        subject: String,
        chapters: List<File>,
        sourceUrl: String,
    ): AddResult = withContext(Dispatchers.IO) {
        dao.getBook(id)?.let { if (it.status != BookStatus.FAILED) return@withContext AddResult.Existing(id) }
        val dir = File(root, id).apply { mkdirs() }
        val dest = File(dir, "book.pdf")
        try {
            mergePdfs(chapters, dest)
            val md = MessageDigest.getInstance("SHA-256")
            dest.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            val checksum = md.digest().joinToString("") { "%02x".format(it) }
            val pages = PdfPageRenderer(dest).use { it.pageCount }
            val book = BookEntity(
                id = id, title = title, classLevel = classLevel, subject = subject, localPath = dest.absolutePath,
                pageCount = pages, checksum = checksum, origin = "DOWNLOAD", sourceUrl = sourceUrl,
                status = BookStatus.INDEXING, indexedPages = 0, message = null, lastPage = 1, addedAt = clock(),
            )
            if (dao.getBook(id) == null) dao.insertBook(book) else dao.updateBook(book)
            enqueueIndex(id, replace = true)
            AddResult.Added(id)
        } catch (e: CancellationException) {
            dir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            dir.deleteRecursively()
            AddResult.Failed("Could not build the book: ${e.message}")
        }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Fetches and validates a hosted index. HTTPS only. Throws IOException on network problems. */
    suspend fun fetchIndex(url: String): IndexParseResult = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "The index URL must start with https://" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) throw IOException("Server answered ${conn.responseCode}")
            val bytes = conn.inputStream.use { it.readBounded(2 * 1024 * 1024) }
            BookIndexParser.parse(bytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readBounded(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("Index is larger than ${max / 1024} KB")
        }
        return out.toByteArray()
    }

    /** Registers the download and queues it. Returns an error message or null. */
    suspend fun startDownload(entry: IndexEntry): String? {
        dao.getBook(entry.id)?.let { if (it.status != BookStatus.FAILED) return "Already in your library" }
        val dir = File(root, entry.id).apply { mkdirs() }
        val book = BookEntity(
            id = entry.id, title = entry.title, classLevel = entry.classLevel, subject = entry.subject,
            localPath = File(dir, "book.pdf").absolutePath, pageCount = 0, checksum = entry.sha256,
            origin = "DOWNLOAD", sourceUrl = entry.url, status = BookStatus.DOWNLOADING, indexedPages = 0,
            message = "Waiting for network", lastPage = 1, addedAt = clock(),
        )
        if (dao.getBook(entry.id) == null) dao.insertBook(book) else dao.updateBook(book)
        enqueueDownload(entry.id, replace = true)
        return null
    }

    fun enqueueDownload(id: String, replace: Boolean) {
        val req = OneTimeWorkRequestBuilder<BookDownloadWorker>()
            .setInputData(workDataOf(BookDownloadWorker.KEY_BOOK to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("book-dl-$id", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    fun enqueueIndex(id: String, replace: Boolean) {
        val req = OneTimeWorkRequestBuilder<BookIndexWorker>()
            .setInputData(workDataOf(BookIndexWorker.KEY_BOOK to id))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("book-index-$id", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    suspend fun deleteBook(id: String) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("book-dl-$id")
        wm.cancelUniqueWork("book-index-$id")
        dao.deleteBook(id)
        layouts.remove(id)
        matchIndexes.remove(id)
        withContext(Dispatchers.IO) { File(root, id).deleteRecursively() }
    }

    suspend fun retry(id: String) {
        val b = dao.getBook(id) ?: return
        if (b.status != BookStatus.FAILED) return
        if (b.pageCount == 0 && b.sourceUrl != null) {
            dao.setState(id, BookStatus.DOWNLOADING, 0, "Waiting for network")
            enqueueDownload(id, replace = true)
        } else {
            dao.setState(id, BookStatus.INDEXING, b.indexedPages, null)
            enqueueIndex(id, replace = true)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Download (runs inside BookDownloadWorker)
    // ------------------------------------------------------------------------------------------

    suspend fun downloadBook(id: String, stop: () -> Boolean): Outcome = withContext(Dispatchers.IO) {
        val book = dao.getBook(id) ?: return@withContext Outcome.FAILED
        val url = book.sourceUrl ?: return@withContext Outcome.FAILED
        val target = File(book.localPath)
        if (target.exists() && book.pageCount > 0) return@withContext Outcome.DONE
        val part = File(book.localPath + ".part")
        try {
            val have = if (part.exists()) part.length() else 0L
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                if (code != 200 && code != 206) {
                    dao.setState(id, BookStatus.FAILED, 0, "Server answered $code")
                    return@withContext Outcome.FAILED
                }
                val resumed = code == 206 && have > 0
                val total = conn.contentLengthLong.let { if (it > 0) it + (if (resumed) have else 0L) else -1L }
                if (total > MAX_BOOK_BYTES) {
                    dao.setState(id, BookStatus.FAILED, 0, "File is larger than ${MAX_BOOK_BYTES / 1024 / 1024} MB")
                    return@withContext Outcome.FAILED
                }
                java.io.FileOutputStream(part, resumed).use { out ->
                    var done = if (resumed) have else 0L
                    var lastPct = -1
                    val buf = ByteArray(64 * 1024)
                    conn.inputStream.use { input ->
                        while (true) {
                            if (stop()) return@withContext Outcome.STOPPED
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else -1
                            if (pct != lastPct) { lastPct = pct; dao.setState(id, BookStatus.DOWNLOADING, 0, if (pct >= 0) "Downloading $pct%" else "Downloading") }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            val digest = MessageDigest.getInstance("SHA-256")
            part.inputStream().use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(book.checksum, ignoreCase = true)) {
                part.delete()
                dao.setState(id, BookStatus.FAILED, 0, "Checksum mismatch: the file was not saved")
                return@withContext Outcome.FAILED
            }
            if (looksLikeZip(part)) return@withContext buildFromZip(book, part, target)
            if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
            val pages = try { PdfPageRenderer(target).use { it.pageCount } } catch (e: Exception) {
                target.delete()
                dao.setState(id, BookStatus.FAILED, 0, "Downloaded file is not a readable PDF")
                return@withContext Outcome.FAILED
            }
            dao.updateBook(dao.getBook(id)!!.copy(pageCount = pages, status = BookStatus.INDEXING, indexedPages = 0, message = null))
            enqueueIndex(id, replace = true)
            Outcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            dao.setState(id, BookStatus.DOWNLOADING, 0, "Network problem, will retry")
            Outcome.RETRY
        }
    }

    // ------------------------------------------------------------------------------------------
    // Indexing (runs inside BookIndexWorker)
    // ------------------------------------------------------------------------------------------

    suspend fun indexBook(id: String, stop: () -> Boolean): Outcome = withContext(Dispatchers.IO) {
        val book = dao.getBook(id) ?: return@withContext Outcome.FAILED
        if (book.status == BookStatus.READY) return@withContext Outcome.DONE
        val file = File(book.localPath)
        if (!file.exists()) { dao.setState(id, BookStatus.FAILED, book.indexedPages, "The stored PDF file is missing"); return@withContext Outcome.FAILED }
        try {
            extractor.open(file).use { doc ->
                val total = doc.numberOfPages
                for (p in (book.indexedPages + 1)..total) {
                    if (stop()) return@withContext Outcome.STOPPED
                    val rows = try {
                        val c = LineCollector()
                        c.sortByPosition = true
                        c.startPage = p
                        c.endPage = p
                        c.getText(doc)
                        val h = doc.getPage(p - 1).mediaBox.height
                        ParagraphSplitter.split(c.lines).map { PassageEntity(id, p, it.index, it.text, it.hash, it.top, it.bottom, h) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    db.withTransaction {
                        dao.deletePagePassages(id, p)
                        if (rows.isNotEmpty()) dao.insertPassages(rows)
                        dao.setState(id, BookStatus.INDEXING, p, null)
                    }
                }
                val chapters = chaptersFor(doc, id, total)
                db.withTransaction {
                    dao.deleteChapters(id)
                    dao.insertChapters(chapters.map { BookChapterEntity(id, it.index, it.title, it.startPage, it.endPage) })
                }
                matchIndexes.remove(id)
                val hasText = dao.passageCount(id) > 0
                dao.setState(id, BookStatus.READY, total, if (hasText) null else "No searchable text (scanned PDF): reading works, search does not")
            }
            Outcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            dao.setState(id, BookStatus.FAILED, book.indexedPages, "The PDF is password-protected")
            Outcome.FAILED
        } catch (e: IOException) {
            dao.setState(id, BookStatus.FAILED, book.indexedPages, "Could not read the PDF: ${e.message}")
            Outcome.FAILED
        } catch (e: OutOfMemoryError) {
            dao.setState(id, BookStatus.FAILED, book.indexedPages, "Ran out of memory while indexing")
            Outcome.FAILED
        }
    }

    private suspend fun chaptersFor(doc: PDDocument, id: String, pageCount: Int): List<BookChapter> {
        layout(id)?.chapters()?.takeIf { it.isNotEmpty() }?.let { return it }
        val fromOutline = ChapterGuess.fromStarts(outlineStarts(doc), pageCount)
        if (fromOutline.size > 1) return fromOutline
        val heads = dao.pageHeads(id).groupBy({ it.page }, { it.text })
        val guessed = ChapterGuess.guess(heads, pageCount)
        return if (guessed.size > 1) guessed else fromOutline
    }

    private fun outlineStarts(doc: PDDocument): List<Pair<Int, String>> = try {
        val outline = doc.documentCatalog.documentOutline
        if (outline == null) emptyList() else {
            var level = children(outline)
            // A single top-level node (the book title) wraps the real chapter list.
            if (level.size <= 2 && level.firstOrNull()?.firstChild != null) level = children(level.first())
            level.mapNotNull { item ->
                val page = try {
                    (item as? com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem)
                        ?.findDestinationPage(doc)?.let { doc.pages.indexOf(it) + 1 }
                } catch (e: Exception) { null }
                val title = (item as? com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem)?.title
                if (page != null && page > 0 && !title.isNullOrBlank()) page to title.trim() else null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun children(node: PDOutlineNode): List<PDOutlineNode> {
        val out = ArrayList<PDOutlineNode>()
        var c = node.firstChild
        while (c != null) { out += c; c = c.nextSibling }
        return out
    }

    // ------------------------------------------------------------------------------------------
    // Reader data
    // ------------------------------------------------------------------------------------------

    suspend fun search(bookId: String, query: String): List<PassageEntity> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val esc = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.search(bookId, esc, 100)
    }

    suspend fun setBookmark(bookId: String, page: Int, on: Boolean) {
        if (on) dao.insertBookmark(BookBookmarkEntity(bookId = bookId, page = page, createdAt = clock())) else dao.deleteBookmark(bookId, page)
    }

    suspend fun savePassage(p: PassageEntity) {
        dao.insertSaved(SavedPassageEntity(bookId = p.bookId, page = p.page, paragraph = p.idx, textHash = p.textHash, text = p.text, createdAt = clock()))
    }

    suspend fun deleteSaved(id: Long) = dao.deleteSaved(id)

    suspend fun markRead(bookId: String, pages: List<Int>) {
        if (pages.isNotEmpty()) dao.markRead(pages.map { ReadPageEntity(bookId, it) })
    }

    suspend fun setLastPage(bookId: String, page: Int) = dao.setLastPage(bookId, page)

    /**
     * Verifies a question's reference: the book must be installed and READY and the passage must still
     * carry the stored hash (at the stored place, or found elsewhere in the book by hash). Null otherwise,
     * so a reference that cannot be verified is simply not shown.
     */
    suspend fun resolve(ref: NcertRef): Resolved? {
        val book = dao.getBook(ref.bookId)?.takeIf { it.status == BookStatus.READY } ?: return null
        val at = dao.passage(ref.bookId, ref.page, ref.paragraph)?.takeIf { it.textHash == ref.textHash }
            ?: dao.passageByHash(ref.bookId, ref.textHash)
            ?: return null
        val chapter = dao.chapters(ref.bookId).lastOrNull { it.startPage <= at.page }?.title
        return Resolved(book, at.page, at.idx, chapter, at.text, pageLabel(ref.bookId, at.page))
    }

    /**
     * Finds where a question is explained by searching its text in the installed, indexed books of
     * its subject (and class, when known). Only chapter pages are searched (not prelims or answers).
     * Null when nothing convincing is found.
     */
    suspend fun findForQuestion(q: Question): Resolved? = withContext(Dispatchers.Default) {
        val ready = dao.books().filter { it.status == BookStatus.READY }
        if (ready.isEmpty()) return@withContext null
        var pool = ready.filter { sameSubject(it.subject, q.subject) }.ifEmpty { ready }
        val sameClass = q.classLevel?.let { c -> pool.filter { it.classLevel == c } }.orEmpty()
        if (sameClass.isNotEmpty()) pool = sameClass
        var best: PassageMatch? = null
        for (b in pool) {
            val m = PassageMatcher.best(q.statement, matchIndex(b)) ?: continue
            val cur = best
            if (cur == null || m.score > cur.score) best = m
        }
        val hit = best ?: return@withContext null
        val book = pool.first { it.id == hit.passage.bookId }
        val chapter = dao.chapters(book.id).lastOrNull { it.startPage <= hit.passage.page }?.title
        Resolved(
            book, hit.passage.page, hit.passage.idx, chapter, hit.passage.text,
            pageLabel(book.id, hit.passage.page), likely = hit.confidence == MatchConfidence.LIKELY,
        )
    }

    private fun sameSubject(book: String, question: String): Boolean {
        val b = book.trim().lowercase()
        val s = question.trim().lowercase()
        if (b == s) return true
        val biology = setOf("biology", "botany", "zoology")
        return b in biology && s in biology
    }

    private suspend fun matchIndex(b: BookEntity): PassageIndex {
        matchIndexes[b.id]?.let { return it }
        val lay = layout(b.id)
        val rows = dao.passagesInRange(b.id, 1, b.pageCount.coerceAtLeast(1))
            .filter { lay == null || lay.isContent(it.page) }
            .map { MatchPassage(b.id, it.page, it.idx, it.text) }
        return PassageIndex(rows).also { matchIndexes[b.id] = it }
    }

    // ------------------------------------------------------------------------------------------
    // Chapter-wise zips (NCERT's keph101.pdf ... keph1an.pdf): layout, printed page numbers
    // ------------------------------------------------------------------------------------------

    /** The layout of a book built from a chapter zip, or null for any other book. */
    fun layout(bookId: String): BookLayout? {
        layouts[bookId]?.let { return it }
        val f = File(File(root, bookId), LAYOUT_FILE)
        if (!f.exists()) return null
        val l = BookLayout.fromJson(f.readText()) ?: return null
        layouts[bookId] = l
        return l
    }

    /** The page number printed in the textbook for [page] of the stored PDF (the PDF page when unknown). */
    fun pageLabel(bookId: String, page: Int): String = layout(bookId)?.printedLabel(page) ?: page.toString()

    private fun looksLikeZip(f: File): Boolean = try {
        f.inputStream().use {
            val h = ByteArray(4)
            it.read(h) == 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte() && h[2].toInt() == 3 && h[3].toInt() == 4
        }
    } catch (e: IOException) {
        false
    }

    private fun mergePdfs(files: List<File>, dest: File) {
        val merger = com.tom_roush.pdfbox.multipdf.PDFMergerUtility()
        merger.destinationFileName = dest.absolutePath
        files.forEach { merger.addSource(it) }
        merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())
    }

    /**
     * The downloaded file is a zip of NCERT chapter PDFs: unpack, merge in book order (prelims, chapters,
     * appendices, answers), write `layout.json` (chapter names, printed page numbers) and queue the indexer.
     */
    private suspend fun buildFromZip(book: BookEntity, zip: File, target: File): Outcome {
        dao.setState(book.id, BookStatus.DOWNLOADING, 0, "Building book")
        val work = File(root, "${book.id}_zip").apply { deleteRecursively(); mkdirs() }
        try {
            val parts = unpackZip(zip, work)
            if (parts.isEmpty()) {
                zip.delete()
                dao.setState(book.id, BookStatus.FAILED, 0, "The zip has no NCERT chapter PDFs (names like keph101.pdf)")
                return Outcome.FAILED
            }
            val infos = parts.map { (p, f) ->
                val pages = PdfPageRenderer(f).use { it.pageCount }
                val title = if (p.kind == SectionKind.CHAPTER) ChapterTitles.fromFirstPage(firstPageText(f)) else null
                PartInfo(p.kind, p.number, pages, title)
            }
            mergePdfs(parts.map { it.second }, target)
            val layout = BookLayout.build(infos, printedOffset(target, infos))
            File(target.parentFile, LAYOUT_FILE).writeText(layout.toJson())
            layouts.remove(book.id)
            matchIndexes.remove(book.id)
            val pages = PdfPageRenderer(target).use { it.pageCount }
            zip.delete()
            dao.updateBook(dao.getBook(book.id)!!.copy(pageCount = pages, status = BookStatus.INDEXING, indexedPages = 0, message = null))
            enqueueIndex(book.id, replace = true)
            return Outcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            target.delete()
            zip.delete()
            dao.setState(book.id, BookStatus.FAILED, 0, "Could not build the book: ${e.message ?: e.javaClass.simpleName}")
            return Outcome.FAILED
        } finally {
            work.deleteRecursively()
        }
    }

    /** Writes the recognised PDFs of [zip] into [work] and returns them in book order. */
    private fun unpackZip(zip: File, work: File): List<Pair<ZipPart, File>> {
        val found = ArrayList<Pair<ZipPart, File>>()
        var total = 0L
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val part = if (entry.isDirectory) null else BookZipPlan.classify(entry.name)
                if (part != null) {
                    val f = File(work, "part${found.size}.pdf")
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zin.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BOOK_BYTES) throw IOException("The zip is larger than ${MAX_BOOK_BYTES / 1024 / 1024} MB when unpacked")
                            out.write(buf, 0, n)
                        }
                    }
                    found += part to f
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return BookZipPlan.plan(found.map { it.first }).mapNotNull { k -> found.firstOrNull { it.first.entryName == k.entryName } }
    }

    private fun firstPageText(f: File): String = try {
        extractor.open(f).use { doc -> extractor.extractPage(doc, 1).text }
    } catch (e: Exception) {
        ""
    }

    /** `printed page - merged page`, read from the header/footer numbers of the first chapter's pages. */
    private fun printedOffset(book: File, infos: List<PartInfo>): Int? {
        val prelims = infos.filter { it.kind == SectionKind.PRELIMS }.sumOf { it.pages }
        val firstChapter = infos.firstOrNull { it.kind == SectionKind.CHAPTER }?.pages ?: return null
        val pages = LinkedHashMap<Int, List<String>>()
        try {
            extractor.open(book).use { doc ->
                for (p in (prelims + 2)..(prelims + minOf(firstChapter, 5))) {
                    pages[p] = extractor.extractPage(doc, p).text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            return null
        }
        return PrintedPages.offset(pages)
    }

    private companion object {
        const val MAX_BOOK_BYTES = 400L * 1024 * 1024
        const val LAYOUT_FILE = "layout.json"
    }
}
