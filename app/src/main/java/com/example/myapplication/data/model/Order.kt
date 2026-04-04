package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class Order(
    @SerialName("id") val id: Long,
    @SerialName("orderNo") val orderNo: String,
    @SerialName("status") val status: Int,  // ⭐ 改为 Int，后端返回 0,1,2...
    @SerialName("poiName") val poiName: String?,  // ⭐ 允许 null
    @SerialName("poiAddress") val poiAddress: String?,  // ⭐ 允许 null
    @SerialName("destAddress") val destAddress: String?,  // ⭐ 新增：兼容后端的 dest_address
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double,
    @SerialName("passengerCount") val passengerCount: Int = 1,
    @SerialName("estimatedPrice") val estimatedPrice: Double,
    @SerialName("actualPrice") val actualPrice: Double? = null,
    @SerialName("createTime") val createTime: String,
    @SerialName("remark") val remark: String? = null
) {
    // ⭐ 提供兼容性方法获取地址
    fun getAddress(): String? = poiAddress ?: destAddress
}