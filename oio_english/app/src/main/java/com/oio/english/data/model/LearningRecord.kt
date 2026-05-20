package com.oio.english.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每个话题的 4 个环节完成记录。
 * dayTopicId 指向 day_topics 的 id。
 */
@Entity(tableName = "learning_records")
data class LearningRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayTopicId: Long,
    val o1Done: Boolean = false,
    val inputDone: Boolean = false,
    val o2Done: Boolean = false,
    val ankiDone: Boolean = false,
    /** 当日全部环节完成后写的反思总结 */
    val reflection: String = "",
    /** 完成时间戳（毫秒） */
    val completedAt: Long? = null,
    /** 自我评估难度: 0=未标记 1=😊简单 2=🤔中等 3=😰困难 */
    val difficulty: Int = 0
)
