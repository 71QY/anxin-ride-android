package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class WebSocketMessage(
    val type: String,
    val content: String? = null,
    val data: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * WebSocket 客户端发送的请求
 * ⭐ 与后端对齐：需要 type 字段
 */
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class WebSocketRequest(
    val sessionId: String,         // 会话 ID
    val type: String = "user_message",  // ⭐ 消息类型：user_message / confirm / cancel / image
    val content: String = "",      // 消息内容（图片时为空）
    val imageBase64: String? = null,  // ⭐ 新增：图片 Base64（仅 image 类型使用）
    val lat: Double? = null,       // 纬度（可选）
    val lng: Double? = null,       // 经度（可选）
    val page: Int? = null,         // 页码（可选）
    val pageSize: Int? = null,     // 每页数量（可选）
    val sortByDistance: Boolean? = null,  // 是否按距离排序（可选）
    val destAddress: String? = null,  // ⭐ 目的地地址（create_order 时使用）
    val destLat: Double? = null,   // 目的地纬度（create_order 时使用）
    val destLng: Double? = null,   // 目的地经度（create_order 时使用）
    val startLat: Double? = null,  // 起点纬度（create_order 时使用）
    val startLng: Double? = null   // 起点经度（create_order 时使用）
)

/**
 * WebSocket 服务端返回的响应
 * ⭐ 新增：code 和 success 字段，与后端统一响应格式
 * ⭐ 修复：type 字段改为可选，兼容后端返回的简单消息
 * ⭐ 新增：places 顶层字段，对齐图片识别 API v2.0
 */
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class WebSocketResponse(
    val type: String? = null,      // ⭐ 改为可选：chat_reply, search_result, poi_list, order_created, image_recognition, error
    val message: String = "",      // 文本消息
    val data: JsonElement? = null, // 数据（POI 列表等）
    val ocrText: String? = null,   // OCR 识别结果
    val places: List<PoiResponse>? = null,  // ⭐ 新增：POI 列表顶层字段（图片识别 API v2.0）
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val preciseAddress: String? = null,
    val code: Int? = null,         // ⭐ 状态码
    val success: Boolean? = null,  // ⭐ 是否成功
    val timestamp: Long? = null    // ⭐ 时间戳
)

// ⭐ 已删除重复的 UserMessage 和 UserImage 类（它们有独立的文件）
