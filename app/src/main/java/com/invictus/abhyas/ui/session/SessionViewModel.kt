package com.invictus.abhyas.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.repo.SessionItem
import com.invictus.abhyas.data.repo.STATUS_SUBMITTED
import com.invictus.abhyas.domain.model.Response
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

fun sessionViewModelFactory(app: AbhyasApp, attemptId: Long) = viewModelFactory {
    initializer { SessionViewModel(app, attemptId) }
}

data class SessionUi(
    val loading: Boolean = true,
    val title: String = "",
    val items: List<SessionItem> = emptyList(),
    val index: Int = 0,
    val timed: Boolean = false,
    val finished: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val current: SessionItem? get() = items.getOrNull(index)
}

class SessionViewModel(private val app: AbhyasApp, private val attemptId: Long) : ViewModel() {
    private val repo = app.attemptRepository
    private val clock: () -> Long = System::currentTimeMillis

    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui

    /** Remaining time in ms for timed attempts, null otherwise. Derived from a wall-clock deadline. */
    private val _remaining = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remaining

    val bookmarkedIds: StateFlow<Set<String>> = app.database.bookmarkDao().observeIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Writes go through one queue so they hit the DB in the order they were made.
    private val saves = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var activeSince: Long? = null
    private var submitStarted = false

    init {
        viewModelScope.launch {
            for (job in saves) {
                try { job() } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = repo.loadSession(attemptId)
        if (loaded == null || loaded.items.isEmpty()) {
            _ui.value = SessionUi(loading = false, error = "This test could not be found.")
            return
        }
        val a = loaded.attempt
        if (a.status == STATUS_SUBMITTED) {
            _ui.value = SessionUi(loading = false, title = a.title, items = loaded.items, finished = true)
            return
        }
        _ui.value = SessionUi(
            loading = false,
            title = a.title,
            items = loaded.items,
            index = a.currentIndex.coerceIn(0, loaded.items.lastIndex),
            timed = a.endsAt != null,
        )
        activeSince = clock()
        val deadline = a.endsAt
        if (deadline != null) {
            while (viewModelScope.isActive && !submitStarted) {
                val left = deadline - clock()
                _remaining.value = max(left, 0)
                if (left <= 0) { submit(); break }
                delay(500)
            }
        }
    }

    // ---- time tracking ----

    fun pause() { flushTime(); activeSince = null }

    fun resume() { if (!_ui.value.finished && !_ui.value.loading) activeSince = clock() }

    private fun flushTime() {
        val since = activeSince ?: return
        val now = clock()
        val elapsed = max(now - since, 0)
        activeSince = now
        val i = _ui.value.index
        val item = _ui.value.items.getOrNull(i) ?: return
        replace(i) { it.copy(timeSpentMs = it.timeSpentMs + elapsed) }
        persist(i)
    }

    // ---- user actions ----

    fun goTo(target: Int) {
        val s = _ui.value
        if (s.finished || target !in s.items.indices || target == s.index) return
        flushTime()
        _ui.update { it.copy(index = target) }
        replace(target) { it.copy(visited = true) }
        persist(target)
        val idx = target
        saves.trySend { repo.setCurrentIndex(attemptId, idx) }
    }

    fun next() = goTo(_ui.value.index + 1)
    fun previous() = goTo(_ui.value.index - 1)

    fun answer(response: Response?) {
        val i = _ui.value.index
        replace(i) { it.copy(response = response) }
        persist(i)
    }

    fun toggleMarked() {
        val i = _ui.value.index
        replace(i) { it.copy(marked = !it.marked) }
        persist(i)
    }

    fun toggleBookmark() {
        val q = _ui.value.current?.question ?: return
        val on = q.id !in bookmarkedIds.value
        saves.trySend { repo.setBookmark(q.id, on) }
    }

    /** Persists everything, scores the attempt and flags the UI as finished. Safe to call twice. */
    fun submit() {
        if (submitStarted) return
        submitStarted = true
        _ui.update { it.copy(submitting = true) }
        flushTime()
        activeSince = null
        viewModelScope.launch {
            val drained = CompletableDeferred<Unit>()
            saves.send { drained.complete(Unit) }
            drained.await()
            val result = repo.submit(attemptId)
            _ui.update {
                if (result == null) it.copy(submitting = false, error = "Could not submit this test.")
                else it.copy(submitting = false, finished = true)
            }
        }
    }

    // ---- helpers ----

    private fun replace(index: Int, transform: (SessionItem) -> SessionItem) {
        _ui.update { s ->
            if (index !in s.items.indices) s
            else s.copy(items = s.items.mapIndexed { i, item -> if (i == index) transform(item) else item })
        }
    }

    private fun persist(index: Int) {
        val item = _ui.value.items.getOrNull(index) ?: return
        saves.trySend { repo.saveItem(attemptId, item) }
    }

    override fun onCleared() {
        saves.close()
        super.onCleared()
    }
}
