package com.invictus.abhyas.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.local.toQuestion
import com.invictus.abhyas.domain.engine.RandomPick
import com.invictus.abhyas.domain.engine.Randomizer
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.query.QuestionFilter
import com.invictus.abhyas.domain.query.QuestionQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/** Filtered/paged question access + random selection. No Android UI types. */
class QuestionRepository(private val db: AbhyasDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.questionDao()

    private fun sql(b: QuestionQuery.Built) = SimpleSQLiteQuery(b.sql, b.args.toTypedArray())

    fun paged(filter: QuestionFilter): Flow<PagingData<Question>> =
        Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) {
            dao.pagingSource(sql(QuestionQuery.select(filter)))
        }.flow.map { data -> data.map { it.toQuestion() } }

    fun observeCount(filter: QuestionFilter): Flow<Int> = dao.observeCountRaw(sql(QuestionQuery.count(filter)))

    suspend fun matchingIds(filter: QuestionFilter): List<String> =
        dao.idsRaw(sql(QuestionQuery.select(filter, columns = "id", orderBy = null)))

    /**
     * Random questions for an attempt. Recently attempted questions (within [recentDays]) are
     * avoided; they are only used to top up when the pool is too small.
     */
    suspend fun pickRandom(
        filter: QuestionFilter,
        count: Int,
        recentDays: Int = 7,
        allowRecentFallback: Boolean = true,
        random: Random = Random.Default,
    ): RandomPick {
        val since = clock() - recentDays * 24L * 60 * 60 * 1000
        val recent = db.attemptDao().recentQuestionIds(since).toSet()
        return Randomizer.pick(matchingIds(filter), count, recent, allowRecentFallback, random)
    }

    suspend fun load(ids: List<String>): List<Question> {
        val byId = ids.chunked(500).flatMap { dao.getByIds(it) }.associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toQuestion() }
    }
}
