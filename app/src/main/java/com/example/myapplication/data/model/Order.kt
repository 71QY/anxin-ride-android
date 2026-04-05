package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class Order(
    @SerialName("id") val id: Long,
    @SerialName("orderNo") val orderNo: String,
    @SerialName("status") val status: Int,
    @SerialName("userId") val userId: Long?,
    @SerialName("driverId") val driverId: Long?,
    @SerialName("destLat") val destLat: Double?,
    @SerialName("destLng") val destLng: Double?,
    @SerialName("poiName") val poiName: String?,
    @SerialName("destAddress") val destAddress: String?,
    @SerialName("platformUsed") val platformUsed: String?,
    @SerialName("platformOrderId") val platformOrderId: String?,
    @SerialName("estimatePrice") val estimatePrice: Double?,
    @SerialName("actualPrice") val actualPrice: Double? = null,
    @SerialName("createTime") val createTime: String,
    @SerialName("remark") val remark: String? = null
) {
    fun getAddress(): String? = poiName ?: destAddress
}