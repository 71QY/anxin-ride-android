package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 头像上传响应
 */
data class AvatarResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val avatarUrl: String
)
