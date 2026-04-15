package com.example.myapplication.core.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ⭐ 新增：认证状态管理器
 * 用于管理登录/注册相关的状态，避免 ViewModel 之间互相注入
 */
@Singleton
class AuthStateManager @Inject constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_state_prefs", Context.MODE_PRIVATE)
    private val TAG = "AuthStateManager"
    
    /**
     * 重置所有登录状态（用于退出登录时调用）
     */
    fun resetAllAuthState() {
        Log.d(TAG, "=== 重置所有认证状态 ===")
        prefs.edit().clear().apply()
        Log.d(TAG, "✅ 所有认证状态已重置")
    }
    
    /**
     * 清除发送验证码的时间戳记录
     */
    fun clearSendCodeTimestamps() {
        prefs.edit().remove("send_code_timestamps").apply()
        Log.d(TAG, "✅ 已清除验证码发送记录")
    }
}
