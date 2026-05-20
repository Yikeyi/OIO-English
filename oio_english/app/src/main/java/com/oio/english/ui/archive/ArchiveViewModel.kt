package com.oio.english.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oio.english.data.model.DayTopic
import com.oio.english.data.model.LearningRecord
import com.oio.english.data.repository.PlanRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ArchiveUiState(
    val completedDays: List<CompletedDay> = emptyList(),
    val stats: StatsData = StatsData(),
    val isLoading: Boolean = true
)

data class CompletedDay(
    val dayNumber: Int,
    val topics: List<DayTopic>,
    val records: List<LearningRecord>,
    val reflection: String
)

data class StatsData(
    val totalDaysCompleted: Int = 0,
    val totalTopicsCompleted: Int = 0,
    val currentStreak: Int = 0,
    val weeklyCompletions: Map<Int, Int> = emptyMap()
)

class ArchiveViewModel(
    val repository: PlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    init {
        // 同时监听话题和记录变化
        viewModelScope.launch {
            combine(
                repository.getAllTopics(),
                repository.observeRecordChanges()
            ) { topics, _ -> topics }
            .collect { allTopics -> loadArchiveData(allTopics) }
        }
    }

    fun refresh() {
        // combine 自动触发
    }

    private suspend fun loadArchiveData(allTopics: List<DayTopic>) {
        val byDay = allTopics.groupBy { it.dayNumber }
        val completedDays = mutableListOf<CompletedDay>()

        for ((day, topics) in byDay) {
            val records = repository.getRecordsByDayOnce(day)
            val isFullyDone = topics.isNotEmpty() && records.size >= topics.size &&
                    records.all { it.o1Done && it.inputDone && it.o2Done && it.ankiDone }
            if (isFullyDone) {
                // 多个话题共享同一反思，只取第一个非空
                val reflection = records.firstOrNull { it.reflection.isNotBlank() }?.reflection ?: ""

                completedDays.add(
                    CompletedDay(
                        dayNumber = day,
                        topics = topics,
                        records = records,
                        reflection = reflection
                    )
                )
            }
        }

        completedDays.sortByDescending { it.dayNumber }

        val totalCompleted = completedDays.count { day ->
            day.records.all { it.o1Done && it.inputDone && it.o2Done && it.ankiDone }
        }
        val totalTopics = completedDays.sumOf { day ->
            day.records.count { it.o1Done && it.inputDone && it.o2Done && it.ankiDone }
        }
        val streak = computeStreak(completedDays)
        val weeklyComp = completedDays
            .filter { day -> day.records.all { it.o1Done && it.inputDone && it.o2Done && it.ankiDone } }
            .groupBy { (it.dayNumber - 1) / 7 + 1 }
            .mapValues { it.value.size }

        _uiState.update {
            it.copy(
                completedDays = completedDays,
                stats = StatsData(
                    totalDaysCompleted = totalCompleted,
                    totalTopicsCompleted = totalTopics,
                    currentStreak = streak,
                    weeklyCompletions = weeklyComp
                ),
                isLoading = false
            )
        }
    }

    /** 从备份文件恢复数据 */
    fun importBackup(topics: List<com.oio.english.data.model.DayTopic>, records: List<com.oio.english.data.model.LearningRecord>) {
        val paired = topics.mapIndexed { idx, t -> t to records.getOrNull(idx) }
        viewModelScope.launch {
            repository.restoreFromBackup(paired)
        }
    }

    fun saveReflection(dayNumber: Int, reflection: String) {
        viewModelScope.launch {
            val records = repository.getRecordsByDayOnce(dayNumber)
            for (record in records) {
                repository.upsertRecord(record.copy(reflection = reflection))
            }
        }
    }

    private fun computeStreak(completedDays: List<CompletedDay>): Int {
        val doneDays = completedDays
            .filter { day -> day.records.all { it.o1Done && it.inputDone && it.o2Done && it.ankiDone } }
            .map { it.dayNumber }
            .sortedDescending()

        if (doneDays.isEmpty()) return 0

        var streak = 1
        for (i in 1 until doneDays.size) {
            if (doneDays[i - 1] - doneDays[i] == 1) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    companion object {
        fun factory(repo: PlanRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ArchiveViewModel(repo) as T
        }
    }
}
