package com.example.myapplication.core.datastore

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun getTokenSync(): String? = prefs.getString("token", null)
    fun getUserIdSync(): Long? = prefs.getLong("user_id", -1L).takeIf { it != -1L }

    suspend fun saveToken(token: String, userId: Long) {
        prefs.edit().putString("token", token).putLong("user_id", userId).apply()
    }

    suspend fun clearToken() {
        prefs.edit().remove("token").remove("user_id").apply()
    }
}