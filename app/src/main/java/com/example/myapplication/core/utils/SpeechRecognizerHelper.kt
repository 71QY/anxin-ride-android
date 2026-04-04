package com.example.myapplication.core.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.iflytek.cloud.*
import org.json.JSONObject
import java.util.*

class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,  // 最终结果
    private val onPartialResult: ((String) -> Unit)? = null,  // ⭐ 新增：实时部分结果
    private val language: String = "zh_cn",  // ⭐ 新增：语言类型（默认普通话）
    private val accent: String = "mandarin"  // ⭐ 新增：方言/口音（默认普通话）
) {
    companion object {
        private const val TAG = "SpeechRecognizer"
    }
    
    private var recognizer: SpeechRecognizer? = null

    init {
        recognizer = SpeechRecognizer.createRecognizer(context, null)
    }

    fun startListening() {
        Log.d(TAG, "=== 开始语音识别 ===")
        Log.d(TAG, "📱 设备信息: ${android.os.Build.MODEL}")
        Log.d(TAG, "🌍 语言配置: language=$language, accent=$accent")
        
        if (recognizer == null) {
            Log.e(TAG, "❌ recognizer 为 null，重新创建")
            recognizer = SpeechRecognizer.createRecognizer(context, null)
            if (recognizer == null) {
                Log.e(TAG, "❌ 创建 recognizer 失败，可能是讯飞 SDK 未初始化")
                return
            }
        }
        
        // ⭐ 设置参数
        try {
            // ⭐ 必须参数：引擎类型（云端识别）
            recognizer?.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
            
            // ⭐ 修改：音频源改为 "1"（从麦克风读取），之前 "-1" 是从文件读取
            recognizer?.setParameter(SpeechConstant.AUDIO_SOURCE, "1")
            Log.d(TAG, "🎤 音频源设置为：麦克风 (AUDIO_SOURCE=1)")
            
            // 识别域名
            recognizer?.setParameter(SpeechConstant.DOMAIN, "iat")
            
            // ⭐ 语言和方言设置（支持方言识别）
            recognizer?.setParameter(SpeechConstant.LANGUAGE, language)
            recognizer?.setParameter(SpeechConstant.ACCENT, accent)
            
            Log.d(TAG, "🌍 识别配置: language=$language, accent=$accent")
            Log.d(TAG, "📋 完整参数列表:")
            Log.d(TAG, "  - ENGINE_TYPE: ${SpeechConstant.TYPE_CLOUD}")
            Log.d(TAG, "  - AUDIO_SOURCE: 1 (麦克风)")
            Log.d(TAG, "  - LANGUAGE: $language")
            Log.d(TAG, "  - ACCENT: $accent")
            Log.d(TAG, "  - VAD_BOS: 2000")
            Log.d(TAG, "  - VAD_EOS: 2000")
            Log.d(TAG, "  - RESULT_TYPE: json")
            
            // ⭐ 关键修复：VAD 前端点超时 - 改为 2 秒（之前 4 秒太长，导致错过说话）
            recognizer?.setParameter(SpeechConstant.VAD_BOS, "2000")
            
            // ⭐ 关键修复：VAD 后端点超时 - 改为 2 秒（之前 1.5 秒容易截断）
            recognizer?.setParameter(SpeechConstant.VAD_EOS, "2000")
            
            // 标点符号
            recognizer?.setParameter(SpeechConstant.ASR_PTT, "0")  // 不加标点
            
            // ⭐ 关键修复：必须设置返回类型为 json
            recognizer?.setParameter(SpeechConstant.RESULT_TYPE, "json")
            
            // ⭐ 新增：启用实时返回
            recognizer?.setParameter(SpeechConstant.ASR_SCH, "1")
            
            // ⭐ 关键修复：音频格式参数
            recognizer?.setParameter(SpeechConstant.SAMPLE_RATE, "16000")
            recognizer?.setParameter(SpeechConstant.AUDIO_FORMAT, "pcm")
            
            Log.d(TAG, "✅ 参数设置完成，开始监听...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设置参数失败: ${e.message}", e)
            return
        }

        try {
            recognizer?.startListening(object : RecognizerListener {
                override fun onVolumeChanged(volume: Int, data: ByteArray?) {
                    if (volume > 0) {
                        Log.d(TAG, "🎤 音量变化: $volume (有声音输入)")
                    }
                }
                
                override fun onBeginOfSpeech() {
                    Log.d(TAG, "✅ 开始说话（检测到声音）")
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "✅ 结束说话（静音检测触发）")
                    Log.d(TAG, "⏳ 等待云端识别结果...")
                }
                
                override fun onResult(results: RecognizerResult?, isLast: Boolean) {
                    if (results == null) {
                        Log.w(TAG, "⚠️ 识别结果为 null")
                        return
                    }
                    
                    Log.d(TAG, "📥 收到原始结果 (isLast=$isLast): ${results.resultString.take(100)}...")
                    val resultText = parseIatResult(results.resultString)
                    Log.d(TAG, "📤 解析后结果 (isLast=$isLast): '$resultText'")
                    
                    if (!isLast) {
                        // ⭐ 实时部分结果
                        onPartialResult?.invoke(resultText)
                    } else {
                        // 最终结果
                        onResult(resultText)
                    }
                }
                
                override fun onError(error: SpeechError?) {
                    if (error != null) {
                        Log.e(TAG, "❌ 语音识别错误: errorCode=${error.errorCode}, message=${error.errorDescription}")
                        Log.e(TAG, "📋 当前配置: language=$language, accent=$accent")
                        when (error.errorCode) {
                            14002 -> {
                                Log.e(TAG, "💡 提示：14002 错误通常是 AppID 无效或网络问题，请检查：\n1. AppID 是否正确\n2. 网络连接是否正常\n3. 讯飞控制台是否开通了 IAT 服务")
                                Log.e(TAG, "⚠️ 特别注意：如果粤语可以识别但普通话不行，请登录讯飞控制台检查普通话服务是否开通")
                            }
                            20005 -> Log.e(TAG, "💡 提示：20005 错误是未检测到有效音频，请检查麦克风权限和录音功能")
                            20006 -> Log.e(TAG, "💡 提示：20006 错误是音频数据不完整，可能是录音时间太短")
                            10118 -> {
                                Log.e(TAG, "💡 提示：10118 错误是超时，可能是网络不稳定")
                            }
                            else -> Log.e(TAG, "💡 未知错误码: ${error.errorCode}，请查阅讯飞文档")
                        }
                    } else {
                        Log.e(TAG, "❌ 语音识别未知错误")
                    }
                }
                
                override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {
                    Log.d(TAG, "事件: eventType=$eventType")
                }
            })
            
            Log.d(TAG, "✅ startListening 调用完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ startListening 异常: ${e.message}", e)
        }
    }

    /**
     * 解析讯飞语音识别结果 JSON
     */
    private fun parseIatResult(json: String?): String {
        if (json.isNullOrEmpty()) return ""
        
        return try {
            val jsonObject = JSONObject(json)
            val ws = jsonObject.getJSONArray("ws")
            val sb = StringBuilder()
            
            for (i in 0 until ws.length()) {
                val w = ws.getJSONObject(i)
                val cw = w.getJSONArray("cw")
                for (j in 0 until cw.length()) {
                    val word = cw.getJSONObject(j).getString("w")
                    sb.append(word)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "解析识别结果失败", e)
            json
        }
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}