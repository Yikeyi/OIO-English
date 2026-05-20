package com.oio.english.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * 全部完成时弹出的庆祝动画。
 * 显示 emoji 飘落 + 恭喜文字，1.5s 后自动消失。
 */
@Composable
fun CelebrationOverlay(
    show: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!show) return

    // 自动消失
    LaunchedEffect(Unit) {
        delay(1800)
        onDismiss()
    }

    val emojis = listOf("🎉", "🌟", "✨", "⭐", "💪", "🎊", "🏆", "👏")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 浮动 emoji
        emojis.forEachIndexed { index, emoji ->
            val duration = 1200
            val delayMs = index * 80
            val infiniteTransition = rememberInfiniteTransition(label = "celebration_$index")
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -200f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, delayMs, easing = EaseOutCubic),
                    repeatMode = RepeatMode.Restart
                ),
                label = "float_$index"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, delayMs, easing = EaseOutCubic),
                    repeatMode = RepeatMode.Restart
                ),
                label = "fade_$index"
            )

            Text(
                text = emoji,
                fontSize = 32.sp,
                modifier = Modifier
                    .offset(
                        x = ((-160 + index * 45).dp),
                        y = offsetY.dp
                    )
                    .alpha(alpha)
            )
        }

        // 中间恭喜文字
        var textAlpha by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            textAlpha = 1f
        }
        BasicText(
            text = "🎉 Great job!\nDay complete!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.alpha(textAlpha)
        )
    }
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
