package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class PageResponse<T>(
    val list: List<T>,  // ⭐ 改为 list
    val total: Int,
    val size: Int,
    val page: Int  // ⭐ 改为 page
)