package com.invictus.abhyas.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.AttemptEntity
import com.invictus.abhyas.data.local.MistakeRow
import com.invictus.abhyas.data.local.QuestionEntity
import com.invictus.abhyas.data.repo.ReviewItem
import com.invictus.abhyas.domain.analytics.StatRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgressViewModel(private val app: AbhyasApp) : ViewModel() {
    private val db = app.database
    private fun <T> kotlinx.coroutines.flow.Flow<T>.hot(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** null = loading. */
    val history: StateFlow<List<AttemptEntity>?> = db.attemptDao().observeHistory().hot(null)
    val stats: StateFlow<List<StatRow>> = db.attemptDao().observeAllStatRows().hot(emptyList())
    val mistakes: StateFlow<List<MistakeRow>> = db.mistakeDao().observeUnresolved().hot(emptyList())
    val bookmarks: StateFlow<List<QuestionEntity>> = db.bookmarkDao().observeQuestions().hot(emptyList())

    fun resolve(questionId: String) { viewModelScope.launch { db.mistakeDao().resolve(questionId) } }
    fun removeBookmark(questionId: String) { viewModelScope.launch { db.bookmarkDao().remove(questionId) } }
    fun deleteAttempt(id: Long) { viewModelScope.launch { app.attemptRepository.deleteAttempt(id) } }
}

data class ResultUi(
    val loading: Boolean = true,
    val attempt: AttemptEntity? = null,
    val stats: List<StatRow> = emptyList(),
    val review: List<ReviewItem> = emptyList(),
)

fun resultViewModelFactory(app: AbhyasApp, id: Long) = viewModelFactory {
    initializer { ResultViewModel(app, id) }
}

class ResultViewModel(private val app: AbhyasApp, private val id: Long) : ViewModel() {
    private val _ui = MutableStateFlow(ResultUi())
    val ui: StateFlow<ResultUi> = _ui

    val bookmarkedIds: StateFlow<Set<String>> = app.database.bookmarkDao().observeIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            val repo = app.attemptRepository
            _ui.value = ResultUi(
                loading = false,
                attempt = repo.getAttempt(id),
                stats = repo.statRows(id),
                review = repo.loadReview(id),
            )
        }
    }

    fun toggleBookmark(questionId: String) {
        viewModelScope.launch { app.attemptRepository.setBookmark(questionId, questionId !in bookmarkedIds.value) }
    }
}
