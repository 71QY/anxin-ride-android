package com.example.myapplication

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.core.network.RetrofitClient
import com.example.myapplication.core.utils.AppIconSwitcher
import com.example.myapplication.debug.HiltDebugChecker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {
    companion object {
        lateinit var tokenManager: TokenManager
        lateinit var apiService: ApiService
        private const val TAG = "MyApplication"
        
        var isHiltInitialized = false
            private set
            
        lateinit var instance: MyApplication
            private set
            
        // ⭐ 新增：全局事件总线 - 代叫车请求通知
        private val _proxyOrderRequestEvent = MutableSharedFlow<ProxyOrderRequestEvent>(replay = 0)
        val proxyOrderRequestEvent: SharedFlow<ProxyOrderRequestEvent> = _proxyOrderRequestEvent
        
        // ⭐ 新增：全局事件总线 - 订单创建通知（用于更新 sharedLocation）
        private val _orderCreatedEvent = MutableSharedFlow<OrderCreatedEvent>(replay = 0)
        val orderCreatedEvent: SharedFlow<OrderCreatedEvent> = _orderCreatedEvent
        
        // ⭐ 新增：全局事件总线 - 导航到行程追踪界面
        private val _navigateToOrderTrackingEvent = MutableSharedFlow<Long>(replay = 0)
        val navigateToOrderTrackingEvent: SharedFlow<Long> = _navigateToOrderTrackingEvent
        
        /**
         * ⭐ 发送代叫车请求事件
         */
        suspend fun sendProxyOrderRequest(orderId: Long, requesterName: String, destination: String) {
            Log.d(TAG, "📤 发送代叫车请求事件：orderId=$orderId, from=$requesterName")
            _proxyOrderRequestEvent.emit(ProxyOrderRequestEvent(orderId, requesterName, destination))
        }
        
        /**
         * ⭐ 发送订单创建事件
         */
        suspend fun sendOrderCreatedEvent(orderId: Long, elderId: Long) {
            Log.d(TAG, "📤 发送订单创建事件：orderId=$orderId, elderId=$elderId")
            _orderCreatedEvent.emit(OrderCreatedEvent(orderId, elderId))
        }
        
        /**
         * ⭐ 新增：发送导航到行程追踪界面事件
         */
        suspend fun sendNavigateToOrderTracking(orderId: Long) {
            Log.d(TAG, "📤 发送导航事件：前往行程追踪 orderId=$orderId")
            _navigateToOrderTrackingEvent.emit(orderId)
        }
    }
    
    // ⭐ 新增：全局协程作用域，用于 WebSocket 消息监听
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    @Inject
    lateinit var webSocketClient: com.example.myapplication.core.websocket.ChatWebSocketClient
    
    /**
     * ⭐ 代叫车请求事件数据类
     */
    data class ProxyOrderRequestEvent(
        val orderId: Long,
        val requesterName: String,
        val destination: String
    )
    
    /**
     * ⭐ 订单创建事件数据类
     */
    data class OrderCreatedEvent(
        val orderId: Long,
        val elderId: Long
    )

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "=== MyApplication onCreate started ===")
        try {
            instance = this
            Log.d(TAG, "instance initialized successfully")
            
            isHiltInitialized = true
            Log.d(TAG, "Hilt initialization status: $isHiltInitialized")
            
            tokenManager = TokenManager(this)
            Log.d(TAG, "TokenManager initialized successfully")
            
            // ⭐ 新增：初始化 ApiService（用于 Token 刷新）
            apiService = RetrofitClient.instance
            Log.d(TAG, "ApiService initialized successfully")
            
            // ⭐ 新增：应用启动时检查并清理无效数据
            checkAndClearInvalidData()
            
            // ⭐ 修复：移除启动时的图标恢复逻辑
            // 原因：activity-alias 切换会导致 Activity 重建，影响用户体验
            // 图标切换仅在登录成功后执行，由 LoginViewModel 负责
            
            // ⭐ 新增：启动全局 WebSocket 消息监听器
            startGlobalWebSocketListener()
            
            Log.d(TAG, "=== MyApplication onCreate completed ===")
        } catch (e: Exception) {
            Log.e(TAG, "MyApplication onCreate failed", e)
            isHiltInitialized = false
            throw e
        }
    }
    
    /**
     * ⭐ 新增：检查并清理无效数据（应用启动时调用）
     * 确保退出登录后重新进入应用时，所有用户数据都被清除
     */
    private fun checkAndClearInvalidData() {
        val token = tokenManager.getToken()
        val userId = tokenManager.getUserId()
        
        Log.d(TAG, "🔍 检查本地数据: token=${if (token != null) "存在" else "null"}, userId=$userId")
        
        // 如果 token 或 userId 为空，说明用户已退出或未登录，清除所有残留数据
        if (token.isNullOrBlank() || userId == null) {
            Log.d(TAG, "⚠️ 检测到未登录状态，清除所有残留数据")
            clearAllUserData()
        } else {
            Log.d(TAG, "✅ 检测到有效登录状态，保留用户数据")
        }
    }
    
    /**
     * ⭐ 新增：清除所有用户数据
     */
    private fun clearAllUserData() {
        try {
            // 1. 清除 SharedPreferences 中的所有认证相关数据
            val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.d(TAG, "✅ 已清除 auth_prefs 中的所有数据")
            
            // 2. 清除其他可能的缓存文件（如果有）
            // 这里可以添加其他需要清理的数据存储
            
            Log.d(TAG, "✅ 所有用户数据已清除完毕")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清除用户数据失败", e)
        }
    }
    
    /**
     * ⭐ 新增：应用启动时恢复应用图标
     * 根据本地缓存的 guardMode 自动切换图标
     */
    private fun restoreAppIcon() {
        try {
            val guardMode = tokenManager.getGuardMode()
            val token = tokenManager.getToken()
            
            Log.d(TAG, "🔄 恢复应用图标：guardMode=$guardMode, token=${if (token != null) "存在" else "null"}")
            
            // 如果有 token 且 guardMode=1，则切换到长辈端图标
            if (!token.isNullOrBlank() && guardMode == 1) {
                Log.d(TAG, "🔄 检测到长辈端用户，切换到长辈端图标")
                AppIconSwitcher.switchToElderIcon(this)
            } else {
                Log.d(TAG, "🔄 检测到普通用户或未登录，切换到默认图标")
                AppIconSwitcher.switchToDefaultIcon(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 恢复应用图标失败", e)
        }
    }
    
    /**
     * ⭐ 新增：启动全局 WebSocket 消息监听器
     * 确保即使 ViewModel 被销毁，也能收到并持久化重要消息
     */
    private fun startGlobalWebSocketListener() {
        applicationScope.launch {
            try {
                Log.d(TAG, "🚀 启动全局 WebSocket 消息监听器")
                
                webSocketClient.messages.collect { message ->
                    // ⭐ 检测 FAVORITE_SHARED 消息并持久化
                    if (message.contains("FAVORITE_SHARED")) {
                        Log.d(TAG, "📍 [全局监听] 收到 FAVORITE_SHARED 消息")
                        persistFavoriteSharedMessage(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 全局 WebSocket 监听异常", e)
            }
        }
    }
    
    /**
     * ⭐ 新增：持久化 FAVORITE_SHARED 消息
     */
    private suspend fun persistFavoriteSharedMessage(message: String) {
        try {
            Log.d(TAG, "💾 [全局持久化] 开始解析消息...")
            val jsonFormat = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            
            val pushMessage = jsonFormat.decodeFromString<com.example.myapplication.data.model.GuardPushMessage>(message)
            Log.d(TAG, "💾 [全局持久化] JSON 解析成功")
            
            // ⭐ 关键修复：优先使用 userId（长辈端收到的消息中包含此字段）
            val elderId = pushMessage.userId ?: pushMessage.elderUserId ?: pushMessage.senderId ?: return
            val elderName = pushMessage.proxyUserName ?: "长辈"
            val favoriteName = pushMessage.favoriteName ?: return
            val favoriteAddress = pushMessage.favoriteAddress ?: ""
            val favoriteLat = pushMessage.favoriteLatitude ?: 0.0
            val favoriteLng = pushMessage.favoriteLongitude ?: 0.0
            val elderCurrentLat = pushMessage.elderCurrentLat
            val elderCurrentLng = pushMessage.elderCurrentLng
            val elderLocationTimestamp = pushMessage.elderLocationTimestamp
            
            Log.d(TAG, "💾 [全局持久化] 开始保存到 SharedPreferences...")
            // ⭐ 保存到 SharedPreferences
            val prefs = getSharedPreferences("shared_location_cache", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("elderId_${elderId}", elderId)
                .putString("elderName_${elderId}", elderName)
                .putString("favoriteName_${elderId}", favoriteName)
                .putString("favoriteAddress_${elderId}", favoriteAddress)
                .putFloat("latitude_${elderId}", favoriteLat.toFloat())
                .putFloat("longitude_${elderId}", favoriteLng.toFloat())
                .apply()
            Log.d(TAG, "💾 [全局持久化] 基本信息保存成功")
            
            if (elderCurrentLat != null && elderCurrentLng != null) {
                prefs.edit()
                    .putFloat("elderCurrentLat_${elderId}", elderCurrentLat.toFloat())
                    .putFloat("elderCurrentLng_${elderId}", elderCurrentLng.toFloat())
                    .putLong("elderLocationTimestamp_${elderId}", elderLocationTimestamp ?: 0L)
                    .apply()
                Log.d(TAG, "💾 [全局持久化] 长辈位置保存成功")
            }
            
            Log.d(TAG, "✅ [全局持久化] 已保存分享地点：elderId=$elderId, favorite=$favoriteName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [全局持久化] 保存失败", e)
            e.printStackTrace()
        }
    }
}
