package com.oio.english.ui.today

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oio.english.data.model.DayTopic
import com.oio.english.data.model.LearningRecord
import com.oio.english.ui.components.CelebrationOverlay
import com.oio.english.ui.components.FlipCard
import com.oio.english.ui.components.RecordPlayButtons
import com.oio.english.ui.components.TtsButton
import com.oio.english.ui.theme.*
import com.oio.english.util.AudioManager
import com.oio.english.util.DayTypeDetector
import com.oio.english.util.QuestionHints
import com.oio.english.util.TopicFormatter

@Composable
fun TodayPage(viewModel: TodayViewModel, onGoToDay: (Int) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val audioManager = remember { AudioManager(context) }

    DisposableEffect(Unit) { onDispose { audioManager.release() } }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Coral) }
        } else if (state.allTopicsByDay.isEmpty()) {
            // 空状态
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📂", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No study plan yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap + to import your .xlsx file", style = MaterialTheme.typography.bodyMedium, color = WarmGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("or create one to start OIO learning!", style = MaterialTheme.typography.bodyMedium, color = WarmGray)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).imePadding()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📋 Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.goToFirstIncomplete() }) { Text("Fst", fontSize = 13.sp) }
                        TextButton(onClick = { viewModel.refresh() }) { Text("Ref", fontSize = 13.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DaySelector(currentDay = state.currentDay, allDays = state.allTopicsByDay.keys.sorted(), onDaySelected = { viewModel.goToDay(it) }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(4.dp))

                val totalStages = state.topics.size * 4
                val completedStages = state.topics.sumOf { topic ->
                    val r = state.records[topic.id]
                    listOf(r?.o1Done == true, r?.inputDone == true, r?.o2Done == true, r?.ankiDone == true).count { it }
                }

                GlobalProgressBar(completed = completedStages, total = totalStages, dayNumber = state.currentDay)
                Spacer(modifier = Modifier.height(12.dp))

                val listState = rememberLazyListState()
                LaunchedEffect(state.currentDay) { listState.animateScrollToItem(0) }
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(state.topics, key = { it.id }) { topic ->
                        TopicStageSection(topic = topic, record = state.records[topic.id], audioManager = audioManager,
                            allTopicsByDay = state.allTopicsByDay, currentDay = state.currentDay,
                            allRecords = state.allRecords,
                            onToggle = { stage, done -> viewModel.toggleStage(topic.id, stage, done) },
                            onDifficultySet = { level -> viewModel.setDifficulty(topic.id, level) },
                            onEditTopic = { viewModel.saveTopicContent(it) })
                    }
                    item {
                        ReflectionCard(reflection = state.currentReflectionInput, onTextChange = { viewModel.updateReflectionInput(it) },
                            onSave = { viewModel.saveReflection() }, hasSaved = state.reflection.isNotEmpty())
                    }
                }
            }
        }
        CelebrationOverlay(show = state.showCelebration, onDismiss = { viewModel.dismissCelebration() })
    }
}

@Composable
private fun DaySelector(currentDay: Int, allDays: List<Int>, onDaySelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(4.dp))
        val scrollState = rememberLazyListState()
        LaunchedEffect(currentDay) { val index = allDays.indexOf(currentDay); if (index >= 0) scrollState.animateScrollToItem(index) }
        LazyRow(state = scrollState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allDays) { day ->
                val isSelected = day == currentDay
                Surface(modifier = Modifier.clip(RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Coral else WarmGrayLight.copy(alpha = 0.4f), onClick = { onDaySelected(day) }) {
                    Text("Day $day", style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) CardWhite else WarmGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun GlobalProgressBar(completed: Int, total: Int, dayNumber: Int) {
    val progress = if (total == 0) 0f else completed.toFloat() / total
    Surface(shape = RoundedCornerShape(10.dp), color = CardWhite, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Day $dayNumber Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("$completed / $total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Coral)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = SuccessGreen, trackColor = WarmGrayLight.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun TopicStageSection(
    topic: DayTopic, record: LearningRecord?, audioManager: AudioManager,
    allTopicsByDay: Map<Int, List<DayTopic>> = emptyMap(), currentDay: Int = 0,
    allRecords: Map<Long, LearningRecord> = emptyMap(),
    onToggle: (StageType, Boolean) -> Unit,
    onDifficultySet: ((Int) -> Unit)? = null,
    onEditTopic: ((DayTopic) -> Unit)? = null
) {
    // 特殊日（休息/复习/模拟）→ 显示简洁卡片
    if (DayTypeDetector.isSpecialDay(topic.topic)) {
        SpecialDayCard(topic = topic, record = record, allTopics = allTopicsByDay, currentDay = currentDay,
            allRecords = allRecords,
            onDone = { onToggle(StageType.O1, record?.o1Done != true) })
        return
    }

    var editingContent by remember { mutableStateOf<String?>(null) }
    var editingStage by remember { mutableStateOf("") }

    // 编辑弹窗
    if (editingContent != null) {
        var editText by remember(editingContent) { mutableStateOf(editingContent ?: "") }
        AlertDialog(
            onDismissRequest = { editingContent = null },
            title = { Text("Edit $editingStage", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = when (editingStage) {
                        "O1" -> topic.copy(o1Content = editText)
                        "I" -> topic.copy(inputContent = editText)
                        "O2" -> topic.copy(o2Content = editText)
                        else -> topic.copy(ankiContent = editText)
                    }
                    onEditTopic?.invoke(updated)
                    editingContent = null
                }) { Text("Save", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { editingContent = null }) { Text("Cancel", color = WarmGray) }
            }
        )
    }

    Surface(shape = RoundedCornerShape(14.dp), color = WarmWhite, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📌", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(TopicFormatter.clean(topic.topic), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))

            FlipCard(stageLabel = "O1 · Try First", topic = QuestionHints.forTopic(topic.topic), content = topic.o1Content,
                accentColor = O1Color, emoji = "", isCompleted = record?.o1Done == true,
                onComplete = { onToggle(StageType.O1, record?.o1Done != true) },
                extraActions = { RecordPlayButtons(topicId = topic.id, stage = "o1", audioManager = audioManager) },
                onEditContent = { editingContent = it; editingStage = "O1" })
            Spacer(modifier = Modifier.height(8.dp))

            FlipCard(stageLabel = "I · Language Blocks", topic = TopicFormatter.clean(topic.topic), content = topic.inputContent,
                accentColor = InputColor, emoji = "", isCompleted = record?.inputDone == true,
                onComplete = { onToggle(StageType.INPUT, record?.inputDone != true) },
                extraActions = { TtsButton(text = topic.inputContent) },
                autoExpand = record?.o1Done == true && record?.inputDone != true,
                onEditContent = { editingContent = it; editingStage = "I" })
            Spacer(modifier = Modifier.height(8.dp))

            FlipCard(stageLabel = "O2 · Combine & Output", topic = TopicFormatter.clean(topic.topic), content = topic.o2Content,
                accentColor = O2Color, emoji = "", isCompleted = record?.o2Done == true,
                onComplete = { onToggle(StageType.O2, record?.o2Done != true) },
                extraActions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TtsButton(text = topic.o2Content)
                        RecordPlayButtons(topicId = topic.id, stage = "o2", audioManager = audioManager)
                    }
                },
                onEditContent = { editingContent = it; editingStage = "O2" })
            Spacer(modifier = Modifier.height(8.dp))

            // Anki 闪卡模式
            AnkiSection(
                content = topic.ankiContent,
                topicName = TopicFormatter.clean(topic.topic),
                isCompleted = record?.ankiDone == true,
                onComplete = { onToggle(StageType.ANKI, record?.ankiDone != true) },
                autoExpand = record?.o2Done == true && record?.ankiDone != true,
                onEdit = { editingContent = it; editingStage = "Anki" }
            )

            // 难度标记（全部完成后显示）
            val allDone = record?.o1Done == true && record?.inputDone == true && record?.o2Done == true && record?.ankiDone == true
            if (allDone) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = WarmGrayLight.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆 How was it?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = WarmDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("(tap to mark)", style = MaterialTheme.typography.labelSmall, color = WarmGray)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "😊 Easy", 2 to "🤔 Medium", 3 to "😰 Hard").forEach { (level, label) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (record.difficulty == level) Coral.copy(alpha = 0.3f) else WarmGrayLight.copy(alpha = 0.2f),
                            onClick = { onDifficultySet?.invoke(level) },
                            border = if (record.difficulty == level) androidx.compose.foundation.BorderStroke(1.dp, Coral) else null
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflectionCard(reflection: String, onTextChange: (String) -> Unit, onSave: () -> Unit, hasSaved: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✏️", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("📝 Daily Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = reflection, onValueChange = onTextChange,
                placeholder = { Text("What did you learn today?") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Coral, unfocusedBorderColor = WarmGrayLight))
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = if (hasSaved) SuccessGreen else Coral),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text = if (hasSaved) "✅ Saved · Tap to Update" else "💾 Save Summary", color = CardWhite)
            }
        }
    }
}

/**
 * 特殊日卡片（休息日/复习日/模拟考等）
 * 复习日显示本周话题，模拟考显示近 4 周话题
 */
/**
 * Anki 闪卡：中文 | 英文 翻转模式
 */
@Composable
private fun AnkiSection(
    content: String,
    topicName: String,
    isCompleted: Boolean,
    onComplete: () -> Unit,
    autoExpand: Boolean = false,
    onEdit: ((String) -> Unit)? = null
) {
    // 按行拆解卡片
    val lines = remember(content) { content.lines().map { it.trim() }.filter { it.isNotEmpty() } }
    val cards = remember(lines) {
        lines.map { line ->
            val parts = line.split(Regex("[|｜]"), limit = 2)
            if (parts.size == 2) CardData(parts[0].trim(), parts[1].trim())
            else CardData("", line)
        }
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }

    // 普通模式（没有 | 分割的内容）
    val isSimple = cards.all { it.front.isEmpty() }

    Surface(shape = RoundedCornerShape(14.dp), color = WarmWhite, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌙", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text("Bedtime Anki", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (isCompleted) {
                    Spacer(Modifier.width(6.dp))
                    Text("✅ Completed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                }
                if (cards.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("${currentIndex + 1}/${cards.size}", style = MaterialTheme.typography.labelMedium, color = WarmGray)
                }
                Spacer(Modifier.weight(1f))
                onEdit?.let {
                    TextButton(onClick = { onEdit(content) }) { Text("✏️", fontSize = 13.sp, color = WarmGray) }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isSimple) {
                // 普通显示
                Text(content, style = MaterialTheme.typography.bodyLarge, color = WarmDark)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onComplete, colors = ButtonDefaults.buttonColors(containerColor = if (isCompleted) SuccessGreen else AnkiColor), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(if (isCompleted) "✅ Done" else "✓ Mark Done", color = CardWhite)
                }
            } else {
                // 闪卡模式
                val card = cards.getOrNull(currentIndex) ?: return@Column

                // 卡片正面/背面
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardWhite,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).clip(RoundedCornerShape(12.dp)).clickable { flipped = !flipped }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (flipped) card.back else card.front,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = WarmDark,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (!flipped) "👆 Tap to reveal" else "👆 Tap to hide",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmGray
                            )
                        }
                    }
                }

                // 翻转后显示操作按钮
                if (flipped) {
                    Spacer(Modifier.height(12.dp))
                    val isLast = currentIndex >= cards.size - 1
                    if (isLast && !isCompleted) {
                        // 最后一张未完成：复习或标记完成
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Surface(shape = RoundedCornerShape(10.dp), color = LavenderLight.copy(alpha = 0.3f),
                                onClick = { flipped = false; currentIndex = 0 }, modifier = Modifier.weight(1f)
                            ) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text("🔄 Review Again", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Lavender)
                            } }
                            Surface(shape = RoundedCornerShape(10.dp), color = SuccessGreenLight.copy(alpha = 0.4f),
                                onClick = { onComplete() }, modifier = Modifier.weight(1f)
                            ) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text("✅ Mark Complete", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SuccessGreen)
                            } }
                        }
                    } else if (!isCompleted) {
                        Button(onClick = { flipped = false; currentIndex++ },
                            colors = ButtonDefaults.buttonColors(containerColor = AnkiColor), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                        ) { Text("Next →", color = CardWhite) }
                    } else {
                        // 已完成：只显示复习按钮
                        Surface(shape = RoundedCornerShape(10.dp), color = LavenderLight.copy(alpha = 0.3f),
                            onClick = { flipped = false; currentIndex = 0 }, modifier = Modifier.fillMaxWidth()
                        ) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                            Text("🔄 Review Again", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Lavender)
                        } }
                    }
                }
            }
        }
    }
}

private data class CardData(val front: String, val back: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecialDayCard(
    topic: DayTopic, record: LearningRecord?,
    allTopics: Map<Int, List<DayTopic>> = emptyMap(), currentDay: Int = 0,
    allRecords: Map<Long, LearningRecord> = emptyMap(),
    onDone: () -> Unit
) {
    val dayType = DayTypeDetector.getType(topic.topic)
    val isMock = dayType == DayTypeDetector.DayType.MOCK
    val showTopics = dayType == DayTypeDetector.DayType.REVIEW || dayType == DayTypeDetector.DayType.MOCK

    // 搜集要显示的话题（过滤掉特殊日）
    val relatedTopics = remember(allTopics, currentDay) {
        val sortedDays = allTopics.keys.sorted().filter { it < currentDay }
        val raw = if (isMock) {
            sortedDays.takeLast(28)
        } else {
            val weekStart = ((currentDay - 1) / 7) * 7 + 1
            sortedDays.filter { it >= weekStart }
        }
        raw.map { day -> day to (allTopics[day] ?: emptyList()) }
            .filter { (_, topics) -> topics.any { !DayTypeDetector.isSpecialDay(it.topic) } }
            .map { (day, topics) -> day to topics.filter { !DayTypeDetector.isSpecialDay(it.topic) } }
    }

    val emoji = when (dayType) {
        DayTypeDetector.DayType.MOCK -> "✏️"
        DayTypeDetector.DayType.REVIEW -> "📖"
        DayTypeDetector.DayType.REST -> "😌"
        DayTypeDetector.DayType.SUMMARY -> "📝"
        else -> "📌"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (record?.o1Done == true) SuccessGreenLight else CardWhite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(TopicFormatter.clean(topic.topic), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (topic.o1Content.isNotBlank()) {
                        Text(
                            text = topic.o1Content.take(80) + if (topic.o1Content.length > 80) "…" else "",
                            style = MaterialTheme.typography.bodySmall, color = WarmGray, maxLines = 2
                        )
                    }
                }
            }

            // 显示相关话题（仅复习日/模拟考）
            if (showTopics && relatedTopics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = WarmGrayLight.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("📋 Topics to cover:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                relatedTopics.forEach { (day, topics) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("D$day", style = MaterialTheme.typography.labelSmall, color = WarmGray,
                            modifier = Modifier.width(28.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            topics.forEach { t ->
                                val diff = allRecords[t.id]?.difficulty ?: 0
                                val chipColor = when (diff) {
                                    1 -> SuccessGreenLight.copy(alpha = 0.5f)
                                    2 -> PeachLight.copy(alpha = 0.5f)
                                    3 -> ErrorRed.copy(alpha = 0.35f)
                                    else -> WarmGrayLight.copy(alpha = 0.25f)
                                }
                                val chipBorder = if (diff in 1..3) androidx.compose.foundation.BorderStroke(
                                    1.dp, when (diff) { 1 -> SuccessGreen; 2 -> AlertOrange; else -> ErrorRed }
                                ) else null
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipColor,
                                    border = chipBorder
                                ) {
                                    Text(
                                        text = TopicFormatter.clean(t.topic),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = if (record?.o1Done == true) SuccessGreen else Coral),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (record?.o1Done == true) "✅ Done" else "✓ Mark Done",
                    color = CardWhite
                )
            }
        }
    }
}
