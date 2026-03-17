package com.example.myapplication.data.model

data class Order(
    val id: Long,
    val orderNo: String,
    val userId: Long,
    val driverId: Long?,
    val destLat: Double,
    val destLng: Double,
    val destAddress: String,
    val status: Int,
    val platformUsed: String,
    val estimatePrice: Double,
    val createTime: String
)