package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class UserMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val imageBase64: String? = null
)
