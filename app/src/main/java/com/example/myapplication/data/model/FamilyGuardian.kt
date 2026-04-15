package com.example.myapplication.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 帮长辈注册账号请求（v2.0 新接口）⭐
 * POST /api/guard/register-elder
 * ⭐ 方案2: 亲友代设密码和昵称，长辈首次登录更简单
 */
@Serializable
data class RegisterElderRequest(
    @SerialName("elderName") val elderName: String,           // 必填：长辈姓名
    @SerialName("elderIdCard") val elderIdCard: String,       // 必填：长辈身份证号（18位）
    @SerialName("elderPhone") val elderPhone: String,         // 必填：长辈手机号（登录用）
    @SerialName("nickname") val nickname: String? = null,     // ⭐ 新增：选填：长辈昵称（默认使用姓名）
    @SerialName("password") val password: String? = null,     // ⭐ 新增：选填：登录密码（10位，包含字母和特殊符号）
    @SerialName("relationship") val relationship: String? = null  // 选填：与长辈关系（仅展示）
)

/**
 * 绑定已有长辈账号请求（v2.0 新接口）
 * POST /api/guard/bind-existing-elder
 */
@Serializable
data class BindExistingElderRequest(
    @SerialName("elderPhone") val elderPhone: String,         // 必填：长辈手机号
    @SerialName("elderName") val elderName: String,           // 必填：长辈姓名
    @SerialName("elderIdCard") val elderIdCard: String        // 必填：长辈身份证号
)

/**
 * 添加长辈请求（旧接口，保留兼容）
 * POST /api/guard/add
 */
@Serializable
data class AddElderRequest(
    @SerialName("elderPhone") val elderPhone: String,           // 必填：长辈手机号
    @SerialName("elderName") val elderName: String? = null,     // 选填：长辈姓名
    @SerialName("guardianName") val guardianName: String,       // 必填：亲友姓名
    @SerialName("guardianIdCard") val guardianIdCard: String    // 必填：亲友身份证号（18位）
)

/**
 * 代叫车请求
 * POST /api/guard/proxyOrder
 */
@Serializable
data class CreateOrderForElderRequest(
    @SerialName("elderId") val elderId: Long,              // ⭐ 必填：长辈的用户ID（不能为null）
    @SerialName("startLat") val startLat: Double? = null,   // ⭐ 新增：起点纬度（可选）
    @SerialName("startLng") val startLng: Double? = null,   // ⭐ 新增：起点经度（可选）
    @SerialName("destLat") val destLat: Double,             // 终点纬度
    @SerialName("destLng") val destLng: Double,             // 终点经度
    @SerialName("destAddress") val destAddress: String,     // ⭐ 必填：目的地名称（不能为null或空字符串）
    @SerialName("needConfirm") val needConfirm: Boolean = true  // ⭐ 可选：是否需要长辈确认，默认true
)

/**
 * 长辈信息
 */
@Serializable
data class ElderInfo(
    @SerialName("userId") val userId: Long?,
    @SerialName("guardId") val guardId: Long,  // ⭐ 新增：绑定关系ID（用于解绑）
    @SerialName("phone") val phone: String,
    @SerialName("name") val name: String,
    @SerialName("status") val status: Int,  // 0-待激活，1-已绑定
    @SerialName("bindTime") val bindTime: String?
)

/**
 * 守护者信息（长辈视角）
 */
@Serializable
data class GuardianInfo(
    @SerialName("userId") val userId: Long,
    @SerialName("phone") val phone: String,
    @SerialName("name") val name: String,
    @SerialName("realName") val realName: String,  // 实名认证姓名
    @SerialName("bindTime") val bindTime: String
)

/**
 * ⭐ 新增：确认代叫车请求
 */
@Serializable
data class ConfirmProxyOrderRequest(
    @SerialName("confirmed") val confirmed: Boolean,  // true-同意，false-拒绝
    @SerialName("rejectReason") val rejectReason: String? = null  // 拒绝原因（可选）
)
