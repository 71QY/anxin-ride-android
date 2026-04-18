package com.example.myapplication.core.network

import com.example.myapplication.data.model.ChangeNicknameRequest
import com.example.myapplication.data.model.ChangePasswordRequest
import com.example.myapplication.data.model.CompleteProfileRequest
import com.example.myapplication.data.model.CreateOrderRequest
import com.example.myapplication.data.model.EmergencyContact
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.LoginResponse
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.ConfirmDriverRequest  // ⭐ 新增：确认司机接单请求
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
import com.example.myapplication.data.model.AddElderRequest
import com.example.myapplication.data.model.RegisterElderRequest  // ⭐ 新增：帮长辈注册请求
import com.example.myapplication.data.model.BindExistingElderRequest  // ⭐ 新增：绑定已有长辈请求
import com.example.myapplication.data.model.CreateOrderForElderRequest
import com.example.myapplication.data.model.ConfirmProxyOrderRequest  // ⭐ 新增：确认代叫车请求
import com.example.myapplication.data.model.ElderInfo  // ⭐ 新增：亲情守护数据模型
import com.example.myapplication.data.model.GuardianInfo  // ⭐ 新增：亲情守护数据模型
import com.example.myapplication.data.model.OrderChatMessage  // ⭐ 新增：订单聊天消息
import com.example.myapplication.data.model.SendChatMessageRequest  // ⭐ 新增：发送聊天消息请求
import com.example.myapplication.data.model.VoiceToTextResponse  // ⭐ 新增：语音转文字响应
import com.example.myapplication.data.model.TextToSpeechResponse  // ⭐ 新增：文字转语音响应
import com.example.myapplication.data.model.CallDriverResponse  // ⭐ 新增：呼叫司机响应
import com.example.myapplication.data.model.CallGuardianResponse  // ⭐ 新增：呼叫亲友响应
import com.example.myapplication.data.model.PrivateChatMessage  // ⭐ 新增：私聊消息
import com.example.myapplication.data.model.SendPrivateMessageRequest  // ⭐ 新增：发送私聊请求
import com.example.myapplication.data.model.FavoriteLocation  // ⭐ 新增：收藏地点
import com.example.myapplication.data.model.SaveFavoriteRequest  // ⭐ 新增：保存收藏请求
import com.example.myapplication.data.model.ShareFavoriteRequest  // ⭐ 新增：分享收藏请求
import com.example.myapplication.data.model.ConfirmArrivalRequest  // ⭐ 新增：确认到达请求
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
    
    // ⭐ 新增：退出登录接口
    @POST("auth/logout")
    suspend fun logout(): Result<Unit>
    
    // ⭐ 新增：刷新 Token 接口
    @POST("auth/refresh-token")
    suspend fun refreshToken(): Result<LoginResponse>
    
    // ⭐ 新增：完善账号信息接口
    @POST("user/complete-profile")
    suspend fun completeProfile(@Body request: CompleteProfileRequest): Result<Unit>

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

    // ⭐ 新增：确认/拒绝司机接单
    @POST("order/driver/confirm")
    suspend fun confirmDriverAcceptance(
        @Body request: ConfirmDriverRequest
    ): Result<Unit>

    // ⭐ 新增：乘客上车/开始行程
    @POST("order/{id}/board")
    suspend fun passengerBoard(@Path("id") orderId: Long): Result<Unit>

    // ⭐ 新增：到达目的地/完成行程
    @POST("order/{id}/complete")
    suspend fun completeTrip(@Path("id") orderId: Long): Result<Unit>

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

    @Multipart
    @POST("user/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Result<String>  // ⭐ 修复：后端返回 data 是字符串（头像 URL）

    // ⭐ 新增：获取头像图片（不需要认证）
    @GET("user/avatar/{filename}")
    suspend fun getAvatar(@Path("filename") filename: String): okhttp3.ResponseBody

    @GET("user/emergency")
    suspend fun getEmergencyContacts(): Result<List<EmergencyContact>>

    @POST("user/emergency")
    suspend fun addEmergencyContact(@Body contact: EmergencyContact): Result<Unit>

    @DELETE("user/emergency/{id}")
    suspend fun deleteEmergencyContact(@Path("id") id: Long): Result<Unit>

    @POST("user/realname")
    suspend fun realNameAuth(@Body request: RealNameRequest): Result<Unit>

    // ========== 亲情守护接口 (对齐后端 /api/guard/*) ==========

    /**
     * 帮长辈注册账号（v2.0 新接口）⭐
     * POST /api/guard/register-elder
     */
    @POST("guard/register-elder")
    suspend fun registerElder(
        @Header("X-User-Id") userId: Long,
        @Body request: RegisterElderRequest
    ): Result<String>  // ⭐ 修复：后端返回 data 为字符串

    /**
     * 绑定已有长辈账号（v2.0 新接口）⭐
     * POST /api/guard/bind-existing-elder
     */
    @POST("guard/bind-existing-elder")
    suspend fun bindExistingElder(
        @Header("X-User-Id") userId: Long,
        @Body request: BindExistingElderRequest
    ): Result<String>  // ⭐ 修复：后端返回 data 为字符串

    /**
     * 添加长辈（旧接口，保留兼容）
     * POST /api/guard/add
     */
    @POST("guard/add")
    suspend fun addElder(
        @Header("X-User-Id") userId: Long,
        @Body request: AddElderRequest
    ): Result<Long>

    /**
     * 获取我的长辈列表（亲友操作）
     * GET /api/guard/myElders
     */
    @GET("guard/myElders")
    suspend fun getMyElders(
        @Header("X-User-Id") userId: Long
    ): Result<List<ElderInfo>>

    /**
     * 获取我的亲友列表（长辈操作）
     * GET /api/guard/myGuardians
     */
    @GET("guard/myGuardians")
    suspend fun getMyGuardians(
        @Header("X-User-Id") userId: Long
    ): Result<List<GuardianInfo>>

    /**
     * 一键解绑所有亲友（长辈操作）
     * POST /api/guard/unbindAll
     */
    @POST("guard/unbindAll")
    suspend fun unbindAllGuardians(
        @Header("X-User-Id") userId: Long
    ): Result<Unit>

    /**
     * 单条解绑（亲友操作）
     * POST /api/guard/unbindOne/{guardId}
     */
    @POST("guard/unbindOne/{guardId}")
    suspend fun unbindOneGuardian(
        @Header("X-User-Id") userId: Long,
        @Path("guardId") guardId: Long
    ): Result<Unit>

    /**
     * 代叫车（亲友操作）
     * POST /api/guard/proxyOrder
     */
    // ⭐ 修复：代叫车接口，后端可能返回字符串或对象，使用 Any 类型
    @POST("guard/proxyOrder")
    suspend fun createOrderForElder(
        @Header("X-User-Id") userId: Long,
        @Body request: CreateOrderForElderRequest
    ): Result<Any>

    /**
     * ⭐ 新增：长辈确认代叫车
     * POST /api/guard/confirmProxyOrder/{orderId}
     */
    @POST("guard/confirmProxyOrder/{orderId}")
    suspend fun confirmProxyOrder(
        @Header("X-User-Id") userId: Long,
        @Path("orderId") orderId: Long,
        @Body request: ConfirmProxyOrderRequest
    ): Result<Map<String, Any>>

    /**
     * 查询代叫订单列表（亲友操作）
     * GET /api/guard/proxyOrders
     */
    @GET("guard/proxyOrders")
    suspend fun getProxyOrders(
        @Header("X-User-Id") userId: Long
    ): Result<List<Order>>

    /**
     * 查询当前订单（长辈/亲友通用）
     * GET /api/order/current
     */
    @GET("order/current")
    suspend fun getCurrentTrip(
        @Header("X-User-Id") userId: Long
    ): Result<Order?>

    /**
     * 呼叫司机
     * GET /api/guard/callDriver/{orderId}
     */
    @GET("guard/callDriver/{orderId}")
    suspend fun callDriver(
        @Header("X-User-Id") userId: Long,
        @Path("orderId") orderId: Long
    ): Result<CallDriverResponse>

    /**
     * 呼叫亲友（长辈操作）
     * GET /api/guard/callGuardian
     */
    @GET("guard/callGuardian")
    suspend fun callGuardian(
        @Header("X-User-Id") userId: Long
    ): Result<CallGuardianResponse>

    // ========== 订单群聊接口 (对齐后端 /api/chat/*) ==========

    /**
     * 获取订单群聊历史
     * GET /api/chat/order/{orderId}
     */
    @GET("chat/order/{orderId}")
    suspend fun getOrderChatHistory(
        @Header("X-User-Id") userId: Long,
        @Path("orderId") orderId: Long
    ): Result<List<OrderChatMessage>>

    /**
     * 发送聊天消息
     * POST /api/chat/order/{orderId}
     */
    @POST("chat/order/{orderId}")
    suspend fun sendOrderChatMessage(
        @Header("X-User-Id") userId: Long,
        @Path("orderId") orderId: Long,
        @Body request: SendChatMessageRequest
    ): Result<Unit>

    /**
     * 语音转文字
     * POST /api/chat/voiceToText
     */
    @POST("chat/voiceToText")
    suspend fun voiceToText(
        @Header("X-User-Id") userId: Long,
        @Body audioData: String  // Base64编码的音频数据
    ): Result<VoiceToTextResponse>

    /**
     * 文字转语音
     * GET /api/chat/textToSpeech?text=xxx
     */
    @GET("chat/textToSpeech")
    suspend fun textToSpeech(
        @Header("X-User-Id") userId: Long,
        @Query("text") text: String
    ): Result<TextToSpeechResponse>

    // ========== 私聊接口 (对齐后端 /api/chat/private/*) ==========

    /**
     * 获取私聊历史
     * GET /api/chat/private/history/{targetUserId}
     */
    @GET("chat/private/history/{targetUserId}")
    suspend fun getPrivateChatHistory(
        @Header("X-User-Id") userId: Long,
        @Path("targetUserId") targetUserId: Long
    ): Result<List<PrivateChatMessage>>

    /**
     * 发送私聊消息
     * POST /api/chat/private/send/{receiverId}
     */
    @POST("chat/private/send/{receiverId}")
    suspend fun sendPrivateMessage(
        @Header("X-User-Id") userId: Long,
        @Path("receiverId") receiverId: Long,
        @Body request: SendPrivateMessageRequest
    ): Result<String>  // ⭐ 修改：后端返回 data 为字符串（消息ID或成功提示）

    /**
     * 标记已读
     * POST /api/chat/private/read/{senderId}
     */
    @POST("chat/private/read/{senderId}")
    suspend fun markAsRead(
        @Header("X-User-Id") userId: Long,
        @Path("senderId") senderId: Long
    ): Result<Unit>

    /**
     * 查询未读数
     * GET /api/chat/private/unread
     */
    @GET("chat/private/unread")
    suspend fun getUnreadCount(
        @Header("X-User-Id") userId: Long
    ): Result<Map<Long, Int>>  // Map<senderId, unreadCount>

    // ========== 收藏常用地点接口 ==========

    /**
     * 获取收藏列表
     * GET /api/favorites
     */
    @GET("favorites")
    suspend fun getFavorites(): Result<List<FavoriteLocation>>

    /**
     * 添加收藏
     * POST /api/favorites
     */
    @POST("favorites")
    suspend fun addFavorite(
        @Body request: SaveFavoriteRequest
    ): Result<FavoriteLocation>

    /**
     * 更新收藏
     * PUT /api/favorites
     */
    @PUT("favorites")
    suspend fun updateFavorite(
        @Body request: SaveFavoriteRequest
    ): Result<FavoriteLocation>

    /**
     * 删除收藏
     * DELETE /api/favorites/{id}
     */
    @DELETE("favorites/{id}")
    suspend fun deleteFavorite(
        @Path("id") favoriteId: Long
    ): Result<Unit>
    
    /**
     * ⭐ 新增：分享收藏地点给亲友
     * POST /api/favorites/share
     */
    @POST("favorites/share")
    suspend fun shareFavorite(
        @Body request: ShareFavoriteRequest
    ): Result<Unit>
    
    /**
     * ⭐ 新增：确认到达目的地
     * POST /api/favorites/confirm-arrival
     */
    @POST("favorites/confirm-arrival")
    suspend fun confirmArrival(
        @Body request: ConfirmArrivalRequest
    ): Result<Unit>
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
