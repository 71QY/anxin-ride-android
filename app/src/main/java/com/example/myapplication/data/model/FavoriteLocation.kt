package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 收藏地点数据模型
 * 对应后端数据库 user_favorites 表
 */
@Serializable
data class FavoriteLocation(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String,           // 地点名称（如"家"、"市人民医院"）
    @SerialName("address") val address: String,     // 详细地址
    @SerialName("latitude") val latitude: Double,   // 纬度
    @SerialName("longitude") val longitude: Double, // 经度
    @SerialName("type") val type: String = "CUSTOM", // 类型：HOME, COMPANY, HOSPITAL, CUSTOM
    @SerialName("phone") val phone: String? = null,  // ⭐ 联系电话
    @SerialName("description") val description: String? = null,  // ⭐ 简介说明
    @SerialName("updatedAt") val updatedAt: String? = null, // 更新时间
    @SerialName("lastVisitedAt") val lastVisitedAt: String? = null  // ⭐ 最后访问时间（行程凭证）
)

/**
 * 添加/更新收藏请求
 */
@Serializable
data class SaveFavoriteRequest(
    @SerialName("id") val id: Long? = null,         // 更新时必传
    @SerialName("name") val name: String,
    @SerialName("address") val address: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("type") val type: String = "CUSTOM",
    @SerialName("phone") val phone: String? = null,  // ⭐ 联系电话
    @SerialName("description") val description: String? = null  // ⭐ 简介说明
)

/**
 * ⭐ 新增：分享收藏地点给亲友的请求
 */
@Serializable
data class ShareFavoriteRequest(
    @SerialName("favoriteId") val favoriteId: Long,  // 收藏地点ID
    @SerialName("guardianUserId") val guardianUserId: Long  // 亲友用户ID
)

/**
 * ⭐ 新增：确认到达目的地的请求
 */
@Serializable
data class ConfirmArrivalRequest(
    @SerialName("orderId") val orderId: Long,  // 订单ID
    @SerialName("favoriteId") val favoriteId: Long? = null  // 关联的收藏地点ID（可选）
)
