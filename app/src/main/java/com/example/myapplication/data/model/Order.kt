package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class Order(
    @SerialName("id") val id: Long,
    @SerialName("orderNo") val orderNo: String,
    @SerialName("status") val status: Int,  // ⭐ 改为 Int，后端返回 0,1,2...
    @SerialName("userId") val userId: Long?,  // ⭐ 新增：用户 ID
    @SerialName("driverId") val driverId: Long?,  // ⭐ 新增：司机 ID
    @SerialName("destLat") val destLat: Double?,  // ⭐ 修改：对应后端的 dest_lat
    @SerialName("destLng") val destLng: Double?,  // ⭐ 修改：对应后端的 dest_lng
    @SerialName("poiName") val poiName: String?,  // ⭐ 允许 null（与 destAddress 相同）
    @SerialName("destAddress") val destAddress: String?,  // ⭐ 后端标准字段
    @SerialName("platformUsed") val platformUsed: String?,  // ⭐ 新增：使用的平台
    @SerialName("platformOrderId") val platformOrderId: String?,  // ⭐ 新增：第三方订单号
    @SerialName("estimatePrice") val estimatePrice: Double?,  // ⭐ 修改：对应后端的 estimate_price
    @SerialName("actualPrice") val actualPrice: Double? = null,
    @SerialName("createTime") val createTime: String,
    @SerialName("remark") val remark: String? = null
) {
    // ⭐ 提供兼容性方法获取地址
    fun getAddress(): String? = poiName ?: destAddress
}