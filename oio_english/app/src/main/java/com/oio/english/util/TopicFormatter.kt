package com.oio.english.util

/**
 * 解析话题字符串中的编号和名称。
 * "1. Food" → TopicParts(number=1, name="Food")
 * "复习日" → TopicParts(number=null, name="复习日")
 */
data class TopicParts(val number: Int?, val name: String)

object TopicFormatter {

    private val pattern = Regex("^(\\d+)\\.?\\s*(.+)$")

    fun parse(raw: String): TopicParts {
        return pattern.find(raw.trim())?.let { match ->
            TopicParts(
                number = match.groupValues[1].toIntOrNull(),
                name = match.groupValues[2].trim()
            )
        } ?: TopicParts(null, raw.trim())
    }

    /** 只在显示时去掉编号（不论数据库存的什么格式） */
    fun clean(raw: String): String = parse(raw).name

    /** 格式化显示："Food" 或 "#1 Food" */
    fun displayName(raw: String): String = parse(raw).name

    /** 带编号显示："#1 Food" */
    fun displayWithNumber(raw: String): String {
        val parts = parse(raw)
        return if (parts.number != null) "#${parts.number} ${parts.name}"
        else parts.name
    }
}
