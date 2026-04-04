package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

/**
 * 修改昵称请求
 */
data class ChangeNicknameRequest(
    @SerializedName("nickname") val nickname: String
)
