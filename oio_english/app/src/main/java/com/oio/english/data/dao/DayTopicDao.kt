package com.oio.english.data.dao

import androidx.room.*
import com.oio.english.data.model.DayTopic
import kotlinx.coroutines.flow.Flow

@Dao
interface DayTopicDao {

    @Query("SELECT * FROM day_topics ORDER BY dayNumber ASC, id ASC")
    fun getAllTopics(): Flow<List<DayTopic>>

    @Query("SELECT * FROM day_topics ORDER BY dayNumber ASC, id ASC")
    suspend fun getAllTopicsOnce(): List<DayTopic>

    @Query("SELECT * FROM day_topics WHERE dayNumber = :day ORDER BY id ASC")
    fun getTopicsByDay(day: Int): Flow<List<DayTopic>>

    @Query("SELECT * FROM day_topics WHERE dayNumber = :day ORDER BY id ASC")
    suspend fun getTopicsByDayOnce(day: Int): List<DayTopic>

    @Query("SELECT * FROM day_topics WHERE weekNumber = :week ORDER BY dayNumber ASC, id ASC")
    fun getTopicsByWeek(week: Int): Flow<List<DayTopic>>

    @Query("SELECT DISTINCT dayNumber FROM day_topics ORDER BY dayNumber ASC")
    fun getAllDayNumbers(): Flow<List<Int>>

    @Query("SELECT DISTINCT dayNumber FROM day_topics ORDER BY dayNumber ASC")
    suspend fun getAllDayNumbersOnce(): List<Int>

    @Query("SELECT DISTINCT weekNumber FROM day_topics ORDER BY weekNumber ASC")
    fun getAllWeekNumbers(): Flow<List<Int>>

    /** 获取当前最大天数（用于判断哪些是新天数） */
    @Query("SELECT COALESCE(MAX(dayNumber), 0) FROM day_topics")
    suspend fun getMaxDayNumber(): Int

    /** 删除指定天数的所有话题（覆盖逻辑用） */
    @Query("DELETE FROM day_topics WHERE dayNumber = :day")
    suspend fun deleteByDay(day: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<DayTopic>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(topic: DayTopic): Long

    @Update
    suspend fun update(topic: DayTopic)
}
