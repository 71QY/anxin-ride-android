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
        
        try {
            instance = this
            // ⭐ 优化：减少不必要的日志输出，提升启动速度
            
            // ⭐ 检查 Hilt 是否已初始化
            isHiltInitialized = true
            
            // 初始化 TokenManager
            tokenManager = TokenManager(this)
        } catch (e: Exception) {
            isHiltInitialized = false
            throw e
        }
    }
}