package com.oio.english.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oio.english.data.model.DayTopic
import com.oio.english.data.repository.PlanRepository
import com.oio.english.util.WeekHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlanUiState(
    val weeks: List<Int> = emptyList(),
    val currentWeek: Int = 1,
    val topicsByWeek: Map<Int, List<DayTopic>> = emptyMap(),
    /** dayNumber → 该天所有 topic 是否全部完成 */
    val dayCompletion: Map<Int, Boolean> = emptyMap(),
    val isLoading: Boolean = true
)

class PlanViewModel(
    private val repository: PlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        // 监听话题变化 + 记录变化 → 自动刷新
        viewModelScope.launch {
            combine(
                repository.getAllTopics(),
                repository.observeRecordChanges()
            ) { allTopics, _ -> allTopics }
            .collect { allTopics ->
                val byDay = allTopics.groupBy { it.dayNumber }
                val dayNumbers = byDay.keys.sorted()
                val weeks = WeekHelper.computeWeeks(dayNumbers)
                val byWeek = allTopics.groupBy { it.weekNumber }

                updateDayCompletion(dayNumbers)

                // 默认显示第一个未完全完成的周
                val currentWeek = findFirstIncompleteWeek(weeks, _uiState.value.dayCompletion)
                _uiState.update {
                    it.copy(
                        weeks = weeks,
                        currentWeek = currentWeek,
                        topicsByWeek = byWeek,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refresh() {
        // combine 会自动触发刷新，此方法保留用于手动触发
    }

    /** 跳转到第一个未完全完成的周 */
    fun goToFirstIncomplete() {
        val week = findFirstIncompleteWeek(_uiState.value.weeks, _uiState.value.dayCompletion)
        goToWeek(week)
    }

    fun goToWeek(week: Int) {
        _uiState.update { it.copy(currentWeek = week) }
    }

    /** 找到第一个还有天数未完成的周 */
    private fun findFirstIncompleteWeek(weeks: List<Int>, dayCompletion: Map<Int, Boolean>): Int {
        if (weeks.isEmpty()) return 1
        val dayRangeMap = weeks.associateWith { w -> (1..7).map { (w - 1) * 7 + it } }
        for (week in weeks) {
            val daysInWeek = dayRangeMap[week] ?: continue
            val allDone = daysInWeek
                .filter { dayCompletion.containsKey(it) }
                .all { dayCompletion[it] == true }
            if (!allDone) return week
        }
        return weeks.first()
    }

    private suspend fun updateDayCompletion(days: List<Int>) {
        val completion = mutableMapOf<Int, Boolean>()
        for (day in days) {
            val records = repository.getRecordsByDayOnce(day)
            val topics = repository.getTopicsByDayOnce(day)
            val allDone = topics.isNotEmpty() && records.size >= topics.size &&
                    records.all { it.o1Done && it.inputDone && it.o2Done && it.ankiDone }
            completion[day] = allDone
        }
        _uiState.update { it.copy(dayCompletion = completion) }
    }

    companion object {
        fun factory(repo: PlanRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlanViewModel(repo) as T
        }
    }
}
