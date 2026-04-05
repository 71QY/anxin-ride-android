package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 智能体模块 Repository（对齐后端文档第三章）
 */
@Singleton
class AgentRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager  // ⭐ 新增：用于获取 userId
) {
    /**
     * 智能搜索目的地
     * @param sessionId 会话 ID（必须保持一致）
     * @param keyword 搜索关键词
     * @param lat 用户纬度
     * @param lng 用户经度
     */
    suspend fun searchDestination(
        sessionId: String,
        keyword: String,
        lat: Double,
        lng: Double
    ): Result<AgentSearchResponse> {
        Log.d("AgentRepository", "=== 开始智能搜索目的地 ===")
        Log.d("AgentRepository", "sessionId=$sessionId, keyword=$keyword, lat=$lat, lng=$lng")
        
        val request = AgentSearchRequest(
            sessionId = sessionId,
            keyword = keyword,
            lat = lat,
            lng = lng
        )
        
        return try {
            // ⭐ 获取 userId
            val userId = tokenManager.getUserId()
            if (userId == null) {
                Log.e("AgentRepository", "❌ UserId 为空，用户未登录")
                return Result(
                    code = -1,
                    message = "用户未登录",
                    data = null
                )
            }
            Log.d("AgentRepository", "UserId: $userId")
            
            val response = apiService.agentSearch(userId, request)
            Log.d("AgentRepository", "响应：code=${response.code}, message=${response.message}")
            Log.d("AgentRepository", "type=${response.data?.type}, needConfirm=${response.data?.needConfirm}")
            response
        } catch (e: Exception) {
            Log.e("AgentRepository", "搜索异常", e)
            Result(
                code = -1,
                message = e.message ?: "搜索失败",
                data = null
            )
        }
    }

    /**
     * 确认选择目的地
     * @param sessionId 会话 ID
     * @param selectedPoiName 选择的 POI 名称
     * @param lat 用户纬度
     * @param lng 用户经度
     */
    suspend fun confirmSelection(
        sessionId: String,
        selectedPoiName: String,
        lat: Double,
        lng: Double
    ): Result<AgentSearchResponse> {
        Log.d("AgentRepository", "=== 开始确认选择 ===")
        Log.d("AgentRepository", "sessionId=$sessionId, selectedPoiName=$selectedPoiName, lat=$lat, lng=$lng")
        
        val request = AgentConfirmRequest(
            sessionId = sessionId,
            selectedPoiName = selectedPoiName,
            lat = lat,
            lng = lng
        )
        
        return try {
            // ⭐ 获取 userId
            val userId = tokenManager.getUserId()
            if (userId == null) {
                Log.e("AgentRepository", "❌ UserId 为空，用户未登录")
                return Result(
                    code = -1,
                    message = "用户未登录",
                    data = null
                )
            }
            Log.d("AgentRepository", "UserId: $userId")
            
            val response = apiService.agentConfirm(userId, request)
            Log.d("AgentRepository", "响应：code=${response.code}, message=${response.message}")
            Log.d("AgentRepository", "type=${response.data?.type}, poi=${response.data?.poi?.name}")
            response
        } catch (e: Exception) {
            Log.e("AgentRepository", "确认异常", e)
            Result(
                code = -1,
                message = e.message ?: "确认失败",
                data = null
            )
        }
    }

    /**
     * 图片识别
     * @param sessionId 会话 ID
     * @param imageBase64 Base64 编码的图片（包含前缀）
     * @param lat 用户纬度
     * @param lng 用户经度
     */
    suspend fun recognizeImage(
        sessionId: String,
        imageBase64: String,
        lat: Double,
        lng: Double
    ): Result<AgentSearchResponse> {
        Log.d("AgentRepository", "=== 开始图片识别 ===")
        Log.d("AgentRepository", "sessionId=$sessionId, imageBase64 length=${imageBase64.length}")
        
        val request = AgentImageRequest(
            sessionId = sessionId,
            imageBase64 = imageBase64,
            lat = lat,
            lng = lng
        )
        
        return try {
            // ⭐ 获取 userId
            val userId = tokenManager.getUserId()
            if (userId == null) {
                Log.e("AgentRepository", "❌ UserId 为空，用户未登录")
                return Result(
                    code = -1,
                    message = "用户未登录",
                    data = null
                )
            }
            Log.d("AgentRepository", "UserId: $userId")
            
            val response = apiService.agentImage(userId, request)
            Log.d("AgentRepository", "响应：code=${response.code}, message=${response.message}")
            Log.d("AgentRepository", "data.type=${response.data?.type}")
            Log.d("AgentRepository", "data.places size=${response.data?.places?.size}")
            Log.d("AgentRepository", "data.candidates size=${response.data?.candidates?.size}")
            Log.d("AgentRepository", "data.poi=${response.data?.poi?.name}")
            response
        } catch (e: Exception) {
            Log.e("AgentRepository", "图片识别异常", e)
            Result(
                code = -1,
                message = e.message ?: "图片识别失败",
                data = null
            )
        }
    }

    /**
     * 更新位置
     * @param sessionId 会话 ID
     * @param lat 纬度
     * @param lng 经度
     */
    suspend fun updateLocation(
        sessionId: String,
        lat: Double,
        lng: Double
    ): Result<Unit> {
        Log.d("AgentRepository", "=== 开始更新位置 ===")
        Log.d("AgentRepository", "sessionId=$sessionId, lat=$lat, lng=$lng")
        
        val request = AgentLocationRequest(
            sessionId = sessionId,
            lat = lat,
            lng = lng
        )
        
        return try {
            val response = apiService.agentLocation(request)
            Log.d("AgentRepository", "响应：code=${response.code}, message=${response.message}")
            response
        } catch (e: Exception) {
            Log.e("AgentRepository", "更新位置异常", e)
            Result(
                code = -1,
                message = e.message ?: "更新位置失败",
                data = null
            )
        }
    }

    /**
     * 清理会话
     * @param sessionId 会话 ID
     */
    suspend fun cleanupSession(sessionId: String): Result<Unit> {
        Log.d("AgentRepository", "=== 开始清理会话 ===")
        Log.d("AgentRepository", "sessionId=$sessionId")
        
        val request = AgentCleanupRequest(sessionId = sessionId)
        
        return try {
            val response = apiService.agentCleanup(request)
            Log.d("AgentRepository", "响应：code=${response.code}, message=${response.message}")
            response
        } catch (e: Exception) {
            Log.e("AgentRepository", "清理会话异常", e)
            Result(
                code = -1,
                message = e.message ?: "清理会话失败",
                data = null
            )
        }
    }
}
