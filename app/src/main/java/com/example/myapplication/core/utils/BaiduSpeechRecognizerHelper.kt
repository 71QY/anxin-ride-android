package com.example.myapplication.core.utils

import android.content.Context
import android.util.Log
import com.baidu.speech.EventListener
import com.baidu.speech.EventManager
import com.baidu.speech.EventManagerFactory
import com.baidu.speech.asr.SpeechConstant
import com.example.myapplication.BuildConfig
import org.json.JSONObject

/**
 * 百度语音识别辅助类
 * 优点：国内支持好、免费额度大、无需 Google 服务
 */
class BaiduSpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: ((String) -> Unit)? = null,
    private val language: String = "zh-CN"  // 默认普通话
) {
    private var eventManager: EventManager? = null
    private var isListening = false
    
    private val TAG = "BaiduSpeechRecognizer"
    
    // 百度语音识别配置（需要在百度智能云控制台创建应用获取）
    private val appId = BuildConfig.BAIDU_APP_ID
    private val apiKey = BuildConfig.BAIDU_API_KEY
    private val secretKey = BuildConfig.BAIDU_SECRET_KEY
    
    init {
        // 初始化百度语音识别
        eventManager = EventManagerFactory.create(context, "asr")
        
        // 设置监听器
        eventManager?.registerListener(object : EventListener {
            override fun onEvent(name: String, params: String?, data: ByteArray?, offset: Int, length: Int) {
                Log.d(TAG, "📢 百度语音事件: $name")
                
                when (name) {
                    SpeechConstant.CALLBACK_EVENT_ASR_READY -> {
                        Log.d(TAG, "✅ 引擎就绪，可以开始说话")
                    }
                    SpeechConstant.CALLBACK_EVENT_ASR_BEGIN -> {
                        Log.d(TAG, "🎤 开始说话（检测到声音）")
                    }
                    SpeechConstant.CALLBACK_EVENT_ASR_VOLUME -> {
                        // 音量回调，可选
                    }
                    SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL -> {
                        // 部分识别结果
                        handlePartialResult(params)
                    }
                    SpeechConstant.CALLBACK_EVENT_ASR_FINISH -> {
                        // 识别完成
                        Log.d(TAG, "✅ 识别结束事件，原始参数: $params")
                        handleFinalResult(params)
                        isListening = false
                    }
                    SpeechConstant.CALLBACK_EVENT_ASR_ERROR -> {
                        Log.e(TAG, "Voice recognition error: $params")
                        
                        try {
                            val json = JSONObject(params ?: "{}")
                            val error = json.optInt("error", -1)
                            val desc = json.optString("desc", "Unknown error")
                            
                            if (error == 4 || desc.contains("-3004")) {
                                Log.e(TAG, "Baidu voice authentication failed! Check configuration.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse error info", e)
                        }
                        
                        isListening = false
                        onResult("")
                    }
                    else -> {
                        Log.d(TAG, "其他事件: $name")
                    }
                }
            }
        })
    }
    
    /**
     * 开始语音识别
     */
    fun startListening() {
        try {
            if (isListening) {
                Log.w(TAG, "Recognition in progress, ignoring duplicate request")
                return
            }
            
            Log.d(TAG, "Starting voice recognition")
            
            if (appId.isBlank() || apiKey.isBlank() || secretKey.isBlank()) {
                Log.e(TAG, "Baidu voice configuration is empty! Check gradle.properties")
                onResult("Configuration error: Baidu voice keys not set")
                return
            }
            
            // 组装识别参数（百度语音 SDK 要求在 start 时传入认证信息）
            val startParams = JSONObject().apply {
                put(SpeechConstant.APP_ID, appId)
                put(SpeechConstant.APP_KEY, apiKey)
                put(SpeechConstant.SECRET, secretKey)
                
                put("package_name", context.packageName)
                
                // 识别参数
                put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false)
                val pid = when (language) {
                    "zh-CN" -> 1537
                    "zh-HK" -> 1637
                    "en-US" -> 1737
                    "zh-SICHUAN" -> 1837
                    else -> 1537
                }
                put(SpeechConstant.PID, pid)
                Log.d(TAG, "Voice recognition language: $language (PID=$pid)")
                put(SpeechConstant.NLU, "enable")
                
                put(SpeechConstant.VAD_ENDPOINT_TIMEOUT, 3000)
            }
            
            Log.d(TAG, "Sending recognition request")
            Log.d(TAG, "   APP_ID: [HIDDEN]")
            Log.d(TAG, "   API_KEY: [HIDDEN]")
            Log.d(TAG, "   SECRET_KEY: [HIDDEN]")
            Log.d(TAG, "   PACKAGE_NAME: ${context.packageName}")
            
            // 开始识别
            eventManager?.send(SpeechConstant.ASR_START, startParams.toString(), null, 0, 0)
            isListening = true
            
            Log.d(TAG, "Voice recognition started, please speak...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recognition", e)
            isListening = false
            onResult("")
        }
    }
    
    /**
     * 停止语音识别
     */
    fun stopListening() {
        try {
            Log.d(TAG, "Stopping voice recognition")
            eventManager?.send(SpeechConstant.ASR_STOP, null, null, 0, 0)
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop voice recognition", e)
        }
    }
    
    /**
     * 取消语音识别
     */
    fun cancel() {
        try {
            Log.d(TAG, "Cancelling voice recognition")
            eventManager?.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0)
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel voice recognition", e)
        }
    }
    
    /**
     * 销毁资源
     */
    fun destroy() {
        try {
            Log.d(TAG, "Destroying voice recognizer")
            eventManager?.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0)
            eventManager = null
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy voice recognizer", e)
        }
    }
    
    /**
     * 处理部分识别结果
     */
    private fun handlePartialResult(params: String?) {
        if (params == null) return
        
        try {
            val json = JSONObject(params)
            val resultType = json.optString("result_type", "")
            
            if (resultType == "partial_result") {
                val bestResult = json.optString("best_result", "")
                if (bestResult.isNotBlank()) {
                    Log.d(TAG, "Partial result: '$bestResult'")
                    onPartialResult?.invoke(bestResult)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse partial result", e)
        }
    }
    
    /**
     * 处理最终识别结果
     */
    private fun handleFinalResult(params: String?) {
        if (params == null) {
            Log.w(TAG, "Recognition result is empty (params is null)")
            onResult("")
            return
        }
        
        try {
            val json = JSONObject(params)
            val error = json.optInt("error", -1)
            
            if (error == 0) {
                val bestResult = json.optString("best_result", "")
                Log.d(TAG, "Final recognition result: '$bestResult'")
                onResult(bestResult)
            } else {
                val errorMessage = json.optString("desc", "Unknown error")
                Log.e(TAG, "Recognition failed: $errorMessage (error=$error)")
                onResult("")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse final result, raw content: $params", e)
            onResult("")
        }
    }
}
