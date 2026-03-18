package com.example.myapplication.data.model

/**
 * 聊天消息数据类
 * @param id 消息唯一ID
 * @param content 文本内容
 * @param isUser 是否为用户发送（true-用户，false-智能体）
 * @param timestamp 时间戳
 * @param suggestions 智能体回复的建议按钮列表（可选）
 * @param imageBase64 图片的Base64编码（仅用户发送图片时使用）
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val suggestions: List<String>? = null,
    val imageBase64: String? = null
)