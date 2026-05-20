package com.oio.english.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oio.english.ui.theme.*

/**
 * 点击展开/收起的卡片。
 *
 * @param stageLabel 环节标签，如 "O1 · 先自己试试"
 * @param topic 当前话题名称
 * @param content 隐藏的正文内容
 * @param accentColor 环节主题色
 * @param emoji 环节图标
 * @param isCompleted 该环节是否已完成
 * @param onComplete 点击完成按钮回调
 * @param extraActions 展开后可选的额外操作栏（录音、TTS 等）
 */
@Composable
fun FlipCard(
    stageLabel: String,
    topic: String,
    content: String,
    accentColor: Color,
    emoji: String,
    isCompleted: Boolean,
    onComplete: () -> Unit,
    extraActions: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    autoExpand: Boolean = false,
    onEditContent: ((String) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(autoExpand) }
    LaunchedEffect(autoExpand) { if (autoExpand && !isExpanded) isExpanded = true }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (isExpanded) 6.dp else 3.dp, RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) SuccessGreenLight else CardWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // ── 顶栏：图标 + 标签 + 完成状态 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 环节图标
                if (emoji.isNotEmpty()) {
                    Text(text = emoji, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stageLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarmGray
                    )
                }
                if (isCompleted) {
                    Text(text = "✅", fontSize = 20.sp)
                } else if (!isExpanded) {
                    Text(
                        text = "👆 Reveal",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmGray
                    )
                }
            }

            // ── 展开内容 ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    HorizontalDivider(
                        color = accentColor.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    @OptIn(ExperimentalFoundationApi::class)
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WarmDark,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        modifier = if (onEditContent != null) Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onEditContent?.invoke(content) }
                        ) else Modifier
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── 附加操作栏（录音、TTS 等） ──
                    extraActions?.invoke()

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── 完成按钮 ──
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCompleted) SuccessGreen
                            else accentColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (isCompleted) "✅ Done" else "✓ Mark Complete",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CardWhite
                        )
                    }
                }
            }
        }
    }
}
