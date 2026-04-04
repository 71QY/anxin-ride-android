package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 登录响应
 * 与后端对齐：token, userId
 */
data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("userId") val userId: Long,
    @SerializedName("phone") val phone: String? = null
)