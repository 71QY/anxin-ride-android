package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class EmergencyContact(
    val id: Long = 0,
    val name: String,
    val phone: String,
    // ⭐ 新增：relationship 字段
    val relationship: String = "其他"
)