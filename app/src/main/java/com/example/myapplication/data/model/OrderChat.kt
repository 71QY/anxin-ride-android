package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 订单聊天消息（对齐后端文档）
 */
@Serializable
data class OrderChatMessage(
    @SerialName("id") val id: Long,
    @SerialName("orderId") val orderId: Long,
    @SerialName("senderId") val senderId: Long,
    @SerialName("senderType") val senderType: Int,  // 1-长辈 2-亲友 3-司机
    @SerialName("messageType") val messageType: Int,  // 1-文字 2-语音 3-快捷短语
    @SerialName("content") val content: String,
    @SerialName("createdAt") val createdAt: String
)

/**
 * 发送聊天消息请求
 */
@Serializable
data class SendChatMessageRequest(
    @SerialName("messageType") val messageType: Int,  // 1-文字 2-语音 3-快捷短语
    @SerialName("content") val content: String
)

/**
 * 语音转文字响应
 */
@Serializable
data class VoiceToTextResponse(
    @SerialName("text") val text: String
)

/**
 * 文字转语音响应
 */
@Serializable
data class TextToSpeechResponse(
    @SerialName("audioUrl") val audioUrl: String
)

/**
 * 呼叫司机响应
 */
@Serializable
data class CallDriverResponse(
    @SerialName("driverPhone") val driverPhone: String,
    @SerialName("driverName") val driverName: String
)

/**
 * 呼叫亲友响应
 */
@Serializable
data class CallGuardianResponse(
    @SerialName("guardianPhone") val guardianPhone: String,
    @SerialName("guardianName") val guardianName: String
)
