package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

/**
 * 头像上传响应
 */
data class AvatarResponse(
    @SerializedName("code") val code: Int = 200,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: JsonElement? = null  // ⭐ 修改：使用 JsonElement 兼容所有类型
) {
    /**
     * 获取 data 的字符串值（兼容字符串和对象）
     */
    fun getDataAsString(): String? {
        return when {
            data == null -> null
            data.isJsonPrimitive -> data.asString
            data.isJsonObject -> {
                // 尝试从对象中提取 avatarUrl 或 url 字段
                val obj = data.asJsonObject
                obj.get("avatarUrl")?.asString 
                    ?: obj.get("url")?.asString
                    ?: obj.toString()
            }
            else -> data.toString()
        }
    }
}
