package com.example.myapplication.data.model

data class CreateOrderRequest(
    val destName: String,
    val destLat: Double,
    val destLng: Double
)