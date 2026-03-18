@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserImage(
    val type: String,
    val sessionId: String,
    val image: String
)