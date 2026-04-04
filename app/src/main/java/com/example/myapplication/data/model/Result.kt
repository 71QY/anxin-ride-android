package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class Result<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String = "success",
    @SerializedName("data") val data: T? = null
) {
    fun isSuccess() = code == 200
}