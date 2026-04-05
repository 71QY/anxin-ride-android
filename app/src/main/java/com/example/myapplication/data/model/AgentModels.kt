package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 智能体搜索请求（对齐后端文档第三章）
 */
@Serializable
data class AgentSearchRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("keyword") val keyword: String,
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double
)

/**
 * 智能体确认请求
 */
@Serializable
data class AgentConfirmRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("selectedPoiName") val selectedPoiName: String,
    @SerialName("lat") val lat: Double,              // ⭐ 新增：用户纬度
    @SerialName("lng") val lng: Double               // ⭐ 新增：用户经度
)

/**
 * 智能体图片识别请求
 */
@Serializable
data class AgentImageRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("imageBase64") val imageBase64: String,
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double
)

/**
 * 智能体位置更新请求
 */
@Serializable
data class AgentLocationRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double
)

/**
 * 智能体会话清理请求
 */
@Serializable
data class AgentCleanupRequest(
    @SerialName("sessionId") val sessionId: String
)

/**
 * 智能体搜索响应（对齐后端文档）
 */
@Serializable
data class AgentSearchResponse(
    @SerialName("type") val type: String? = null,          // SEARCH / ORDER / CHAT / IMAGE_RECOGNITION
    @SerialName("success") val success: Boolean? = null,   // ⭐ 新增：是否成功
    @SerialName("message") val message: String? = null,
    @SerialName("places") val places: List<PoiResponse>? = null,  // ⭐ 顶层 places
    @SerialName("candidates") val candidates: List<PoiResponse>? = null,
    @SerialName("needConfirm") val needConfirm: Boolean = false,
    @SerialName("poi") val poi: PoiResponse? = null,
    @SerialName("route") val route: RouteInfo? = null,
    @SerialName("data") val data: ImageRecognitionData? = null  // ⭐ 新增：嵌套的 data 对象（图片识别专用）
)

/**
 * 图片识别嵌套数据（对齐后端文档 data.data 结构）
 */
@Serializable
data class ImageRecognitionData(
    @SerialName("ocrText") val ocrText: String? = null,     // OCR 识别的文字
    @SerialName("places") val places: List<PoiResponse>? = null,  // 候选地点列表
    @SerialName("order") val order: OrderInfo? = null,      // 订单信息（如果直接下单）
    @SerialName("message") val message: String? = null      // 提示信息
)

/**
 * 订单信息（图片识别直接下单时返回）
 */
@Serializable
data class OrderInfo(
    @SerialName("orderId") val orderId: Long? = null,
    @SerialName("orderNo") val orderNo: String? = null,
    @SerialName("destName") val destName: String? = null,
    @SerialName("destLat") val destLat: Double? = null,
    @SerialName("destLng") val destLng: Double? = null,
    @SerialName("distance") val distance: Double? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("price") val price: Double? = null
)

/**
 * 路线信息
 */
@Serializable
data class RouteInfo(
    @SerialName("distance") val distance: Int,      // 米
    @SerialName("duration") val duration: Int,      // 秒
    @SerialName("price") val price: Double          // 元
)

/**
 * POI 详情响应（地图点击选点）
 */
@Serializable
data class PoiDetailResponse(
    @SerialName("poi") val poi: PoiResponse,
    @SerialName("route") val route: RouteInfo,
    @SerialName("canOrder") val canOrder: Boolean,
    @SerialName("sessionId") val sessionId: String   // ⭐ 重要：后续下单需要传递
)
