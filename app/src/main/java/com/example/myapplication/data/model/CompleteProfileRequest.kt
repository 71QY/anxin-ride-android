package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 完善账号信息请求
 * POST /api/user/complete-profile
 */
data class CompleteProfileRequest(
    @SerializedName("password") val password: String,
    @SerializedName("nickname") val nickname: String
)
