package com.oio.english.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.oio.english.data.dao.DayTopicDao
import com.oio.english.data.dao.LearningRecordDao
import com.oio.english.data.model.DayTopic
import com.oio.english.data.model.LearningRecord

@Database(
    entities = [DayTopic::class, LearningRecord::class],
    version = 2,
    exportSchema = false
)
abstract class OioDatabase : RoomDatabase() {
    abstract fun dayTopicDao(): DayTopicDao
    abstract fun learningRecordDao(): LearningRecordDao

    companion object {
        @Volatile
        private var INSTANCE: OioDatabase? = null

        fun getInstance(context: Context): OioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OioDatabase::class.java,
                    "oio_english.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
