package com.example.myapplication.data.model

data class UserProfile(
    val id: Long? = null,
    val phone: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null
)