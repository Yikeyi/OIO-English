package com.oio.english.data.repository

import com.oio.english.data.dao.DayTopicDao
import com.oio.english.data.dao.LearningRecordDao
import com.oio.english.data.model.DayTopic
import com.oio.english.data.model.LearningRecord
import kotlinx.coroutines.flow.Flow

class PlanRepository(
    private val dayTopicDao: DayTopicDao,
    private val learningRecordDao: LearningRecordDao
) {
    // ── 话题查询 ──

    fun getAllTopics(): Flow<List<DayTopic>> = dayTopicDao.getAllTopics()

    suspend fun getAllTopicsOnce(): List<DayTopic> = dayTopicDao.getAllTopicsOnce()

    fun getTopicsByDay(day: Int): Flow<List<DayTopic>> = dayTopicDao.getTopicsByDay(day)

    suspend fun getTopicsByDayOnce(day: Int): List<DayTopic> = dayTopicDao.getTopicsByDayOnce(day)

    fun getTopicsByWeek(week: Int): Flow<List<DayTopic>> = dayTopicDao.getTopicsByWeek(week)

    fun getAllDayNumbers(): Flow<List<Int>> = dayTopicDao.getAllDayNumbers()

    suspend fun getAllDayNumbersOnce(): List<Int> = dayTopicDao.getAllDayNumbersOnce()

    fun getAllWeekNumbers(): Flow<List<Int>> = dayTopicDao.getAllWeekNumbers()

    suspend fun getMaxDayNumber(): Int = dayTopicDao.getMaxDayNumber()

    // ── 学习记录 ──

    fun getRecord(dayTopicId: Long): Flow<LearningRecord?> = learningRecordDao.getRecord(dayTopicId)

    fun getRecordsByDay(dayNumber: Int): Flow<List<LearningRecord>> =
        learningRecordDao.getRecordsByDay(dayNumber)

    suspend fun getRecordsByDayOnce(dayNumber: Int): List<LearningRecord> =
        learningRecordDao.getRecordsByDayOnce(dayNumber)

    fun getCompletedRecords(): Flow<List<LearningRecord>> =
        learningRecordDao.getCompletedRecords()

    /** 监听学习记录表的任何变化 — 用于其他页面自动刷新 */
    fun observeRecordChanges(): Flow<Int> = learningRecordDao.observeRecordCount()

    suspend fun upsertRecord(record: LearningRecord): Long =
        learningRecordDao.upsert(record)

    suspend fun updateTopic(topic: DayTopic) = dayTopicDao.update(topic)

    suspend fun getRecordOnce(dayTopicId: Long): LearningRecord? =
        learningRecordDao.getRecordOnce(dayTopicId)

    // ── 导入（覆盖逻辑） ──

    /**
     * 导入一批话题：
     * - 对于 DB 中已存在的天数 → 删除旧数据后插入新数据
     * - 对于新天数 → 直接追加
     */
    /**
     * @param onProgress 0..100 进度回调（在主线程调用，可安全更新 UI）
     */
    suspend fun importTopics(
        topics: List<DayTopic>,
        onProgress: (Int) -> Unit = {}
    ) {
        onProgress(5)

        // 找出已有天数
        val existingDays = dayTopicDao.getAllDayNumbersOnce().toSet()
        val incomingDays = topics.map { it.dayNumber }.toSet()
        onProgress(15)

        // 删除需要覆盖的天的旧数据
        val daysToOverwrite = incomingDays.intersect(existingDays)
        for (day in daysToOverwrite) {
            learningRecordDao.deleteByDay(day)
            dayTopicDao.deleteByDay(day)
        }
        onProgress(30)

        // 插入新数据
        dayTopicDao.insertAll(topics)
        onProgress(50)

        // 为新话题创建空的学习记录
        val allTopics = (incomingDays).flatMap { dayTopicDao.getTopicsByDayOnce(it) }
        val total = allTopics.size
        allTopics.forEachIndexed { idx, topic ->
            val existing = learningRecordDao.getRecordOnce(topic.id)
            if (existing == null) {
                learningRecordDao.upsert(LearningRecord(dayTopicId = topic.id))
            }
            onProgress(50 + ((idx + 1) * 50 / total))
        }
    }

    /** 从备份恢复：清空旧数据 → 插入话题 → 按(天数,话题名)匹配记录 → 重建 */
    suspend fun restoreFromBackup(
        pairedTopics: List<Pair<DayTopic, LearningRecord?>>
    ) {
        val topics = pairedTopics.map { it.first }
        // 清空旧数据
        val oldDays = dayTopicDao.getAllDayNumbersOnce()
        for (day in oldDays) {
            learningRecordDao.deleteByDay(day)
            dayTopicDao.deleteByDay(day)
        }

        // 插入话题（Room 分配新 id）
        dayTopicDao.insertAll(topics)

        // 获取刚插入的话题（带有真实 id）
        val insertedTopics = dayTopicDao.getAllTopicsOnce()
        // 按 (dayNumber, topic) 建立索引
        val topicIndex = mutableMapOf<Pair<Int, String>, Long>()
        for (t in insertedTopics) {
            topicIndex[t.dayNumber to t.topic] = t.id
        }

        // 按配对写入记录
        for ((backupTopic, record) in pairedTopics) {
            val newId = topicIndex[backupTopic.dayNumber to backupTopic.topic] ?: continue
            if (record != null) {
                learningRecordDao.upsert(record.copy(id = 0, dayTopicId = newId))
            } else {
                // 无记录 → 创建空记录
                learningRecordDao.upsert(LearningRecord(dayTopicId = newId))
            }
        }
    }
}
