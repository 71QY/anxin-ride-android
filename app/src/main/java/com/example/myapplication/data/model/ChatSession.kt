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
) {
    companion object {
        // 模拟数据
        fun getMockSessions(): List<ChatSession> {
            return listOf(
                ChatSession(
                    id = "friend_1",
                    name = "张三",
                    avatar = "👤",
                    lastMessage = "明天一起去吃饭吗？",
                    lastMessageTime = System.currentTimeMillis() - 3600000,
                    unreadCount = 2,
                    isOnline = true
                ),
                ChatSession(
                    id = "friend_2",
                    name = "李四",
                    avatar = "👤",
                    lastMessage = "好的，收到了",
                    lastMessageTime = System.currentTimeMillis() - 86400000,
                    unreadCount = 0,
                    isOnline = false
                ),
                ChatSession(
                    id = "group_1",
                    name = "家庭群",
                    avatar = "👨‍👩‍👧‍👦",
                    lastMessage = "妈妈: 晚上回家吃饭",
                    lastMessageTime = System.currentTimeMillis() - 1800000,
                    unreadCount = 5,
                    memberCount = 6
                ),
                ChatSession(
                    id = "group_2",
                    name = "同事群",
                    avatar = "💼",
                    lastMessage = "王五: 会议改到下午3点",
                    lastMessageTime = System.currentTimeMillis() - 7200000,
                    unreadCount = 0,
                    memberCount = 12
                )
            )
        }
        
        // 根据ID获取模拟消息
        fun getMockMessages(sessionId: String): List<ChatMessage> {
            return when (sessionId) {
                "friend_1" -> listOf(
                    ChatMessage(
                        id = "msg_1",
                        content = "在吗？",
                        isUser = false,
                        timestamp = System.currentTimeMillis() - 7200000
                    ),
                    ChatMessage(
                        id = "msg_2",
                        content = "在的，怎么了？",
                        isUser = true,
                        timestamp = System.currentTimeMillis() - 7000000
                    ),
                    ChatMessage(
                        id = "msg_3",
                        content = "明天一起去吃饭吗？",
                        isUser = false,
                        timestamp = System.currentTimeMillis() - 3600000
                    )
                )
                "friend_2" -> listOf(
                    ChatMessage(
                        id = "msg_4",
                        content = "文件发给你了",
                        isUser = false,
                        timestamp = System.currentTimeMillis() - 172800000
                    ),
                    ChatMessage(
                        id = "msg_5",
                        content = "好的，收到了",
                        isUser = true,
                        timestamp = System.currentTimeMillis() - 86400000
                    )
                )
                "group_1" -> listOf(
                    ChatMessage(
                        id = "msg_6",
                        content = "妈妈: 晚上回家吃饭",
                        isUser = false,
                        timestamp = System.currentTimeMillis() - 1800000
                    ),
                    ChatMessage(
                        id = "msg_7",
                        content = "爸爸: 我也回去",
                        isUser = false,
                        timestamp = System.currentTimeMillis() - 1200000
                    )
                )
                "group_2" -> listOf(
                    ChatMessage(
                        id = "msg_8",
                        content = "王五: 会议改到下午3点",
                        isUser = false,
                        timestamp = System.currentTimeMillis() - 7200000
                    )
                )
                else -> emptyList()
            }
        }
    }
}
