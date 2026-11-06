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
    ],
    version = 2,
    exportSchema = true,
)
abstract class AbhyasDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun bankDao(): BankDao
    abstract fun attemptDao(): AttemptDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun mistakeDao(): MistakeDao

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

        fun build(context: Context): AbhyasDatabase =
            Room.databaseBuilder(context, AbhyasDatabase::class.java, "abhyas.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
