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
        Log.d(TAG, "🔧 BaiduSpeechRecognizerHelper 初始化开始")
        Log.d(TAG, "   配置信息 - appId=${if (appId.isBlank()) "[EMPTY]" else "[SET]"}, apiKey=${if (apiKey.isBlank()) "[EMPTY]" else "[SET]"}, secretKey=${if (secretKey.isBlank()) "[EMPTY]" else "[SET]"}")
        Log.d(TAG, "   当前语言: $language")
        
        // ⭐ 检查配置是否为空
        if (appId.isBlank() || apiKey.isBlank() || secretKey.isBlank()) {
            Log.e(TAG, "❌ 百度语音配置为空！BuildConfig 没有正确读取配置")
            Log.e(TAG, "   appId 长度: ${appId.length}")
            Log.e(TAG, "   apiKey 长度: ${apiKey.length}")
            Log.e(TAG, "   secretKey 长度: ${secretKey.length}")
            // init 块不能使用 return，通过后续判断阻止初始化
        } else {
            try {
                // 初始化百度语音识别
                Log.d(TAG, "📦 开始创建 EventManager...")
                eventManager = EventManagerFactory.create(context, "asr")
                Log.d(TAG, "✅ EventManager 创建成功: ${eventManager != null}")
                
                if (eventManager != null) {
                    // 设置监听器
                    Log.d(TAG, "🎧 注册事件监听器...")
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
                                    Log.e(TAG, "❌ 语音识别错误: $params")
                                    
                                    try {
                                        val json = JSONObject(params ?: "{}")
                                        val error = json.optInt("error", -1)
                                        val desc = json.optString("desc", "Unknown error")
                                        
                                        if (error == 4 || desc.contains("-3004")) {
                                            Log.e(TAG, "百度语音认证失败！请检查配置。")
                                            onResult("配置错误：请检查百度语音密钥")
                                        } else {
                                            Log.e(TAG, "识别错误码: $error, 描述: $desc")
                                            // ⭐ 不要立即清空结果，保留已识别的内容
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "解析错误信息失败", e)
                                    }
                                    
                                    isListening = false
                                    // ⭐ 不在这里调用 onResult("")，让 handleFinalResult 处理
                                }
                                else -> {
                                    Log.d(TAG, "其他事件: $name")
                                }
                            }
                        }
                    })
                    Log.d(TAG, "✅ BaiduSpeechRecognizerHelper 初始化完成")
                } else {
                    Log.e(TAG, "❌ EventManager 创建失败，返回 null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ BaiduSpeechRecognizerHelper 初始化失败: ${e.message}", e)
            }
        }
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
                
                // ⭐ 添加包名参数(用于鉴权)
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
                
                // ⭐ 强制使用在线识别（禁用离线引擎）
                put(SpeechConstant.DECODER, 2) // 2=纯在线识别
                put("kws-file", "")            // 禁用离线唤醒词
                put("lm-file", "")             // 禁用离线语言模型
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
            
            // ⭐ 支持多种 partial_result 格式
            if (resultType == "partial_result" || resultType == "final_result") {
                val bestResult = json.optString("best_result", "")
                val resultsArray = json.optJSONArray("results_recognition")
                
                // 尝试从不同字段获取结果
                var text = bestResult
                if (text.isBlank() && resultsArray != null && resultsArray.length() > 0) {
                    text = resultsArray.optString(0, "")
                }
                
                if (text.isNotBlank()) {
                    Log.d(TAG, "🔄 实时识别结果: '$text'")
                    onPartialResult?.invoke(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析部分结果失败", e)
        }
    }
    
    /**
     * 处理最终识别结果
     */
    private fun handleFinalResult(params: String?) {
        if (params == null) {
            Log.w(TAG, "⚠️ 识别结果为空 (params is null)")
            // ⭐ 不立即返回空字符串，等待用户手动停止
            return
        }
        
        try {
            val json = JSONObject(params)
            val error = json.optInt("error", -1)
            
            if (error == 0) {
                val bestResult = json.optString("best_result", "")
                val resultsArray = json.optJSONArray("results_recognition")
                
                // ⭐ 尝试从多个字段获取结果
                var finalText = bestResult
                if (finalText.isBlank() && resultsArray != null && resultsArray.length() > 0) {
                    finalText = resultsArray.optString(0, "")
                }
                
                Log.d(TAG, "✅ 最终识别结果: '$finalText'")
                if (finalText.isNotBlank()) {
                    onResult(finalText)
                } else {
                    Log.w(TAG, "⚠️ 识别结果为空字符串")
                    // ⭐ 不调用 onResult，保持当前状态
                }
            } else {
                val errorMessage = json.optString("desc", "未知错误")
                Log.e(TAG, "❌ 识别失败: $errorMessage (error=$error)")
                // ⭐ 不自动调用 onResult("")，让用户看到已识别的内容
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析最终结果失败，原始内容: $params", e)
            // ⭐ 不自动调用 onResult("")
        }
    }
}
