package com.invictus.abhyas.data.repo

import androidx.room.withTransaction
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.local.AttemptEntity
import com.invictus.abhyas.data.local.AttemptQuestionEntity
import com.invictus.abhyas.data.local.BookmarkEntity
import com.invictus.abhyas.data.local.MistakeEntity
import com.invictus.abhyas.domain.attempt.ExamTemplate
import com.invictus.abhyas.domain.attempt.PracticeConfig
import com.invictus.abhyas.domain.attempt.SummaryCalculator
import com.invictus.abhyas.domain.engine.OptionShuffler
import com.invictus.abhyas.domain.engine.ResponseCodec
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.Verdict
import com.invictus.abhyas.domain.pack.AbhyasJson
import com.invictus.abhyas.domain.pack.QuestionFactory
import com.invictus.abhyas.domain.query.KindFilter
import com.invictus.abhyas.domain.query.QuestionFilter
import kotlinx.coroutines.flow.Flow
import kotlin.math.min

sealed interface StartResult {
    data class Started(val attemptId: Long, val warnings: List<String>) : StartResult
    data class Failed(val message: String) : StartResult
}

data class SessionItem(
    val position: Int,
    val question: Question,
    val response: Response?,
    val marked: Boolean,
    val visited: Boolean,
    val timeSpentMs: Long,
)

data class ReviewItem(
    val position: Int,
    val question: Question,
    val response: Response?,
    val marked: Boolean,
    val verdict: Verdict,
    val marks: Double,
    val timeSpentMs: Long,
)

data class LoadedAttempt(val attempt: AttemptEntity, val items: List<SessionItem>)

const val STATUS_IN_PROGRESS = "IN_PROGRESS"
const val STATUS_SUBMITTED = "SUBMITTED"

/** Creates, persists, resumes and scores attempts. All state lives in Room so it survives process death. */
class AttemptRepository(
    private val db: AbhyasDatabase,
    private val questions: QuestionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = db.attemptDao()

    fun observeInProgress(): Flow<AttemptEntity?> = dao.observeInProgress()

    suspend fun getAttempt(id: Long): AttemptEntity? = dao.getAttempt(id)

    // ---- starting ----

    suspend fun startPractice(cfg: PracticeConfig): StartResult {
        if (dao.getInProgress() != null) return StartResult.Failed("Finish or submit your current test first.")
        val pick = questions.pickRandom(
            filter = cfg.filter,
            count = cfg.count,
            recentDays = if (cfg.mode.avoidRecent) 7 else 0,
        )
        if (pick.ids.isEmpty()) return StartResult.Failed("No questions match these filters.")
        val qs = questions.load(pick.ids).map { OptionShuffler.shuffle(it) }
        if (qs.isEmpty()) return StartResult.Failed("No questions match these filters.")
        val warnings = if (pick.shortfall > 0) {
            listOf("Only ${qs.size} of ${cfg.count} questions are available.")
        } else emptyList()
        val endsAt = cfg.durationSec?.let { clock() + it * 1000L }
        val id = create(cfg.title, cfg.mode.name, "{}", null, endsAt, qs)
        return StartResult.Started(id, warnings)
    }

    suspend fun startTemplate(t: ExamTemplate): StartResult {
        if (dao.getInProgress() != null) return StartResult.Failed("Finish or submit your current test first.")
        val ordered = ArrayList<Question>()
        val warnings = ArrayList<String>()
        val taken = HashSet<String>()

        if (t.sections.isEmpty()) {
            val filter = QuestionFilter(exam = t.examEnum)
            val pick = questions.pickRandom(filter, t.questionCount ?: 0)
            ordered += questions.load(pick.ids).map { applyMarking(OptionShuffler.shuffle(it), t.marking) }
            if (pick.shortfall > 0) warnings += "Only ${pick.ids.size} of ${pick.requested} questions are available."
        } else {
            for (section in t.sections) {
                val kind = when (section.kind?.uppercase()) {
                    "THEORY" -> KindFilter.THEORY
                    "NUMERICAL" -> KindFilter.NUMERICAL
                    else -> KindFilter.ALL
                }
                val filter = QuestionFilter(exam = t.examEnum, subject = section.subject, kind = kind)
                // Over-ask so ids already used by an earlier section can be dropped without a shortfall.
                val pick = questions.pickRandom(filter, section.count + taken.size)
                val fresh = pick.ids.filter { it !in taken }.take(section.count)
                taken += fresh
                ordered += questions.load(fresh).map {
                    applyMarking(OptionShuffler.shuffle(it), t.markingFor(section))
                }
                if (fresh.size < section.count) {
                    warnings += "${section.title}: only ${fresh.size} of ${section.count} questions are available."
                }
            }
        }
        if (ordered.isEmpty()) return StartResult.Failed("No questions are available for this test.")
        val endsAt = t.durationMinutes?.let { clock() + it * 60_000L }
        val templateJson = AbhyasJson.json.encodeToString(ExamTemplate.serializer(), t)
        val id = create(t.name, "TEMPLATE", "{}", templateJson, endsAt, ordered)
        return StartResult.Started(id, warnings)
    }

    private fun applyMarking(q: Question, m: com.invictus.abhyas.domain.attempt.Marking?): Question =
        if (m == null) q else q.copy(marks = m.correct, negativeMarks = m.wrong)

    private suspend fun create(
        title: String,
        mode: String,
        configJson: String,
        templateJson: String?,
        endsAt: Long?,
        qs: List<Question>,
    ): Long = db.withTransaction {
        val now = clock()
        val attemptId = dao.insertAttempt(
            AttemptEntity(
                mode = mode,
                title = title,
                status = STATUS_IN_PROGRESS,
                startedAt = now,
                finishedAt = null,
                endsAt = endsAt,
                currentIndex = 0,
                templateJson = templateJson,
                configJson = configJson,
                score = 0.0,
                maxScore = SummaryCalculator.maxScore(qs),
                correctCount = 0,
                wrongCount = 0,
                unattemptedCount = 0,
                unscoredCount = 0,
            ),
        )
        dao.insertQuestions(
            qs.mapIndexed { i, q ->
                AttemptQuestionEntity(
                    attemptId = attemptId,
                    position = i,
                    questionId = q.id,
                    snapshotJson = QuestionFactory.toSnapshotJson(q),
                    type = q.type.name,
                    subject = q.subject,
                    chapter = q.chapter,
                    topic = q.topic,
                    difficulty = q.difficulty?.name,
                    responseJson = null,
                    marked = false,
                    visited = i == 0,
                    timeSpentMs = 0,
                    verdict = null,
                    marksAwarded = 0.0,
                )
            },
        )
        attemptId
    }

    // ---- running ----

    suspend fun loadSession(id: Long): LoadedAttempt? {
        val attempt = dao.getAttempt(id) ?: return null
        val items = dao.getQuestions(id).mapNotNull { row ->
            val q = runCatching { QuestionFactory.fromSnapshotJson(row.snapshotJson) }.getOrNull()
                ?: return@mapNotNull null
            SessionItem(row.position, q, ResponseCodec.decode(row.responseJson), row.marked, row.visited, row.timeSpentMs)
        }
        return LoadedAttempt(attempt, items)
    }

    suspend fun loadReview(id: Long): List<ReviewItem> = dao.getQuestions(id).mapNotNull { row ->
        val q = runCatching { QuestionFactory.fromSnapshotJson(row.snapshotJson) }.getOrNull() ?: return@mapNotNull null
        ReviewItem(
            row.position, q, ResponseCodec.decode(row.responseJson), row.marked,
            runCatching { Verdict.valueOf(row.verdict ?: "") }.getOrDefault(Verdict.UNATTEMPTED),
            row.marksAwarded, row.timeSpentMs,
        )
    }

    suspend fun statRows(id: Long) = dao.statRows(id)

    suspend fun deleteAttempt(id: Long) = dao.deleteAttempt(id)

    suspend fun saveItem(attemptId: Long, item: SessionItem) = dao.saveQuestionState(
        attemptId = attemptId,
        position = item.position,
        responseJson = item.response?.let { ResponseCodec.encode(it) },
        marked = item.marked,
        visited = item.visited,
        timeSpentMs = item.timeSpentMs,
    )

    suspend fun setCurrentIndex(attemptId: Long, index: Int) = dao.setCurrentIndex(attemptId, index)

    suspend fun setBookmark(questionId: String, on: Boolean) {
        if (on) db.bookmarkDao().add(BookmarkEntity(questionId, clock())) else db.bookmarkDao().remove(questionId)
    }

    // ---- finishing ----

    /** Scores the attempt and updates the mistake notebook. Idempotent. Returns null if the attempt is gone. */
    suspend fun submit(id: Long): AttemptEntity? = db.withTransaction {
        val attempt = dao.getAttempt(id) ?: return@withTransaction null
        if (attempt.status == STATUS_SUBMITTED) return@withTransaction attempt

        val now = clock()
        val rows = dao.getQuestions(id)
        val decoded = rows.map { row ->
            row to runCatching { QuestionFactory.fromSnapshotJson(row.snapshotJson) }.getOrNull()
        }
        val scorable = decoded.filter { it.second != null }
        val summary = SummaryCalculator.compute(
            scorable.map { (row, q) -> q!! to ResponseCodec.decode(row.responseJson) },
        )

        scorable.forEachIndexed { i, (row, q) ->
            val s = summary.perQuestion[i]
            dao.updateQuestion(row.copy(verdict = s.verdict.name, marksAwarded = s.marks))
            val existing = db.mistakeDao().get(q!!.id)
            when (s.verdict) {
                Verdict.WRONG -> db.mistakeDao().upsert(
                    MistakeEntity(q.id, (existing?.wrongCount ?: 0) + 1, now, id, resolved = false),
                )
                Verdict.CORRECT -> if (existing != null && !existing.resolved) {
                    db.mistakeDao().upsert(existing.copy(resolved = true))
                }
                else -> Unit
            }
        }

        val finishedAt = attempt.endsAt?.let { min(now, it) } ?: now
        val done = attempt.copy(
            status = STATUS_SUBMITTED,
            finishedAt = finishedAt,
            score = summary.score,
            maxScore = summary.maxScore,
            correctCount = summary.correct,
            wrongCount = summary.wrong,
            unattemptedCount = summary.unattempted,
            unscoredCount = summary.unscored,
        )
        dao.updateAttempt(done)
        done
    }
}
