package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * WebSocket 推送消息统一格式
 */
@Serializable
data class WsMessage(
    @SerialName("type") val type: String,           // 消息类型
    @SerialName("success") val success: Boolean? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("orderId") val orderId: Long? = null,
    @SerialName("timestamp") val timestamp: Long? = null,
    
    // ORDER_ACCEPTED 专用字段
    @SerialName("driverName") val driverName: String? = null,
    @SerialName("driverPhone") val driverPhone: String? = null,
    @SerialName("driverAvatar") val driverAvatar: String? = null,
    @SerialName("carNo") val carNo: String? = null,
    @SerialName("carType") val carType: String? = null,
    @SerialName("carColor") val carColor: String? = null,
    @SerialName("rating") val rating: Double? = null,
    @SerialName("driverLat") val driverLat: Double? = null,
    @SerialName("driverLng") val driverLng: Double? = null,
    @SerialName("startLat") val startLat: Double? = null,
    @SerialName("startLng") val startLng: Double? = null,
    @SerialName("destLat") val destLat: Double? = null,
    @SerialName("destLng") val destLng: Double? = null,
    
    // DRIVER_LOCATION 专用字段
    @SerialName("etaMinutes") val etaMinutes: Int? = null,
    
    // DRIVER_ARRIVED 专用字段
    // 复用 driverLat/driverLng
    
    // ORDER_CREATED 专用字段（代叫车）
    @SerialName("data") val data: OrderCreatedData? = null,
    
    // PROXY_ORDER_CONFIRMED 专用字段
    @SerialName("elderUserId") val elderUserId: Long? = null,
    @SerialName("confirmed") val confirmed: Boolean? = null,
    @SerialName("rejectReason") val rejectReason: String? = null,
    @SerialName("confirmTime") val confirmTime: String? = null
) {
    companion object {
        const val TYPE_CONNECTED = "connected"
        const val TYPE_DRIVER_REQUEST = "DRIVER_REQUEST"  // ⭐ 新增：司机接单请求
        const val TYPE_ORDER_ACCEPTED = "ORDER_ACCEPTED"
        const val TYPE_DRIVER_REJECTED = "DRIVER_REJECTED"  // ⭐ 新增：用户拒绝接单
        const val TYPE_DRIVER_LOCATION = "DRIVER_LOCATION"
        const val TYPE_DRIVER_ARRIVED = "DRIVER_ARRIVED"
        const val TYPE_TRIP_STARTED = "TRIP_STARTED"  // ⭐ 新增：行程开始
        const val TYPE_TRIP_COMPLETED = "TRIP_COMPLETED"  // ⭐ 新增：行程完成
        const val TYPE_ORDER_CREATED = "ORDER_CREATED"
        const val TYPE_PROXY_ORDER_CONFIRMED = "PROXY_ORDER_CONFIRMED"
    }
}

/**
 * ORDER_CREATED 消息中的 data 对象
 */
@Serializable
data class OrderCreatedData(
    @SerialName("orderId") val orderId: Long? = null,
    @SerialName("orderNo") val orderNo: String? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("userId") val userId: Long? = null,
    @SerialName("guardianUserId") val guardianUserId: Long? = null,
    @SerialName("destLat") val destLat: Double? = null,
    @SerialName("destLng") val destLng: Double? = null,
    @SerialName("poiName") val poiName: String? = null,
    @SerialName("destAddress") val destAddress: String? = null,
    @SerialName("estimatePrice") val estimatePrice: Double? = null,
    @SerialName("createTime") val createTime: String? = null,
    @SerialName("requesterName") val requesterName: String? = null,
    @SerialName("destination") val destination: String? = null
)
