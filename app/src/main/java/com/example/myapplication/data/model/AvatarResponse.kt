package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 头像上传响应
 */
data class AvatarResponse(
    @SerializedName("code") val code: Int = 200,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: String? = null  // ⭐ 修改：直接使用 String 类型，避免 Gson 反序列化异常
)

/**
 * 头像数据（当 data 是对象时使用）
 */
data class AvatarData(
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("success") val success: Boolean = true
)
