package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class CreateOrderRequest(
    @SerialName("poiName")
    val poiName: String,  // ⭐ 修改：目的地名称（必填）
    
    @SerialName("destLat")
    val destLat: Double,
    
    @SerialName("destLng")
    val destLng: Double,
    
    @SerialName("passengerCount")
    val passengerCount: Int = 1,
    
    @SerialName("remark")
    val remark: String? = null,
    
    // ⭐ 新增：起点位置（用于代叫车，长辈位置）
    @SerialName("startLat")
    val startLat: Double? = null,
    
    @SerialName("startLng")
    val startLng: Double? = null,
    
    // ⭐ 新增：长辈ID（用于代叫车，指定为谁叫车）
    @SerialName("elderId")
    val elderId: Long? = null
)
