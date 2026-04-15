package com.example.myapplication

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.core.network.RetrofitClient
import com.example.myapplication.debug.HiltDebugChecker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

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
        
        /**
         * ⭐ 发送代叫车请求事件
         */
        suspend fun sendProxyOrderRequest(orderId: Long, requesterName: String, destination: String) {
            Log.d(TAG, "📤 发送代叫车请求事件：orderId=$orderId, from=$requesterName")
            _proxyOrderRequestEvent.emit(ProxyOrderRequestEvent(orderId, requesterName, destination))
        }
    }
    
    /**
     * ⭐ 代叫车请求事件数据类
     */
    data class ProxyOrderRequestEvent(
        val orderId: Long,
        val requesterName: String,
        val destination: String
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
}