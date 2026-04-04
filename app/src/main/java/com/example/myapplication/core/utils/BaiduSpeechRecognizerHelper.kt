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
                        Log.e(TAG, "❌ 语音识别错误: $params")
                        
                        // ⭐ 解析错误信息，提供更友好的提示
                        try {
                            val json = JSONObject(params ?: "{}")
                            val error = json.optInt("error", -1)
                            val desc = json.optString("desc", "未知错误")
                            
                            if (error == 4 || desc.contains("-3004")) {
                                Log.e(TAG, "⚠️ 百度语音认证失败！请检查：")
                                Log.e(TAG, "   1. gradle.properties 中的 baidu.app.id/api.key/secret.key 是否正确")
                                Log.e(TAG, "   2. 百度智能云控制台中的应用是否启用")
                                Log.e(TAG, "   3. 是否有足够的调用额度")
                                Log.e(TAG, "   4. APP_ID/API_KEY/SECRET_KEY 是否匹配")
                                Log.e(TAG, "💡 临时解决方案：切换到讯飞语音或重新配置百度语音密钥")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析错误信息失败", e)
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
                Log.w(TAG, "⚠️ 正在识别中，忽略重复请求")
                return
            }
            
            Log.d(TAG, "🎤 开始语音识别")
            
            // 组装识别参数（百度语音 SDK 要求在 start 时传入认证信息）
            val startParams = JSONObject().apply {
                // ⭐ 认证信息（必须）
                put(SpeechConstant.APP_ID, appId)
                put(SpeechConstant.APP_KEY, apiKey)
                put(SpeechConstant.SECRET, secretKey)
                
                // 识别参数
                put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false)
                // ⭐ 百度语音 PID 参数（方言映射）
                val pid = when (language) {
                    "zh-CN" -> 1537    // 普通话
                    "zh-HK" -> 1637    // 粤语
                    "en-US" -> 1737    // 英语
                    "zh-SICHUAN" -> 1837  // 四川话
                    else -> 1537
                }
                put(SpeechConstant.PID, pid)
                Log.d(TAG, "🗣️ 语音识别语言: $language (PID=$pid)")
                put(SpeechConstant.NLU, "enable")  // 启用语义理解
            }
            
            Log.d(TAG, "📤 发送识别请求")
            Log.d(TAG, "   APP_ID: $appId")
            Log.d(TAG, "   API_KEY: ${apiKey.take(8)}...")
            Log.d(TAG, "   SECRET_KEY: ${secretKey.take(8)}...")
            Log.d(TAG, "   PACKAGE_NAME: ${context.packageName}")
            Log.d(TAG, "   完整参数 JSON: $startParams")
            
            // 开始识别
            eventManager?.send(SpeechConstant.ASR_START, startParams.toString(), null, 0, 0)
            isListening = true
            
            Log.d(TAG, "✅ 语音识别已启动，请开始说话...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动语音识别失败", e)
            isListening = false
            onResult("")
        }
    }
    
    /**
     * 停止语音识别
     */
    fun stopListening() {
        try {
            Log.d(TAG, "⏹️ 停止语音识别")
            eventManager?.send(SpeechConstant.ASR_STOP, null, null, 0, 0)
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止语音识别失败", e)
        }
    }
    
    /**
     * 取消语音识别
     */
    fun cancel() {
        try {
            Log.d(TAG, "❌ 取消语音识别")
            eventManager?.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0)
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ 取消语音识别失败", e)
        }
    }
    
    /**
     * 销毁资源
     */
    fun destroy() {
        try {
            Log.d(TAG, "🗑️ 销毁语音识别器")
            eventManager?.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0)
            eventManager = null
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ 销毁语音识别器失败", e)
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
                    Log.d(TAG, " 部分结果: '$bestResult'")
                    onPartialResult?.invoke(bestResult)
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
            onResult("")
            return
        }
        
        try {
            val json = JSONObject(params)
            val error = json.optInt("error", -1)
            
            if (error == 0) {
                val bestResult = json.optString("best_result", "")
                Log.d(TAG, "📥 最终识别结果: '$bestResult'")
                onResult(bestResult)
            } else {
                val errorMessage = json.optString("desc", "未知错误")
                Log.e(TAG, "❌ 识别失败: $errorMessage (error=$error)")
                onResult("")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析最终结果失败，原始内容: $params", e)
            onResult("")
        }
    }
}
