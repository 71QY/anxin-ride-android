package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * ⭐ 标准化 POI 数据模型（与后端完全对齐）
 * 对应后端 PoiDTO 数据结构
 */
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
@Parcelize
data class PoiData(
    val id: String,  // ⭐ 新增：POI 唯一标识
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distance: Double? = null,
    val type: String? = null,  // ⭐ 地点类型
    val duration: Int? = null,  // ⭐ 路线耗时（秒）
    val price: Double? = null,  // ⭐ 预估价格（元）
    val score: Double? = null   // ⭐ 新增：相关性评分（0-1，越高越相关）
) : Parcelable
