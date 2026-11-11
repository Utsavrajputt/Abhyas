package com.invictus.abhyas.ui.ncert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BookChapterEntity
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.data.repo.StartResult
import com.invictus.abhyas.domain.ai.AiPrompts
import com.invictus.abhyas.domain.ai.AiQuestionParser
import com.invictus.abhyas.domain.ai.AiResult
import com.invictus.abhyas.domain.ai.AiSource
import com.invictus.abhyas.domain.model.Exam
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.ncert.ExerciseExtractor
import com.invictus.abhyas.domain.ncert.GenKind
import com.invictus.abhyas.domain.ncert.NcertQuestionGenerator
import com.invictus.abhyas.domain.ncert.SourcePassage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ncertPracticeFactory(app: AbhyasApp) = viewModelFactory { initializer { NcertPracticeViewModel(app) } }

val PRACTICE_COUNTS = listOf(5, 10, 20, 30)

data class PracticeUi(
    val books: List<BookEntity> = emptyList(),
    val subject: String? = null,
    val bookId: String? = null,
    val chapters: List<BookChapterEntity> = emptyList(),
    /** -1 = the whole book. */
    val chapterIdx: Int = -1,
    val kinds: Set<GenKind> = setOf(GenKind.FILL_BLANK, GenKind.TRUE_FALSE),
    val countIdx: Int = 1,
    val aiReady: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    /** Set when an attempt was created but notes should be shown before opening it. */
    val pending: Pair<Long, List<String>>? = null,
)

class NcertPracticeViewModel(private val app: AbhyasApp) : ViewModel() {
    private val repo = app.books
    private val _ui = MutableStateFlow(PracticeUi())
    val ui: StateFlow<PracticeUi> = _ui

    init {
        viewModelScope.launch {
            combine(repo.observeBooks(), app.aiGateway.ready) { books, ai -> books.filter { it.status == BookStatus.READY } to ai }
                .collect { (books, ai) ->
                    _ui.update { cur ->
                        val keep = cur.bookId?.takeIf { id -> books.any { it.id == id } }
                        val subject = cur.subject?.takeIf { sub -> books.any { it.subject == sub } } ?: books.firstOrNull()?.subject
                        val kinds = if (ai) cur.kinds else cur.kinds.filterNot { it.needsAi }.toSet()
                        cur.copy(books = books, subject = subject, aiReady = ai, kinds = kinds, bookId = keep, chapters = if (keep == null) emptyList() else cur.chapters)
                    }
                    if (_ui.value.bookId == null) {
                        val first = books.firstOrNull { it.subject == _ui.value.subject } ?: books.firstOrNull()
                        first?.let { selectBook(it.id) }
                    }
                }
        }
    }

    private var chaptersJob: kotlinx.coroutines.Job? = null

    fun selectSubject(subject: String) {
        _ui.update { it.copy(subject = subject) }
        _ui.value.books.firstOrNull { it.subject == subject }?.let { selectBook(it.id) }
    }

    fun selectBook(id: String?) {
        chaptersJob?.cancel()
        _ui.update { it.copy(bookId = id, chapters = emptyList(), chapterIdx = -1, message = null) }
        if (id == null) return
        // Observed, not read once: chapters can be written after the book first shows as ready.
        chaptersJob = viewModelScope.launch {
            repo.observeChapters(id).collect { chapters ->
                _ui.update { if (it.bookId == id) it.copy(chapters = chapters, chapterIdx = it.chapterIdx.takeIf { i -> i < chapters.size } ?: -1) else it }
            }
        }
    }

    fun selectChapter(idx: Int) = _ui.update { it.copy(chapterIdx = idx, message = null) }
    fun selectCount(i: Int) = _ui.update { it.copy(countIdx = i) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }
    fun dismissPending() = _ui.update { it.copy(pending = null) }

    fun toggle(kind: GenKind) = _ui.update {
        it.copy(kinds = if (kind in it.kinds) it.kinds - kind else it.kinds + kind, message = null)
    }

    fun start() {
        val s = _ui.value
        if (s.busy) return
        val book = s.books.firstOrNull { it.id == s.bookId }
        if (book == null) { fail("Choose a book first."); return }
        if (s.kinds.isEmpty()) { fail("Choose at least one kind of question."); return }
        if (GenKind.EXERCISE in s.kinds && s.chapterIdx < 0) { fail("Choose a chapter to practise its book exercises."); return }
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val outcome = try { build(book, s) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) {
                Built("Could not make questions: ${e.message ?: "unknown error"}", emptyList(), emptyList())
            }
            if (outcome.questions.isEmpty()) {
                _ui.update { it.copy(busy = false, message = outcome.error ?: outcome.notes.firstOrNull() ?: "No questions could be made from this text.") }
                return@launch
            }
            val title = "NCERT practice: ${book.title}"
            when (val r = app.attemptRepository.startFromQuestions(title, "NCERT", outcome.questions)) {
                is StartResult.Failed -> _ui.update { it.copy(busy = false, message = r.message) }
                is StartResult.Started -> _ui.update { it.copy(busy = false, pending = r.attemptId to outcome.notes) }
            }
        }
    }

    private fun fail(msg: String) = _ui.update { it.copy(message = msg) }

    private class Built(val error: String?, val questions: List<Question>, val notes: List<String>)

    private suspend fun build(book: BookEntity, s: PracticeUi): Built {
        val chapter = s.chapters.getOrNull(s.chapterIdx)
        val from = chapter?.startPage ?: 1
        val to = chapter?.endPage ?: book.pageCount
        val passages = withContext(Dispatchers.IO) {
            repo.passagesInRange(book.id, from, to).map { SourcePassage(book.id, it.page, it.idx, it.text, it.textHash, chapter?.title, repo.pageLabel(book.id, it.page)) }
        }
        if (passages.isEmpty()) {
            return Built("This ${if (chapter != null) "chapter" else "book"} has no readable text yet. Scanned books cannot be used, and indexing may still be running.", emptyList(), emptyList())
        }
        val ctx = AiSource(book.id, book.subject, book.classLevel, chapter?.title, Exam.NEET)
        val count = PRACTICE_COUNTS[s.countIdx]
        val share = (count + s.kinds.size - 1) / s.kinds.size
        val notes = ArrayList<String>()
        val all = ArrayList<Question>()

        val ruleKinds = s.kinds.filter { it == GenKind.FILL_BLANK || it == GenKind.TRUE_FALSE }.toSet()
        if (ruleKinds.isNotEmpty()) {
            val r = withContext(Dispatchers.Default) { NcertQuestionGenerator.generate(passages, ctx, ruleKinds, share * ruleKinds.size) }
            all += r.questions
            notes += r.notes
        }
        if (GenKind.EXERCISE in s.kinds) {
            val r = withContext(Dispatchers.Default) { ExerciseExtractor.extract(passages, ctx) }
            all += r.questions.shuffled().take(share)
            r.note?.let { notes += it }
        }
        var aiError: String? = null
        val aiKinds = s.kinds.filter { it.needsAi }
        if (aiKinds.isNotEmpty()) {
            val text = aiContext(passages)
            if (text.isBlank()) {
                notes += "Not enough text for AI questions."
            } else {
                for (kind in aiKinds) {
                    val n = minOf(share, 8)
                    val prompt = if (kind == GenKind.AI_MCQ) AiPrompts.mcq(text, n) else AiPrompts.assertionReason(text, n)
                    when (val r = app.aiGateway.run(prompt)) {
                        is AiResult.Text -> {
                            val parsed = if (kind == GenKind.AI_MCQ) AiQuestionParser.parseMcq(r.text, ctx, limit = share)
                            else AiQuestionParser.parseAssertionReason(r.text, ctx, limit = share)
                            all += parsed.questions
                            notes += if (parsed.questions.isEmpty()) "The AI reply for ${kind.label} could not be turned into questions."
                            else "${parsed.questions.size} AI question${if (parsed.questions.size == 1) "" else "s"} (${kind.label}) are unverified and not scored."
                        }
                        is AiResult.Failure -> { aiError = r.message; notes += "${kind.label} skipped: ${r.message}" }
                    }
                }
            }
        }
        val picked = all.distinctBy { it.id }.shuffled().take(count)
        if (picked.size < count && picked.isNotEmpty()) notes += "Only ${picked.size} of $count questions could be made."
        return Built(if (picked.isEmpty()) aiError else null, picked, notes)
    }

    /** A few substantial paragraphs, sampled at random, that fit one request. */
    private fun aiContext(passages: List<SourcePassage>): String {
        val sb = StringBuilder()
        for (p in passages.filter { it.text.length >= 120 }.shuffled()) {
            if (sb.length + p.text.length > 4500) continue
            sb.append(p.text).append("\n\n")
            if (sb.length > 3500) break
        }
        return sb.toString().trim()
    }
}
