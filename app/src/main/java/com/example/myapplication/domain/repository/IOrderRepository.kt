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
        remark: String? = null
    ): Result<Order>

    suspend fun getOrder(orderId: Long): Result<Order>

    suspend fun cancelOrder(orderId: Long): Result<Unit>

    suspend fun confirmOrder(orderId: Long): Result<Unit>

    suspend fun getOrderList(status: Int? = null, page: Int, size: Int = 10): Result<PageResponse<Order>>
}