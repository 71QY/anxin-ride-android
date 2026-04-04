package com.example.myapplication.core.network

import android.util.Log
import com.example.myapplication.MyApplication
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = MyApplication.tokenManager.getTokenSync()
        Log.d("AuthInterceptor", "Token: ${token?.take(10)}...")  // ⭐ 脱敏显示
        
        val newRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        
        val response = chain.proceed(newRequest)
        
        // ⭐ 修改：检测 401 响应，同步清除 Token（拦截器中不能使用协程）
        if (response.code == 401) {
            Log.e("AuthInterceptor", "Token 已过期或无效，清除本地 Token")
            runBlocking {
                MyApplication.tokenManager.clearToken()
            }
        }
        
        return response
    }
}