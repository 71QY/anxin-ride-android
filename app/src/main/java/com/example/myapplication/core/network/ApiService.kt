package com.example.myapplication.core.network

import com.example.myapplication.data.model.ChangeNicknameRequest
import com.example.myapplication.data.model.ChangePasswordRequest
import com.example.myapplication.data.model.CreateOrderRequest
import com.example.myapplication.data.model.EmergencyContact
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.LoginResponse
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.PageResponse
import com.example.myapplication.data.model.PoiResponse
import com.example.myapplication.data.model.RealNameRequest
import com.example.myapplication.data.model.RegisterRequest
import com.example.myapplication.data.model.Result
import com.example.myapplication.data.model.UserProfile
import com.example.myapplication.data.model.AvatarResponse
import com.example.myapplication.data.model.AgentSearchRequest
import com.example.myapplication.data.model.AgentConfirmRequest
import com.example.myapplication.data.model.AgentImageRequest
import com.example.myapplication.data.model.AgentLocationRequest
import com.example.myapplication.data.model.AgentCleanupRequest
import com.example.myapplication.data.model.AgentSearchResponse
import com.example.myapplication.data.model.PoiDetailResponse
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {
    // ========== 认证接口 ==========
    @FormUrlEncoded
    @POST("auth/code")
    suspend fun sendCode(@Field("phone") phone: String): Result<Unit>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Result<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Result<LoginResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ChangePasswordRequest): Result<Unit>

    @POST("user/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Result<Unit>

    // ========== 智能体模块 ==========
    /**
     * 智能搜索目的地（WebSocket 方式的 HTTP 版本）
     */
    @POST("agent/search")
    suspend fun agentSearch(
        @Header("X-User-Id") userId: Long,  // ⭐ 新增：必须传递用户 ID
        @Body request: AgentSearchRequest
    ): Result<AgentSearchResponse>

    /**
     * 确认选择目的地
     */
    @POST("agent/confirm")
    suspend fun agentConfirm(
        @Header("X-User-Id") userId: Long,  // ⭐ 新增：必须传递用户 ID
        @Body request: AgentConfirmRequest
    ): Result<AgentSearchResponse>

    /**
     * 图片识别
     */
    @POST("agent/image")
    suspend fun agentImage(
        @Header("X-User-Id") userId: Long,  // ⭐ 新增：必须传递用户 ID
        @Body request: AgentImageRequest
    ): Result<AgentSearchResponse>

    /**
     * 更新位置
     */
    @POST("agent/location")
    suspend fun agentLocation(@Body request: AgentLocationRequest): Result<Unit>

    /**
     * 清理会话
     */
    @POST("agent/cleanup")
    suspend fun agentCleanup(@Body request: AgentCleanupRequest): Result<Unit>

    // ========== 地图模块 ==========
    // ⭐ 智能搜索目的地（主入口）- 简化版，直接返回 POI 列表
    @GET("map/search-destination")
    suspend fun searchDestination(
        @Query("keyword") keyword: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): Result<List<PoiResponse>>

    // ⭐ POI 周边搜索（通用搜索）
    @GET("map/poi/nearby")
    suspend fun searchNearby(
        @Query("keyword") keyword: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("radius") radius: Int = 5000,
        @Query("nationwide") nationwide: Boolean = false
    ): Result<List<PoiResponse>>

    // ⭐ 逆地理编码
    @GET("map/geocode/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): Result<ReverseGeocodeResponse>  // ⭐ 改为结构化响应
    
    // ⭐ 获取 POI 详情和路线（点击地点时调用）- 返回完整信息
    @GET("map/poi/detail")
    suspend fun getPoiDetail(
        @Query("poiName") poiName: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("mode") mode: String = "driving"
    ): Result<PoiDetailResponse>  // ⭐ 改为 PoiDetailResponse

    // ⭐ 路线规划
    @GET("map/route")
    suspend fun getRoute(
        @Query("origin") origin: String,  // ⭐ 格式："lat,lng"
        @Query("destination") destination: String,  // ⭐ 格式："lat,lng"
        @Query("mode") mode: String = "driving"
    ): Result<RouteResponse>
    
    // ⭐ 确认下单（地图选点完成后）
    @FormUrlEncoded
    @POST("map/order/confirm")
    suspend fun confirmOrder(
        @Field("sessionId") sessionId: String,
        @Field("poiName") poiName: String
    ): Result<OrderConfirmResponse>

    @GET("map/poi/search")
    suspend fun searchPoiNationwide(
        @Query("keyword") keyword: String,
        @Query("lat") lat: Double?,
        @Query("lng") lng: Double?,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("nationwide") nationwide: Boolean = true
    ): Result<List<PoiResponse>>

    // ========== 订单接口 ==========
    @POST("order/create")
    suspend fun createOrder(@Body request: CreateOrderRequest): Result<Order>

    @GET("order/{id}")
    suspend fun getOrder(@Path("id") orderId: Long): Result<Order>

    @POST("order/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: Long): Result<Unit>

    @POST("order/{id}/confirm")
    suspend fun confirmOrder(@Path("id") orderId: Long): Result<Unit>

    @GET("order/list")
    suspend fun getOrderList(
        @Query("status") status: Int?,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10
    ): Result<PageResponse<Order>>

    // ========== 用户接口 ==========
    @GET("user/profile")
    suspend fun getUserProfile(): Result<UserProfile>

    @PUT("user/profile")
    suspend fun updateUserProfile(@Body profile: UserProfile): Result<Unit>

    @PUT("user/profile")
    suspend fun updateNickname(@Body profile: UserProfile): Result<Unit>

    @Multipart
    @POST("user/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Result<AvatarResponse>

    @GET("user/emergency")
    suspend fun getEmergencyContacts(): Result<List<EmergencyContact>>

    @POST("user/emergency")
    suspend fun addEmergencyContact(@Body contact: EmergencyContact): Result<Unit>

    @DELETE("user/emergency/{id}")
    suspend fun deleteEmergencyContact(@Path("id") id: Long): Result<Unit>

    @POST("user/realname")
    suspend fun realNameAuth(@Body request: RealNameRequest): Result<Unit>
}

// ⭐ 新增：路线规划响应
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@kotlinx.serialization.Serializable
data class RouteResponse(
    val mode: String,
    val duration: Int,
    val distance: Int,
    val price: Double
)

// ⭐ 新增：订单确认响应（对齐后端文档）
data class OrderConfirmResponse(
    val orderId: String,
    val status: String
)

// ⭐ 新增：逆地理编码响应（对齐后端文档）
data class ReverseGeocodeResponse(
    val province: String,
    val city: String,
    val district: String,
    val township: String,            // ⭐ 街道/乡镇
    val formattedAddress: String     // ⭐ 完整地址
)
