package com.invictus.abhyas.data.ncert

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A Class 11 / 12 PCBM textbook on the official NCERT site (https://ncert.nic.in/textbook/pdf/).
 * NCERT publishes each chapter as `<code><NN>.pdf`, e.g. keph101.pdf = Class 11 Physics Part I,
 * chapter 1 (k = XI, l = XII; ph / ch / bo / mh; then the part number).
 */
data class OfficialBook(
    val id: String,
    val title: String,
    val classLevel: Int,
    val subject: String,
    val code: String,
)

object OfficialBooks {
    const val BASE_URL = "https://ncert.nic.in/textbook/pdf/"

    val all: List<OfficialBook> = listOf(
        OfficialBook("ncert-11-physics-1", "NCERT Physics Part I", 11, "Physics", "keph1"),
        OfficialBook("ncert-11-physics-2", "NCERT Physics Part II", 11, "Physics", "keph2"),
        OfficialBook("ncert-11-chemistry-1", "NCERT Chemistry Part I", 11, "Chemistry", "kech1"),
        OfficialBook("ncert-11-chemistry-2", "NCERT Chemistry Part II", 11, "Chemistry", "kech2"),
        OfficialBook("ncert-11-biology", "NCERT Biology", 11, "Biology", "kebo1"),
        OfficialBook("ncert-11-maths", "NCERT Mathematics", 11, "Mathematics", "kemh1"),
        OfficialBook("ncert-12-physics-1", "NCERT Physics Part I", 12, "Physics", "leph1"),
        OfficialBook("ncert-12-physics-2", "NCERT Physics Part II", 12, "Physics", "leph2"),
        OfficialBook("ncert-12-chemistry-1", "NCERT Chemistry Part I", 12, "Chemistry", "lech1"),
        OfficialBook("ncert-12-chemistry-2", "NCERT Chemistry Part II", 12, "Chemistry", "lech2"),
        OfficialBook("ncert-12-biology", "NCERT Biology", 12, "Biology", "lebo1"),
        OfficialBook("ncert-12-maths-1", "NCERT Mathematics Part I", 12, "Mathematics", "lemh1"),
        OfficialBook("ncert-12-maths-2", "NCERT Mathematics Part II", 12, "Mathematics", "lemh2"),
    )

    fun chapterUrl(book: OfficialBook, chapter: Int): String =
        BASE_URL + book.code + chapter.toString().padStart(2, '0') + ".pdf"
}

sealed interface OfficialBookState {
    data class Running(val label: String) : OfficialBookState
    data class Failed(val message: String) : OfficialBookState
}

/**
 * Downloads NCERT textbooks straight from ncert.nic.in, one book at a time, and hands them to
 * [BookRepository.addMerged]. The chapter count is not hard-coded: chapters are fetched in order
 * until the server has no more, so a revised edition still works. Runs in an app-wide scope, so
 * leaving the store screen does not cancel it.
 */
class OfficialBookDownloader(
    private val context: Context,
    private val books: BookRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val _state = MutableStateFlow<Map<String, OfficialBookState>>(emptyMap())
    val state: StateFlow<Map<String, OfficialBookState>> = _state

    fun download(book: OfficialBook) {
        if (_state.value[book.id] is OfficialBookState.Running) return
        set(book.id, OfficialBookState.Running("Waiting..."))
        scope.launch { gate.withLock { run(book) } }
    }

    private fun set(id: String, s: OfficialBookState?) {
        _state.value = if (s == null) _state.value - id else _state.value + (id to s)
    }

    private suspend fun run(book: OfficialBook) {
        val work = File(context.cacheDir, "ncert_${book.id}").apply { deleteRecursively(); mkdirs() }
        try {
            val files = ArrayList<File>()
            var misses = 0
            var n = 1
            while (n <= MAX_CHAPTERS) {
                set(book.id, OfficialBookState.Running("Downloading chapter $n..."))
                val target = File(work, "c%02d.pdf".format(n))
                when (fetch(OfficialBooks.chapterUrl(book, n), target)) {
                    Fetch.OK -> { files += target; misses = 0 }
                    Fetch.MISSING -> {
                        misses++
                        // Stop after two missing numbers in a row once we have chapters,
                        // or right away if the book is not on the server at all.
                        if ((files.isNotEmpty() && misses >= 2) || (files.isEmpty() && n >= 3)) break
                    }
                }
                n++
            }
            if (files.isEmpty()) {
                set(book.id, OfficialBookState.Failed("Not found on ncert.nic.in (the book may have been renamed)"))
                return
            }
            set(book.id, OfficialBookState.Running("Building book (${files.size} chapters)..."))
            when (val r = books.addMerged(
                id = book.id,
                title = "${book.title} (Class ${book.classLevel})",
                classLevel = book.classLevel,
                subject = book.subject,
                chapters = files,
                sourceUrl = OfficialBooks.BASE_URL + book.code,
            )) {
                is BookRepository.AddResult.Failed -> set(book.id, OfficialBookState.Failed(r.message))
                else -> set(book.id, null)
            }
        } catch (e: CancellationException) {
            set(book.id, null)
            throw e
        } catch (e: Exception) {
            set(book.id, OfficialBookState.Failed("Download failed: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            work.deleteRecursively()
        }
    }

    private enum class Fetch { OK, MISSING }

    private fun fetch(url: String, target: File): Fetch {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/pdf,*/*")
            val code = conn.responseCode
            if (code == 404 || code == 403 || code == 410) return Fetch.MISSING
            if (code != 200) throw IOException("ncert.nic.in answered $code")
            var total = 0L
            target.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > MAX_FILE_BYTES) throw IOException("A chapter is larger than ${MAX_FILE_BYTES / 1024 / 1024} MB")
                        out.write(buf, 0, r)
                    }
                }
            }
            val head = ByteArray(8)
            val read = target.inputStream().use { it.read(head) }
            if (read < 4 || String(head, 0, 4, Charsets.ISO_8859_1) != "%PDF") {
                target.delete()
                return Fetch.MISSING
            }
            return Fetch.OK
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_CHAPTERS = 30
        const val MAX_FILE_BYTES = 60L * 1024 * 1024
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
