package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ⭐ 出行记录数据模型(行程凭证)
 * 对应后端数据库 travel_records 表
 */
@Serializable
data class TravelRecord(
    @SerialName("id") val id: Long? = null,
    @SerialName("orderId") val orderId: Long,                    // 订单ID
    @SerialName("favoriteId") val favoriteId: Long? = null,      // 关联的收藏地点ID
    @SerialName("destinationName") val destinationName: String,  // 目的地名称
    @SerialName("destinationAddress") val destinationAddress: String,  // 目的地地址
    @SerialName("destinationLat") val destinationLat: Double,    // 目的地纬度
    @SerialName("destinationLng") val destinationLng: Double,    // 目的地经度
    @SerialName("startTime") val startTime: String,              // 出发时间
    @SerialName("arriveTime") val arriveTime: String? = null,    // 到达时间
    @SerialName("durationMinutes") val durationMinutes: Int? = null,  // 行程时长(分钟)
    @SerialName("distanceMeters") val distanceMeters: Int? = null,    // 行程距离(米)
    @SerialName("status") val status: Int = 0,                   // 状态: 0-进行中, 1-已完成, 2-已取消
    @SerialName("createdAt") val createdAt: String? = null       // 创建时间
)

/**
 * ⭐ 出行记录查询响应
 */
@Serializable
data class TravelRecordsResponse(
    @SerialName("total") val total: Int,
    @SerialName("page") val page: Int,
    @SerialName("size") val size: Int,
    @SerialName("records") val records: List<TravelRecord>
)
