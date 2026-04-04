package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 实名认证请求
 */
data class RealNameRequest(
    @SerializedName("realName") val realName: String,
    @SerializedName("idCard") val idCard: String
)
