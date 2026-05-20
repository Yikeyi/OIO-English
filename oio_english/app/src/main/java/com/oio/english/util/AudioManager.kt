package com.oio.english.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * 录音 + 播放管理。
 * 每个话题的 O1 和 O2 各自保存一个 .m4a 文件。
 */
class AudioManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentAudioFile: File? = null

    /** 获取某个话题某环节的音频文件路径 */
    fun getAudioFile(topicId: Long, stage: String): File {
        val dir = File(context.filesDir, "audio")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${topicId}_${stage}.m4a")
    }

    /** 是否正在录音 */
    var isRecording: Boolean = false
        private set

    /** 是否正在播放录音 */
    var isPlaying: Boolean = false
        private set

    /** 开始录音 */
    fun startRecording(topicId: Long, stage: String): Boolean {
        val file = getAudioFile(topicId, stage)
        currentAudioFile = file

        // 确保目录可写
        try { file.parentFile?.mkdirs() } catch (_: Exception) {}

        // 清理旧实例
        try { recorder?.apply { try { stop() } catch (_: Exception) {}; release() } } catch (_: Exception) {}
        recorder = null

        return try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 停止录音 */
    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recorder = null
        isRecording = false
    }

    /** 是否有已保存的录音 */
    fun hasRecording(topicId: Long, stage: String): Boolean {
        return getAudioFile(topicId, stage).exists()
    }

    /** 播放录音（speed: 0.5f~2.0f） */
    fun playRecording(topicId: Long, stage: String, onStart: () -> Unit, onComplete: () -> Unit, speed: Float = 1.0f) {
        val file = getAudioFile(topicId, stage)
        if (!file.exists()) {
            onComplete()
            return
        }

        stopPlayback()

        isPlaying = true

        player = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    this@AudioManager.isPlaying = false
                    onComplete()
                    release()
                    player = null
                }
                setOnErrorListener { _, _, _ ->
                    this@AudioManager.isPlaying = false
                    onComplete()
                    release()
                    player = null
                    true
                }
                // 设置播放速度（API 23+）
                if (android.os.Build.VERSION.SDK_INT >= 23 && speed != 1.0f) {
                    setOnPreparedListener {
                        val params = playbackParams
                        params.speed = speed
                        playbackParams = params
                        start()
                    }
                } else {
                    setOnPreparedListener { start() }
                }
                prepareAsync()
                onStart()
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete()
            }
        }
    }

    /** 停止播放 */
    fun stopPlayback() {
        isPlaying = false
        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        player = null
    }

    /** 释放所有资源 */
    fun release() {
        stopRecording()
        stopPlayback()
    }
}
