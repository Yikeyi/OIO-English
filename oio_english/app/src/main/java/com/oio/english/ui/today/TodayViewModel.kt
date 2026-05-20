package com.oio.english.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oio.english.data.model.DayTopic
import com.oio.english.data.model.LearningRecord
import com.oio.english.data.repository.PlanRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TodayUiState(
    val currentDay: Int = 1,
    val topics: List<DayTopic> = emptyList(),
    val records: Map<Long, LearningRecord> = emptyMap(),
    val allTopicsByDay: Map<Int, List<DayTopic>> = emptyMap(),
    val isLoading: Boolean = true,
    val showCelebration: Boolean = false,
    val showReflectionCard: Boolean = false,
    val reflection: String = "",
    val currentReflectionInput: String = "",
    val streak: Int = 0,
    val todayWords: Int = 0,
    val allRecords: Map<Long, LearningRecord> = emptyMap()
)

class TodayViewModel(
    private val repository: PlanRepository,
    private val appContext: android.content.Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    /** 跳转到指定天时设为 true，阻止 refresh() 的自动跳转 */
    var skipAutoJump = false
        private set

    init {
        var isFirstLoad = true
        viewModelScope.launch {
            combine(
                repository.getAllTopics(),
                repository.observeRecordChanges()
            ) { topics, _ -> topics }
            .collect { allTopics ->
                val byDay = allTopics.groupBy { it.dayNumber }

                if (isFirstLoad) {
                    // 首次加载 → 跳转到第一个未完成的天
                    isFirstLoad = false
                    val targetDay = findFirstIncompleteDay(byDay)
                    val topics = byDay[targetDay] ?: emptyList()
                    val savedReflection = getSavedReflection(targetDay)
                    _uiState.update {
                        it.copy(
                            currentDay = targetDay,
                            topics = topics,
                            allTopicsByDay = byDay,
                            isLoading = false,
                            reflection = savedReflection,
                            currentReflectionInput = savedReflection,
                            showReflectionCard = savedReflection.isNotEmpty()
                        )
                    }
                    loadAllRecords()
                    loadRecordsForDay(targetDay)
                } else {
                    // 数据变化 → 只更新当前天的记录，不跳转
                    val currentDay = _uiState.value.currentDay
                    val currentTopics = byDay[currentDay] ?: emptyList()
                    _uiState.update {
                        it.copy(
                            topics = currentTopics,
                            allTopicsByDay = byDay
                        )
                    }
                    loadAllRecords()
                    loadRecordsForDay(currentDay)
                }
            }
        }
    }

    /** 找到第一个未完全完成的天 */
    /** 保存当前学习进度 */
    fun saveProgress(context: android.content.Context) {
        val day = _uiState.value.currentDay
        context.getSharedPreferences("progress", android.content.Context.MODE_PRIVATE)
            .edit().putInt("current_day", day).apply()
    }

    /** 恢复上次学习进度 */
    fun restoreProgress(context: android.content.Context) {
        val savedDay = context.getSharedPreferences("progress", android.content.Context.MODE_PRIVATE)
            .getInt("current_day", 0)
        if (savedDay > 0 && _uiState.value.allTopicsByDay.containsKey(savedDay)) {
            goToDay(savedDay)
        }
    }

    private suspend fun findFirstIncompleteDay(
        byDay: Map<Int, List<DayTopic>>
    ): Int {
        val sortedDays = byDay.keys.sorted()
        for (day in sortedDays) {
            val records = repository.getRecordsByDayOnce(day)
            val topics = byDay[day] ?: emptyList()
            val allDone = topics.isNotEmpty() && records.size >= topics.size &&
                    records.all { it.o1Done && it.inputDone && it.o2Done && it.ankiDone }
            if (!allDone) return day
        }
        return sortedDays.lastOrNull() ?: 1
    }

    /** 跳转到第一个未完成的天 */
    fun goToFirstIncomplete() {
        viewModelScope.launch {
            val byDay = _uiState.value.allTopicsByDay
            val firstIncomplete = findFirstIncompleteDay(byDay)
            goToDay(firstIncomplete)
        }
    }

    /** 手动刷新 → 跳转到第一个未完成的天 */
    fun refresh() {
        if (skipAutoJump) { skipAutoJump = false; return }
        viewModelScope.launch {
            val byDay = _uiState.value.allTopicsByDay
            val targetDay = findFirstIncompleteDay(byDay)
            goToDay(targetDay)
        }
    }

    fun goToDay(day: Int) {
        skipAutoJump = true
        val topics = _uiState.value.allTopicsByDay[day] ?: return
        _uiState.update { it.copy(currentDay = day, topics = topics, showCelebration = false) }
        loadRecordsForDay(day)
        viewModelScope.launch {
            val reflection = getSavedReflection(day)
            _uiState.update { it.copy(
                reflection = reflection,
                currentReflectionInput = reflection,
                showReflectionCard = reflection.isNotEmpty()
            ) }
        }
    }

    fun toggleStage(dayTopicId: Long, stage: StageType, done: Boolean) {
        viewModelScope.launch {
            val existing = repository.getRecordOnce(dayTopicId)
            if (existing != null) {
                // 检查是否是特殊日（休息/复习/模拟）
                val isSpecial = _uiState.value.topics.any { t ->
                    t.id == dayTopicId && com.oio.english.util.DayTypeDetector.isSpecialDay(t.topic)
                }

                val updated = if (isSpecial && stage == StageType.O1 && done) {
                    // 特殊日：勾选 O1 自动完成全部环节
                    existing.copy(o1Done = true, inputDone = true, o2Done = true, ankiDone = true,
                        completedAt = System.currentTimeMillis())
                } else {
                    when (stage) {
                        StageType.O1 -> existing.copy(o1Done = done)
                        StageType.INPUT -> existing.copy(inputDone = done)
                        StageType.O2 -> existing.copy(o2Done = done)
                        StageType.ANKI -> existing.copy(ankiDone = done)
                    }.let { rec ->
                        val allDone = rec.o1Done && rec.inputDone && rec.o2Done && rec.ankiDone
                        if (allDone && rec.completedAt == null) rec.copy(completedAt = System.currentTimeMillis())
                        else rec
                    }
                }
                repository.upsertRecord(updated)
                if (updated.o1Done && updated.inputDone && updated.o2Done && updated.ankiDone) {
                    checkAllTopicsComplete()
                }
            }
        }
    }

    fun updateReflectionInput(text: String) {
        _uiState.update { it.copy(currentReflectionInput = text) }
    }

    fun saveReflection() {
        viewModelScope.launch {
            val day = _uiState.value.currentDay
            val text = _uiState.value.currentReflectionInput
            val records = repository.getRecordsByDayOnce(day)
            for (record in records) {
                repository.upsertRecord(record.copy(reflection = text))
            }
            _uiState.update { it.copy(reflection = text, showReflectionCard = true) }
        }
    }

    /** 保存编辑后的内容 */
    fun saveTopicContent(updatedTopic: DayTopic) {
        viewModelScope.launch {
            repository.updateTopic(updatedTopic)
            // combine 流会自动触发刷新
        }
    }

    /** 设置话题难度标记 */
    fun setDifficulty(dayTopicId: Long, level: Int) {
        viewModelScope.launch {
            repository.getRecordOnce(dayTopicId)?.let { record ->
                repository.upsertRecord(record.copy(difficulty = level))
                // 立即刷新 allRecords
                loadAllRecords()
            }
        }
    }

    fun dismissCelebration() {
        _uiState.update { it.copy(showCelebration = false) }
    }

    private suspend fun getSavedReflection(day: Int): String {
        val records = repository.getRecordsByDayOnce(day)
        return records.firstOrNull()?.reflection ?: ""
    }

    private fun loadRecordsForDay(day: Int) {
        viewModelScope.launch {
            val records = repository.getRecordsByDayOnce(day)
            val map = records.associateBy { it.dayTopicId }
            val streak = computeStreak()
            val words = computeTodayWords(day, map)
            _uiState.update { it.copy(records = map, streak = streak, todayWords = words) }
        }
    }

    /** 计算连续学习天数 */
    private suspend fun computeStreak(): Int {
        val allDays = repository.getAllDayNumbersOnce().sorted()
        if (allDays.isEmpty()) return 0
        // 从最新一天往前数连续完成的天数
        var streak = 0
        for (day in allDays.reversed()) {
            val records = repository.getRecordsByDayOnce(day)
            val topics = _uiState.value.allTopicsByDay[day] ?: emptyList()
            val allDone = topics.isNotEmpty() && records.size >= topics.size &&
                    records.all { it.o1Done && it.inputDone && it.o2Done && it.ankiDone }
            if (allDone) streak++ else break
        }
        return streak
    }

    /** 计算今天说了多少个词（O1+O2 内容的总词数） */
    private fun computeTodayWords(day: Int, records: Map<Long, LearningRecord>): Int {
        val topics = _uiState.value.allTopicsByDay[day] ?: return 0
        return topics.sumOf { topic ->
            val r = records[topic.id]
            val o1Words = if (r?.o1Done == true) topic.o1Content.split(Regex("\\s+")).count { it.isNotBlank() } else 0
            val o2Words = if (r?.o2Done == true) topic.o2Content.split(Regex("\\s+")).count { it.isNotBlank() } else 0
            o1Words + o2Words
        }
    }

    /** 加载所有话题的学习记录（供复习日难度显示） */
    private suspend fun loadAllRecords() {
        val allTopics = _uiState.value.allTopicsByDay.values.flatten()
        val allRecs = mutableMapOf<Long, LearningRecord>()
        for (topic in allTopics) {
            val rec = repository.getRecordOnce(topic.id) ?: continue
            allRecs[topic.id] = rec
        }
        _uiState.update { it.copy(allRecords = allRecs) }
    }

    private fun checkAllTopicsComplete() {
        viewModelScope.launch {
            val day = _uiState.value.currentDay
            val records = repository.getRecordsByDayOnce(day)
            val topicCount = _uiState.value.topics.size
            if (records.size >= topicCount && records.all {
                    it.o1Done && it.inputDone && it.o2Done && it.ankiDone
                }) {
                _uiState.update { it.copy(showCelebration = true, showReflectionCard = true) }
            }
        }
    }

    /** 自动备份到用户可见的文档目录 */
    companion object {
        fun factory(repo: PlanRepository, appCtx: android.content.Context? = null): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayViewModel(repo, appCtx?.applicationContext) as T
        }
    }
}

enum class StageType { O1, INPUT, O2, ANKI }
