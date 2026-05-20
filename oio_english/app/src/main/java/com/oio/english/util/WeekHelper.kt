package com.oio.english.util

/**
 * 天数 → 周数的辅助方法。
 */
object WeekHelper {

    /** 天数 → 周号（1-based） */
    fun weekOfDay(dayNumber: Int): Int = (dayNumber - 1) / 7 + 1

    /** 周号 → 该周的天数范围 */
    fun dayRangeOfWeek(week: Int): IntRange {
        val start = (week - 1) * 7 + 1
        val end = week * 7
        return start..end
    }

    /** 该周的中文标签 */
    fun weekLabel(week: Int): String = "Week $week"

    /**
     * 所有导入的天数 → 计算出有哪些周，按顺序返回。
     */
    fun computeWeeks(dayNumbers: List<Int>): List<Int> {
        return dayNumbers
            .map { weekOfDay(it) }
            .distinct()
            .sorted()
    }
}
