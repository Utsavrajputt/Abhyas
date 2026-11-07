package com.invictus.abhyas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuestionEntity::class,
        BankEntity::class,
        AttemptEntity::class,
        AttemptQuestionEntity::class,
        BookmarkEntity::class,
        MistakeEntity::class,
        PdfJobEntity::class,
        PdfPageEntity::class,
        PdfDraftEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AbhyasDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun bankDao(): BankDao
    abstract fun attemptDao(): AttemptDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun pdfDao(): PdfDao

    companion object {
        /** v1 -> v2: question-bank, attempt, bookmark and mistake tables. `questions` is unchanged. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `banks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`version` INTEGER NOT NULL, `exam` TEXT NOT NULL, `classLevel` INTEGER, " +
                        "`subject` TEXT NOT NULL, `kind` TEXT NOT NULL, `checksum` TEXT NOT NULL, " +
                        "`questionCount` INTEGER NOT NULL, `installedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mode` TEXT NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `endsAt` INTEGER, " +
                        "`currentIndex` INTEGER NOT NULL, `templateJson` TEXT, `configJson` TEXT NOT NULL, " +
                        "`score` REAL NOT NULL, `maxScore` REAL NOT NULL, `correctCount` INTEGER NOT NULL, " +
                        "`wrongCount` INTEGER NOT NULL, `unattemptedCount` INTEGER NOT NULL, " +
                        "`unscoredCount` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attempts_status` ON `attempts` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attempts_startedAt` ON `attempts` (`startedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attempt_questions` (`attemptId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, `questionId` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `subject` TEXT NOT NULL, `chapter` TEXT, `topic` TEXT, " +
                        "`difficulty` TEXT, `responseJson` TEXT, `marked` INTEGER NOT NULL, " +
                        "`visited` INTEGER NOT NULL, `timeSpentMs` INTEGER NOT NULL, `verdict` TEXT, " +
                        "`marksAwarded` REAL NOT NULL, PRIMARY KEY(`attemptId`, `position`), " +
                        "FOREIGN KEY(`attemptId`) REFERENCES `attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attempt_questions_questionId` " +
                        "ON `attempt_questions` (`questionId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (`questionId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`questionId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mistakes` (`questionId` TEXT NOT NULL, " +
                        "`wrongCount` INTEGER NOT NULL, `lastWrongAt` INTEGER NOT NULL, `lastAttemptId` INTEGER, " +
                        "`resolved` INTEGER NOT NULL, PRIMARY KEY(`questionId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistakes_resolved` ON `mistakes` (`resolved`)")
            }
        }

        /** v2 -> v3: PDF Studio tables (jobs, extracted pages, parsed drafts). Existing tables are unchanged. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `localPath` TEXT NOT NULL, `pageCount` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `nextPage` INTEGER NOT NULL, `ocrPages` INTEGER NOT NULL, " +
                        "`message` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`configJson` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_jobs_status` ON `pdf_jobs` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_pages` (`jobId` INTEGER NOT NULL, `page` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, `method` TEXT NOT NULL, `hasImages` INTEGER NOT NULL, " +
                        "`linesJson` TEXT, `pageHeight` REAL NOT NULL, PRIMARY KEY(`jobId`, `page`), " +
                        "FOREIGN KEY(`jobId`) REFERENCES `pdf_jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_drafts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`jobId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `number` TEXT NOT NULL, " +
                        "`page` INTEGER NOT NULL, `type` TEXT NOT NULL, `statement` TEXT NOT NULL, " +
                        "`payloadJson` TEXT NOT NULL, `explanation` TEXT, `subject` TEXT, `chapter` TEXT, " +
                        "`topic` TEXT, `difficulty` TEXT, `confidence` REAL NOT NULL, `issues` TEXT NOT NULL, " +
                        "`errors` TEXT NOT NULL, `status` TEXT NOT NULL, `duplicateOf` TEXT, " +
                        "`selected` INTEGER NOT NULL, `edited` INTEGER NOT NULL, `hasFigureHint` INTEGER NOT NULL, " +
                        "`imagePath` TEXT, `suggestedChapter` TEXT, " +
                        "FOREIGN KEY(`jobId`) REFERENCES `pdf_jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_drafts_jobId_position` ON `pdf_drafts` (`jobId`, `position`)")
            }
        }

        fun build(context: Context): AbhyasDatabase =
            Room.databaseBuilder(context, AbhyasDatabase::class.java, "abhyas.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
