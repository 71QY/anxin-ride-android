package com.example.myapplication

import android.app.Application
import com.example.myapplication.core.datastore.TokenManager

class MyApplication : Application() {
    companion object {
        lateinit var tokenManager: TokenManager
    }

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
    }
}