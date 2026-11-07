package com.invictus.abhyas.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.invictus.abhyas.data.local.AbhyasDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AbhyasDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsQuestionsAndCreatesNewTables() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO questions (id, bankId, type, statement, payloadJson, exam, subject, tags, " +
                    "marks, negativeMarks, language, answerVerified, contentHash) " +
                    "VALUES ('b:1','b','SINGLE_MCQ','test','{}','NEET','Physics','',4.0,1.0,'en',0,'h1')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, AbhyasDatabase.MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM questions").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        listOf("banks", "attempts", "attempt_questions", "bookmarks", "mistakes").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To3_addsPdfTablesAndKeepsData() {
        helper.createDatabase(DB, 2).apply {
            execSQL(
                "INSERT INTO questions (id, bankId, type, statement, payloadJson, exam, subject, tags, " +
                    "marks, negativeMarks, language, answerVerified, contentHash) " +
                    "VALUES ('b:1','b','SINGLE_MCQ','test','{}','NEET','Physics','',4.0,1.0,'en',0,'h1')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 3, true, AbhyasDatabase.MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM questions").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        listOf("pdf_jobs", "pdf_pages", "pdf_drafts").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
