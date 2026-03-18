package com.example.myapplication

import android.app.Application
import com.example.myapplication.core.datastore.TokenManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    companion object {
        lateinit var tokenManager: TokenManager
    }

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
    }
}