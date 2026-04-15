package com.example.myapplication.data.model

/**
 * 聊天会话数据类
 */
data class ChatSession(
    val id: String,
    val name: String,              // 聊天名称（亲友名/群聊名）
    val avatar: String? = null,    // 头像URL或标识
    val lastMessage: String,       // 最后一条消息
    val lastMessageTime: Long,     // 最后消息时间
    val unreadCount: Int = 0,      // 未读消息数
    val isOnline: Boolean = false, // 是否在线（仅好友）
    val memberCount: Int? = null   // 成员数（仅群聊）
)
