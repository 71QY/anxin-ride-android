package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 私聊消息数据模型（对齐后端 PrivateChatMessage）
 */
@Serializable
data class PrivateChatMessage(
    @SerialName("id") val id: Long,
    @SerialName("senderId") val senderId: Long,
    @SerialName("receiverId") val receiverId: Long,
    @SerialName("messageType") val messageType: Int,  // 1=文字, 2=语音, 3=图片
    @SerialName("content") val content: String,
    @SerialName("isRead") val isRead: Int = 0,  // ⭐ 修复：后端返回 0/1，不是 Boolean
    @SerialName("createdAt") val createdAt: String
)

/**
 * 发送私聊消息请求
 */
@Serializable
data class SendPrivateMessageRequest(
    @SerialName("messageType") val messageType: Int,  // 1=文字, 2=语音, 3=图片
    @SerialName("content") val content: String
)
