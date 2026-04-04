package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class UserImage(
    val id: String,
    val imageUrl: String,
    val description: String? = null,
    val timestamp: Long
)
