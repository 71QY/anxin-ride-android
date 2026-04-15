package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 登录响应
 * 与后端对齐：token, userId
 */
data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("userId") val userId: Long,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("isGuarded") val isGuarded: Int = 0,    // ⭐ 新增：是否被守护 0否 1是
    @SerializedName("guardMode") val guardMode: Int = 0,     // ⭐ 新增：0普通模式 1长辈精简模式
    @SerializedName("needSetup") val needSetup: Boolean = false  // ⭐ 修改：是否需要完善账号 true-需要完善 false-已完善
)