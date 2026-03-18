package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PageResponse<T>(
    val records: List<T>,
    val total: Int,
    val size: Int,
    val current: Int,
    val pages: Int
)