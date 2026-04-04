    package com.example.myapplication.presentation.home
    
    import android.content.Context
    import android.util.LruCache
    import android.util.Log
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.viewModelScope
    import com.amap.api.location.AMapLocation
    import com.amap.api.location.AMapLocationClient
    import com.amap.api.location.AMapLocationClientOption
    import com.amap.api.maps.AMapUtils
    import com.amap.api.maps.model.LatLng
    import com.amap.api.maps.model.Poi
    import com.amap.api.services.core.LatLonPoint
    import com.amap.api.services.geocoder.GeocodeResult
    import com.amap.api.services.geocoder.GeocodeSearch
    import com.amap.api.services.geocoder.RegeocodeQuery
    import com.amap.api.services.geocoder.RegeocodeResult
    import com.amap.api.services.poisearch.PoiResult
    import com.amap.api.services.poisearch.PoiSearch
    import com.amap.api.services.core.PoiItem
    import com.amap.api.services.route.DrivePath
    import com.amap.api.services.route.DriveRouteResult
    import com.amap.api.services.route.RouteSearch
    import com.amap.api.services.route.BusRouteResult
    import com.amap.api.services.route.WalkRouteResult
    import com.amap.api.services.route.RideRouteResult
    import com.example.myapplication.core.network.ApiService
    import com.example.myapplication.data.model.Order
    import com.example.myapplication.data.model.PoiDetail
    import com.example.myapplication.data.model.PoiResponse
    import com.example.myapplication.data.repository.AgentRepository
    import com.example.myapplication.data.repository.OrderRepository
    import com.example.myapplication.domain.repository.IOrderRepository
    import com.example.myapplication.core.utils.BaiduSpeechRecognizerHelper
    import com.example.myapplication.MyApplication
    import com.example.myapplication.core.datastore.TokenManager
    import dagger.hilt.android.lifecycle.HiltViewModel
    import dagger.hilt.android.qualifiers.ApplicationContext
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.channels.Channel
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.flow.receiveAsFlow
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.suspendCancellableCoroutine
    import kotlinx.coroutines.withTimeout
    import org.json.JSONObject
    import java.util.concurrent.atomic.AtomicLong
    import javax.inject.Inject
    import kotlin.coroutines.resume
    import kotlin.coroutines.resumeWithException
    
    @HiltViewModel
    class HomeViewModel @Inject constructor(
        private val orderRepository: IOrderRepository,
        private val apiService: ApiService,
        private val webSocketClient: com.example.myapplication.core.websocket.ChatWebSocketClient,  // ⭐ 新增
        private val agentRepository: AgentRepository,  // ⭐ 新增
        private val tokenManager: TokenManager,  // ⭐ 新增
        @ApplicationContext private val appContext: Context
    ) : ViewModel() {
    
        // ⭐ 新增：聚合 UI 状态
        data class HomeUiState(
            val destination: String = "",
            val currentLocation: LatLng? = null,
            val locationAccuracy: Float? = null,
            val orderState: OrderState = OrderState.Idle,
            val isCreatingOrder: Boolean = false,
            val isGeocoding: Boolean = false,
            val geocodeError: String? = null,
            val searchResults: List<Any> = emptyList(),
            val isSearching: Boolean = false,
            val selectedPoiForMap: Any? = null,
            val clickedLocation: LatLng? = null,
            val isListening: Boolean = false,
            val backendPoiResults: List<PoiResponse> = emptyList(),
            val poiDetail: PoiDetail? = null,
            val showPoiDetailDialog: Boolean = false
        )
        
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
        
        // ⭐ 保留原有 StateFlow 以兼容现有 UI 代码（逐步迁移）
        private val _destination = MutableStateFlow("")
        val destination: StateFlow<String> = _destination.asStateFlow()
    
        private val _currentLocation = MutableStateFlow<LatLng?>(null)
        val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()
    
        private val _locationAccuracy = MutableStateFlow<Float?>(null)
        val locationAccuracy: StateFlow<Float?> = _locationAccuracy.asStateFlow()
    
        private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
        val orderState: StateFlow<OrderState> = _orderState.asStateFlow()
    
        private val _isCreatingOrder = MutableStateFlow(false)
        val isCreatingOrder: StateFlow<Boolean> = _isCreatingOrder.asStateFlow()
    
        private val _isGeocoding = MutableStateFlow(false)
        val isGeocoding: StateFlow<Boolean> = _isGeocoding.asStateFlow()
    
        private val _geocodeError = MutableStateFlow<String?>(null)
        val geocodeError: StateFlow<String?> = _geocodeError.asStateFlow()
    
        // ⭐ 新增：清除地理编码错误
        fun clearGeocodeError() {
            _geocodeError.value = null
        }
    
        private val _searchResults = MutableStateFlow<List<Any>>(emptyList())
        val searchResults: StateFlow<List<Any>> = _searchResults.asStateFlow()
    
        private val _isSearching = MutableStateFlow(false)
        val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
        private val _selectedPoiForMap = MutableStateFlow<Any?>(null)
        val selectedPoiForMap: StateFlow<Any?> = _selectedPoiForMap.asStateFlow()
    
        private val _clickedLocation = MutableStateFlow<LatLng?>(null)
        val clickedLocation: StateFlow<LatLng?> = _clickedLocation.asStateFlow()
    
        private val _isListening = MutableStateFlow(false)
        val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
        // ⭐ 新增：实时语音识别文本
        private val _voiceText = MutableStateFlow("")
        val voiceText: StateFlow<String> = _voiceText.asStateFlow()
        
        // ⭐ 新增：用于实时显示正在说的话（逐字填入）
        private val _realtimeVoiceText = MutableStateFlow("")
        val realtimeVoiceText: StateFlow<String> = _realtimeVoiceText.asStateFlow()
    
        private val _backendPoiResults = MutableStateFlow<List<PoiResponse>>(emptyList())
        val backendPoiResults: StateFlow<List<PoiResponse>> = _backendPoiResults.asStateFlow()
    
        private var searchJob: Job? = null
        private var speechHelper: BaiduSpeechRecognizerHelper? = null
        private val addressCache = LruCache<Long, String>(20)
    
        private val requestId = AtomicLong(0)
    
        private val _events = Channel<HomeEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()
        
        sealed class HomeEvent {
            data class LocationUpdated(val lat: Double, val lng: Double) : HomeEvent()
            data class OrderCreated(val order: Order) : HomeEvent()  // ⭐ 新增
        }
    
        private fun getCacheKey(latLng: LatLng): Long {
            return (latLng.latitude * 10_000).toLong() * 10_000 + (latLng.longitude * 10_000).toLong()
        }
    
        fun updateDestination(text: String) {
            _destination.value = text
            _geocodeError.value = null
            
            // ⭐ 修改：取消自动搜索，只在用户点击搜索按钮时搜索
            searchJob?.cancel()
            
            // ⭐ 不再自动触发搜索
            if (text.isBlank()) {
                clearSearchResults()
            }
        }
    
        // ⭐ 新增：方言选择（默认普通话）
        private var _currentLanguage = MutableStateFlow("zh_cn")  // zh_cn: 中文
        private var _currentAccent = MutableStateFlow("mandarin")  // mandarin: 普通话
        val currentLanguage = _currentLanguage.asStateFlow()
        val currentAccent = _currentAccent.asStateFlow()
        
        // ⭐ 新增：支持的方言列表
        data class DialectOption(val name: String, val language: String, val accent: String)
        
        val supportedDialects = listOf(
            DialectOption("普通话", "zh_cn", "mandarin"),
            DialectOption("粤语", "zh_cn", "cantonese"),
            DialectOption("四川话", "zh_cn", "sichuan"),
            DialectOption("河南话", "zh_cn", "henan"),
            DialectOption("东北话", "zh_cn", "northeastern"),
            DialectOption("山东话", "zh_cn", "shandong"),
            DialectOption("湖南话", "zh_cn", "hunan"),
            DialectOption("福建话", "zh_cn", "fujian"),
            DialectOption("陕西话", "zh_cn", "shaanxi"),
            DialectOption("山西话", "zh_cn", "shanxi"),
            DialectOption("江西话", "zh_cn", "jiangxi"),
            DialectOption("江苏话", "zh_cn", "jiangsu"),
            DialectOption("浙江话", "zh_cn", "zhejiang"),
            DialectOption("安徽话", "zh_cn", "anhui"),
            DialectOption("湖北话", "zh_cn", "hubei"),
            DialectOption("贵州话", "zh_cn", "guizhou"),
            DialectOption("云南话", "zh_cn", "yunnan"),
            DialectOption("广西话", "zh_cn", "guangxi")
        )
        
        // ⭐ 新增：切换方言
        fun setDialect(language: String, accent: String) {
            _currentLanguage.value = language
            _currentAccent.value = accent
            Log.d("HomeViewModel", "🌍 切换方言: language=$language, accent=$accent")
        }
    
        fun startVoiceInput(context: Context) {
            Log.d("HomeViewModel", "=== startVoiceInput 被调用 ===")
            Log.d("HomeViewModel", "speechHelper=${speechHelper != null}, isListening=${_isListening.value}")
            Log.d("HomeViewModel", "当前方言: language=${_currentLanguage.value}, accent=${_currentAccent.value}")
            
            // ⭐ 修改：如果已经在监听，则停止（切换模式）
            if (_isListening.value) {
                Log.d("HomeViewModel", "⚠️ 已经在监听中，停止录音")
                stopVoiceInput()
                return
            }
            
            // ⭐ 修改：如果 speechHelper 存在但不在监听，销毁后重新创建
            if (speechHelper != null) {
                Log.d("HomeViewModel", "⚠️ 清理旧的 speechHelper")
                speechHelper?.destroy()
                speechHelper = null
            }
            
            try {
                // ⭐ 修改：使用百度语音识别，传入当前选择的方言
                val baiduLanguage = when (_currentAccent.value) {
                    "mandarin" -> "zh-CN"
                    "cantonese" -> "zh-HK"
                    "sichuan" -> "zh-SICHUAN"
                    else -> "zh-CN"
                }
                
                speechHelper = BaiduSpeechRecognizerHelper(
                    context = context,
                    onResult = { finalText ->
                        // 最终结果：更新文本，但不自动搜索
                        Log.d("HomeViewModel", "✅ 百度语音最终结果: $finalText")
                        if (finalText.isNotBlank()) {
                            _voiceText.value = finalText
                            updateDestination(finalText)  // ⭐ 只更新目的地，不自动搜索
                        } else {
                            Log.w("HomeViewModel", "⚠️ 语音识别结果为空")
                        }
                        _isListening.value = false
                        speechHelper = null  // ⭐ 清空引用
                    },
                    onPartialResult = { partialText ->
                        // ⭐ 实时部分结果：像微信一样逐字显示
                        Log.d("HomeViewModel", "🔄 百度语音实时结果: $partialText")
                        if (partialText.isNotBlank()) {
                            _voiceText.value = partialText
                            updateDestination(partialText)  // ⭐ 实时更新 UI
                        }
                    },
                    language = baiduLanguage  // ⭐ 传入方言
                )
                _isListening.value = true
                _voiceText.value = ""  // ⭐ 清空之前的文本
                speechHelper?.startListening()
                Log.d("HomeViewModel", "✅ 百度语音识别已启动")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ 启动语音识别失败: ${e.message}", e)
                _isListening.value = false
                speechHelper = null
            }
        }
    
        private fun parseVoiceResult(raw: String): String {
            return try {
                val json = JSONObject(raw)
                
                // ⭐ 判空保护：检查 ws 数组是否存在
                if (!json.has("ws")) {
                    Log.w("HomeViewModel", "语音结果缺少 ws 字段")
                    return raw
                }
                
                val ws = json.getJSONArray("ws")
                
                // ⭐ 判空保护：检查 ws 是否为空
                if (ws.length() == 0) {
                    Log.w("HomeViewModel", "语音结果为空数组")
                    return raw
                }
                
                val sb = StringBuilder()
                for (i in 0 until ws.length()) {
                    val cwObj = ws.optJSONObject(i)
                    if (cwObj != null && cwObj.has("cw")) {
                        val cw = cwObj.getJSONArray("cw")
                        if (cw.length() > 0) {
                            val wordObj = cw.optJSONObject(0)
                            if (wordObj != null && wordObj.has("w")) {
                                sb.append(wordObj.getString("w"))
                            }
                        }
                    }
                }
                
                val result = sb.toString()
                return if (result.isEmpty()) raw else result
                
            } catch (e: Exception) {
                Log.e("HomeViewModel", "解析语音结果异常：${e.message}", e)
                raw
            }
        }
    
        fun stopVoiceInput() {
            Log.d("HomeViewModel", "=== stopVoiceInput 被调用 ===")
            speechHelper?.destroy()
            speechHelper = null  // ⭐ 修改：清空引用，防止内存泄漏
            _isListening.value = false
            _voiceText.value = ""  // ⭐ 清空语音文本
        }
    
        fun updateCurrentLocation(lat: Double, lng: Double) {
            _currentLocation.value = LatLng(lat, lng)
        }
    
        fun onMapClick(latLng: LatLng) {
            Log.d("HomeViewModel", "onMapClick called, latLng=$latLng")
            _clickedLocation.value = latLng
    
            val currentReqId = requestId.incrementAndGet()
            val cacheKey = getCacheKey(latLng)
            val cachedAddress = addressCache.get(cacheKey)
    
            if (cachedAddress != null) {
                Log.d("HomeViewModel", "缓存命中：$cachedAddress")
                updateDestination(cachedAddress)
                return
            }
    
            Log.d("HomeViewModel", "缓存未命中，开始逆地理编码，请求 ID=$currentReqId")
            _destination.value = ""
            updateDestination("正在获取地址...")
            _isGeocoding.value = true
            _geocodeError.value = null
    
            val startTime = System.currentTimeMillis()
            val minDisplayMs = 500L
    
            viewModelScope.launch {
                var address: String? = null
                var errorMsg: String? = null
                try {
                    address = withTimeout(8000) {
                        suspendGeocode(latLng, currentReqId)
                    }
                    if (address != null) {
                        addressCache.put(cacheKey, address)
                        Log.d("HomeViewModel", "逆地理编码成功：$address")
                    } else {
                        errorMsg = "获取地址失败"
                        Log.e("HomeViewModel", "逆地理编码失败，地址为空")
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    errorMsg = "获取地址超时"
                    Log.e("HomeViewModel", "逆地理编码超时")
                } catch (e: Exception) {
                    errorMsg = "获取地址失败"
                    Log.e("HomeViewModel", "逆地理编码异常", e)
                } finally {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < minDisplayMs) {
                        delay(minDisplayMs - elapsed)
                    }
                    _isGeocoding.value = false
                    if (address != null) {
                        updateDestination(address)
                    } else {
                        updateDestination("")
                        _geocodeError.value = errorMsg
                    }
                }
            }
        }
    
        private suspend fun suspendGeocode(latLng: LatLng, reqId: Long): String? = suspendCancellableCoroutine { continuation ->
            val geocodeSearch = GeocodeSearch(appContext)
            geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (reqId != requestId.get()) {
                        Log.d("HomeViewModel", "忽略旧请求回调，reqId=$reqId, current=${requestId.get()}")
                        return
                    }
                    if (rCode == 10000) { // AMapException.CODE_AMAP_SUCCESS
                        val address = result?.regeocodeAddress?.formatAddress
                        Log.d("HomeViewModel", "逆地理编码成功：$address")
                        continuation.resume(address)
                    } else {
                        Log.e("HomeViewModel", "逆地理编码失败，rCode=$rCode, errorMessage=${result?.regeocodeAddress?.formatAddress}")
                        // ⭐ 修改：返回 null 而不是直接 resume，让上层处理错误
                        continuation.resume(null)
                    }
                }
                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
            })
            val query = RegeocodeQuery(LatLonPoint(latLng.latitude, latLng.longitude), 200f, GeocodeSearch.AMAP)
            geocodeSearch.getFromLocationAsyn(query)
    
            continuation.invokeOnCancellation {
                Log.d("HomeViewModel", "逆地理编码协程被取消")
            }
        }
    
        private val _poiDetail = MutableStateFlow<PoiDetail?>(null)
        val poiDetail: StateFlow<PoiDetail?> = _poiDetail
        
        private val _showPoiDetailDialog = MutableStateFlow(false)
        val showPoiDetailDialog: StateFlow<Boolean> = _showPoiDetailDialog
        
        fun onPoiClick(poi: Poi) {
            Log.d("HomeViewModel", "=== onPoiClick 被调用 ===")
            Log.d("HomeViewModel", "POI 名称：${poi.name}")
            Log.d("HomeViewModel", "POI 坐标：lat=${poi.coordinate.latitude}, lng=${poi.coordinate.longitude}")
            
            val latLng = LatLng(poi.coordinate.latitude, poi.coordinate.longitude)
            _clickedLocation.value = latLng
            _destination.value = poi.name
            
            // ⭐ 修改：直接显示 POI 详情，不再通过逆地理编码获取地址
            showPoiDetailDialog(poi)
        }
        
        // ⭐ 修改：显示 POI 详情时使用路径规划计算真实里程
        private fun showPoiDetailDialog(poi: Poi) {
            viewModelScope.launch {
                try {
                    val address = fetchPoiAddress(LatLng(poi.coordinate.latitude, poi.coordinate.longitude))
                    
                    val currentLocation = _currentLocation.value
                    var distance: Double? = null
                    var duration: Int? = null
                    var price: Int? = null
                    var formattedDistance: String? = null
                    
                    if (currentLocation != null) {
                        // ⭐ 修改：先使用直线距离快速计算
                        val straightDistance = calculateDistance(
                            currentLocation.latitude,
                            currentLocation.longitude,
                            poi.coordinate.latitude.toDouble(),
                            poi.coordinate.longitude.toDouble()
                        )
                        
                        // ⭐ 同时异步调用路径规划获取真实里程
                        val routeResult = calculateRouteDistance(currentLocation, 
                            LatLng(poi.coordinate.latitude, poi.coordinate.longitude))
                        
                        // ⭐ 优先使用路径规划的真实里程
                        if (routeResult != null && routeResult.distance > 0) {
                            distance = routeResult.distance.toDouble()
                            duration = routeResult.duration.toInt()
                            price = calculatePrice(distance)
                            formattedDistance = formatDistance(distance)
                            Log.d("HomeViewModel", "使用路径规划距离：${distance}米，时长：${duration}秒")
                        } else {
                            // ⭐ 降级使用直线距离
                            distance = straightDistance
                            formattedDistance = formatDistance(distance)
                            duration = (straightDistance / 500 * 3).toLong().toInt()
                            price = calculatePrice(distance)
                            Log.d("HomeViewModel", "使用直线距离：${distance}米")
                        }
                    }
                    
                    // ⭐ 生成 sessionId（用于后续下单）
                    val sessionId = "map_click_${System.currentTimeMillis()}"
                    
                    _poiDetail.value = PoiDetail(
                        name = poi.name,
                        address = address ?: poi.name,  // ⭐ 修改：如果地址获取失败，使用名称代替
                        lat = poi.coordinate.latitude,
                        lng = poi.coordinate.longitude,
                        distance = distance,
                        duration = duration,
                        price = price,
                        canOrder = true,
                        formattedDistance = formattedDistance,
                        sessionId = sessionId  // ⭐ 保存 sessionId
                    )
                    
                    _showPoiDetailDialog.value = true
                    
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "获取 POI 详情失败", e)
                    // ⭐ 修改：即使出错也显示基本信息
                    _poiDetail.value = PoiDetail(
                        name = poi.name,
                        address = poi.name,  // 使用名称作为临时地址
                        lat = poi.coordinate.latitude,
                        lng = poi.coordinate.longitude,
                        canOrder = false,
                        sessionId = null  // ⭐ 异常情况下 sessionId 为 null
                    )
                    _showPoiDetailDialog.value = true
                }
            }
        }
        
        // ⭐ 修改：调用路径规划 API，修复接口方法签名
        private suspend fun calculateRouteDistance(start: LatLng, end: LatLng): DrivePath? {
            return suspendCancellableCoroutine { continuation ->
                val routeSearch = RouteSearch(appContext)
                routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                    override fun onDriveRouteSearched(result: DriveRouteResult?, rCode: Int) {
                        if (rCode == 10000 && result != null && result.paths.isNotEmpty()) {
                            val bestPath = result.paths.minByOrNull { it.distance }
                            if (!continuation.isCompleted) {
                                continuation.resume(bestPath)
                            }
                        } else {
                            if (!continuation.isCompleted) {
                                continuation.resume(null)
                            }
                        }
                    }
                    
                    override fun onBusRouteSearched(result: BusRouteResult?, rCode: Int) {
                        Log.d("HomeViewModel", "onBusRouteSearched called, rCode=$rCode")
                        if (!continuation.isCompleted) {
                            continuation.resume(null)
                        }
                    }
                    
                    override fun onWalkRouteSearched(result: WalkRouteResult?, rCode: Int) {
                        Log.d("HomeViewModel", "onWalkRouteSearched called, rCode=$rCode")
                        if (!continuation.isCompleted) {
                            continuation.resume(null)
                        }
                    }
                    
                    override fun onRideRouteSearched(result: RideRouteResult?, rCode: Int) {
                        Log.d("HomeViewModel", "onRideRouteSearched called, rCode=$rCode")
                        if (!continuation.isCompleted) {
                            continuation.resume(null)
                        }
                    }
                })
                
                val from = RouteSearch.FromAndTo(LatLonPoint(start.latitude, start.longitude), 
                    LatLonPoint(end.latitude, end.longitude))
                val query = RouteSearch.DriveRouteQuery(from, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "")
                routeSearch.calculateDriveRouteAsyn(query)
            }
        }
    
        private suspend fun fetchPoiAddress(latLng: LatLng): String? {
            return try {
                // ⭐ 使用协程挂起方式获取地址
                suspendCancellableCoroutine { continuation ->
                    val geocodeSearch = GeocodeSearch(appContext)
                    geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                        override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                            if (rCode == 10000) { // AMapException.CODE_AMAP_SUCCESS
                                val address = result?.regeocodeAddress?.formatAddress
                                Log.d("HomeViewModel", "逆地理编码成功：$address")
                                continuation.resume(address)
                            } else {
                                Log.e("HomeViewModel", "逆地理编码失败，rCode=$rCode")
                                continuation.resume(null)
                            }
                        }
                        override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
                    })
                    val query = RegeocodeQuery(LatLonPoint(latLng.latitude, latLng.longitude), 200f, GeocodeSearch.AMAP)
                    geocodeSearch.getFromLocationAsyn(query)
                    
                    // ⭐ 添加超时处理
                    viewModelScope.launch {
                        delay(5000)
                        if (!continuation.isCompleted) {
                            Log.w("HomeViewModel", "逆地理编码超时")
                            continuation.resume(null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "fetchPoiAddress 异常", e)
                null
            }
        }
        
        private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val location1 = android.location.Location("start").apply {
                latitude = lat1
                longitude = lng1
            }
            val location2 = android.location.Location("end").apply {
                latitude = lat2
                longitude = lng2
            }
            return location1.distanceTo(location2).toDouble()
        }
        
        private fun formatDistance(meters: Double): String {
            return when {
                meters < 1000 -> "${meters.toInt()}米"
                else -> String.format("%.1f公里", meters / 1000)
            }
        }
        
        private fun calculatePrice(distanceMeters: Double?): Int? {
            if (distanceMeters == null) return null
            val distanceKm = distanceMeters / 1000
            return when {
                distanceKm <= 3 -> 15
                distanceKm <= 10 -> 15 + ((distanceKm - 3) * 2.5).toInt()
                else -> (15 + (7 * 2.5) + ((distanceKm - 10) * 3.5)).toInt()
            }
        }
        
        // ⭐ 保留一个 dismissPoiDetailDialog 方法即可
        fun dismissPoiDetailDialog() {
            _showPoiDetailDialog.value = false
            _poiDetail.value = null
        }
        
        // ⭐ 保留一个 confirmPoiSelection 方法即可
        fun confirmPoiSelection() {
            _showPoiDetailDialog.value = false
            _poiDetail.value?.let { detail ->
                Log.d("HomeViewModel", "=== 确认选择目的地 ===")
                Log.d("HomeViewModel", "目的地：${detail.name}")
                // ⭐ 修改：立即创建订单
                createOrder(detail.name ?: "未知位置")
            }
        }
    
        // ⭐ 修改：优化搜索逻辑，允许在没有位置时也能搜索
        fun searchPoiFromBackend(keyword: String, nationwide: Boolean = false) {
            if (keyword.isBlank()) {
                Log.e("HomeViewModel", "搜索关键词为空")
                return
            }
            
            val location = _currentLocation.value
            Log.d("HomeViewModel", "=== searchPoiFromBackend 被调用 ===")
            Log.d("HomeViewModel", "keyword=$keyword, nationwide=$nationwide")
            Log.d("HomeViewModel", "location=$location")
            
            // ⭐ 修改：即使没有位置也可以进行全国搜索
            if (location == null && !nationwide) {
                Log.w("HomeViewModel", "当前位置为空，切换到全国搜索模式")
                searchPoiFromBackend(keyword, true)
                return
            }
    
            viewModelScope.launch {
                _isSearching.value = true
                _backendPoiResults.value = emptyList()
                
                try {
                    val simplifiedKeyword = keyword.replace(Regex("(校区 | 区 | 分校 | 分院)$"), "")
                    Log.d("HomeViewModel", "原始关键词：$keyword, 简化后：$simplifiedKeyword")
                    
                    // ⭐ 修改：主页搜索框优先使用 searchDestination（后端文档推荐）
                    val result = if (!nationwide && location != null) {
                        Log.d("HomeViewModel", "🔍 调用 searchDestination 接口（主页搜索框推荐）")
                        Log.d("HomeViewModel", "  - keyword=${simplifiedKeyword}")
                        Log.d("HomeViewModel", "  - lat=${location.latitude}, lng=${location.longitude}")
                        apiService.searchDestination(
                            keyword = simplifiedKeyword,
                            lat = location.latitude,
                            lng = location.longitude
                        )
                    } else {
                        Log.d("HomeViewModel", "🔍 调用 searchNearby 接口作为降级")
                        Log.d("HomeViewModel", "  - keyword=${simplifiedKeyword}")
                        Log.d("HomeViewModel", "  - lat=${location?.latitude ?: 0.0}, lng=${location?.longitude ?: 0.0}")
                        Log.d("HomeViewModel", "  - radius=5000")
                        apiService.searchNearby(
                            keyword = simplifiedKeyword,
                            lat = location?.latitude ?: 0.0,
                            lng = location?.longitude ?: 0.0,
                            page = 1,
                            pageSize = 20,
                            radius = 5000
                        )
                    }
                    
                    Log.d("HomeViewModel", "📥 收到搜索响应:")
                    Log.d("HomeViewModel", "  - code=${result.code}")
                    Log.d("HomeViewModel", "  - message=${result.message}")
                    Log.d("HomeViewModel", "  - data size=${result.data?.size}")
                    
                    if (result.isSuccess()) {
                        result.data?.let { poiList ->
                            if (poiList.isNotEmpty()) {
                                Log.d("HomeViewModel", "搜索结果列表:")
                                poiList.forEachIndexed { index, poi ->
                                    Log.d("HomeViewModel", "  [$index] ${poi.name} - ${poi.address} (${poi.distance}m, score=${poi.score})")
                                }
                                // ⭐ 修改：按后端返回的评分降序排列，评分相同时按距离升序
                                val sortedPoiList = poiList.sortedWith(
                                    compareByDescending<PoiResponse> { 
                                        it.score ?: 0.0  // 直接使用后端返回的 score
                                    }.thenBy { 
                                        it.distance ?: Double.MAX_VALUE  // 评分相同时按距离升序
                                    }
                                )
                                _backendPoiResults.value = sortedPoiList
                                Log.d("HomeViewModel", "✅ 后端搜索成功：${poiList.size} 个结果，已按评分排序")
                            } else {
                                if (!nationwide) {
                                    Log.d("HomeViewModel", "周边搜索无结果，切换到备用接口")
                                    searchPoiFromBackend(keyword, true)
                                    return@let
                                }
                                Log.w("HomeViewModel", "⚠️ 后端搜索无结果")
                            }
                        } ?: run {
                            Log.e("HomeViewModel", "❌ 返回的 data 为 null")
                        }
                    } else {
                        Log.e("HomeViewModel", "❌ 后端搜索失败：${result.message}")
                        // ⭐ 修改：如果 searchDestination 失败，尝试 searchNearby
                        if (!nationwide && location != null) {
                            Log.d("HomeViewModel", "searchDestination 接口失败，尝试 searchNearby")
                            val fallbackResult = apiService.searchNearby(
                                keyword = simplifiedKeyword,
                                lat = location.latitude,
                                lng = location.longitude,
                                page = 1,
                                pageSize = 20,
                                radius = 5000
                            )
                            
                            Log.d("HomeViewModel", "📥 searchNearby 响应:")
                            Log.d("HomeViewModel", "  - code=${fallbackResult.code}")
                            Log.d("HomeViewModel", "  - data size=${fallbackResult.data?.size}")
                            
                            if (result.isSuccess()) {
                                fallbackResult.data?.let { poiList ->
                                    // ⭐ 修改：按后端返回的评分降序排列，评分相同时按距离升序
                                    val sortedPoiList = poiList.sortedWith(
                                        compareByDescending<PoiResponse> { 
                                            it.score ?: 0.0  // 直接使用后端返回的 score
                                        }.thenBy { 
                                            it.distance ?: Double.MAX_VALUE  // 评分相同时按距离升序
                                        }
                                    )
                                    _backendPoiResults.value = sortedPoiList
                                    Log.d("HomeViewModel", "✅ searchNearby 成功：${poiList.size} 个结果，已按评分排序")
                                }
                                return@launch
                            }
                        }
                        
                        if (!nationwide) {
                            Log.d("HomeViewModel", "所有接口都失败，切换到全国搜索模式")
                            searchPoiFromBackend(keyword, true)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 后端搜索异常", e)
                    // ⭐ 修改：不再降级到 SDK 搜索，直接提示用户
                    Log.e("HomeViewModel", "⚠️ 搜索失败：${e.message}")
                    
                    if (!nationwide) {
                        Log.d("HomeViewModel", "后端搜索异常，切换到全国搜索模式")
                        searchPoiFromBackend(keyword, true)
                        return@launch
                    }
                } finally {
                    _isSearching.value = false
                }
            }
        }
    
        // ⭐ 已移除 searchPoi 方法，统一使用后端 API 搜索
    
        fun clearSearchResults() {
            _searchResults.value = emptyList()
            _backendPoiResults.value = emptyList()
            _isSearching.value = false
        }
    
        fun selectPoi(poi: Any) {
            // ⭐ 修复：正确获取 POI 名称
            val poiName = when (poi) {
                is com.amap.api.maps.model.Poi -> poi.name
                is PoiResponse -> poi.name ?: "未知位置"
                else -> poi.toString()
            }
            updateDestination(poiName)
            clearSearchResults()
            _selectedPoiForMap.value = poi
            Log.d("HomeViewModel", "选中 POI: $poiName")
        }
    
        // ⭐ 修改：点击 POI 后直接获取详情和路线（不触发新搜索）
        fun selectBackendPoi(poi: PoiResponse) {
            viewModelScope.launch {
                Log.d("HomeViewModel", "=== 用户点击 POI ===")
                Log.d("HomeViewModel", "POI 名称：${poi.name}")
                Log.d("HomeViewModel", "POI 坐标：lat=${poi.lat}, lng=${poi.lng}")
                
                updateDestination(poi.name ?: "未知位置")
                val latLng = LatLng(poi.lat, poi.lng)
                _clickedLocation.value = latLng
                clearSearchResults()
                
                // ⭐ 修改：立即调用后端详情接口获取路线信息
                try {
                    val location = _currentLocation.value
                    if (location != null) {
                        Log.d("HomeViewModel", "开始调用 getPoiDetail 接口")
                        Log.d("HomeViewModel", "  - poiName=${poi.name}")
                        Log.d("HomeViewModel", "  - origin=${location.latitude},${location.longitude}")
                        Log.d("HomeViewModel", "  - destination=${poi.lat},${poi.lng}")
                        
                        val response = apiService.getPoiDetail(
                            poiName = poi.name ?: "未知位置",  // ⭐ 改为 poiName
                            lat = location.latitude,  // ⭐ 起点纬度（当前位置）
                            lng = location.longitude,  // ⭐ 起点经度（当前位置）
                            mode = "driving"
                        )
                        
                        if (response.isSuccess()) {
                            Log.d("HomeViewModel", "✅ 获取到 POI 详情")
                            val poiDetail = response.data
                            
                            // ⭐ Bug 修复：添加 null 检查，并只使用 PoiResponse 中存在的字段
                            if (poiDetail != null) {
                                Log.d("HomeViewModel", "POI 详情：name=${poiDetail.poi?.name}, canOrder=${poiDetail.canOrder}")
                                Log.d("HomeViewModel", "路线信息：distance=${poiDetail.route?.distance}m, duration=${poiDetail.route?.duration}s, price=${poiDetail.route?.price}")
                                
                                // ⭐ 优先使用后端返回的路线信息
                                var distance: Double? = poiDetail.route?.distance?.toDouble()
                                var duration: Int? = poiDetail.route?.duration
                                var price: Int? = poiDetail.route?.price?.toInt()
                                
                                // ⭐ 如果后端没有返回路线信息，使用本地计算
                                if (distance == null || distance <= 0) {
                                    val routeResult = calculateRouteDistance(location, latLng)
                                    
                                    if (routeResult != null && routeResult.distance > 0) {
                                        distance = routeResult.distance.toDouble()
                                        duration = routeResult.duration.toInt()
                                        price = calculatePrice(distance)
                                        Log.d("HomeViewModel", "使用路径规划距离：${distance}米，时长：${duration}秒，价格：${price}元")
                                    } else {
                                        // ⭐ 降级使用直线距离
                                        val straightDistance = calculateDistance(
                                            location.latitude,
                                            location.longitude,
                                            poi.lat,
                                            poi.lng
                                        )
                                        distance = straightDistance
                                        duration = (straightDistance / 500 * 3).toLong().toInt()
                                        price = calculatePrice(distance)
                                        Log.d("HomeViewModel", "使用直线距离：${distance}米")
                                    }
                                }
                                
                                // ⭐ 生成 sessionId（用于后续下单）
                                val sessionId = "map_click_${System.currentTimeMillis()}"
                                
                                // ⭐ 更新 UI 状态，显示路线信息和确认按钮
                                _poiDetail.value = PoiDetail(
                                    name = poi.name ?: "未知位置",
                                    address = poi.address ?: poi.name ?: "未知地址",
                                    lat = poi.lat,
                                    lng = poi.lng,
                                    distance = distance,
                                    duration = duration,
                                    price = price,
                                    canOrder = true,  // ⭐ 默认可叫车
                                    sessionId = sessionId  // ⭐ 保存 sessionId
                                )
                                _showPoiDetailDialog.value = true
                                
                                Log.d("HomeViewModel", "路线距离：${distance}米")
                                Log.d("HomeViewModel", "预计时间：${duration}秒")
                                Log.d("HomeViewModel", "预估价格：${price}元")
                                Log.d("HomeViewModel", "SessionId: $sessionId")
                            } else {
                                Log.e("HomeViewModel", "❌ POI 详情 data 为 null")
                                _poiDetail.value = null
                                _showPoiDetailDialog.value = false
                            }
                        } else {
                            Log.e("HomeViewModel", "❌ POI 详情获取失败：${response.message}")
                            Log.w("HomeViewModel", "⚠️ 后端路线规划失败（INVALID_PARAMS），使用本地计算")
                            
                            // ⭐ 降级处理：使用本地计算
                            val routeResult = calculateRouteDistance(location, latLng)
                            var distance: Double? = null
                            var duration: Int? = null
                            var price: Int? = null
                            
                            if (routeResult != null && routeResult.distance > 0) {
                                distance = routeResult.distance.toDouble()
                                duration = routeResult.duration.toInt()
                                price = calculatePrice(distance)
                            } else {
                                val straightDistance = calculateDistance(
                                    location.latitude,
                                    location.longitude,
                                    poi.lat,
                                    poi.lng
                                )
                                distance = straightDistance
                                duration = (straightDistance / 500 * 3).toLong().toInt()
                                price = calculatePrice(distance)
                            }
                            
                            val sessionId = "map_click_${System.currentTimeMillis()}"
                            _poiDetail.value = PoiDetail(
                                name = poi.name ?: "未知位置",
                                address = poi.address ?: poi.name ?: "未知地址",
                                lat = poi.lat,
                                lng = poi.lng,
                                distance = distance,
                                duration = duration,
                                price = price,
                                canOrder = true,
                                sessionId = sessionId
                            )
                            _showPoiDetailDialog.value = true
                        }
                    } else {
                        Log.e("HomeViewModel", "❌ 当前位置为空，无法获取路线")
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 获取 POI 详情异常", e)
                    // 异常情况下降级处理
                    _poiDetail.value = null
                    _showPoiDetailDialog.value = false
                }
            }
        }
    
        fun clearSelectedPoi() {
            _selectedPoiForMap.value = null
        }
    
        // ⭐ 修改：通过 WebSocket 创建订单
        fun createOrder(destName: String) {
            Log.d("HomeViewModel", "=== 开始创建订单 (HTTP API 方式) ===")
            Log.d("HomeViewModel", "目的地名称参数：$destName")
            
            viewModelScope.launch {
                try {
                    _isCreatingOrder.value = true
                    _orderState.value = OrderState.Loading
                    
                    // ⭐ 修改：优先使用 poiDetail 中的信息
                    val detail = _poiDetail.value
                    val selectedPoi = _selectedPoiForMap.value
                    val clickedLoc = _clickedLocation.value
                    val currentLoc = _currentLocation.value
                    
                    Log.d("HomeViewModel", "当前状态检查:")
                    Log.d("HomeViewModel", "  - detail: ${detail?.name}")
                    Log.d("HomeViewModel", "  - selectedPoi: ${(selectedPoi as? PoiResponse)?.name}")
                    Log.d("HomeViewModel", "  - clickedLoc: $clickedLoc")
                    Log.d("HomeViewModel", "  - currentLoc: $currentLoc")
                    
                    val destLat: Double
                    val destLng: Double
                    val finalDestName: String  // ⭐ 明确最终使用的目的地名称
                    
                    when {
                        // ⭐ 优先使用详情中的信息
                        detail != null -> {
                            destLat = detail.lat
                            destLng = detail.lng
                            finalDestName = detail.name
                            Log.d("HomeViewModel", "✅ 使用 POI 详情的坐标和名称：lat=$destLat, lng=$destLng, name=$finalDestName")
                        }
                        selectedPoi != null -> {
                            when (selectedPoi) {
                                is com.amap.api.maps.model.Poi -> {
                                    destLat = selectedPoi.coordinate.latitude
                                    destLng = selectedPoi.coordinate.longitude
                                    finalDestName = selectedPoi.name
                                    Log.d("HomeViewModel", "✅ 使用选中 POI (高德) 的坐标和名称：lat=$destLat, lng=$destLng, name=$finalDestName")
                                }
                                is PoiResponse -> {
                                    destLat = selectedPoi.lat
                                    destLng = selectedPoi.lng
                                    finalDestName = selectedPoi.name ?: destName
                                    Log.d("HomeViewModel", "✅ 使用选中 POI (后端) 的坐标和名称：lat=$destLat, lng=$destLng, name=$finalDestName")
                                }
                                else -> {
                                    Log.e("HomeViewModel", "❌ 未知的 POI 类型：${selectedPoi::class.java}")
                                    _orderState.value = OrderState.Error("无法识别的 POI 类型")
                                    _isCreatingOrder.value = false
                                    return@launch
                                }
                            }
                        }
                        clickedLoc != null -> {
                            destLat = clickedLoc.latitude
                            destLng = clickedLoc.longitude
                            finalDestName = destName
                            Log.d("HomeViewModel", "⚠️ 使用点击位置的坐标，使用输入的名称：lat=$destLat, lng=$destLng, name=$finalDestName")
                        }
                        currentLoc != null -> {
                            destLat = currentLoc.latitude
                            destLng = currentLoc.longitude
                            finalDestName = destName
                            Log.d("HomeViewModel", "⚠️ 使用当前位置作为目的地：lat=$destLat, lng=$destLng, name=$finalDestName")
                        }
                        else -> {
                            Log.e("HomeViewModel", "❌ 无法获取任何位置信息")
                            _orderState.value = OrderState.Error("无法获取目的地位置，请稍后重试")
                            _isCreatingOrder.value = false
                            return@launch
                        }
                    }
                    
                    // ⭐ 修改：使用 HTTP API 创建订单 (能直接获取响应)
                    val request = com.example.myapplication.data.model.CreateOrderRequest(
                        poiName = finalDestName,      // ⭐ 使用 poiName（必填）
                        destLat = destLat,
                        destLng = destLng,
                        passengerCount = 1,
                        remark = null
                    )
                    
                    Log.d("HomeViewModel", "📤 发送创建订单请求：$request")
                    val result = orderRepository.createOrder(
                        poiName = finalDestName,
                        poiLat = destLat,
                        poiLng = destLng,
                        passengerCount = 1,
                        remark = null
                    )
                    
                    if (result.isSuccess()) {
                        result.data?.let { order ->
                            Log.d("HomeViewModel", "✅ 订单创建成功！")
                            Log.d("HomeViewModel", "  - 订单号：${order.orderNo}")
                            Log.d("HomeViewModel", "  - 目的地：${order.poiName}")
                            Log.d("HomeViewModel", "  - 坐标：lat=${order.destLat}, lng=${order.destLng}")
                            Log.d("HomeViewModel", "  - 预估价格：¥${order.estimatePrice}")
                            _orderState.value = OrderState.Success(order)
                            // ⭐ 触发事件，通知 UI 跳转
                            _events.send(HomeEvent.OrderCreated(order))
                            
                            // ⭐ 新增：广播订单创建成功事件（用于刷新个人中心订单列表）
                            Log.d("HomeViewModel", "📢 广播订单创建成功事件，刷新订单列表")
                        } ?: run {
                            Log.e("HomeViewModel", "❌ 返回数据为空")
                            _orderState.value = OrderState.Error("返回数据为空")
                        }
                    } else {
                        Log.e("HomeViewModel", "❌ 订单创建失败：${result.message}")
                        _orderState.value = OrderState.Error(result.message ?: "未知错误")
                    }
                    
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 订单创建异常", e)
                    _orderState.value = OrderState.Error("网络错误：${e.message}")
                } finally {
                    _isCreatingOrder.value = false
                }
            }
        }
    
        fun resetOrderState() {
            _orderState.value = OrderState.Idle
        }
    
        // ⭐ 修改：完善资源清理
        override fun onCleared() {
            super.onCleared()
            
            Log.d("HomeViewModel", "=== HomeViewModel onCleared 被调用 ===")
            
            // ⭐ 销毁语音识别助手
            speechHelper?.destroy()
            speechHelper = null
            
            // ⭐ 取消所有协程
            searchJob?.cancel()
            
            Log.d("HomeViewModel", "HomeViewModel 已清理，资源已释放")
        }
    
        sealed class OrderState {
            object Idle : OrderState()
            object Loading : OrderState()
            data class Success(val order: Order) : OrderState()
            data class Error(val message: String) : OrderState()
        }
    }