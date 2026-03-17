package com.example.myapplication.core.utils

import android.content.Context
import android.os.Bundle
import com.iflytek.cloud.*
import java.util.*

class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    init {
        recognizer = SpeechRecognizer.createRecognizer(context, null)
    }

    fun startListening() {
        recognizer?.setParameter(SpeechConstant.DOMAIN, "iat")
        recognizer?.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
        recognizer?.setParameter(SpeechConstant.ACCENT, "mandarin")
        recognizer?.setParameter(SpeechConstant.VAD_BOS, "4000")
        recognizer?.setParameter(SpeechConstant.VAD_EOS, "1000")
        recognizer?.setParameter(SpeechConstant.ASR_PTT, "0")

        recognizer?.startListening(object : RecognizerListener {
            override fun onVolumeChanged(volume: Int, data: ByteArray?) {}
            override fun onBeginOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onResult(results: RecognizerResult?, isLast: Boolean) {
                if (!isLast) return
                // 简化处理：直接返回结果字符串（实际需要解析 JSON，可后续完善）
                val text = results?.resultString ?: ""
                onResult(text)
            }
            override fun onError(error: SpeechError?) {}
            override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {}
        })
    }

    fun destroy() {
        recognizer?.destroy()
    }
}