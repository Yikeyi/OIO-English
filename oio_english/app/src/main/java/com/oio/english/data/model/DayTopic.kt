package com.oio.english.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单日单话题的 OIO 数据。
 * 同一个 dayNumber 可以有多条（多个话题）。
 */
@Entity(tableName = "day_topics")
data class DayTopic(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayNumber: Int,        // 1, 2, 3…
    val topic: String,         // "Food", "Cooking"…
    val o1Content: String,     // O1（尝试）
    val inputContent: String,  // I（语言块）
    val o2Content: String,     // O2（组合输出）
    val ankiContent: String,   // 睡前 Anki 卡片
    val weekNumber: Int        // (dayNumber - 1) / 7 + 1
)
