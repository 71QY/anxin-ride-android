package com.example.myapplication.core.network

import android.util.Log
import com.example.myapplication.MyApplication
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

class AuthInterceptor : Interceptor {
    
    // ⭐ 新增：防止并发刷新 Token
    private var isRefreshing = AtomicBoolean(false)
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = MyApplication.tokenManager.getToken()
        Log.d("AuthInterceptor", "Token: ${token?.take(10)}...")
        
        val newRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        
        val response = chain.proceed(newRequest)
        
        // ⭐ 修改：检测到 401 时尝试刷新 Token
        if (response.code == 401) {
            Log.w("AuthInterceptor", "⚠️ Token expired or invalid, attempting to refresh...")
            
            // 关闭旧响应
            response.close()
            
            // ⭐ 尝试刷新 Token
            val newToken = refreshTokenIfNeeded()
            
            if (newToken != null) {
                Log.d("AuthInterceptor", "✅ Token refreshed successfully, retrying request...")
                
                // 使用新 Token 重试请求
                val retryRequest = originalRequest.newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer $newToken")
                    .build()
                
                return chain.proceed(retryRequest)
            } else {
                Log.e("AuthInterceptor", "❌ Token refresh failed, clearing local token")
                runBlocking {
                    MyApplication.tokenManager.clearToken()
                }
            }
        }
        
        return response
    }
    
    /**
     * ⭐ 刷新 Token（带并发控制）
     * @return 新的 Token，如果刷新失败返回 null
     */
    private fun refreshTokenIfNeeded(): String? {
        // CAS 操作：确保只有一个线程执行刷新
        if (!isRefreshing.compareAndSet(false, true)) {
            Log.d("AuthInterceptor", "⏳ Another thread is already refreshing token, waiting...")
            
            // 等待其他线程完成刷新（最多等待 5 秒）
            var waitTime = 0
            while (isRefreshing.get() && waitTime < 5000) {
                Thread.sleep(100)
                waitTime += 100
            }
            
            // 返回最新的 Token
            return MyApplication.tokenManager.getToken()
        }
        
        return try {
            Log.d("AuthInterceptor", "🔄 Starting token refresh...")
            
            // 同步调用刷新接口
            val result = runBlocking {
                MyApplication.apiService.refreshToken()
            }
            
            if (result.isSuccess() && result.data != null) {
                val newToken = result.data.token
                val newUserId = result.data.userId
                
                // 保存新 Token
                runBlocking {
                    MyApplication.tokenManager.saveToken(newToken, newUserId)
                }
                
                Log.d("AuthInterceptor", "✅ Token refreshed: userId=$newUserId")
                newToken
            } else {
                Log.e("AuthInterceptor", "❌ Token refresh failed: ${result.message}")
                null
            }
        } catch (e: Exception) {
            Log.e("AuthInterceptor", "❌ Token refresh exception", e)
            null
        } finally {
            // 重置刷新标志
            isRefreshing.set(false)
            Log.d("AuthInterceptor", "🔓 Token refresh lock released")
        }
    }
}