package com.example.myapplication

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.debug.HiltDebugChecker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    companion object {
        lateinit var tokenManager: TokenManager
        private const val TAG = "MyApplication"
        
        // ⭐ 新增：用于检查 Hilt 是否初始化成功
        var isHiltInitialized = false
            private set
            
        // ⭐ 新增：提供 context 引用
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "=== MyApplication onCreate 开始 ===")
        try {
            instance = this
            Log.d(TAG, "instance 初始化成功")
            
            // ⭐ 检查 Hilt 是否已初始化
            isHiltInitialized = true
            Log.d(TAG, "Hilt 初始化状态: $isHiltInitialized")
            
            // 初始化 TokenManager
            tokenManager = TokenManager(this)
            Log.d(TAG, "TokenManager 初始化成功")
            
            Log.d(TAG, "=== MyApplication onCreate 完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "MyApplication onCreate 失败", e)
            isHiltInitialized = false
            throw e
        }
    }
}