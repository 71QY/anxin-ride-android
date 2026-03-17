package com.example.myapplication.core.network

import com.example.myapplication.data.model.CreateOrderRequest
import com.example.myapplication.data.model.EmergencyContact
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.LoginResponse
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.Result
import com.example.myapplication.data.model.UserProfile
import retrofit2.http.*

interface ApiService {
    // ========== 认证接口 ==========
    @POST("auth/code")
    suspend fun sendCode(@Query("phone") phone: String): Result<Unit>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Result<LoginResponse>

    // ========== 订单接口 ==========
    @POST("order/create")
    suspend fun createOrder(@Body request: CreateOrderRequest): Result<Order>

    // 🔧 新增：查询订单详情
    @GET("order/{id}")
    suspend fun getOrder(@Path("id") orderId: Long): Result<Order>

    // 🔧 新增：取消订单
    @POST("order/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: Long): Result<Unit>

    // ========== 个人中心接口 ==========
    @GET("user/profile")
    suspend fun getUserProfile(): Result<UserProfile>

    @PUT("user/profile")
    suspend fun updateUserProfile(@Body profile: UserProfile): Result<Unit>

    @GET("user/emergency")
    suspend fun getEmergencyContacts(): Result<List<EmergencyContact>>

    @POST("user/emergency")
    suspend fun addEmergencyContact(@Body contact: EmergencyContact): Result<Unit>
}