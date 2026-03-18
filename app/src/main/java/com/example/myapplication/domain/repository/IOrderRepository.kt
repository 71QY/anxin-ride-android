package com.example.myapplication.domain.repository

import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.PageResponse
import com.example.myapplication.data.model.Result

interface IOrderRepository {
    // 创建订单
    suspend fun createOrder(destName: String, destLat: Double, destLng: Double): Result<Order>

    // 获取订单详情
    suspend fun getOrder(orderId: Long): Result<Order>

    // 取消订单
    suspend fun cancelOrder(orderId: Long): Result<Unit>

    // 获取订单列表（分页）
    suspend fun getOrderList(page: Int, size: Int): Result<PageResponse<Order>>
}