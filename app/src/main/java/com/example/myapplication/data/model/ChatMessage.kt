package com.example.myapplication.data.model

data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,          // true-用户, false-智能体
    val timestamp: Long,
    val suggestions: List<String>? = null  // 智能体返回的可选建议按钮
)