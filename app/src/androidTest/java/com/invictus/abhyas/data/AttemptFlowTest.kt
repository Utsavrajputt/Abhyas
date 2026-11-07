package com.invictus.abhyas.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.repo.AttemptRepository
import com.invictus.abhyas.data.repo.BankImporter
import com.invictus.abhyas.data.repo.ImportResult
import com.invictus.abhyas.data.repo.QuestionRepository
import com.invictus.abhyas.data.repo.StartResult
import com.invictus.abhyas.domain.attempt.PracticeConfig
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.domain.engine.Sha256
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.query.QuestionFilter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Attempt persistence, "restart during test" (fresh repository on the same DB), scoring and mistakes. */
@RunWith(AndroidJUnit4::class)
class AttemptFlowTest {
    private lateinit var db: AbhyasDatabase
    private var now = 1_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AbhyasDatabase::class.java)
            .allowMainThreadQueries().build()
        val q = (1..3).joinToString(",", "[", "]") {
            """{"id":"$it","type":"SINGLE_MCQ","statement":"TEST question $it","payload":{"options":[{"id":"a","text":"TEST A"},{"id":"b","text":"TEST B"}],"correctId":"a"}}"""
        }
        val m = """{"id":"t","version":1,"exam":"NEET","class":11,"subject":"Physics","kind":"theory","sha256":"${Sha256.hex(q.toByteArray())}","questionCount":3}"""
        val r = runBlocking { BankImporter(db).install(m.toByteArray(), q.toByteArray()) }
        assertTrue(r is ImportResult.Success)
    }

    @After fun tearDown() = db.close()

    private fun repo() = AttemptRepository(db, QuestionRepository(db), clock = { now })

    private fun start(r: AttemptRepository, durationSec: Int? = null): Long {
        val s = runBlocking { r.startPractice(PracticeConfig(PracticeMode.QUICK, QuestionFilter(), 3, durationSec)) }
        return (s as StartResult.Started).attemptId
    }

    private fun correctId(item: com.invictus.abhyas.data.repo.SessionItem) =
        (item.question.payload as SingleChoicePayload).correctId!!

    @Test
    fun answersSurviveRestartAndScoreOnSubmit() = runBlocking {
        val id = start(repo())
        val first = repo().loadSession(id)!!.items[0]
        repo().saveItem(id, first.copy(response = Response.Choice(setOf(correctId(first))), marked = true, timeSpentMs = 500))

        val reopened = repo().loadSession(id)!!            // "process restarted": fresh repository, same DB
        assertEquals(Response.Choice(setOf(correctId(first))), reopened.items[0].response)
        assertTrue(reopened.items[0].marked)
        assertEquals(500L, reopened.items[0].timeSpentMs)
        assertNotNull(db.attemptDao().getInProgress())

        val done = repo().submit(id)!!
        assertEquals(1, done.correctCount); assertEquals(2, done.unattemptedCount); assertEquals(0, done.wrongCount)
        assertEquals(4.0, done.score, 0.0); assertEquals(12.0, done.maxScore, 0.0)
        assertEquals(done, repo().submit(id))               // idempotent
        assertNull(db.attemptDao().getInProgress())
    }

    @Test
    fun wrongAnswerFillsMistakeNotebookAndLaterCorrectResolvesIt() = runBlocking {
        val id1 = start(repo())
        val item = repo().loadSession(id1)!!.items[0]
        val wrong = (item.question.payload as SingleChoicePayload).options.first { it.id != correctId(item) }.id
        repo().saveItem(id1, item.copy(response = Response.Choice(setOf(wrong))))
        assertEquals(1, repo().submit(id1)!!.wrongCount)
        val m = db.mistakeDao().get(item.question.id)!!
        assertEquals(1, m.wrongCount); assertFalse(m.resolved)

        val id2 = start(repo())
        val again = repo().loadSession(id2)!!.items.first { it.question.id == item.question.id }
        repo().saveItem(id2, again.copy(response = Response.Choice(setOf(correctId(again)))))
        repo().submit(id2)
        assertTrue(db.mistakeDao().get(item.question.id)!!.resolved)
    }

    @Test
    fun onlyOneAttemptInProgress() = runBlocking {
        start(repo())
        val second = repo().startPractice(PracticeConfig(PracticeMode.QUICK, QuestionFilter(), 3))
        assertTrue(second is StartResult.Failed)
    }

    @Test
    fun timedAttemptFinishTimeIsClampedToDeadline() = runBlocking {
        val id = start(repo(), durationSec = 60)
        val deadline = db.attemptDao().getAttempt(id)!!.endsAt!!
        assertEquals(now + 60_000, deadline)
        now += 3_600_000                                    // app reopened an hour later
        val done = repo().submit(id)!!
        assertEquals(deadline, done.finishedAt)
    }

    @Test
    fun snapshotIsFrozenWhenBankIsRemoved() = runBlocking {
        val id = start(repo())
        BankImporter(db).remove("t")
        val loaded = repo().loadSession(id)!!
        assertEquals(3, loaded.items.size)                  // attempt still readable and submittable
        assertNotNull(repo().submit(id))
    }
}
