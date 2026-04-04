package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 修改密码请求
 */
data class ChangePasswordRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("code") val code: String,
    @SerializedName("newPassword") val newPassword: String
)

