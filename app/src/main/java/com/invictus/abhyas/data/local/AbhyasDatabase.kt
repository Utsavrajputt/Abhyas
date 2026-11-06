package com.invictus.abhyas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AbhyasDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        fun build(context: Context): AbhyasDatabase =
            Room.databaseBuilder(context, AbhyasDatabase::class.java, "abhyas.db").build()
    }
}
