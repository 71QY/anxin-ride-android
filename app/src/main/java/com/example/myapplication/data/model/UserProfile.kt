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
    @SerialName("verified") val verified: Int = 0,
    
    // ⭐ 新增：亲情守护相关字段
    @SerialName("isGuarded") val isGuarded: Int = 0,                   // 是否被守护（长辈视角）0-否 1-是
    @SerialName("guardMode") val guardMode: Int = 0,                   // ⭐ 新增：0普通模式 1长辈精简模式
    @SerialName("guardianName") val guardianName: String? = null,      // 守护者姓名（长辈视角）
    @SerialName("guardedElders") val guardedElders: List<ElderInfo>? = null  // 被守护的长辈列表（亲友视角）
)