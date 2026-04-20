package com.example.myapplication.domain.repository

import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.PageResponse
import com.example.myapplication.data.model.Result

interface IOrderRepository {
    suspend fun createOrder(
        poiName: String,
        poiLat: Double,  // ⭐ 参数名改为 destLat 的语义
        poiLng: Double,  // ⭐ 参数名改为 destLng 的语义
        passengerCount: Int = 1,
        remark: String? = null,
        elderId: Long? = null,  // ⭐ 新增：长辈ID（用于代叫车）
        startLat: Double? = null,  // ⭐ 新增：起点纬度（用于代叫车）
        startLng: Double? = null   // ⭐ 新增：起点经度（用于代叫车）
    ): Result<Order>

    suspend fun getOrder(orderId: Long): Result<Order>

    suspend fun cancelOrder(orderId: Long): Result<Unit>

    suspend fun confirmOrder(orderId: Long): Result<Unit>

    suspend fun getOrderList(status: Int? = null, page: Int, size: Int = 10): Result<PageResponse<Order>>
}