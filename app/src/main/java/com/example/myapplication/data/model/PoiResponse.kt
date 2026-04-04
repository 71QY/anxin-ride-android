package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ⭐ 后端 POI 搜索响应（完全对齐后端文档）
 * 对应后端 PoiDTO 数据结构
 */
@Serializable
data class PoiResponse(
    @SerialName("id") val id: String,  // ⭐ 新增：POI 唯一标识
    @SerialName("name") val name: String,
    @SerialName("address") val address: String,
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double,
    @SerialName("distance") val distance: Double? = null,
    @SerialName("type") val type: String? = null,  // ⭐ 新增：地点类型
    @SerialName("duration") val duration: Int? = null,  // ⭐ 新增：路线耗时（秒）
    @SerialName("price") val price: Double? = null,  // ⭐ 新增：预估价格（元）
    @SerialName("score") val score: Double? = null  // ⭐ 新增：评分（0-5 分），直接使用后端返回的评分
)
