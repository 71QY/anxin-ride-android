package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 头像上传响应
 */
data class AvatarResponse(
    @SerializedName("code") val code: Int = 200,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: Any? = null  // ⭐ 修改：使用 Any? 兼容字符串和对象
)

/**
 * 头像数据（当 data 是对象时使用）
 */
data class AvatarData(
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("success") val success: Boolean = true
)
