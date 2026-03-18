package com.example.myapplication.data.repository

import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.CreateOrderRequest
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.PageResponse
import com.example.myapplication.data.model.Result
import com.example.myapplication.domain.repository.IOrderRepository
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class OrderRepository @Inject constructor(
    private val api: ApiService
) : IOrderRepository {

    override suspend fun createOrder(destName: String, destLat: Double, destLng: Double): Result<Order> {
        Log.d("OrderRepository", "createOrder: destName=$destName, destLat=$destLat, destLng=$destLng")
        val request = CreateOrderRequest(destName, destLat, destLng)
        return api.createOrder(request)
    }

    override suspend fun getOrder(orderId: Long): Result<Order> {
        return api.getOrder(orderId)
    }

    override suspend fun cancelOrder(orderId: Long): Result<Unit> {
        return api.cancelOrder(orderId)
    }

    override suspend fun getOrderList(page: Int, size: Int): Result<PageResponse<Order>> {
        return api.getOrderList(page, size)
    }
}