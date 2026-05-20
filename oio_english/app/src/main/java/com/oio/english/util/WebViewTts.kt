package com.oio.english.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 使用 WebView + Web Speech API 朗读文字。
 * 不依赖系统 TTS 引擎，走 Chrome 自带语音合成。
 */
class WebViewTts {

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isReady = false
    var isSpeaking = false
        private set
    private var onEndCallback: (() -> Unit)? = null

    /** JS 接口 */
    inner class SpeechBridge {
        @JavascriptInterface
        fun onSpeechEnd() {
            isSpeaking = false
            handler.post { onEndCallback?.invoke() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun init(ctx: Context, onReady: () -> Unit) {
        handler.post {
            val wv = WebView(ctx)
            wv.settings.javaScriptEnabled = true
            wv.setBackgroundColor(0) // 透明
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    isReady = true
                    onReady()
                }
            }
            wv.addJavascriptInterface(SpeechBridge(), "Android")
            wv.loadDataWithBaseURL(null, """
                <html><body><script>
                function speak(text) {
                    speechSynthesis.cancel();
                    var u = new SpeechSynthesisUtterance(text);
                    u.lang = 'en-US';
                    u.rate = 0.85;
                    u.onend = function() { Android.onSpeechEnd(); };
                    u.onerror = function() { Android.onSpeechEnd(); };
                    speechSynthesis.speak(u);
                }
                </script></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
            webView = wv
        }
    }

    fun speak(text: String, onStart: () -> Unit, onEnd: () -> Unit) {
        if (!isReady || webView == null) { onEnd(); return }
        isSpeaking = true
        onStart()
        onEndCallback = onEnd

        val safeText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        handler.post {
            webView?.evaluateJavascript("speak('$safeText');", null)
        }

        handler.postDelayed({
            if (isSpeaking) { isSpeaking = false; onEnd() }
        }, maxOf(2000L, text.length * 120L))
    }

    fun stop() {
        isSpeaking = false
        handler.post { webView?.evaluateJavascript("speechSynthesis.cancel();", null) }
    }

    fun release() {
        stop()
        handler.post { webView?.destroy(); webView = null }
    }
}
