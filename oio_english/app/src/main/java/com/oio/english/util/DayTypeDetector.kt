package com.oio.english.util

/**
 * 检测特殊日类型（休息日/复习日/模拟考/总结日）。
 * 只识别精确的日名称，不会把普通话题误判为特殊日。
 */
object DayTypeDetector {

    // 精确匹配的特殊日名称（用户 xlsx 中的 topic 列）
    private val specialDayNames = setOf(
        "休息日", "休息", "day off",
        "复习日", "复习", "review day", "review",
        "模拟考", "模拟", "模拟考试", "mock test", "mock",
        "总结日", "总结", "summary day", "summary",
        "rest day", "revision day"
    )

    /** 判断话题名是否是特殊日 */
    fun isSpecialDay(topicName: String): Boolean {
        return topicName.trim().lowercase() in specialDayNames.map { it.lowercase() }
    }

    /** 判断特殊日类型 */
    fun getType(topicName: String): DayType {
        val lower = topicName.trim().lowercase()
        return when {
            lower.contains("休息") || lower.contains("rest") || lower.contains("day off") -> DayType.REST
            lower.contains("复习") || lower.contains("review") || lower.contains("revision") -> DayType.REVIEW
            lower.contains("模拟") || lower.contains("mock") -> DayType.MOCK
            lower.contains("总结") || lower.contains("summary") -> DayType.SUMMARY
            else -> DayType.OTHER
        }
    }

    enum class DayType { REST, REVIEW, MOCK, SUMMARY, OTHER }
}
