package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("code") val code: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("loginType") val loginType: String
) {
    companion object {
        const val TYPE_CODE = "code"
        const val TYPE_PASSWORD = "password"
    }
}
