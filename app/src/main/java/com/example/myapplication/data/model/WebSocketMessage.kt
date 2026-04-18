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
 * ⭐ 对齐后端文档第2章：请求格式
 */
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class WebSocketRequest(
    val type: String = "chat",             // 消息类型: chat/image/confirm/ping
    val sessionId: String,                 // 会话ID（前端生成，保持同一对话使用相同ID）
    val content: String? = null,           // 用户输入文本（type=chat时必填）
    val imageBase64: String? = null,       // Base64图片数据（type=image时必填）
    val lat: Double? = null,               // 纬度（可选，建议每次都传）
    val lng: Double? = null,               // 经度（可选，建议每次都传）
    val timestamp: Long = System.currentTimeMillis()  // 时间戳（毫秒）
)

/**
 * WebSocket 服务端返回的响应
 * ⭐ 对齐后端文档第3章：响应格式
 */
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class WebSocketResponse(
    val success: Boolean = true,              // 是否成功
    val sessionId: String? = null,            // 会话ID（与请求一致）
    val type: String? = null,                 // 响应类型: chat/search/order/error
    val message: String? = null,              // AI生成的自然语言回复
    val content: String? = null,              // 同message字段（兼容旧版）
    val data: JsonElement? = null,            // 附加数据（根据type不同而不同）
    val places: List<PoiResponse>? = null,    // POI列表（type=search时）
    val needConfirm: Boolean = false,         // 是否需要用户确认（type=search时）
    val code: Int? = null,                    // 错误码（type=error时）
    val timestamp: Long? = null               // 服务器时间戳（毫秒）
)

/**
 * 亲情守护 - WebSocket 推送消息
 * ⭐ 对齐后端文档第五章
 */
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class GuardPushMessage(
    val type: String,                          // NEW_ORDER / CHAT_MESSAGE / FAVORITE_SHARED
    val orderId: Long? = null,                 // 订单ID
    val orderNo: String? = null,               // 订单号
    val destAddress: String? = null,           // 目的地地址
    val proxyUserName: String? = null,         // 代叫人姓名
    val senderId: Long? = null,                // 发送者ID（聊天消息）
    val senderType: Int? = null,               // 发送者类型：1-长辈 2-亲友 3-司机（聊天消息）
    val messageType: Int? = null,              // 消息类型：1-文字 2-语音 3-快捷短语（聊天消息）
    val content: String? = null,               // 消息内容（聊天消息）
    val timestamp: Long = System.currentTimeMillis(),  // 时间戳
    
    // ⭐ 新增：分享收藏地点相关字段
    val favoriteId: Long? = null,              // 收藏ID
    val favoriteName: String? = null,          // 收藏地点名称
    val favoriteAddress: String? = null,       // 收藏地点地址
    val favoriteLatitude: Double? = null,      // 纬度
    val favoriteLongitude: Double? = null,     // 经度
    val favoritePhone: String? = null,         // 电话
    val favoriteDescription: String? = null    // 描述
)

// ⭐ 已删除重复的 UserMessage 和 UserImage 类（它们有独立的文件）
