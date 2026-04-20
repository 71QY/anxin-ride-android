package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.MyApplication
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.CreateOrderRequest
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.Result
import com.example.myapplication.domain.repository.IOrderRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val apiService: ApiService
) : IOrderRepository {

    override suspend fun createOrder(
        poiName: String,
        poiLat: Double,
        poiLng: Double,
        passengerCount: Int,
        remark: String?,
        elderId: Long?,
        startLat: Double?,  // ⭐ 新增：起点纬度（默认值在接口中定义）
        startLng: Double?   // ⭐ 新增：起点经度（默认值在接口中定义）
    ): Result<Order> {
        Log.d("OrderRepository", "=== 开始创建订单 (v2.0 - 已支持起点坐标) ===")
        Log.d("OrderRepository", "参数：poiName=$poiName, poiLat=$poiLat, poiLng=$poiLng, passengerCount=$passengerCount, elderId=$elderId")
        Log.d("OrderRepository", "⭐ 关键参数：startLat=$startLat, startLng=$startLng")
        
        // ⭐ 修改：使用正确的字段名 poiName（与后端文档对齐）
        val request = CreateOrderRequest(
            poiName = poiName,      // ⭐ 目的地名称（必填）
            destLat = poiLat,
            destLng = poiLng,
            passengerCount = passengerCount,
            remark = remark,
            elderId = elderId,  // ⭐ 新增：长辈ID
            startLat = startLat,  // ⭐ 新增：起点纬度
            startLng = startLng   // ⭐ 新增：起点经度
        )
        
        Log.d("OrderRepository", "请求体：$request")
        Log.d("OrderRepository", "请求体 JSON 序列化后：poiName=${request.poiName}, destLat=${request.destLat}, destLng=${request.destLng}")
        
        return try {
            val response = apiService.createOrder(request)
            Log.d("OrderRepository", "响应：code=${response.code}, message=${response.message}")
            
            // ⭐ 修改：直接返回 response
            response
        } catch (e: Exception) {
            Log.e("OrderRepository", "创建订单异常", e)
            // ⭐ 修改：返回错误的 Result
            Result(
                code = -1,
                message = e.message ?: "创建订单失败",
                data = null
            )
        }
    }

    override suspend fun getOrder(orderId: Long): Result<Order> {
        return apiService.getOrder(orderId)
    }

    override suspend fun cancelOrder(orderId: Long): Result<Unit> {
        return apiService.cancelOrder(orderId)
    }

    override suspend fun confirmOrder(orderId: Long): Result<Unit> {
        return apiService.confirmOrder(orderId)
    }

    override suspend fun getOrderList(status: Int?, page: Int, size: Int): Result<com.example.myapplication.data.model.PageResponse<Order>> {
        // ⭐ 修复：获取 userId 并传递给 API
        val userId = MyApplication.tokenManager.getUserId()
        if (userId == null) {
            Log.e("OrderRepository", "❌ 用户未登录，无法获取订单列表")
            return Result(
                code = -1,
                message = "用户未登录",
                data = null
            )
        }
        
        Log.d("OrderRepository", "📦 获取订单列表：userId=$userId, status=$status, page=$page, size=$size")
        return apiService.getOrderList(userId, status, page, size)
    }
}