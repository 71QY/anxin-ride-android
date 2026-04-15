package com.example.myapplication.core.agent

import android.util.Log
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.PoiData
import com.example.myapplication.data.model.WebSocketResponse
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 智能体消息处理器
 * 负责解析后端返回的 WebSocket 消息，转换为 UI 可用的数据结构
 * 
 * 对齐后端文档第 1.2 节：消息类型与格式
 */
class AgentMessageHandler {
    
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 解析服务端消息
     * @param json 原始 JSON 字符串
     * @return 解析后的响应对象，失败返回 null
     */
    fun parseMessage(json: String): WebSocketResponse? {
        return try {
            val response = jsonFormat.decodeFromString<WebSocketResponse>(json)
            Log.d("AgentMessageHandler", "✅ 消息解析成功: type=${response.type}")
            response
        } catch (e: Exception) {
            Log.e("AgentMessageHandler", "❌ 消息解析失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 处理搜索结果消息（type=search）
     * @param response WebSocket 响应
     * @return POI 列表和提示信息
     */
    fun handleSearchResponse(response: WebSocketResponse): SearchResult {
        val places = response.places ?: emptyList()
        val message = response.message ?: "为您找到以下地点"
        
        Log.d("AgentMessageHandler", "📍 搜索结果: ${places.size} 个地点, needConfirm=${response.needConfirm}")
        
        // 转换为 PoiData（保持与现有数据模型一致）
        val poiDataList = places.mapIndexed { index, poi ->
            PoiData(
                id = poi.id ?: "",
                name = poi.name ?: "",
                address = poi.address ?: "",
                lat = poi.lat ?: 0.0,
                lng = poi.lng ?: 0.0,
                distance = poi.distance,  // ⭐ 保持可空类型
                type = poi.type,  // ⭐ 保持可空类型
                duration = poi.duration,  // ⭐ 保持可空类型
                price = poi.price,  // ⭐ 保持可空类型
                score = poi.score  // ⭐ 保持可空类型
            )
        }
        
        return SearchResult(
            places = poiDataList,
            message = message,
            needConfirm = response.needConfirm,
            suggestions = poiDataList.take(3).map { it.name }
        )
    }
    
    /**
     * 处理聊天回复消息（type=chat）
     * @param response WebSocket 响应
     * @return 聊天消息对象
     */
    fun handleChatResponse(response: WebSocketResponse): ChatMessage? {
        val content = response.message ?: response.content
        if (content.isNullOrBlank()) {
            Log.w("AgentMessageHandler", "⚠️ 聊天消息内容为空")
            return null
        }
        
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 处理订单创建消息（type=order）
     * @param response WebSocket 响应
     * @return 订单相关信息
     */
    fun handleOrderResponse(response: WebSocketResponse): OrderResult? {
        val data = response.data
        if (data == null) {
            Log.w("AgentMessageHandler", "⚠️ 订单响应数据为空")
            return null
        }
        
        Log.d("AgentMessageHandler", "🚗 订单创建成功: $data")
        
        return OrderResult(
            success = response.success,
            message = response.message ?: "订单创建成功",
            data = data
        )
    }
    
    /**
     * 处理图片识别结果（type=image_result）
     * @param response WebSocket 响应
     * @return 识别结果
     */
    fun handleImageResult(response: WebSocketResponse): ImageRecognitionResult {
        val ocrText = extractOcrText(response)
        val places = response.places ?: emptyList()
        
        Log.d("AgentMessageHandler", "🖼️ 图片识别: ocrText=$ocrText, places=${places.size}")
        
        val poiDataList = places.map { poi ->
            PoiData(
                id = poi.id ?: "",
                name = poi.name ?: "",
                address = poi.address ?: "",
                lat = poi.lat ?: 0.0,
                lng = poi.lng ?: 0.0,
                distance = poi.distance,  // ⭐ 保持可空类型
                type = poi.type,  // ⭐ 保持可空类型
                duration = poi.duration,  // ⭐ 保持可空类型
                price = poi.price,  // ⭐ 保持可空类型
                score = poi.score  // ⭐ 保持可空类型
            )
        }
        
        return ImageRecognitionResult(
            success = response.success,
            message = response.message ?: "识别完成",
            ocrText = ocrText,
            places = poiDataList,
            needConfirm = response.needConfirm
        )
    }
    
    /**
     * 处理错误消息（type=error）
     * @param response WebSocket 响应
     * @return 错误信息
     */
    fun handleErrorResponse(response: WebSocketResponse): ErrorMessage {
        val errorCode = response.code ?: 500
        val errorMessage = response.message ?: "未知错误"
        
        Log.e("AgentMessageHandler", "❌ 错误响应: code=$errorCode, message=$errorMessage")
        
        return ErrorMessage(
            code = errorCode,
            message = errorMessage,
            userFriendlyMessage = getUserFriendlyErrorMessage(errorCode, errorMessage)
        )
    }
    
    /**
     * 从响应中提取 OCR 文本
     */
    private fun extractOcrText(response: WebSocketResponse): String {
        // ⭐ 优化：尝试从 data 字段中直接获取 ocrText
        return try {
            // 方法1：如果 response.message 包含 OCR 文本，直接使用
            if (!response.message.isNullOrBlank() && 
                !response.message.contains("为您找到") && 
                !response.message.contains("请选择")) {
                return response.message
            }
            
            // 方法2：尝试从 data 字段解析
            val dataStr = response.data?.toString() ?: ""
            if (dataStr.contains("ocrText")) {
                // 简单提取 ocrText 值
                val regex = """"ocrText"\s*:\s*"([^"]*)"""".toRegex()
                regex.find(dataStr)?.groupValues?.get(1) ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("AgentMessageHandler", "提取 OCR 文本失败", e)
            ""
        }
    }
    
    /**
     * 获取用户友好的错误提示
     */
    private fun getUserFriendlyErrorMessage(code: Int, message: String): String {
        return when {
            code == 400 -> "参数错误，请重试"
            code == 401 -> "登录已过期，请重新登录"
            code == 422 -> "未识别到有效信息，请换一张图片或重新输入"
            code == 500 -> "服务器繁忙，请稍后重试"
            code == 503 -> "服务暂时不可用，请稍后重试"
            message.contains("系统繁忙") || message.contains("EXCEEDED") -> 
                "系统繁忙，请稍后重试"
            else -> message
        }
    }
    
    /**
     * 搜索结果数据类
     */
    data class SearchResult(
        val places: List<PoiData>,
        val message: String,
        val needConfirm: Boolean,
        val suggestions: List<String>
    )
    
    /**
     * 订单结果数据类
     */
    data class OrderResult(
        val success: Boolean,
        val message: String,
        val data: kotlinx.serialization.json.JsonElement
    )
    
    /**
     * 图片识别结果数据类
     */
    data class ImageRecognitionResult(
        val success: Boolean,
        val message: String,
        val ocrText: String,
        val places: List<PoiData>,
        val needConfirm: Boolean
    )
    
    /**
     * 错误信息数据类
     */
    data class ErrorMessage(
        val code: Int,
        val message: String,
        val userFriendlyMessage: String
    )
}
