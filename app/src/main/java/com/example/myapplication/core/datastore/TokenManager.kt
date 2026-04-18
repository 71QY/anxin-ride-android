package com.example.myapplication.core.datastore

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // ⭐ 统一命名：移除 Sync 后缀，所有方法均为同步读取
    fun getToken(): String? = prefs.getString("token", null)
    fun getUserId(): Long? = prefs.getLong("user_id", -1L).takeIf { it != -1L }
    fun getGuardMode(): Int = prefs.getInt("guard_mode", 0)

    suspend fun getTokenAsync(): String? = getToken()
    suspend fun getUserIdAsync(): Long? = getUserId()
    suspend fun getGuardModeAsync(): Int = getGuardMode()

    suspend fun saveToken(token: String, userId: Long) {
        prefs.edit().putString("token", token).putLong("user_id", userId).apply()
    }
    
    // ⭐ 新增：保存长辈模式标识
    suspend fun saveGuardMode(guardMode: Int) {
        prefs.edit().putInt("guard_mode", guardMode).apply()
    }

    suspend fun clearToken() {
        // ⭐ 修复：只清除 token，保留 user_id 和 guard_mode
        // 避免 Token 刷新失败时丢失用户信息，导致功能异常
        prefs.edit().remove("token").apply()
    }
    
    // ⭐ 新增：同步清除 Token（用于回调中）
    fun clearTokenSync() {
        // ⭐ 修复：只清除 token，保留 user_id 和 guard_mode
        prefs.edit().remove("token").apply()
    }
}