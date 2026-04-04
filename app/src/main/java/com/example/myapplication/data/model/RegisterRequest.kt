package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 注册请求
 */
data class RegisterRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("code") val code: String,
    @SerializedName("password") val password: String,
    @SerializedName("nickname") val nickname: String? = null
)
