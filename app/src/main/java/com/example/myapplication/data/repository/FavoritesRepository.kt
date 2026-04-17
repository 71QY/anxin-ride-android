package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.FavoriteLocation
import com.example.myapplication.data.model.SaveFavoriteRequest
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
}
