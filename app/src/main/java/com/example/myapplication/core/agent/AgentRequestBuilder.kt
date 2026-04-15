package com.example.myapplication.core.agent

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * 智能体请求构建器
 * 负责构建符合后端文档规范的 WebSocket 请求
 * 
 * 对齐后端文档第 1.2 节：消息类型与格式
 */
class AgentRequestBuilder {
    
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 构建用户文本消息（user_message）
     * 对齐后端文档 1.2.1 节
     * 
     * @param sessionId 会话 ID
     * @param content 用户输入的文本内容
     * @param lat 纬度（可选）
     * @param lng 经度（可选）
     * @param dialectType 方言类型，默认 mandarin
     * @return JSON 字符串
     */
    fun buildUserMessage(
        sessionId: String,
        content: String,
        lat: Double? = null,
        lng: Double? = null,
        dialectType: String = "mandarin"
    ): String {
        val jsonObject = JSONObject().apply {
            put("type", "user_message")
            put("sessionId", sessionId)
            put("content", content)
            put("dialectType", dialectType)
            
            if (lat != null && lng != null) {
                put("lat", lat)
                put("lng", lng)
            }
        }
        
        val json = jsonObject.toString()
        Log.d("AgentRequestBuilder", "📤 构建用户消息: $json")
        return json
    }
    
    /**
     * 构建图片识别消息（image）
     * 对齐后端文档 1.2.2 节
     * 
     * @param sessionId 会话 ID
     * @param imageBase64 主图片 Base64（包含 data:image/jpeg;base64, 前缀）
     * @param additionalImages 额外图片数组（批量识别）
     * @param lat 纬度（可选）
     * @param lng 经度（可选）
     * @return JSON 字符串
     */
    fun buildImageMessage(
        sessionId: String,
        imageBase64: String,
        additionalImages: List<String>? = null,
        lat: Double? = null,
        lng: Double? = null
    ): String {
        val jsonObject = JSONObject().apply {
            put("type", "image")
            put("sessionId", sessionId)
            put("imageBase64", imageBase64)
            
            // 批量图片支持
            if (!additionalImages.isNullOrEmpty()) {
                put("additionalImages", JSONArray(additionalImages))
                put("imageCount", additionalImages.size + 1)  // +1 是主图
            } else {
                put("imageCount", 1)
            }
            
            if (lat != null && lng != null) {
                put("lat", lat)
                put("lng", lng)
            }
        }
        
        val json = jsonObject.toString()
        Log.d("AgentRequestBuilder", "📤 构建图片消息: 长度=${json.length}")
        return json
    }
    
    /**
     * 构建确认选择消息（confirm）
     * 对齐后端文档 1.2.3 节
     * 
     * @param sessionId 会话 ID
     * @param poiName 用户选择的 POI 名称（必须与之前返回的 places 中的 name 完全一致）
     * @param lat 纬度（必填）
     * @param lng 经度（必填）
     * @return JSON 字符串
     */
    fun buildConfirmMessage(
        sessionId: String,
        poiName: String,
        lat: Double,
        lng: Double
    ): String {
        val jsonObject = JSONObject().apply {
            put("type", "confirm")
            put("sessionId", sessionId)
            put("content", poiName)  // 后端文档中 confirm 使用 content 字段
            put("lat", lat)
            put("lng", lng)
        }
        
        val json = jsonObject.toString()
        Log.d("AgentRequestBuilder", "📤 构建确认消息: poi=$poiName")
        return json
    }
    
    /**
     * 构建心跳消息（ping）
     * 对齐后端文档 1.2.4 节
     * 
     * @param sessionId 会话 ID
     * @return JSON 字符串
     */
    fun buildPingMessage(sessionId: String): String {
        val jsonObject = JSONObject().apply {
            put("type", "ping")
            put("sessionId", sessionId)
            put("timestamp", System.currentTimeMillis())
        }
        
        return jsonObject.toString()
    }
    
    /**
     * 验证图片 Base64 格式是否符合后端要求
     * 对齐后端文档 5.3 节：图片上传规范
     * 
     * @param base64 待验证的 Base64 字符串
     * @return 是否有效
     */
    fun isValidImageBase64(base64: String): Boolean {
        // 必须包含前缀
        if (!base64.startsWith("data:image/")) {
            Log.e("AgentRequestBuilder", "❌ Base64 缺少前缀: data:image/")
            return false
        }
        
        // 检查格式
        val validPrefixes = listOf("data:image/jpeg;base64,", "data:image/png;base64,")
        val hasValidPrefix = validPrefixes.any { base64.startsWith(it) }
        
        if (!hasValidPrefix) {
            Log.e("AgentRequestBuilder", "❌ Base64 前缀不支持，仅支持 JPG/PNG")
            return false
        }
        
        // 检查大小（5MB 限制）
        val base64Length = base64.length
        val estimatedSizeKB = (base64Length * 0.75) / 1024  // Base64 编码后约增加 33%
        
        if (estimatedSizeKB > 5120) {  // 5MB = 5120KB
            Log.e("AgentRequestBuilder", "❌ 图片过大: ${estimatedSizeKB.toInt()}KB > 5120KB")
            return false
        }
        
        return true
    }
    
    /**
     * 验证坐标是否在有效范围内
     * 对齐后端文档 5.2 节：坐标系统
     * 
     * @param lat 纬度
     * @param lng 经度
     * @return 是否有效
     */
    fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        val isLatValid = lat in -90.0..90.0
        val isLngValid = lng in -180.0..180.0
        
        if (!isLatValid || !isLngValid) {
            Log.e("AgentRequestBuilder", "❌ 坐标超出范围: lat=$lat, lng=$lng")
            return false
        }
        
        return true
    }
    
    /**
     * 生成符合规范的 SessionId
     * 对齐后端文档 5.1 节：SessionId 管理
     * 
     * @param userId 用户 ID
     * @return SessionId 字符串
     */
    fun generateSessionId(userId: Long): String {
        // 格式：user_{userId}
        return "user_$userId"
    }
    
    /**
     * 验证 SessionId 格式
     */
    fun isValidSessionId(sessionId: String): Boolean {
        return sessionId.isNotBlank() && sessionId.startsWith("user_")
    }
    
    companion object {
        // 支持的方言类型
        const val DIALECT_MANDARIN = "mandarin"      // 普通话
        const val DIALECT_CANTONESE = "cantonese"    // 粤语
        const val DIALECT_TEOCHEW = "teochew"        // 潮汕话
        const val DIALECT_HAKKA = "hakka"            // 客家话
        
        // 图片最大大小（5MB）
        const val MAX_IMAGE_SIZE_KB = 5120
        
        // 建议的心跳间隔（30秒）
        const val HEARTBEAT_INTERVAL_MS = 30000L
    }
}
