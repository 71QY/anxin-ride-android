package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.FavoriteLocation
import com.example.myapplication.data.model.SaveFavoriteRequest
import com.example.myapplication.data.model.ShareFavoriteRequest  // ⭐ 新增：分享收藏请求
import com.example.myapplication.data.model.ConfirmArrivalRequest  // ⭐ 新增：确认到达请求
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收藏地点仓库类
 * 负责处理收藏相关的网络请求和数据管理
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val apiService: ApiService
) {
    private val TAG = "FavoritesRepository"

    /**
     * 获取收藏列表
     */
    suspend fun getFavorites(): Result<List<FavoriteLocation>> {
        return try {
            Log.d(TAG, "📋 获取收藏列表")
            val response = apiService.getFavorites()
            
            if (response.isSuccess()) {
                val favorites = response.data ?: emptyList()
                Log.d(TAG, "✅ 获取成功，共 ${favorites.size} 个收藏")
                Result.success(favorites)
            } else {
                Log.e(TAG, "❌ 获取失败：${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取收藏列表异常", e)
            Result.failure(e)
        }
    }

    /**
     * 添加收藏
     */
    suspend fun addFavorite(request: SaveFavoriteRequest): Result<FavoriteLocation> {
        return try {
            Log.d(TAG, "➕ 添加收藏：${request.name}")
            val response = apiService.addFavorite(request)
            
            if (response.isSuccess()) {
                val favorite = response.data
                Log.d(TAG, "✅ 添加成功：$favorite")
                Result.success(favorite!!)
            } else {
                Log.e(TAG, "❌ 添加失败：${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 添加收藏异常", e)
            Result.failure(e)
        }
    }

    /**
     * 更新收藏
     */
    suspend fun updateFavorite(request: SaveFavoriteRequest): Result<FavoriteLocation> {
        return try {
            Log.d(TAG, "✏️ 更新收藏：ID=${request.id}, 名称=${request.name}")
            val response = apiService.updateFavorite(request)
            
            if (response.isSuccess()) {
                val favorite = response.data
                Log.d(TAG, "✅ 更新成功：$favorite")
                Result.success(favorite!!)
            } else {
                Log.e(TAG, "❌ 更新失败：${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 更新收藏异常", e)
            Result.failure(e)
        }
    }

    /**
     * 删除收藏
     */
    suspend fun deleteFavorite(favoriteId: Long): Result<Unit> {
        return try {
            Log.d(TAG, "🗑️ 删除收藏：ID=$favoriteId")
            val response = apiService.deleteFavorite(favoriteId)
            
            if (response.isSuccess()) {
                Log.d(TAG, "✅ 删除成功")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ 删除失败：${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除收藏异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * ⭐ 新增：分享收藏地点给亲友
     */
    suspend fun shareFavorite(request: ShareFavoriteRequest): Result<Unit> {
        return try {
            Log.d(TAG, "📤 分享收藏地点：favoriteId=${request.favoriteId}, guardianUserId=${request.guardianUserId}")
            val response = apiService.shareFavorite(request)
            
            if (response.isSuccess()) {
                Log.d(TAG, "✅ 分享成功")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ 分享失败：${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 分享收藏异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * ⭐ 新增：确认到达目的地
     */
    suspend fun confirmArrival(request: ConfirmArrivalRequest): Result<Unit> {
        return try {
            Log.d(TAG, "✅ 确认到达：orderId=${request.orderId}, favoriteId=${request.favoriteId}")
            val response = apiService.confirmArrival(request)
            
            if (response.isSuccess()) {
                Log.d(TAG, "✅ 确认到达成功")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ 确认到达失败：${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 确认到达异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * ⭐ 新增：分享收藏地点给长辈（添加到长辈的收藏列表）
     */
    suspend fun shareFavoriteToElder(request: ShareFavoriteRequest): Result<Unit> {
        return try {
            Log.d(TAG, "📤 分享收藏到长辈: favoriteId=${request.favoriteId}, elderUserId=${request.elderUserId}")
            val response = apiService.shareFavoriteToElder(request)
            
            // ⭐ 调试：打印完整响应
            Log.d(TAG, "📥 后端响应: code=${response.code}, message=${response.message}, data=${response.data}")
            
            if (response.isSuccess()) {
                // ⭐ 修复：检查message是否包含错误信息
                if (response.message.contains("系统繁忙") || response.message.contains("失败") || response.message.contains("错误")) {
                    Log.e(TAG, "❌ 分享失败（code=200但message异常）：${response.message}")
                    Result.failure(Exception(response.message))
                } else {
                    Log.d(TAG, "✅ 分享成功，已添加到长辈收藏列表")
                    Result.success(Unit)
                }
            } else {
                Log.e(TAG, "❌ 分享失败：${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 分享收藏到长辈异常", e)
            Result.failure(e)
        }
    }
}
