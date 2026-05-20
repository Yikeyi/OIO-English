package com.oio.english.ui.components

import android.Manifest
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oio.english.ui.theme.*
import com.oio.english.util.AudioManager
import java.util.Locale

@Composable
fun RecordPlayButtons(
    topicId: Long,
    stage: String,
    audioManager: AudioManager,
    modifier: Modifier = Modifier
) {
    val hasRecording = audioManager.hasRecording(topicId, stage)
    var isCurrentlyRecording by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val ok = audioManager.startRecording(topicId, stage)
            if (ok) isCurrentlyRecording = true
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrentlyRecording) ErrorRed.copy(alpha = 0.2f) else CoralLight.copy(alpha = 0.3f))
            .clickable {
                if (isCurrentlyRecording) { audioManager.stopRecording(); isCurrentlyRecording = false }
                else { permLauncher.launch(Manifest.permission.RECORD_AUDIO); val ok = audioManager.startRecording(topicId, stage); if (ok) isCurrentlyRecording = true }
            }.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (isCurrentlyRecording) "⏹ Rec" else "🎤 Rec", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = WarmDark)
            }
        }
        if (hasRecording) {
            var isPlaybackPlaying by remember { mutableStateOf(false) }
            var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
            val speeds = listOf(0.75f, 1.0f, 1.25f)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isPlaybackPlaying) {
                    Surface(shape = RoundedCornerShape(6.dp), color = LavenderLight.copy(alpha = 0.3f), onClick = {
                        val idx = speeds.indexOf(playbackSpeed); playbackSpeed = speeds[(idx + 1) % speeds.size]
                    }) { Text("${playbackSpeed}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Lavender, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) }
                }
                Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (isPlaybackPlaying) ErrorRed.copy(alpha = 0.2f) else MintLight.copy(alpha = 0.3f))
                    .clickable {
                        if (isPlaybackPlaying) { audioManager.stopPlayback(); isPlaybackPlaying = false }
                        else { audioManager.playRecording(topicId, stage, onStart = { isPlaybackPlaying = true }, onComplete = { isPlaybackPlaying = false }, speed = playbackSpeed) }
                    }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(text = if (isPlaybackPlaying) "⏹ Stop" else "▶️ Play", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = WarmDark) }
                }
            }
        }
    }
}

@Composable
fun TtsButton(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context.applicationContext) { _ -> try { tts?.setLanguage(Locale.US) } catch (_: Exception) {} }
    }

    DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))
        .background(if (isSpeaking) MintLight.copy(alpha = 0.5f) else LavenderLight.copy(alpha = 0.5f))
        .clickable {
            if (isSpeaking) { tts?.stop(); isSpeaking = false; return@clickable }
            val cleanText = text.replace(Regex("\\s+"), " ").trim()
            if (cleanText.isEmpty()) return@clickable
            isSpeaking = true
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "tts")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ isSpeaking = false }, maxOf(2000L, cleanText.length * 120L))
        }.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text = if (isSpeaking) "⏹ stop" else "🔊 Read",
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (!isSpeaking) WarmDark else WarmGray)
    }
}
