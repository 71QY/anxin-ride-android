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
    @SerialName("updatedAt") val updatedAt: String? = null // 更新时间
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
    @SerialName("type") val type: String = "CUSTOM"
)
