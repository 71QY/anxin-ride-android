package com.example.myapplication.data.model

/**
 * ⭐ POI 详情（对齐后端文档）
 */
data class PoiDetail(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distance: Double? = null,
    val duration: Int? = null,
    val price: Int? = null,
    val canOrder: Boolean = true,
    val formattedDistance: String? = null,
    val sessionId: String? = null  // ⭐ 新增：用于后续下单
)
