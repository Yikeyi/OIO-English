package com.oio.english

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oio.english.data.database.OioDatabase
import com.oio.english.data.repository.PlanRepository
import com.oio.english.ui.archive.ArchivePage
import com.oio.english.ui.archive.ArchiveViewModel
import com.oio.english.ui.plan.PlanPage
import com.oio.english.ui.plan.PlanViewModel
import com.oio.english.ui.theme.*
import com.oio.english.ui.today.TodayPage
import com.oio.english.ui.today.TodayViewModel
import com.oio.english.util.ExcelParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val database by lazy { OioDatabase.getInstance(this) }
    private val repository by lazy {
        PlanRepository(database.dayTopicDao(), database.learningRecordDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 全局未捕获异常处理（防止闪退）
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            // 保存错误日志，下次启动时显示
            getSharedPreferences("crash", MODE_PRIVATE).edit()
                .putString("last_error", "${e.message}")
                .apply()
        }
        // 检查上次是否有崩溃
        val lastError = getSharedPreferences("crash", MODE_PRIVATE).getString("last_error", null)
        if (lastError != null) {
            getSharedPreferences("crash", MODE_PRIVATE).edit().remove("last_error").apply()
        }
        super.onCreate(savedInstanceState)
        setContent {
            OioEnglishTheme {
                MainScreen(repository = repository)
            }
        }
    }
}

@Composable
private fun MainScreen(repository: PlanRepository) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importFeedback by remember { mutableStateOf<ImportFeedback?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ViewModels
    val todayViewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.factory(repository, context.applicationContext)
    )
    val planViewModel: PlanViewModel = viewModel(
        factory = PlanViewModel.factory(repository)
    )
    val archiveViewModel: ArchiveViewModel = viewModel(
        factory = ArchiveViewModel.factory(repository)
    )

    // 导入预览状态
    var showImportPreview by remember { mutableStateOf(false) }
    var parsedPreview by remember { mutableStateOf<ParsePreview?>(null) }

    // 导入进度
    var importProgress by remember { mutableFloatStateOf(0f) }
    var importStatusText by remember { mutableStateOf("") }

    // 执行导入（含进度）
    suspend fun doImport(result: ExcelParser.ParseResult) {
        importProgress = 0f
        importStatusText = "Writing to database…"
        importFeedback = ImportFeedback(loading = true)
        withContext(Dispatchers.IO) {
            repository.importTopics(result.topics) { pct ->
                importProgress = pct / 100f
                importStatusText = when {
                    pct < 30 -> "Cleaning old data…"
                    pct < 50 -> "Saving new plan…"
                    else -> "Creating records…"
                }
            }
        }
        importFeedback = ImportFeedback(loading = false, count = result.topics.size, errors = result.errors)
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                importFeedback = ImportFeedback(loading = true)
                importStatusText = "Parsing file…"

                val result = withContext(Dispatchers.IO) {
                    ExcelParser.parse(context, uri)
                }

                if (result.topics.isNotEmpty()) {
                    // 显示预览 → 用户确认后再导入
                    parsedPreview = ParsePreview(result)
                    showImportPreview = true
                    importFeedback = null
                } else {
                    importFeedback = ImportFeedback(loading = false, count = 0, errors = result.errors)
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showImportDialog = true },
                    containerColor = Coral,
                    contentColor = CardWhite,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val appCtx = androidx.compose.ui.platform.LocalContext.current
            // 首次加载恢复上次进度
            LaunchedEffect(Unit) { todayViewModel.restoreProgress(appCtx) }
            // 切换到今日页时自动刷新（但跳过从 Plan 跳转过来的情况）
            LaunchedEffect(selectedTab) {
                if (selectedTab == 0) {
                    todayViewModel.refresh()
                } else {
                    todayViewModel.saveProgress(appCtx)
                }
            }

            when (selectedTab) {
                0 -> TodayPage(viewModel = todayViewModel)
                1 -> PlanPage(viewModel = planViewModel, onGoToDay = { day -> selectedTab = 0; todayViewModel.goToDay(day) })
                2 -> ArchivePage(viewModel = archiveViewModel)
            }
        }
    }

    // ── 导入确认弹窗 ──
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text("📂 Import Plan", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Select your .xlsx study plan.\n\n" +
                        "Columns: Day | Topic | O1 | I(Input) | O2 | Anki",
                        color = WarmGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("💡 Same day overwrites, new days append", color = Mint)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportDialog = false
                        filePickerLauncher.launch(
                            arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Select", color = CardWhite) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = WarmGray)
                }
            }
        )
    }

    // ── 导入预览确认 ──
    if (showImportPreview && parsedPreview != null) {
        val preview = parsedPreview!!
        AlertDialog(
            onDismissRequest = { showImportPreview = false; parsedPreview = null },
            title = { Text("📋 Preview Import", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This file contains:")
                    Spacer(Modifier.height(8.dp))
                    Text("📝 ${preview.topics.size} topics", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("📅 ${preview.dayCount} days", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("🔄 ${preview.overwriteCount} days will be overwritten", style = MaterialTheme.typography.bodySmall, color = AlertOrange)

                    if (preview.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        preview.errors.take(3).forEach { err ->
                            Text("⚠️ $err", color = ErrorRed, fontSize = 13.sp)
                        }
                        if (preview.errors.size > 3) {
                            Text("…and ${preview.errors.size - 3} more", fontSize = 13.sp, color = WarmGray)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Proceed with import?", style = MaterialTheme.typography.bodyMedium, color = WarmGray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportPreview = false
                        scope.launch { doImport(preview.result) }
                        parsedPreview = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Import", color = CardWhite) }
            },
            dismissButton = {
                TextButton(onClick = { showImportPreview = false; parsedPreview = null }) {
                    Text("Cancel", color = WarmGray)
                }
            }
        )
    }

    // ── 导入结果反馈 ──
    importFeedback?.let { feedback ->
        if (!feedback.loading) {
            AlertDialog(
                onDismissRequest = { importFeedback = null },
                title = {
                    Text(
                        if (feedback.errors.isEmpty()) "✅ Import Complete"
                        else "⚠️ Import Done (with errors)",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text("Imported ${feedback.count} topics")
                        if (feedback.errors.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            feedback.errors.forEach { err ->
                                Text("• $err", color = ErrorRed, fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { importFeedback = null }) {
                        Text("好的", color = Coral)
                    }
                }
            )
        } else {
            // 加载中 — 显示进度条
            AlertDialog(
                onDismissRequest = {},
                title = { Text("⏳ Importing…") },
                text = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Coral
                            )
                            Text(importStatusText, color = WarmDark)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Coral,
                            trackColor = WarmGrayLight.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(importProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmGray,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                },
                confirmButton = {}
            )
        }
    }
}

/** 导入预览数据 */
private data class ParsePreview(
    val result: com.oio.english.util.ExcelParser.ParseResult
) {
    val topics = result.topics
    val errors = result.errors
    val dayCount = topics.map { it.dayNumber }.distinct().size
    val overwriteCount: Int get() = dayCount // 简化：预览时无法知道实际覆盖数
}

/** 导入反馈状态 */
private data class ImportFeedback(
    val loading: Boolean = false,
    val count: Int = 0,
    val errors: List<String> = emptyList()
)

// ══════════════════════════════════════════
// 底部导航
// ══════════════════════════════════════════

@Composable
private fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = CardWhite,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("📋", "Today", selectedTab == 0) { onTabSelected(0) }
            NavItem("📅", "Plan", selectedTab == 1) { onTabSelected(1) }
            NavItem("📚", "Archive", selectedTab == 2) { onTabSelected(2) }
        }
    }
}

@Composable
private fun NavItem(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) CoralLight.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Coral else WarmGray
        )
    }
}
