package com.oio.english.data.dao

import androidx.room.*
import com.oio.english.data.model.LearningRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningRecordDao {

    @Query("SELECT * FROM learning_records WHERE dayTopicId = :dayTopicId")
    fun getRecord(dayTopicId: Long): Flow<LearningRecord?>

    @Query("SELECT * FROM learning_records WHERE dayTopicId = :dayTopicId")
    suspend fun getRecordOnce(dayTopicId: Long): LearningRecord?

    @Query("SELECT * FROM learning_records WHERE dayTopicId IN " +
           "(SELECT id FROM day_topics WHERE dayNumber = :dayNumber)")
    fun getRecordsByDay(dayNumber: Int): Flow<List<LearningRecord>>

    @Query("SELECT * FROM learning_records WHERE dayTopicId IN " +
           "(SELECT id FROM day_topics WHERE dayNumber = :dayNumber)")
    suspend fun getRecordsByDayOnce(dayNumber: Int): List<LearningRecord>

    /** 获取某天所有话题中某环节是否全部完成 */
    @Query("""
        SELECT COUNT(*) = 0 FROM learning_records 
        WHERE dayTopicId IN (SELECT id FROM day_topics WHERE dayNumber = :dayNumber)
        AND :fieldDone = 0
    """)
    suspend fun isStageFullyDone(dayNumber: Int, fieldDone: String): Boolean

    @Query("SELECT * FROM learning_records WHERE completedAt IS NOT NULL ORDER BY completedAt DESC")
    fun getCompletedRecords(): Flow<List<LearningRecord>>

    /** 监听记录表的任何变化（用于触发其他页面的刷新） */
    @Query("SELECT COUNT(*) FROM learning_records")
    fun observeRecordCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: LearningRecord): Long

    @Query("DELETE FROM learning_records WHERE dayTopicId = :dayTopicId")
    suspend fun deleteByTopicId(dayTopicId: Long)

    /** 删除某天所有学习记录（覆盖导入时用） */
    @Query("DELETE FROM learning_records WHERE dayTopicId IN " +
           "(SELECT id FROM day_topics WHERE dayNumber = :dayNumber)")
    suspend fun deleteByDay(dayNumber: Int)
}
