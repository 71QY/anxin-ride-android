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
        
        var isHiltInitialized = false
            private set
            
        lateinit var instance: MyApplication
            private set
    }

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
            
            Log.d(TAG, "=== MyApplication onCreate completed ===")
        } catch (e: Exception) {
            Log.e(TAG, "MyApplication onCreate failed", e)
            isHiltInitialized = false
            throw e
        }
    }
}