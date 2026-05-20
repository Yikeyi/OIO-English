package com.oio.english.ui.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oio.english.ui.theme.*
import com.oio.english.util.AudioManager
import com.oio.english.util.BackupManager
import com.oio.english.util.TopicFormatter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ImportResult(val success: Boolean, val count: Int = 0)

private data class AudioFileInfo(val file: java.io.File, val topicId: Long, val stage: String, val day: Int = 0, val topic: String = "", val size: Long = 0)

private fun loadAudioFiles(context: android.content.Context): List<AudioFileInfo> {
    val dir = java.io.File(context.filesDir, "audio")
    if (!dir.exists()) return emptyList()
    val list = dir.listFiles()?.filter { it.name.endsWith(".m4a") }?.mapNotNull { f ->
        val name = f.nameWithoutExtension
        val parts = name.split("_")
        if (parts.size >= 2) {
            val topicId = parts[0].toLongOrNull() ?: return@mapNotNull null
            val stage = parts[1]
            AudioFileInfo(f, topicId, stage, 0, "", f.length())
        } else null
    } ?: emptyList()
    return list.sortedByDescending { it.file.lastModified() }
}

private fun formatFileSize(bytes: Long): String {
    return if (bytes < 1024) "${bytes}B"
    else if (bytes < 1024 * 1024) "${bytes / 1024}KB"
    else "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
}

@Composable
fun ArchivePage(viewModel: ArchiveViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImportResult by remember { mutableStateOf<ImportResult?>(null) }
    var showAudioManager by remember { mutableStateOf(false) }
    var audioFiles by remember { mutableStateOf<List<AudioFileInfo>>(emptyList()) }

    // 录音管理
    if (showAudioManager) {
        var recDialogOpen by remember { mutableStateOf(true) }
        if (recDialogOpen) {
            val audioManager = remember { AudioManager(context) }
            LaunchedEffect(Unit) {
                audioFiles = loadAudioFiles(context)
            }
            AlertDialog(
                onDismissRequest = { showAudioManager = false },
                title = { Text("🎤 Recordings", fontWeight = FontWeight.Bold) },
                text = {
                    if (audioFiles.isEmpty()) {
                        Text("No recordings found", color = WarmGray)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                            items(audioFiles) { af ->
                                Surface(shape = RoundedCornerShape(8.dp), color = CardWhite) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("D${af.day} · ${af.topic}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            Text(af.stage, style = MaterialTheme.typography.labelSmall, color = WarmGray)
                                            Text(formatFileSize(af.size), style = MaterialTheme.typography.labelSmall, color = WarmGray)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = MintLight.copy(alpha = 0.4f), onClick = {
                                                audioManager.playRecording(af.topicId, af.stage, {}, {})
                                            }) { Text("▶️", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                                            Surface(shape = RoundedCornerShape(6.dp), color = ErrorRed.copy(alpha = 0.2f), onClick = {
                                                af.file.delete()
                                                scope.launch { audioFiles = loadAudioFiles(context) }
                                            }) { Text("🗑️", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAudioManager = false; audioManager.release() }) { Text("Done", color = Coral) } }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val file = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val tmp = java.io.File(context.cacheDir, "import_backup.xlsx")
                tmp.outputStream().use { output -> input.copyTo(output) }
                input.close()
                BackupManager.parseBackup(tmp)
            }
            if (file != null) {
                viewModel.importBackup(file.topics, file.records)
                showImportResult = ImportResult(true, file.topics.size)
            } else showImportResult = ImportResult(false)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                val file = BackupManager.export(context, viewModel.repository)
                if (file != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    file.delete()
                }
            }
        }
    }

    showImportResult?.let { r ->
        AlertDialog(
            onDismissRequest = { showImportResult = null },
            title = { Text(if (r.success) "✅ Restored" else "❌ Restore Failed", fontWeight = FontWeight.Bold) },
            text = { Text(if (r.success) "Restored ${r.count} topics" else "Invalid file format") },
            confirmButton = { TextButton(onClick = { showImportResult = null }) { Text("OK", color = Coral) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📚 Archive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Text("Imp", fontSize = 13.sp) }
                TextButton(onClick = { exportLauncher.launch("oio_backup.xlsx") }) { Text("Exp", fontSize = 13.sp) }
                TextButton(onClick = { showAudioManager = true }) { Text("Aud", fontSize = 13.sp) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Coral) }
        } else {
            StatsCard(stats = state.stats)
            Spacer(modifier = Modifier.height(16.dp))
            Text("📋 Completed Days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (state.completedDays.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No completed days yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Finish a day in Today to see it here!", style = MaterialTheme.typography.bodyMedium, color = WarmGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Your learning journey starts now 💪", style = MaterialTheme.typography.bodyMedium, color = WarmGray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(state.completedDays) { day ->
                        CompletedDayCard(day, onReflectionChanged = { viewModel.saveReflection(day.dayNumber, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: StatsData) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("🔥", "${stats.currentStreak}", "Streak")
            StatItem("✅", "${stats.totalDaysCompleted}", "Done Days")
            StatItem("📝", "${stats.totalTopicsCompleted}", "Topics")
        }
    }
}

@Composable
private fun StatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Coral)
        Text(label, style = MaterialTheme.typography.labelSmall, color = WarmGray)
    }
}

@Composable
private fun CompletedDayCard(day: CompletedDay, onReflectionChanged: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var reflection by remember(day.reflection) { mutableStateOf(day.reflection) }

    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✅", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Day ${day.dayNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("${day.records.size}/${day.topics.size} topics", style = MaterialTheme.typography.labelSmall, color = WarmGray)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                day.topics.forEach { topic ->
                    Surface(shape = RoundedCornerShape(6.dp), color = CoralLight.copy(alpha = 0.3f)) {
                        Text(TopicFormatter.clean(topic.topic), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = WarmGrayLight.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("✏️ Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = reflection, onValueChange = { reflection = it },
                        placeholder = { Text("What did you learn today?") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Coral, unfocusedBorderColor = WarmGrayLight))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onReflectionChanged(reflection) }, colors = ButtonDefaults.buttonColors(containerColor = Coral), shape = RoundedCornerShape(8.dp)) { Text("💾 Save Summary") }
                }
            }
        }
    }
}
