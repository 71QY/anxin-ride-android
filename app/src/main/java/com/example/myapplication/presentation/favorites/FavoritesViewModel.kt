package com.example.myapplication.presentation.favorites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.FavoriteLocation
import com.example.myapplication.data.model.PoiResponse
import com.example.myapplication.data.model.SaveFavoriteRequest
import com.example.myapplication.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 收藏地点 ViewModel
 * 管理收藏列表的状态和业务逻辑
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: FavoritesRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val TAG = "FavoritesViewModel"

    // 收藏列表状态
    private val _favorites = MutableStateFlow<List<FavoriteLocation>>(emptyList())
    val favorites: StateFlow<List<FavoriteLocation>> = _favorites

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    // ⭐ 新增：搜索结果
    private val _searchResults = MutableStateFlow<List<PoiResponse>>(emptyList())
    val searchResults: StateFlow<List<PoiResponse>> = _searchResults
    
    // ⭐ 新增：搜索状态
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching
    
    // ⭐ 新增：搜索关键词
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword
    
    // ⭐ 新增：从首页传来的待添加 POI
    private val _poiForAdd = MutableStateFlow<Triple<String, Double, Double>?>(null)
    val poiForAdd: StateFlow<Triple<String, Double, Double>?> = _poiForAdd

    /**
     * 刷新收藏列表
     */
    fun refreshFavorites() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "🔄 开始刷新收藏列表")
                val result = repository.getFavorites()
                
                result.onSuccess { favorites ->
                    _favorites.value = favorites
                    Log.d(TAG, "✅ 刷新成功，共 ${favorites.size} 个收藏")
                }.onFailure { error ->
                    _errorMessage.value = error.message ?: "获取收藏列表失败"
                    Log.e(TAG, "❌ 刷新失败：${error.message}")
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络异常"
                Log.e(TAG, "❌ 刷新异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 添加收藏
     */
    fun addFavorite(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        type: String = "CUSTOM",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "➕ 添加收藏：$name")
                val request = SaveFavoriteRequest(
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                    type = type
                )
                
                val result = repository.addFavorite(request)
                
                result.onSuccess { favorite ->
                    Log.d(TAG, "✅ 添加成功，直接使用返回对象更新列表")
                    // ⭐ 优化：直接将新收藏添加到列表头部，无需重新请求
                    _favorites.value = listOf(favorite) + _favorites.value
                    onSuccess()
                }.onFailure { error ->
                    // ⭐ 处理特殊错误码
                    val errorMsg = when {
                        error.message?.contains("429") == true || error.message?.contains("超限") == true -> {
                            "收藏数量已达上限（最多50个）"
                        }
                        else -> error.message ?: "添加收藏失败"
                    }
                    _errorMessage.value = errorMsg
                    Log.e(TAG, "❌ 添加失败：${error.message}")
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络异常"
                Log.e(TAG, "❌ 添加异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 更新收藏
     */
    fun updateFavorite(
        id: Long,
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        type: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "✏️ 更新收藏：ID=$id")
                val request = SaveFavoriteRequest(
                    id = id,
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                    type = type
                )
                
                val result = repository.updateFavorite(request)
                
                result.onSuccess { updatedFavorite ->
                    Log.d(TAG, "✅ 更新成功，直接使用返回对象更新列表")
                    // ⭐ 优化：直接在列表中替换更新后的对象，无需重新请求
                    _favorites.value = _favorites.value.map { fav ->
                        if (fav.id == id) updatedFavorite else fav
                    }
                    onSuccess()
                }.onFailure { error ->
                    // ⭐ 处理特殊错误码
                    val errorMsg = when {
                        error.message?.contains("403") == true || error.message?.contains("无权") == true -> {
                            "无权修改此收藏"
                        }
                        else -> error.message ?: "更新收藏失败"
                    }
                    _errorMessage.value = errorMsg
                    Log.e(TAG, "❌ 更新失败：${error.message}")
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络异常"
                Log.e(TAG, "❌ 更新异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 删除收藏
     */
    fun deleteFavorite(favoriteId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "🗑️ 删除收藏：ID=$favoriteId")
                val result = repository.deleteFavorite(favoriteId)
                
                result.onSuccess {
                    Log.d(TAG, "✅ 删除成功")
                    // 重新加载列表
                    refreshFavorites()
                    onSuccess()
                }.onFailure { error ->
                    _errorMessage.value = error.message ?: "删除收藏失败"
                    Log.e(TAG, "❌ 删除失败：${error.message}")
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络异常"
                Log.e(TAG, "❌ 删除异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    // ⭐ 新增：设置从首页传来的 POI
    fun setPoiForAdd(name: String, lat: Double, lng: Double) {
        Log.d(TAG, "📍 收到从首页传来的 POI：name=$name, lat=$lat, lng=$lng")
        _poiForAdd.value = Triple(name, lat, lng)
    }
    
    // ⭐ 新增：清除待添加的 POI
    fun clearPoiForAdd() {
        _poiForAdd.value = null
    }
    
    // ⭐ 新增：搜索 POI（使用与首页相同的搜索策略）
    fun searchPoi(keyword: String, lat: Double? = null, lng: Double? = null) {
        viewModelScope.launch {
            if (keyword.isBlank()) {
                _searchResults.value = emptyList()
                _searchKeyword.value = ""
                return@launch
            }
            
            _isSearching.value = true
            _searchKeyword.value = keyword
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "🔍 搜索 POI：$keyword, lat=$lat, lng=$lng")
                
                // ⭐ 修复：简化关键词，去除"校区"、"区"等后缀
                val simplifiedKeyword = keyword.replace(Regex("(校区|区|分校|分院)$"), "")
                
                // ⭐ 修复：优先使用周边搜索，失败后降级全国搜索
                var searchSuccess = false
                
                // 第一步：尝试周边搜索
                if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                    Log.d(TAG, "🔍 第一步：尝试周边搜索（lat=$lat, lng=$lng）")
                    val nearbyResult = apiService.searchNearby(
                        keyword = simplifiedKeyword,
                        lat = lat,
                        lng = lng,
                        page = 1,
                        pageSize = 20,
                        radius = 50000,
                        nationwide = false
                    )
                    
                    if (nearbyResult.isSuccess() && !nearbyResult.data.isNullOrEmpty()) {
                        _searchResults.value = nearbyResult.data!!
                        Log.d(TAG, "✅ 周边搜索成功：${_searchResults.value.size} 个结果")
                        searchSuccess = true
                    } else {
                        Log.w(TAG, "⚠️ 周边搜索无结果，尝试全国搜索")
                    }
                } else {
                    Log.d(TAG, "🌍 无定位坐标，跳过周边搜索，直接全国搜索")
                }
                
                // 第二步：如果周边搜索失败，使用全国搜索
                if (!searchSuccess) {
                    Log.d(TAG, "🌍 第二步：使用全国搜索模式")
                    
                    // ⭐ 修复：全国搜索也尝试 searchNearby（lat=0, lng=0），因为 searchPoiNationwide 可能不工作
                    val nationwideResult = apiService.searchNearby(
                        keyword = simplifiedKeyword,
                        lat = 0.0,
                        lng = 0.0,
                        page = 1,
                        pageSize = 20,
                        radius = 50000,
                        nationwide = true
                    )
                    
                    if (nationwideResult.isSuccess() && !nationwideResult.data.isNullOrEmpty()) {
                        _searchResults.value = nationwideResult.data!!
                        Log.d(TAG, "✅ 全国搜索成功：${_searchResults.value.size} 个结果")
                    } else {
                        // 如果 searchNearby 失败，再尝试 searchPoiNationwide
                        Log.w(TAG, "⚠️ searchNearby 全国模式失败，尝试 searchPoiNationwide")
                        val fallbackResult = apiService.searchPoiNationwide(
                            keyword = simplifiedKeyword,
                            lat = null,
                            lng = null,
                            page = 1,
                            pageSize = 20,
                            nationwide = true
                        )
                        
                        if (fallbackResult.isSuccess() && !fallbackResult.data.isNullOrEmpty()) {
                            _searchResults.value = fallbackResult.data!!
                            Log.d(TAG, "✅ searchPoiNationwide 成功：${_searchResults.value.size} 个结果")
                        } else {
                            _errorMessage.value = "未找到相关地点，请换一个关键词试试"
                            _searchResults.value = emptyList()
                            Log.e(TAG, "❌ 所有搜索接口都失败")
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络异常"
                Log.e(TAG, "❌ 搜索异常", e)
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    // ⭐ 新增：清空搜索结果
    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _searchKeyword.value = ""
    }
}
