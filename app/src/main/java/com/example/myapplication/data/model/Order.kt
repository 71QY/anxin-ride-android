package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class Order(
    @SerialName("id") val id: Long,
    @SerialName("orderNo") val orderNo: String,
    @SerialName("status") val status: Int,  // ⭐ 0-待确认 1-已确认 2-等待司机 3-司机已接单 4-司机已到达 5-行程中 6-已完成 7-已取消 8-已拒绝
    @SerialName("userId") val userId: Long?,
    @SerialName("driverId") val driverId: Long?,
    @SerialName("guardianUserId") val guardianUserId: Long? = null,  // ⭐ 新增：代叫人ID（亲友）
    @SerialName("destLat") val destLat: Double?,
    @SerialName("destLng") val destLng: Double?,
    @SerialName("poiName") val poiName: String?,
    @SerialName("destAddress") val destAddress: String?,
    @SerialName("platformUsed") val platformUsed: String?,
    @SerialName("platformOrderId") val platformOrderId: String?,
    @SerialName("estimatePrice") val estimatePrice: Double?,
    @SerialName("actualPrice") val actualPrice: Double? = null,
    @SerialName("createTime") val createTime: String,
    @SerialName("confirmTime") val confirmTime: String? = null,  // ⭐ 新增：确认时间
    @SerialName("rejectReason") val rejectReason: String? = null,  // ⭐ 新增：拒绝原因
    @SerialName("remark") val remark: String? = null,
    
    // ⭐ 新增：司机和车辆信息（用于长辈模式显示）
    @SerialName("driverName") val driverName: String? = null,      // 司机姓名
    @SerialName("driverPhone") val driverPhone: String? = null,    // 司机电话
    @SerialName("driverAvatar") val driverAvatar: String? = null,  // 司机头像URL
    @SerialName("carNo") val carNo: String? = null,                // 车牌号
    @SerialName("carType") val carType: String? = null,            // 车型
    @SerialName("carColor") val carColor: String? = null,          // 车辆颜色
    @SerialName("rating") val rating: Double? = null,              // 司机评分
    @SerialName("eta") val eta: Int? = null,                       // 预计到达时间（分钟）
    
    // ⭐ 新增：位置信息（用于地图显示）
    @SerialName("startLat") val startLat: Double? = null,          // 起点纬度
    @SerialName("startLng") val startLng: Double? = null,          // 起点经度
    @SerialName("driverLat") val driverLat: Double? = null,        // 司机当前纬度
    @SerialName("driverLng") val driverLng: Double? = null         // 司机当前经度
) {
    fun getAddress(): String? = poiName ?: destAddress
    
    // ⭐ 新增：获取状态描述
    fun getStatusText(): String {
        return when (status) {
            0 -> "待确认"
            1 -> "已确认"
            2 -> "等待司机接单"
            3 -> "司机已接单"
            4 -> "司机已到达"  // ⭐ 修复：状态4是司机已到达，不是行程中
            5 -> "行程中"      // ⭐ 修复：状态5是行程中，不是已完成
            6 -> "已完成"      // ⭐ 修复：状态6是已完成，不是已取消
            7 -> "已取消"      // ⭐ 修复：状态7是已取消
            8 -> "已拒绝"      // ⭐ 修复：状态8是已拒绝
            else -> "未知状态"
        }
    }
}

// ⭐ 新增：确认/拒绝司机接单请求
@Serializable
data class ConfirmDriverRequest(
    @SerialName("orderId") val orderId: Long,
    @SerialName("accepted") val accepted: Boolean  // true=同意, false=拒绝
)