package com.example.myapplication.presentation.favorites

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.FavoriteLocation
import com.example.myapplication.data.model.PoiResponse
import com.example.myapplication.data.model.SaveFavoriteRequest
import com.example.myapplication.data.model.ShareFavoriteRequest  // ⭐ 新增：分享收藏请求
import com.example.myapplication.data.model.ConfirmArrivalRequest  // ⭐ 新增：确认到达请求
import com.example.myapplication.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * 收藏地点 ViewModel
 * 管理收藏列表的状态和业务逻辑
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: FavoritesRepository,
    private val apiService: ApiService,
    private val tokenManager: com.example.myapplication.core.datastore.TokenManager  // ⭐ 新增：注入 TokenManager
) : ViewModel() {

    private val TAG = "FavoritesViewModel"

    // ⭐ 新增：TTS语音播报
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

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
    
    // ⭐ 新增：当前位置（用于搜索）
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation
    
    // ⭐ 新增：搜索任务引用，用于取消之前的搜索
    private var searchJob: kotlinx.coroutines.Job? = null
    
    // ⭐ 新增：搜索防抖延迟（毫秒）
    private val SEARCH_DEBOUNCE_MS = 500L

    /**
     * ⭐ 初始化TTS语音播报
     */
    fun initTTS(context: Context) {
        if (textToSpeech != null) return
        
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "❌ TTS不支持中文")
                    ttsInitialized = false
                } else {
                    Log.d(TAG, "✅ TTS初始化成功")
                    ttsInitialized = true
                }
            } else {
                Log.e(TAG, "❌ TTS初始化失败")
                ttsInitialized = false
            }
        }
    }

    /**
     * ⭐ 语音播报地点信息
     */
    fun speakLocationInfo(name: String, address: String, phone: String? = null) {
        if (!ttsInitialized) {
            Log.w(TAG, "⚠️ TTS未初始化，无法播报")
            return
        }
        
        val message = buildString {
            append("这是$name")
            append("，地址：$address")
            phone?.let {
                if (it.isNotEmpty()) {
                    append("，电话：$it")
                }
            }
        }
        
        Log.d(TAG, "🔊 语音播报：$message")
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * ⭐ 释放TTS资源
     */
    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        Log.d(TAG, "♻️ TTS资源已释放")
    }

    /**
     * 刷新收藏列表
     */
    fun refreshFavorites() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // ⭐ 新增：打印当前用户信息
            val userId = tokenManager.getUserId()
            val guardMode = tokenManager.getGuardMode()
            Log.d(TAG, "👤 当前用户: userId=$userId, guardMode=${if (guardMode == 1) "长辈端" else "普通用户"}")
            
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
    
    // ⭐ 新增：更新当前位置（用于搜索）
    fun updateCurrentLocation(lat: Double, lng: Double) {
        _currentLocation.value = LatLng(lat, lng)
        Log.d(TAG, "📍 更新当前位置：lat=$lat, lng=$lng")
    }
    
    // ⭐ 新增：搜索 POI（使用与首页完全相同的搜索策略）
    fun searchPoi(keyword: String, lat: Double? = null, lng: Double? = null) {
        // ⭐ 关键修复：取消之前的搜索任务，实现防抖
        searchJob?.cancel()
        
        searchJob = viewModelScope.launch {
            // ⭐ 防抖延迟：等待用户停止输入 500ms 后再搜索
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            
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
                
                // ✅ 修复：收藏页面使用与首页相同的搜索逻辑
                // 1. 如果有定位坐标，优先使用 searchDestination（后端推荐）
                // 2. 失败后降级到 searchNearby
                // 3. 都没有结果才尝试全国搜索
                
                // ⭐ 修复：使用 ViewModel 内部的当前位置
                val location = _currentLocation.value
                val useLat = lat ?: location?.latitude
                val useLng = lng ?: location?.longitude
                
                if (useLat != null && useLng != null) {
                    Log.d(TAG, "📍 有定位坐标，使用周边搜索模式")
                    Log.d(TAG, "  - 坐标：lat=$useLat, lng=$useLng")
                    
                    // 第一步：调用 searchDestination（主页搜索框推荐）
                    Log.d(TAG, "🔍 调用 searchDestination 接口")
                    val result = apiService.searchDestination(
                        keyword = simplifiedKeyword,
                        lat = useLat,
                        lng = useLng
                    )
                    
                    Log.d(TAG, "📥 searchDestination 响应: code=${result.code}, data size=${result.data?.size}")
                    
                    if (result.isSuccess() && !result.data.isNullOrEmpty()) {
                        _searchResults.value = result.data!!
                        Log.d(TAG, "✅ searchDestination 成功：${_searchResults.value.size} 个结果")
                        result.data!!.forEachIndexed { index, poi ->
                            Log.d(TAG, "  [$index] ${poi.name} - ${poi.address} (${poi.distance}m)")
                        }
                        _isSearching.value = false
                        return@launch
                    } else {
                        Log.w(TAG, "⚠️ searchDestination 无结果，尝试 searchNearby")
                        
                        // 第二步：降级到 searchNearby
                        val fallbackResult = apiService.searchNearby(
                            keyword = simplifiedKeyword,
                            lat = useLat,
                            lng = useLng,
                            page = 1,
                            pageSize = 20,
                            radius = 5000
                        )
                        
                        Log.d(TAG, "📥 searchNearby 响应: code=${fallbackResult.code}, data size=${fallbackResult.data?.size}")
                        
                        if (fallbackResult.isSuccess() && !fallbackResult.data.isNullOrEmpty()) {
                            _searchResults.value = fallbackResult.data!!
                            Log.d(TAG, "✅ searchNearby 成功：${_searchResults.value.size} 个结果")
                            fallbackResult.data!!.forEachIndexed { index, poi ->
                                Log.d(TAG, "  [$index] ${poi.name} - ${poi.address} (${poi.distance}m)")
                            }
                            _isSearching.value = false
                            return@launch
                        }
                    }
                    
                    // 第三步：周边搜索都失败，尝试全国搜索
                    Log.w(TAG, "⚠️ 周边搜索无结果，切换到全国搜索")
                    val nationwideResult = apiService.searchPoiNationwide(
                        keyword = simplifiedKeyword,
                        lat = null,
                        lng = null,
                        page = 1,
                        pageSize = 20,
                        nationwide = true
                    )
                    
                    Log.d(TAG, "📥 searchPoiNationwide 响应: code=${nationwideResult.code}, data size=${nationwideResult.data?.size}")
                    
                    if (nationwideResult.isSuccess() && !nationwideResult.data.isNullOrEmpty()) {
                        _searchResults.value = nationwideResult.data!!
                        Log.d(TAG, "✅ 全国搜索成功：${_searchResults.value.size} 个结果")
                        nationwideResult.data!!.forEachIndexed { index, poi ->
                            Log.d(TAG, "  [$index] ${poi.name} - ${poi.address}")
                        }
                    } else {
                        _errorMessage.value = "未找到相关地点，请换一个关键词试试"
                        _searchResults.value = emptyList()
                        Log.e(TAG, "❌ 所有搜索方式都无结果")
                    }
                } else {
                    // 没有定位坐标，直接全国搜索
                    Log.w(TAG, "⚠️ 没有定位坐标，直接使用全国搜索")
                    val result = apiService.searchPoiNationwide(
                        keyword = simplifiedKeyword,
                        lat = null,
                        lng = null,
                        page = 1,
                        pageSize = 20,
                        nationwide = true
                    )
                    
                    Log.d(TAG, "📥 全国搜索响应: code=${result.code}, data size=${result.data?.size}")
                    
                    if (result.isSuccess() && !result.data.isNullOrEmpty()) {
                        _searchResults.value = result.data!!
                        Log.d(TAG, "✅ 全国搜索成功：${_searchResults.value.size} 个结果")
                        result.data!!.forEachIndexed { index, poi ->
                            Log.d(TAG, "  [$index] ${poi.name} - ${poi.address}")
                        }
                    } else {
                        _errorMessage.value = "未找到相关地点，请换一个关键词试试"
                        _searchResults.value = emptyList()
                        Log.e(TAG, "❌ 全国搜索无结果")
                    }
                }
            } catch (e: Exception) {
                // ⭐ 修复：忽略请求取消异常，避免闪退
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("canceled", ignoreCase = true) || 
                    errorMsg.contains("closed", ignoreCase = true) ||
                    e is java.util.concurrent.CancellationException) {
                    Log.w(TAG, "⚠️ 搜索请求被取消或协程被取消，安全忽略")
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = e.message ?: "网络异常"
                    Log.e(TAG, "❌ 搜索异常", e)
                }
            } finally {
                // ⭐ 确保 finally 块不会抛出异常
                try {
                    _isSearching.value = false
                } catch (e: Exception) {
                    Log.e(TAG, "❌ finally 块异常", e)
                }
            }
        }
    }
    
    // ⭐ 新增：清空搜索结果
    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _searchKeyword.value = ""
    }
    
    /**
     * ⭐ 新增：分享收藏地点给亲友（通过WebSocket）
     */
    fun shareFavoriteToGuardian(
        favoriteId: Long,
        guardianUserId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📤 分享收藏地点给亲友：favoriteId=$favoriteId, guardianUserId=$guardianUserId")
                
                // 调用后端API分享
                val result = apiService.shareFavoriteToGuardian(favoriteId, guardianUserId)
                
                if (result.isSuccess()) {
                    Log.d(TAG, "✅ 分享成功")
                    onSuccess()
                } else {
                    val errorMsg = result.message ?: "分享失败"
                    Log.e(TAG, "❌ 分享失败：$errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "网络异常"
                Log.e(TAG, "❌ 分享异常", e)
                onError(errorMsg)
            }
        }
    }
    
    /**
     * ⭐ 新增：确认到达目的地（通过WebSocket通知亲友）
     */
    fun confirmArrival(
        favoriteId: Long,
        orderId: Long? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "✅ 确认到达：favoriteId=$favoriteId, orderId=$orderId")
                
                // 调用后端API确认到达
                val result = apiService.confirmArrival(favoriteId, orderId)
                
                if (result.isSuccess()) {
                    Log.d(TAG, "✅ 确认到达成功，已通知亲友")
                    onSuccess()
                } else {
                    val errorMsg = result.message ?: "确认到达失败"
                    Log.e(TAG, "❌ 确认到达失败：$errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "网络异常"
                Log.e(TAG, "❌ 确认到达异常", e)
                onError(errorMsg)
            }
        }
    }
    
    /**
     * ⭐ 新增：获取出行记录列表(行程凭证)
     */
    private val _travelRecords = MutableStateFlow<List<com.example.myapplication.data.model.TravelRecord>>(emptyList())
    val travelRecords: StateFlow<List<com.example.myapplication.data.model.TravelRecord>> = _travelRecords
    
    fun getTravelRecords(
        page: Int = 1,
        size: Int = 10,
        startDate: String? = null,
        endDate: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📋 获取出行记录：page=$page, size=$size")
                
                val result = apiService.getTravelRecords(page, size, startDate, endDate)
                
                if (result.isSuccess()) {
                    val response = result.data
                    if (response != null) {
                        _travelRecords.value = response.records
                        Log.d(TAG, "✅ 获取出行记录成功，共${response.total}条")
                        onSuccess()
                    } else {
                        onError("数据为空")
                    }
                } else {
                    val errorMsg = result.message ?: "获取失败"
                    Log.e(TAG, "❌ 获取出行记录失败：$errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "网络异常"
                Log.e(TAG, "❌ 获取出行记录异常", e)
                onError(errorMsg)
            }
        }
    }
    
    /**
     * ⭐ 新增：分享收藏地点给长辈（添加到长辈的收藏列表）
     */
    fun shareFavoriteToElder(
        favoriteId: Long,
        elderUserId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "📤 分享收藏到长辈: favoriteId=$favoriteId, elderUserId=$elderUserId")
                
                val request = ShareFavoriteRequest(
                    favoriteId = favoriteId,
                    elderUserId = elderUserId,
                    saveAsNew = true  // 保存为新收藏
                )
                
                val result = repository.shareFavoriteToElder(request)
                
                result.onSuccess {
                    Log.d(TAG, "✅ 分享成功，已添加到长辈收藏列表")
                    onSuccess()
                }.onFailure { error ->
                    val errorMsg = error.message ?: "分享失败"
                    Log.e(TAG, "❌ 分享失败: $errorMsg")
                    _errorMessage.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "网络异常"
                Log.e(TAG, "❌ 分享异常", e)
                _errorMessage.value = errorMsg
                onError(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
