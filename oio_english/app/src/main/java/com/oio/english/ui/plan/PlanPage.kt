package com.oio.english.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oio.english.data.model.DayTopic
import com.oio.english.ui.theme.*
import com.oio.english.util.DayTypeDetector
import com.oio.english.util.TopicFormatter
import com.oio.english.util.WeekHelper

@Composable
fun PlanPage(viewModel: PlanViewModel, onGoToDay: (Int) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📅 Plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewModel.goToFirstIncomplete() }) { Text("Fst", fontSize = 13.sp) }
                TextButton(onClick = { viewModel.refresh() }) { Text("Ref", fontSize = 13.sp) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Coral) }
        } else if (state.weeks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📅", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No study plan yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap + to import your .xlsx", style = MaterialTheme.typography.bodyMedium, color = WarmGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("and plan your OIO week!", style = MaterialTheme.typography.bodyMedium, color = WarmGray)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekSelector(weeks = state.weeks, currentWeek = state.currentWeek, onWeekSelected = { viewModel.goToWeek(it) }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            val weekTopics = state.topicsByWeek[state.currentWeek] ?: emptyList()
            val weekDays = weekTopics.groupBy { it.dayNumber }
            val totalDays = weekDays.size
            val completedDays = weekDays.keys.count { day -> state.dayCompletion[day] == true }

            Surface(shape = RoundedCornerShape(10.dp), color = CardWhite, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(WeekHelper.weekLabel(state.currentWeek), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text("$completedDays / $totalDays days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Lavender)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (totalDays == 0) 0f else completedDays.toFloat() / totalDays },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = SuccessGreen, trackColor = WarmGrayLight.copy(alpha = 0.3f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val currentWeekFirstDay = (state.currentWeek - 1) * 7 + 1

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (i in 0 until 7) {
                    val dayNumber = currentWeekFirstDay + i
                    val dayTopics = weekTopics.filter { it.dayNumber == dayNumber }
                    if (dayTopics.isNotEmpty()) {
                        DayColumn(dayLabel = dayLabels.getOrElse(i) { "" }, dayNumber = dayNumber, topics = dayTopics, isComplete = state.dayCompletion[dayNumber] == true, onGoToDay = onGoToDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSelector(weeks: List<Int>, currentWeek: Int, onWeekSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberLazyListState()
    LaunchedEffect(currentWeek) {
        val index = weeks.indexOf(currentWeek)
        if (index >= 0) scrollState.animateScrollToItem(index)
    }
    LazyRow(state = scrollState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(weeks) { week ->
            val isSelected = week == currentWeek
            Surface(modifier = Modifier.clip(RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Lavender else WarmGrayLight.copy(alpha = 0.4f), onClick = { onWeekSelected(week) }) {
                Text(WeekHelper.weekLabel(week), style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) CardWhite else WarmGray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun DayColumn(dayLabel: String, dayNumber: Int, topics: List<DayTopic>, isComplete: Boolean, onGoToDay: (Int) -> Unit = {}) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = CardWhite, shadowElevation = 1.dp) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = WarmDark)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Day $dayNumber", style = MaterialTheme.typography.labelLarge, color = WarmGray)
                if (isComplete) { Spacer(modifier = Modifier.width(6.dp)); Text("✅", fontSize = 14.sp) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onGoToDay(dayNumber) }) {
                    Text("→", fontSize = 14.sp, color = Coral, fontWeight = FontWeight.Bold)
                }
                Text("${topics.size} topics", style = MaterialTheme.typography.labelSmall, color = WarmGray)
            }
            Spacer(modifier = Modifier.height(6.dp))
            topics.forEach { topic ->
                var expanded by remember { mutableStateOf(false) }

                val isSpecial = DayTypeDetector.isSpecialDay(topic.topic)
                val isClickable = isComplete && !isSpecial
                val specialEmoji = when {
                    isSpecial -> when (DayTypeDetector.getType(topic.topic)) {
                        DayTypeDetector.DayType.REST -> "😌"
                        DayTypeDetector.DayType.REVIEW -> "📖"
                        DayTypeDetector.DayType.MOCK -> "✏️"
                        DayTypeDetector.DayType.SUMMARY -> "📝"
                        else -> "📌"
                    }
                    else -> null
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isComplete) SuccessGreen.copy(alpha = 0.12f) else O1Color.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clickable(enabled = isClickable) { expanded = !expanded }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(specialEmoji ?: "🅾️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(TopicFormatter.clean(topic.topic), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = WarmDark)
                        if (isComplete && !isSpecial) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text("👆", fontSize = 12.sp, color = WarmGray)
                        }
                    }
                }

                // 特殊日不展开 O2
                if (!isSpecial) {
                    AnimatedVisibility(visible = isComplete && expanded) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("O2: ${topic.o2Content}", style = MaterialTheme.typography.bodySmall, color = WarmGray)
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}
