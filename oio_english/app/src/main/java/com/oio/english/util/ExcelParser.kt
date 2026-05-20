package com.oio.english.util

import android.content.Context
import android.net.Uri
import com.oio.english.data.model.DayTopic
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.util.regex.Pattern

/**
 * 解析 xlsx 文件，提取 DayTopic 列表。
 *
 * 期望的 Excel 列顺序（必须按此顺序）：
 * 天数 | 雅思Part 1题目 | O1（尝试） | 核心"语言块"（I-输入） | O2（组合输出） | 睡前Anki卡片
 */
object ExcelParser {

    data class ParseResult(
        val topics: List<DayTopic>,
        val errors: List<String> = emptyList()
    )

    fun parse(context: Context, uri: Uri): ParseResult {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return ParseResult(emptyList(), listOf("无法打开文件"))

            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val topics = mutableListOf<DayTopic>()
            val errors = mutableListOf<String>()

            for (row in sheet) {
                val rowNum = row.rowNum
                if (rowNum == 0) continue // 跳过标题行

                try {
                    val dayCell = row.getCell(0)
                    val topicCell = row.getCell(1)
                    val o1Cell = row.getCell(2)
                    val inputCell = row.getCell(3)
                    val o2Cell = row.getCell(4)
                    val ankiCell = row.getCell(5)

                    // 跳过空行
                    if (dayCell == null) continue

                    val dayNumber = when {
                        dayCell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                            dayCell.numericCellValue.toInt()
                        dayCell.cellType == org.apache.poi.ss.usermodel.CellType.STRING -> {
                            val text = dayCell.stringCellValue.trim()
                            // 支持 "第6天" / "6" / "Day 6" / "day6" 等多种格式
                            extractNumber(text)
                        }
                        else -> null
                    }

                    if (dayNumber == null) {
                        errors.add("第 ${rowNum + 1} 行：天数格式异常（\"${dayCell}\"），已跳过")
                        continue
                    }

                    val topicRaw = topicCell?.toString()?.trim() ?: ""
                    // 去掉 "1. " 编号前缀，只存名称
                    val topic = topicRaw.replaceFirst(Regex("^\\d+\\.?\\s*"), "")
                    val o1 = o1Cell?.toString()?.trim() ?: ""
                    val input = inputCell?.toString()?.trim() ?: ""
                    val o2 = o2Cell?.toString()?.trim() ?: ""
                    val anki = ankiCell?.toString()?.trim() ?: ""

                    val weekNumber = (dayNumber - 1) / 7 + 1

                    // 同一天 + 同话题 → 合并内容（追加）
                    val existing = topics.indexOfFirst { it.dayNumber == dayNumber && it.topic == topic }
                    if (existing >= 0) {
                        val t = topics[existing]
                        topics[existing] = t.copy(
                            o1Content = t.o1Content + "\n" + o1,
                            inputContent = t.inputContent + "\n" + input,
                            o2Content = t.o2Content + "\n" + o2,
                            ankiContent = t.ankiContent + "\n" + anki
                        )
                    } else {
                        topics.add(
                            DayTopic(
                                dayNumber = dayNumber, topic = topic,
                                o1Content = o1, inputContent = input,
                                o2Content = o2, ankiContent = anki,
                                weekNumber = weekNumber
                            )
                        )
                    }
                } catch (e: Exception) {
                    errors.add("第 ${rowNum + 1} 行：解析失败（${e.message}）")
                }
            }

            workbook.close()
            inputStream.close()

            ParseResult(topics, errors)
        } catch (e: Exception) {
            ParseResult(emptyList(), listOf("文件解析失败：${e.message}"))
        }
    }

    /** 从字符串中提取数字，支持 "第6天" / "Day 6" / "6" 等格式 */
    private fun extractNumber(text: String): Int? {
        val matcher = Pattern.compile("\\d+").matcher(text)
        return if (matcher.find()) {
            matcher.group().toIntOrNull()
        } else {
            null
        }
    }
}
