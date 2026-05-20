package com.oio.english.util

import android.content.Context
import com.oio.english.data.model.DayTopic
import com.oio.english.data.model.LearningRecord
import com.oio.english.data.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object BackupManager {

    /** 导出为 xlsx */
    suspend fun export(context: Context, repository: PlanRepository): File? = withContext(Dispatchers.IO) {
        try {
            val allTopics = repository.getAllTopicsOnce()
            val records = allTopics.mapNotNull { topic ->
                val r = repository.getRecordOnce(topic.id)
                r?.let { topic to it }
            }

            val wb = XSSFWorkbook()
            val sheet = wb.createSheet("OIO Backup")

            // 表头
            val header = listOf("Day", "Topic", "O1", "I(Input)", "O2", "Anki",
                "O1_Done", "I_Done", "O2_Done", "Anki_Done", "Reflection", "Difficulty", "CompletedAt")

            val headerRow = sheet.createRow(0)
            header.forEachIndexed { i, text ->
                val cell = headerRow.createCell(i)
                cell.setCellValue(text)
                cell.cellStyle = wb.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.CORAL.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }
            }

            // 数据行
            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            records.forEachIndexed { idx, (topic, rec) ->
                val row = sheet.createRow(idx + 1)
                row.createCell(0).setCellValue(topic.dayNumber.toDouble())
                row.createCell(1).setCellValue(topic.topic)
                row.createCell(2).setCellValue(topic.o1Content)
                row.createCell(3).setCellValue(topic.inputContent)
                row.createCell(4).setCellValue(topic.o2Content)
                row.createCell(5).setCellValue(topic.ankiContent)
                row.createCell(6).setCellValue(if (rec.o1Done) "Yes" else "")
                row.createCell(7).setCellValue(if (rec.inputDone) "Yes" else "")
                row.createCell(8).setCellValue(if (rec.o2Done) "Yes" else "")
                row.createCell(9).setCellValue(if (rec.ankiDone) "Yes" else "")
                row.createCell(10).setCellValue(rec.reflection)
                row.createCell(11).setCellValue(when (rec.difficulty) { 1 -> "Easy"; 2 -> "Medium"; 3 -> "Hard"; else -> "" })
                row.createCell(12).setCellValue(rec.completedAt?.let { dateFmt.format(Date(it)) } ?: "")
            }

            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = File(context.filesDir, "backup")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "oio_backup_$dateStr.xlsx")
            FileOutputStream(file).use { wb.write(it) }
            wb.close()

            file
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    /** 从 xlsx 解析导出文件（包含计划 + 学习记录，已配对的） */
    data class XlsxBackup(val topics: List<DayTopic>, val records: List<LearningRecord>, val errors: List<String>) {
        /** topic → record 配对列表（record 可为 null） */
        val paired: List<Pair<DayTopic, LearningRecord?>> = topics.mapIndexed { idx, topic ->
            topic to (records.getOrNull(idx))
        }
    }

    suspend fun parseBackup(file: File): XlsxBackup = withContext(Dispatchers.IO) {
        val topics = mutableListOf<DayTopic>()
        val records = mutableListOf<LearningRecord>()
        val errors = mutableListOf<String>()

        try {
            FileInputStream(file).use { fis ->
                val wb = XSSFWorkbook(fis)
                val sheet = wb.getSheetAt(0)

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    try {
                        val dayNum = row.getCell(0)?.numericCellValue?.toInt() ?: continue
                        val topic = row.getCell(1)?.stringCellValue ?: ""
                        val o1 = row.getCell(2)?.stringCellValue ?: ""
                        val input = row.getCell(3)?.stringCellValue ?: ""
                        val o2 = row.getCell(4)?.stringCellValue ?: ""
                        val anki = row.getCell(5)?.stringCellValue ?: ""
                        val week = (dayNum - 1) / 7 + 1

                        val dt = DayTopic(dayNumber = dayNum, topic = topic, o1Content = o1,
                            inputContent = input, o2Content = o2, ankiContent = anki, weekNumber = week)
                        topics.add(dt)

                        // 读取记录数据（如果有）
                        val getCell = { idx: Int -> try { row.getCell(idx)?.stringCellValue ?: "" } catch (_: Exception) { "" } }
                        val o1Done = getCell(6) == "Yes"
                        val iDone = getCell(7) == "Yes"
                        val o2Done = getCell(8) == "Yes"
                        val ankiDone = getCell(9) == "Yes"
                        val reflection = getCell(10)
                        val diffStr = getCell(11)
                        val diff = when (diffStr.lowercase()) { "easy" -> 1; "medium" -> 2; "hard" -> 3; else -> 0 }

                        if (o1Done || iDone || o2Done || ankiDone || reflection.isNotBlank() || diff > 0) {
                            records.add(LearningRecord(
                                dayTopicId = 0, // 临时值，恢复时重新映射
                                o1Done = o1Done, inputDone = iDone, o2Done = o2Done, ankiDone = ankiDone,
                                reflection = reflection, difficulty = diff
                            ))
                        }
                    } catch (e: Exception) {
                        errors.add("Row ${i+1}: ${e.message}")
                    }
                }
                wb.close()
            }
        } catch (e: Exception) {
            errors.add("Failed to read file: ${e.message}")
        }

        XlsxBackup(topics, records, errors)
    }
}
