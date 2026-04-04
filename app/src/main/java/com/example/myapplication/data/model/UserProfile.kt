package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class UserProfile(
    @SerialName("id") val id: Long = 0,
    @SerialName("phone") val phone: String = "",
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("realName") val realName: String? = null,
    @SerialName("idCard") val idCard: String? = null,
    // ⭐ 修改：添加 verified 字段，默认为 0 (未认证)
    @SerialName("verified") val verified: Int = 0
)