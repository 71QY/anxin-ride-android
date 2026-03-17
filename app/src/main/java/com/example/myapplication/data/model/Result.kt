package com.example.myapplication.data.model

data class Result<T>(
    val code: Int,
    val message: String,
    val data: T?
) {
    fun isSuccess() = code == 0
}