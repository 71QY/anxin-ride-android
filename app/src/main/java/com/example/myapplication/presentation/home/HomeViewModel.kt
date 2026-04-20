@file:Suppress("DEPRECATION", "NewApi", "UNRESOLVED_REFERENCE")

    package com.example.myapplication.presentation.home
    
    import android.app.PendingIntent
    import android.content.Context
    import android.content.Intent
    import android.os.Build
    import android.util.LruCache
    import android.util.Log
    import android.widget.Toast  // ⭐ 新增：Toast 导入
    import androidx.core.app.NotificationCompat
    // import com.example.myapplication.R  // ⭐ R类会在编译后自动生成
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
    import com.example.myapplication.data.model.GuardianInfo  // ⭐ 新增：亲友信息
    import com.example.myapplication.data.repository.AgentRepository
    import com.example.myapplication.data.repository.OrderRepository
    import com.example.myapplication.domain.repository.IOrderRepository
    import com.example.myapplication.core.utils.BaiduSpeechRecognizerHelper
    import com.example.myapplication.MyApplication
    import com.example.myapplication.core.datastore.TokenManager
    // ⭐ 修复：移除 AppIconSwitcher 导入，图标切换由 LoginViewModel 负责
    import dagger.hilt.android.lifecycle.HiltViewModel
    import dagger.hilt.android.qualifiers.ApplicationContext
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.channels.Channel
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.SupervisorJob
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.flow.receiveAsFlow
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.suspendCancellableCoroutine
    import kotlinx.coroutines.withContext
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
        private val favoritesRepository: com.example.myapplication.data.repository.FavoritesRepository,  // ⭐ 新增：收藏仓库
        @ApplicationContext private val appContext: Context
    ) : ViewModel() {
    
        // ⭐ 新增：伴生对象，存放静态标志
        companion object {
            // ⭐ 修复：使用伴生对象的静态变量，避免跨实例重复监听 WebSocket
            @Volatile
            private var wsMessageListenerStarted = false
        }
    
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
    
        // ⭐ 新增：起点位置（用于代叫车，长辈位置）
        private val _startLocation = MutableStateFlow<LatLng?>(null)
        val startLocation: StateFlow<LatLng?> = _startLocation.asStateFlow()
    
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
        
        // ⭐ 新增：长辈模式标识
        private val _isElderMode = MutableStateFlow(false)
        val isElderMode: StateFlow<Boolean> = _isElderMode.asStateFlow()
        
        // ⭐ 新增：userId
        private val _userId = MutableStateFlow<Long?>(null)
        val userId: StateFlow<Long?> = _userId.asStateFlow()
        
        // ⭐ 修复：用户信息加载状态（用于自动登录验证）
        private val _isProfileLoaded = MutableStateFlow(false)
        val isProfileLoaded: StateFlow<Boolean> = _isProfileLoaded.asStateFlow()
        
        // ⭐ 新增：缓存用户信息（用于代叫车时获取真实姓名）
        private val _userNickname = MutableStateFlow<String?>(null)
        val userNickname: StateFlow<String?> = _userNickname.asStateFlow()
        
        // ⭐ 新增：亲友列表（长辈模式）
        private val _guardianInfoList = MutableStateFlow<List<GuardianInfo>>(emptyList())
        val guardianInfoList: StateFlow<List<GuardianInfo>> = _guardianInfoList.asStateFlow()
        
        // ⭐ 新增：长辈列表（普通用户模式，用于帮长辈叫车）
        private val _elderInfoList = MutableStateFlow<List<com.example.myapplication.data.model.ElderInfo>>(emptyList())
        val elderInfoList: StateFlow<List<com.example.myapplication.data.model.ElderInfo>> = _elderInfoList.asStateFlow()
        
        // ⭐ 新增：长辈列表加载标志（用于缓存）
        private var _elderListLoaded = false
        
        // ⭐ 新增：checkElderMode 防抖时间戳（避免重复调用）
        private var lastCheckElderModeTime = 0L
        private val CHECK_ELDER_MODE_DEBOUNCE_MS = 3000L  // 3秒内只允许调用一次
        
        // ⚠️ 已移除：_wsMessageListenerStarted，改用伴生对象的静态变量
        
        // ⭐ 高优先级2：代叫车请求通知（用于长辈端）
        data class ProxyOrderRequest(
            val orderId: Long,
            val requesterName: String,  // 代叫人姓名
            val destination: String     // 目的地
        )
        private val _proxyOrderRequest = MutableStateFlow<ProxyOrderRequest?>(null)
        val proxyOrderRequest: StateFlow<ProxyOrderRequest?> = _proxyOrderRequest.asStateFlow()
        
        private var searchJob: Job? = null
        private var speechHelper: BaiduSpeechRecognizerHelper? = null
        private val addressCache = LruCache<Long, String>(20)
    
        private val requestId = AtomicLong(0)
    
        private val _events = Channel<HomeEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()
        
        sealed class HomeEvent {
            data class LocationUpdated(val lat: Double, val lng: Double) : HomeEvent()
            data class OrderCreated(val order: Order) : HomeEvent()  // ⭐ 新增
            data class NavigateToOrderTracking(val orderId: Long) : HomeEvent()  // ⭐ 新增：跳转到行程追踪页面
        }
    
        private fun getCacheKey(latLng: LatLng): Long {
            return (latLng.latitude * 10_000).toLong() * 10_000 + (latLng.longitude * 10_000).toLong()
        }
        
        // ⭐ 新增：同步获取 userId（用于自动登录判断）
        fun getUserIdSync(): Long? {
            return tokenManager.getUserId()
        }
        
        init {
            // ⭐ 新增：获取 userId（在协程中调用 suspend 函数）
            viewModelScope.launch {
                _userId.value = tokenManager.getUserId()
                Log.d("HomeViewModel", "🔑 userId: ${_userId.value}")
                
                // ⭐ 修复：自动调用 checkElderMode，确保 isElderMode 状态正确
                if (_userId.value != null) {
                    Log.d("HomeViewModel", "✅ 检测到 userId，自动检查长辈模式")
                    checkElderMode(
                        onAuthFailure = {
                            Log.w("HomeViewModel", "⚠️ Token已失效，需要重新登录")
                            _isProfileLoaded.value = false
                        }
                    )
                } else {
                    Log.d("HomeViewModel", "ℹ️ 无 userId，跳过自动登录")
                }
            }
            
            // ⭐ 关键修复：监听全局代叫车请求事件
            viewModelScope.launch {
                MyApplication.proxyOrderRequestEvent.collect { event ->
                    Log.d("HomeViewModel", "📩 收到全局代叫车请求事件：orderId=${event.orderId}, from=${event.requesterName}, to=${event.destination}")
                    
                    onProxyOrderRequestReceived(
                        orderId = event.orderId,
                        requesterName = event.requesterName,
                        destination = event.destination
                    )
                }
            }
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
        
        // ⭐ 新增：从收藏设置目的地（带坐标，直接显示POI详情）
        fun setDestinationFromFavorite(
            name: String, 
            latitude: Double, 
            longitude: Double,
            startLat: Double? = null,  // ⭐ 新增：起点纬度（长辈位置）
            startLng: Double? = null   // ⭐ 新增：起点经度（长辈位置）
        ) {
            Log.d("HomeViewModel", "📍 从收藏设置目的地：$name, lat=$latitude, lng=$longitude")
            if (startLat != null && startLng != null) {
                Log.d("HomeViewModel", "🚗 使用长辈位置作为起点：lat=$startLat, lng=$startLng")
                // ⭐ 保存起点位置
                _startLocation.value = com.amap.api.maps.model.LatLng(startLat, startLng)
            } else {
                // ⭐ 清除之前的起点
                _startLocation.value = null
            }
            
            // ⭐ 设置目的地
            _destination.value = name
            _clickedLocation.value = com.amap.api.maps.model.LatLng(latitude, longitude)
            _geocodeError.value = null
            
            // ⭐ 清空之前的搜索结果
            clearSearchResults()
            
            // ⭐ 直接显示POI详情对话框（类似搜索后点击地点的效果）
            viewModelScope.launch {
                try {
                    val latLng = com.amap.api.maps.model.LatLng(latitude, longitude)
                    
                    // 获取地址
                    val address = fetchPoiAddress(latLng)
                    
                    // 计算距离和费用
                    val currentLocation = _currentLocation.value
                    var distance: Double? = null
                    var duration: Int? = null
                    var price: Int? = null
                    var formattedDistance: String? = null
                    
                    if (currentLocation != null) {
                        // 先使用直线距离
                        val straightDistance = calculateDistance(
                            currentLocation.latitude,
                            currentLocation.longitude,
                            latitude,
                            longitude
                        )
                        
                        // 异步调用路径规划获取真实里程
                        val routeResult = calculateRouteDistance(currentLocation, latLng)
                        
                        if (routeResult != null && routeResult.distance > 0) {
                            distance = routeResult.distance.toDouble()
                            duration = routeResult.duration.toInt()
                            price = calculatePrice(distance)
                            formattedDistance = formatDistance(distance)
                            Log.d("HomeViewModel", "使用路径规划距离：${distance}米")
                        } else {
                            distance = straightDistance
                            formattedDistance = formatDistance(distance)
                            duration = (straightDistance / 500 * 3).toLong().toInt()
                            price = calculatePrice(distance)
                        }
                    }
                    
                    // 生成 sessionId
                    val sessionId = "favorite_${System.currentTimeMillis()}"
                    
                    // 设置 POI 详情
                    _poiDetail.value = PoiDetail(
                        name = name,
                        address = address ?: name,
                        lat = latitude,
                        lng = longitude,
                        distance = distance,
                        duration = duration,
                        price = price,
                        canOrder = true,
                        formattedDistance = formattedDistance,
                        sessionId = sessionId
                    )
                    
                    // 显示 POI 详情对话框
                    _showPoiDetailDialog.value = true
                    
                    Log.d("HomeViewModel", "✅ 已设置 POI 详情对话框，等待用户确认目的地")
                    
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 设置收藏目的地失败", e)
                    // 降级处理：至少显示基本信息
                    _poiDetail.value = PoiDetail(
                        name = name,
                        address = name,
                        lat = latitude,
                        lng = longitude,
                        canOrder = true,
                        sessionId = "favorite_${System.currentTimeMillis()}"
                    )
                    _showPoiDetailDialog.value = true
                }
            }
            
            Log.d("HomeViewModel", "✅ 收藏目的地设置完成，POI详情对话框将自动显示")
        }
        
        // ⭐ 新增：聚焦到长辈位置（在地图上显示长辈实时位置）
        fun focusOnElderLocation(elderName: String, latitude: Double, longitude: Double) {
            Log.d("HomeViewModel", "📍 聚焦到长辈位置：$elderName - lat=$latitude, lng=$longitude")
            
            // ⭐ 设置点击位置为长辈位置
            _clickedLocation.value = com.amap.api.maps.model.LatLng(latitude, longitude)
            
            // ⭐ 清除之前的目的地和 POI 详情
            _destination.value = ""  // ⭐ 修复：使用空字符串而不是 null
            _poiDetail.value = null
            _showPoiDetailDialog.value = false
            
            // ⭐ 清空搜索结果
            clearSearchResults()
            
            Log.d("HomeViewModel", "✅ 已设置地图中心点为长辈位置，地图将自动移动")
        }
    
        // ⭐ 新增：从 POI 详情对话框添加收藏
        fun addFavorite(
            name: String,
            address: String,
            latitude: Double,
            longitude: Double,
            onSuccess: () -> Unit = {},
            onError: (String) -> Unit = {}
        ) {
            viewModelScope.launch {
                try {
                    Log.d("HomeViewModel", "➕ 从 POI 详情添加收藏：$name")
                    val request = com.example.myapplication.data.model.SaveFavoriteRequest(
                        name = name,
                        address = address,
                        latitude = latitude,
                        longitude = longitude,
                        type = "CUSTOM"
                    )
                    
                    val result = favoritesRepository.addFavorite(request)
                    
                    result.onSuccess { favorite ->
                        Log.d("HomeViewModel", "✅ 收藏成功")
                        onSuccess()
                    }.onFailure { error ->
                        val errorMsg = when {
                            error.message?.contains("429") == true || error.message?.contains("超限") == true -> {
                                "收藏数量已达上限（最多50个）"
                            }
                            else -> error.message ?: "添加收藏失败"
                        }
                        Log.e("HomeViewModel", "❌ 收藏失败：$errorMsg")
                        onError(errorMsg)
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 收藏异常", e)
                    onError(e.message ?: "网络异常")
                }
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
                        if (finalText.isNotBlank() && !finalText.contains("配置错误")) {
                            // ⭐ 新增：应用敏感词过滤
                            val filteredText = com.example.myapplication.core.utils.SensitiveWordFilter.filterText(finalText)
                            _voiceText.value = filteredText
                            updateDestination(filteredText)  // ⭐ 只更新目的地，不自动搜索
                            
                            // ⭐ 如果检测到敏感词，提示用户
                            if (filteredText != finalText) {
                                Log.w("HomeViewModel", "⚠️ 检测到敏感词，已过滤")
                            }
                        } else if (finalText.contains("配置错误")) {
                            Log.e("HomeViewModel", "⚠️ 语音配置错误")
                            // ⭐ 显示错误提示
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
                            // ⭐ 实时结果也应用敏感词过滤
                            val filteredText = com.example.myapplication.core.utils.SensitiveWordFilter.filterText(partialText)
                            _voiceText.value = filteredText
                            updateDestination(filteredText)  // ⭐ 实时更新 UI，让用户看到自己说的话
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
            speechHelper?.stopListening()  // ⭐ 修改：使用 stopListening 而不是 destroy
            speechHelper = null  // ⭐ 修改：清空引用，防止内存泄漏
            _isListening.value = false
            // ⭐ 不清空 voiceText，让用户看到最后识别的内容
        }
    
        fun updateCurrentLocation(lat: Double, lng: Double) {
            _currentLocation.value = LatLng(lat, lng)
            // ⭐ 同步到全局位置管理器，供其他页面使用
            com.example.myapplication.core.utils.LocationManager.updateLocation(lat, lng)
        }
    
        // ⭐ 新增：更新定位精度
        fun updateLocationAccuracy(accuracy: Float) {
            _locationAccuracy.value = accuracy
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
            _destination.value = poi.name  // ⭐ 关键修复：立即设置目的地，不等待逆地理编码
            
            Log.d("HomeViewModel", "✅ 已设置 destination='${poi.name}', clickedLocation=$latLng")
            
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
                    // ✅ 修复：即使出错也显示基本信息，但标记为不可下单
                    _poiDetail.value = PoiDetail(
                        name = poi.name,
                        address = poi.name,  // ✅ 降级：使用名称作为地址
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
                            // ⭐ 关键修复：高德地图成功码是 1000，不是 10000
                            if (rCode == 1000) { // AMapException.CODE_AMAP_SUCCESS
                                val address = result?.regeocodeAddress?.formatAddress
                                Log.d("HomeViewModel", "✅ 逆地理编码成功：$address")
                                continuation.resume(address)
                            } else {
                                Log.e("HomeViewModel", "❌ 逆地理编码失败，rCode=$rCode")
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
            // ⭐ 关键修复：关闭弹窗时，将 poiDetail 保存到 selectedPoiForMap，供后续代叫车使用
            _poiDetail.value?.let { detail ->
                Log.d("HomeViewModel", "💾 保存 POI 详情到 selectedPoiForMap：name=${detail.name}, lat=${detail.lat}, lng=${detail.lng}")
                _selectedPoiForMap.value = detail
            }
            _poiDetail.value = null
        }
        
        // ⭐ 保留一个 confirmPoiSelection 方法即可
        fun confirmPoiSelection() {
            _showPoiDetailDialog.value = false
            _poiDetail.value?.let { detail ->
                Log.d("HomeViewModel", "=== 确认选择目的地 ===")
                Log.d("HomeViewModel", "目的地：${detail.name}")
                // ⭐ 修改：不再立即创建订单，而是由 UI 层决定下单类型
                // UI 层会显示选择对话框：帮自己叫车 / 帮长辈叫车
            }
        }
    
        // ⭐ 修改：优化搜索逻辑，允许在没有位置时也能搜索
        fun searchPoiFromBackend(keyword: String, nationwide: Boolean = false) {
            Log.d("HomeViewModel", "🔍 === searchPoiFromBackend 被调用 ===")
            Log.d("HomeViewModel", "  - keyword=$keyword")
            Log.d("HomeViewModel", "  - nationwide=$nationwide")
            
            if (keyword.isBlank()) {
                Log.e("HomeViewModel", "❌ 搜索关键词为空")
                return
            }
            
            val location = _currentLocation.value
            Log.d("HomeViewModel", "  - currentLocation=$location")
            
            // ⭐ 修改：即使没有位置也可以进行全国搜索
            if (location == null && !nationwide) {
                Log.w("HomeViewModel", "⚠️ 当前位置为空，切换到全国搜索模式")
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
                            
                            // ⭐ 修复：检查 fallbackResult 而不是 result
                            if (fallbackResult.isSuccess()) {
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
        
        // ⭐ 新增：为帮长辈叫车保存 POI 详情
        fun selectPoiForProxyOrder(detail: PoiDetail) {
            Log.d("HomeViewModel", "💾 保存 POI 详情用于帮长辈叫车：name=${detail.name}, lat=${detail.lat}, lng=${detail.lng}")
            _selectedPoiForMap.value = detail
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
        fun createOrder(destName: String, elderId: Long? = null) {
            Log.d("HomeViewModel", "=== 开始创建订单 (HTTP API 方式) ===")
            Log.d("HomeViewModel", "目的地名称参数：$destName, elderId: $elderId")
            
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
                                // ⭐ 新增：处理 PoiDetail 类型（代叫车场景）
                                is com.example.myapplication.data.model.PoiDetail -> {
                                    destLat = selectedPoi.lat
                                    destLng = selectedPoi.lng
                                    finalDestName = selectedPoi.name
                                    Log.d("HomeViewModel", "✅ 使用选中 POI Detail 的坐标和名称：lat=$destLat, lng=$destLng, name=$finalDestName")
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
                    // ⭐ 关键修复：如果 _startLocation 为 null，使用当前位置作为起点
                    val startLat = _startLocation.value?.latitude ?: _currentLocation.value?.latitude
                    val startLng = _startLocation.value?.longitude ?: _currentLocation.value?.longitude
                    
                    if (startLat == null || startLng == null) {
                        Log.e("HomeViewModel", "❌ 无法获取起点位置")
                        _orderState.value = OrderState.Error("无法获取起点位置，请确保已开启定位")
                        _isCreatingOrder.value = false
                        return@launch
                    }
                    
                    Log.d("HomeViewModel", "📍 起点坐标：lat=$startLat, lng=$startLng")
                    Log.d("HomeViewModel", "📍 终点坐标：lat=$destLat, lng=$destLng")
                    
                    // ⭐ 检查起点和终点是否相同（误差 < 10米）
                    val distance = calculateDistance(startLat, startLng, destLat, destLng)
                    if (distance < 10.0) {
                        Log.w("HomeViewModel", "⚠️ 起点和终点距离过近！距离=${distance}米，这会导致路线长度为0")
                        Toast.makeText(
                            appContext,
                            "⚠️ 起点和终点距离过近（${String.format("%.0f", distance)}米），请重新选择目的地",
                            Toast.LENGTH_LONG
                        ).show()
                        _orderState.value = OrderState.Error("起点和终点不能相同或过于接近")
                        _isCreatingOrder.value = false
                        return@launch
                    }
                    
                    val request = com.example.myapplication.data.model.CreateOrderRequest(
                        poiName = finalDestName,      // ⭐ 使用 poiName（必填）
                        destLat = destLat,
                        destLng = destLng,
                        passengerCount = 1,
                        remark = null,
                        startLat = startLat,  // ⭐ 新增：起点纬度
                        startLng = startLng   // ⭐ 新增：起点经度
                    )
                    
                    Log.d("HomeViewModel", "📤 发送创建订单请求：$request")
                    val result = orderRepository.createOrder(
                        poiName = finalDestName,
                        poiLat = destLat,
                        poiLng = destLng,
                        passengerCount = 1,
                        remark = null,
                        elderId = elderId,  // ⭐ 新增：传递长辈ID
                        startLat = startLat,  // ⭐ 关键修复：传递起点纬度
                        startLng = startLng   // ⭐ 关键修复：传递起点经度
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
                            
                            // ⭐ 关键修复：通过事件总线通知 ChatViewModel 更新 sharedLocation
                            if (elderId != null) {
                                viewModelScope.launch {
                                    try {
                                        // 发送全局事件，让 ChatViewModel 监听并更新
                                        com.example.myapplication.MyApplication.sendOrderCreatedEvent(order.id, elderId)
                                        Log.d("HomeViewModel", "✅ 已发送订单创建事件: orderId=${order.id}, elderId=$elderId")
                                    } catch (e: Exception) {
                                        Log.e("HomeViewModel", "❌ 发送订单创建事件失败", e)
                                    }
                                }
                            }
                            
                            // ⭐ 修复：延迟发送导航事件，确保 orderState 已被 UI 层收集
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(100)  // 延迟 100ms
                                _events.send(HomeEvent.NavigateToOrderTracking(order.id))
                                Log.d("HomeViewModel", "🚀 已发送导航事件: orderId=${order.id}")
                            }
                            
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
    
        /**
         * ⭐ 检查是否为长辈模式
         */
        /**
         * 呼叫亲友（长辈操作）
         */
        fun callGuardian(
            onSuccess: (phone: String, name: String) -> Unit,
            onError: (String) -> Unit
        ) {
            viewModelScope.launch {
                try {
                    val userId = tokenManager.getUserId()
                    if (userId == null) {
                        onError("用户未登录")
                        return@launch
                    }
                    
                    Log.d("HomeViewModel", "开始呼叫亲友")
                    
                    val response = apiService.callGuardian(userId)
                    if (response.isSuccess() && response.data != null) {
                        onSuccess(response.data!!.guardianPhone, response.data!!.guardianName)
                        Log.d("HomeViewModel", "呼叫亲友成功：${response.data!!.guardianName}")
                    } else {
                        onError(response.message ?: "呼叫失败")
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "呼叫亲友异常", e)
                    onError(e.message ?: "网络错误")
                }
            }
        }
        
        /**
         * 加载用户信息
         */
        private fun loadProfile(onAuthFailure: (() -> Unit)? = null) {
            viewModelScope.launch {
                try {
                    Log.d("HomeViewModel", "🔄 开始加载用户信息...")
                    val profile = apiService.getUserProfile()
                    if (profile.isSuccess() && profile.data != null) {
                        val serverGuardMode = profile.data.guardMode
                        _isElderMode.value = serverGuardMode == 1
                        tokenManager.saveGuardMode(serverGuardMode)
                        _isProfileLoaded.value = true  // ⭐ 标记为已加载
                        Log.d("HomeViewModel", "✅ 刷新用户信息成功，guardMode=$serverGuardMode")
                    } else {
                        Log.e("HomeViewModel", "❌ 获取用户信息失败：${profile.message}")
                        
                        // ⭐ 关键修复：只有认证失败才触发退出登录，网络错误等使用本地缓存
                        val isAuthError = profile.code == 401 || 
                                         profile.message?.contains("Unauthorized", ignoreCase = true) == true ||
                                         profile.message?.contains("token invalid", ignoreCase = true) == true ||
                                         profile.message?.contains("token expired", ignoreCase = true) == true ||
                                         profile.message?.contains("token失效", ignoreCase = true) == true ||
                                         profile.message?.contains("认证失败", ignoreCase = true) == true ||
                                         profile.message?.contains("登录已过期", ignoreCase = true) == true
                        
                        if (isAuthError) {
                            Log.e("HomeViewModel", "❌ 认证失败，需要重新登录")
                            _isProfileLoaded.value = false
                            onAuthFailure?.invoke()
                        } else {
                            // ⭐ 网络错误、服务器错误等，不修改 isProfileLoaded，保持之前的状态
                            Log.w("HomeViewModel", "⚠️ 获取用户信息失败（非认证错误），保留本地缓存状态：code=${profile.code}, message=${profile.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 刷新用户信息异常", e)
                    
                    // ⭐ 关键修复：只有在认证相关错误时才触发退出登录
                    // 网络异常、超时等情况不应踢用户回登录页
                    val isAuthError = e.message?.contains("401") == true || 
                                     e.message?.contains("Unauthorized", ignoreCase = true) == true ||
                                     e.message?.contains("token invalid", ignoreCase = true) == true ||
                                     e.message?.contains("token expired", ignoreCase = true) == true
                    
                    if (isAuthError) {
                        Log.w("HomeViewModel", "⚠️ Token已失效，需要重新登录")
                        _isProfileLoaded.value = false
                        onAuthFailure?.invoke()
                    } else {
                        // ⭐ 其他异常（网络错误等）不影响自动登录，不修改 isProfileLoaded
                        Log.w("HomeViewModel", "⚠️ 网络异常，保留本地缓存的用户信息状态：${e.message}")
                    }
                }
            }
        }
        
        // ⭐ 新增：WebSocket 连接防抖标记
        private var isConnectingWebSocket = false
        
        // ⭐ 新增：防抖标记，避免 checkElderMode 重复调用
        private var isCheckingElderMode = false
        
        // ⭐ 新增：WebSocket connected 消息防抖（避免每秒打印）
        private var lastConnectedLogTime = 0L
        private val CONNECTED_LOG_INTERVAL = 30000L  // ⭐ 增加到 30 秒内只打印一次
        
        /**
         * ⭐ 检查长辈模式并建立 WebSocket 连接
         */
        fun checkElderMode(onAuthFailure: (() -> Unit)? = null) {
            // ⭐ 新增：防抖机制，3秒内只允许调用一次
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCheckElderModeTime < CHECK_ELDER_MODE_DEBOUNCE_MS) {
                Log.d("HomeViewModel", "⏭️ checkElderMode 被频繁调用，跳过（距离上次调用仅 ${currentTime - lastCheckElderModeTime}ms）")
                return
            }
            lastCheckElderModeTime = currentTime
            
            // ⭐ 修复：移除防重复调用逻辑，允许每次调用都执行
            Log.d("HomeViewModel", "🔍 [checkElderMode入口] 开始执行")
            
            Log.d("HomeViewModel", "🚀 [checkElderMode] 即将启动 viewModelScope.launch...")
            viewModelScope.launch {
                Log.d("HomeViewModel", "✅ [checkElderMode] viewModelScope.launch 已启动")
                try {
                    Log.d("HomeViewModel", "🔍 === 开始检查长辈模式（自动登录验证）===")
                    Log.d("HomeViewModel", "📱 当前 userId: ${tokenManager.getUserId()}")
                    Log.d("HomeViewModel", "📱 当前 guardMode: ${tokenManager.getGuardMode()}")
                    
                    // ⭐ 关键修复：先设置为 true，防止 UI 层误判为加载失败
                    _isProfileLoaded.value = true
                    
                    // ⭐ 优先从本地存储读取
                    val localGuardMode = tokenManager.getGuardMode()
                    _isElderMode.value = localGuardMode == 1
                    Log.d("HomeViewModel", "📱 从本地读取长辈模式：${_isElderMode.value}, guardMode=$localGuardMode")
                    
                    // ⭐ 再从服务器同步最新状态
                    Log.d("HomeViewModel", "🌐 开始请求 getUserProfile API...")
                    val profile = try {
                        apiService.getUserProfile()
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "❌ getUserProfile API 调用异常", e)
                        // ⭐ 网络异常时使用本地缓存
                        Log.w("HomeViewModel", "⚠️ 网络异常，使用本地缓存的长辈模式：${_isElderMode.value}")
                        // ⭐ 继续执行后续逻辑，不中断流程
                        null
                    }
                    
                    if (profile != null) {
                        Log.d("HomeViewModel", "📥 getUserProfile 响应：isSuccess=${profile.isSuccess()}, code=${profile.code}, message=${profile.message}")
                        
                        if (profile.isSuccess() && profile.data != null) {
                            // ⭐ 修改：使用 guardMode 字段（0普通模式 1长辈精简模式）
                            val serverGuardMode = profile.data.guardMode
                            _isElderMode.value = serverGuardMode == 1
                            
                            // ⭐ 更新本地存储
                            tokenManager.saveGuardMode(serverGuardMode)
                            
                            // ⭐ 修复：移除图标切换逻辑，避免 Activity 重建导致 ViewModel 销毁
                            // 图标切换仅在登录成功后由 LoginViewModel 执行
                            Log.d("HomeViewModel", "📊 从服务器同步长辈模式：${_isElderMode.value}, guardMode=$serverGuardMode")
                            Log.d("HomeViewModel", "📊 用户信息：userId=${profile.data.id}, nickname=${profile.data.nickname}")
                        } else {
                            // ⭐ 关键修复：只有认证失败才触发退出登录，网络错误等使用本地缓存
                            val isAuthError = profile.code == 401 || 
                                             profile.message?.contains("Unauthorized", ignoreCase = true) == true ||
                                             profile.message?.contains("token invalid", ignoreCase = true) == true ||
                                             profile.message?.contains("token expired", ignoreCase = true) == true ||
                                             profile.message?.contains("token失效", ignoreCase = true) == true ||
                                             profile.message?.contains("认证失败", ignoreCase = true) == true ||
                                             profile.message?.contains("登录已过期", ignoreCase = true) == true
                            
                            if (isAuthError) {
                                Log.e("HomeViewModel", "❌ 认证失败，需要重新登录：code=${profile.code}, message=${profile.message}")
                                _isProfileLoaded.value = false  // ⭐ 明确标记为未加载
                                onAuthFailure?.invoke()
                                return@launch
                            } else {
                                // 网络错误、服务器错误等，使用本地缓存
                                // ⭐ 保持 _isProfileLoaded = true，不触发退出登录
                                Log.w("HomeViewModel", "⚠️ 获取用户信息失败（非认证错误），使用本地缓存：code=${profile.code}, message=${profile.message}")
                            }
                        }
                    } else {
                        // ⭐ API 调用异常，使用本地缓存
                        Log.w("HomeViewModel", "⚠️ API 调用失败，使用本地缓存的长辈模式：${_isElderMode.value}")
                    }
                    
                    // ⭐ 修复：无论长辈模式还是普通模式，都保持 WebSocket 连接
                    // 普通用户需要通过 WebSocket 接收长辈的代叫车确认/拒绝响应
                    // ⭐ 新增：防抖机制，避免重复连接
                    if (!webSocketClient.isConnected() && !isConnectingWebSocket) {
                        Log.d("HomeViewModel", "🔌 WebSocket 未连接，开始连接...")
                        isConnectingWebSocket = true
                        try {
                            connectWebSocketForElderMode()
                        } finally {
                            // ⭐ 延迟重置标记，给连接留出时间
                            kotlinx.coroutines.delay(2000)
                            isConnectingWebSocket = false
                        }
                    } else if (webSocketClient.isConnected()) {
                        Log.d("HomeViewModel", "✅ WebSocket 已连接，跳过重复连接")
                    } else {
                        Log.d("HomeViewModel", "⏳ WebSocket 正在连接中，跳过")
                    }
                    
                    // ⭐ 关键修复：根据模式执行不同的业务逻辑
                    if (_isElderMode.value) {
                        // 长辈模式：启动独立定位 + 加载亲友列表
                        Log.d("HomeViewModel", "👴 长辈模式：启动独立定位和加载亲友列表")
                        startIndependentLocation()
                        loadGuardianInfo()
                        
                        // ⭐ 修复：移除不必要的断开重连逻辑
                        // WebSocket 已经在上面连接成功(sessionId=user_19)，不需要再重连
                        if (!webSocketClient.isConnected() && !isConnectingWebSocket) {
                            Log.d("HomeViewModel", "🔌 WebSocket 未连接，开始连接...")
                            isConnectingWebSocket = true
                            try {
                                connectWebSocketForElderMode()
                            } finally {
                                kotlinx.coroutines.delay(2000)
                                isConnectingWebSocket = false
                            }
                        } else if (webSocketClient.isConnected()) {
                            val userId = tokenManager.getUserId()
                            Log.d("HomeViewModel", "✅ WebSocket 已连接，sessionId=user_$userId")
                        } else {
                            Log.d("HomeViewModel", "⏳ WebSocket 正在连接中，跳过")
                        }
                    } else {
                        // 普通模式：加载长辈列表（用于帮长辈叫车）
                        Log.d("HomeViewModel", "👤 普通模式：加载长辈列表")
                        loadElderList(forceRefresh = false)
                    }
                    
                    // ⭐ 关键修复：无论长辈还是普通用户，都启动后台定位追踪服务
                    // 确保应用在后台时也能持续获取位置，维持WebSocket连接
                    Log.d("HomeViewModel", "🚀 启动后台定位追踪服务（双端都需要）")
                    startBackgroundTracking()
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 检查长辈模式异常", e)
                    isConnectingWebSocket = false  // ⭐ 异常时重置标记
                    // ⭐ 关键修复：只有在认证相关错误时才触发退出登录
                    // 网络异常、超时等情况不应踢用户回登录页
                    val isAuthError = e.message?.contains("401") == true || 
                                     e.message?.contains("Unauthorized", ignoreCase = true) == true ||
                                     e.message?.contains("token invalid", ignoreCase = true) == true ||
                                     e.message?.contains("token expired", ignoreCase = true) == true
                    if (isAuthError) {
                        Log.w("HomeViewModel", "⚠️ Token已失效，需要重新登录")
                        _isProfileLoaded.value = false  // ⭐ 明确标记为未加载
                        onAuthFailure?.invoke()
                    } else {
                        // ⭐ 其他异常（网络错误等）不影响自动登录，继续使用本地缓存
                        // ⭐ 保持 _isProfileLoaded = true，不触发退出登录
                        Log.w("HomeViewModel", "⚠️ 网络异常，使用本地缓存的长辈模式：${_isElderMode.value}")
                    }
                } finally {
                    // ⭐ 修复：移除标志重置逻辑（已删除防重复调用）
                    Log.d("HomeViewModel", "✅ checkElderMode 执行完成")
                    Log.d("HomeViewModel", "📊 最终状态：isElderMode=${_isElderMode.value}, isProfileLoaded=${_isProfileLoaded.value}")
                    Log.d("HomeViewModel", "📊 WebSocket 连接状态：${webSocketClient.isConnected()}")
                }
            }
        }
        
        /**
         * ⭐ 新增：加载亲友列表（长辈模式）
         */
        fun loadGuardianInfo() {
            viewModelScope.launch {
                try {
                    val userId = tokenManager.getUserId()
                    if (userId == null) {
                        Log.w("HomeViewModel", "用户未登录，无法获取亲友列表")
                        return@launch
                    }
                    
                    Log.d("HomeViewModel", "开始获取亲友列表，userId=$userId")
                    
                    val response = apiService.getMyGuardians(userId)
                    Log.d("HomeViewModel", "API 响应：isSuccess=${response.isSuccess()}, data=${response.data}, message=${response.message}")
                    
                    if (response.isSuccess()) {
                        val dataList = response.data ?: emptyList()
                        _guardianInfoList.value = dataList
                        Log.d("HomeViewModel", "✅ 亲友列表更新成功：${dataList.size} 个")
                        dataList.forEachIndexed { index, guardian ->
                            Log.d("HomeViewModel", "  [$index] userId=${guardian.userId}, name=${guardian.name}, phone=${guardian.phone}, realName=${guardian.realName}")
                        }
                    } else {
                        Log.w("HomeViewModel", "❌ 获取亲友列表失败：${response.message}")
                        _guardianInfoList.value = emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ loadGuardianInfo exception", e)
                    _guardianInfoList.value = emptyList()
                }
            }
        }
        
        /**
         * ⭐ 新增：一键解绑所有亲友（长辈模式）
         */
        fun unbindAllGuardians(
            onSuccess: () -> Unit = {},
            onError: (String) -> Unit = {}
        ) {
            viewModelScope.launch {
                try {
                    val userId = tokenManager.getUserId()
                    if (userId == null) {
                        onError("用户未登录")
                        return@launch
                    }
                    
                    Log.d("HomeViewModel", "开始一键解绑所有亲友")
                    
                    val response = apiService.unbindAllGuardians(userId)
                    if (response.isSuccess()) {
                        Log.d("HomeViewModel", "解绑成功")
                        _guardianInfoList.value = emptyList()
                        onSuccess()
                        // 刷新用户信息
                        loadProfile()
                    } else {
                        Log.e("HomeViewModel", "解绑失败：${response.message}")
                        onError(response.message ?: "解绑失败")
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "unbindAllGuardians exception", e)
                    onError(e.message ?: "网络错误")
                }
            }
        }
    
        // ⭐ 新增：独立定位客户端（不依赖地图）
        private var locationClient: AMapLocationClient? = null
        
        /**
         * ⭐ 新增：启动独立定位（用于长辈模式）
         */
        fun startIndependentLocation() {
            // ⭐ 修复：防止重复启动定位
            if (locationClient != null) {
                Log.d("HomeViewModel", "⚠️ 定位客户端已存在，跳过重复启动")
                return
            }
            
            Log.d("HomeViewModel", "=== 启动独立定位 ===")
            
            try {
                // 创建新的定位客户端
                locationClient = AMapLocationClient(appContext)
                
                // 配置定位选项
                val option = AMapLocationClientOption().apply {
                    // ⭐ 修复：使用高精度模式（GPS+网络），优先GPS但允许降级
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    // 设置是否返回地址信息
                    isNeedAddress = true
                    // 设置是否允许模拟位置
                    isMockEnable = false
                    // ⭐ 修复：设置定位间隔为5秒,减少频繁定位
                    interval = 5000
                    // ⭐ 修改：禁用单次定位模式，使用连续定位
                    isOnceLocation = false
                    isOnceLocationLatest = false
                    // 设置定位超时时间
                    httpTimeOut = 20000
                    // ⭐ 新增：设置定位超时后不返回上次位置
                    isLocationCacheEnable = false
                    // ⭐ 新增：设置是否使用GPS
                    isGpsFirst = true
                }
                
                locationClient?.setLocationOption(option)
                
                // 设置定位监听器
                locationClient?.setLocationListener { location ->
                    if (location != null) {
                        when (location.errorCode) {
                            0 -> {
                                // 定位成功
                                Log.d("HomeViewModel", "📍 独立定位成功：lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}m, provider=${location.provider}")
                                updateCurrentLocation(location.latitude, location.longitude)
                                updateLocationAccuracy(location.accuracy)
                            }
                            else -> {
                                // 定位失败
                                Log.e("HomeViewModel", "❌ 独立定位失败：errorCode=${location.errorCode}, errorInfo=${location.errorInfo}")
                            }
                        }
                    } else {
                        Log.e("HomeViewModel", "❌ 独立定位返回 null")
                    }
                }
                
                // 启动定位
                locationClient?.startLocation()
                Log.d("HomeViewModel", "✅ 独立定位已启动（高精度模式，GPS优先）")
                
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ 启动独立定位异常", e)
            }
        }
        
        /**
         * ⭐ 新增：停止独立定位
         */
        fun stopIndependentLocation() {
            Log.d("HomeViewModel", "=== 停止独立定位 ===")
            locationClient?.stopLocation()
            locationClient?.onDestroy()
            locationClient = null
        }
        
        // ========== ⭐ 新增：后台定位追踪服务 ==========
        
        /**
         * ⭐ 新增：启动后台定位追踪服务
         * 用于确保应用在后台时也能持续获取位置，维持WebSocket连接
         */
        fun startBackgroundTracking() {
            Log.d("HomeViewModel", "🚀 启动后台定位追踪服务")
            try {
                val intent = Intent(appContext, com.example.myapplication.service.LocationTrackingService::class.java).apply {
                    action = com.example.myapplication.service.LocationTrackingService.ACTION_START_TRACKING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                Log.d("HomeViewModel", "✅ 后台定位追踪服务已启动")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ 启动后台定位追踪服务失败", e)
            }
        }
        
        /**
         * ⭐ 新增：停止后台定位追踪服务
         */
        fun stopBackgroundTracking() {
            Log.d("HomeViewModel", "🛑 停止后台定位追踪服务")
            try {
                val intent = Intent(appContext, com.example.myapplication.service.LocationTrackingService::class.java).apply {
                    action = com.example.myapplication.service.LocationTrackingService.ACTION_STOP_TRACKING
                }
                appContext.startService(intent)
                Log.d("HomeViewModel", "✅ 后台定位追踪服务已停止")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ 停止后台定位追踪服务失败", e)
            }
        }
        
        // ========== ⭐ 帮长辈叫车功能 ==========
        
        /**
         * ⭐ 新增：帮长辈叫车（需要长辈确认）
         * @param elderUserId 长辈用户ID
         * @param poiName 目的地名称
         * @param destLat 目的地纬度
         * @param destLng 目的地经度
         * @param startLat 起点纬度（可选，默认使用当前位置）
         * @param startLng 起点经度（可选，默认使用当前位置）
         */
        fun createProxyOrderForElder(
            elderUserId: Long,
            poiName: String,
            destLat: Double,
            destLng: Double,
            startLat: Double? = null,
            startLng: Double? = null
        ) {
            // ⭐ 修复：使用 viewModelScope + SupervisorJob，防止切换后台时协程被取消
            viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
                try {
                    val userId = tokenManager.getUserId()
                    if (userId == null) {
                        withContext(Dispatchers.Main) {
                            _orderState.value = OrderState.Error("用户未登录")
                        }
                        return@launch
                    }
                    
                    // ⭐ 新增：打印当前用户信息和目标长辈信息
                    Log.d("HomeViewModel", "🔍 当前用户ID: $userId")
                    Log.d("HomeViewModel", "🔍 目标长辈ID: $elderUserId")
                    Log.d("HomeViewModel", "🔍 是否为同一人: ${userId == elderUserId}")
                    
                    // ⭐ 修复：移除冗余的绑定检查，因为用户已经从长辈列表中选择，说明已绑定
                    // 原来的检查会导致竞态条件问题：用户选择长辈后，列表可能被清空或更新，导致检查失败
                    Log.d("HomeViewModel", "🚗 开始帮长辈叫车：elderUserId=$elderUserId, poiName=$poiName")
                    
                    withContext(Dispatchers.Main) {
                        _isCreatingOrder.value = true
                        _orderState.value = OrderState.Loading
                    }
                    
                    // ⭐ 关键修复：按照后端API文档构造请求
                    val request = com.example.myapplication.data.model.CreateOrderForElderRequest(
                        elderId = elderUserId,              // ⭐ 必填：长辈的用户ID
                        startLat = startLat ?: _startLocation.value?.latitude ?: currentLocation.value?.latitude,  // ⭐ 优先级：参数 > 收藏起点 > 当前位置
                        startLng = startLng ?: _startLocation.value?.longitude ?: currentLocation.value?.longitude, // ⭐ 优先级：参数 > 收藏起点 > 当前位置
                        destLat = destLat,                  // 终点纬度
                        destLng = destLng,                  // 终点经度
                        destAddress = poiName.trim(),       // ⭐ 必填：目的地名称（不能为null或空字符串）
                        needConfirm = true                  // ⭐ 默认需要长辈确认
                    )
                    
                    Log.d("HomeViewModel", "📤 发送代叫车请求：$request")
                    Log.d("HomeViewModel", "📤 请求详情：elderId=${request.elderId}, destAddress=${request.destAddress}, needConfirm=${request.needConfirm}")
                    Log.d("HomeViewModel", "📤 起点坐标：lat=${request.startLat}, lng=${request.startLng}")
                    Log.d("HomeViewModel", "📤 终点坐标：lat=${request.destLat}, lng=${request.destLng}")
                    
                    // ⭐ 关键修复：网络请求不受 UI 生命周期影响
                    val response = apiService.createOrderForElder(userId, request)
                    
                    Log.d("HomeViewModel", "📥 收到响应：isSuccess=${response.isSuccess()}, message=${response.message}, code=${response.code}, data=${response.data}")
                    
                    withContext(Dispatchers.Main) {
                        if (response.isSuccess()) {
                            // ⭐ 修复：处理后端返回的不同数据类型
                            val responseData = response.data
                            when (responseData) {
                                is Order -> {
                                    // 后端返回完整的订单对象
                                    Log.d("HomeViewModel", "✅ 代叫车请求已发送，订单ID: ${responseData.id}, 状态: ${responseData.status}")
                                    _orderState.value = OrderState.Success(responseData)
                                    _events.send(HomeEvent.OrderCreated(responseData))
                                    
                                    // ⭐ 修复：普通用户代叫车后也跳转到行程追踪，显示“等待长辈确认”状态
                                    launch {
                                        kotlinx.coroutines.delay(500)  // 延迟 500ms，让 UI 动画完成
                                        _events.send(HomeEvent.NavigateToOrderTracking(responseData.id))
                                        Log.d("HomeViewModel", "🚀 已发送导航事件（亲友代叫车-Order类型）: orderId=${responseData.id}")
                                    }
                                    
                                    // ⭐ 修复：直接发送全局事件，不使用嵌套协程
                                    val requesterName = _userNickname.value ?: "亲友"
                                    launch {
                                        MyApplication.sendProxyOrderRequest(
                                            orderId = responseData.id,
                                            requesterName = requesterName,
                                            destination = responseData.destAddress ?: responseData.poiName ?: "未知目的地"
                                        )
                                        Log.d("HomeViewModel", "📢 已发送代叫车请求事件：orderId=${responseData.id}, requester=$requesterName")
                                    }
                                }
                                is Map<*, *> -> {
                                    // ⭐ 修复：后端返回 Map 类型（LinkedTreeMap），需要转换
                                    Log.d("HomeViewModel", "⚠️ 后端返回 Map 类型，尝试解析...")
                                    try {
                                        // 从 Map 中提取订单ID
                                        val orderId = (responseData["id"] as? Number)?.toLong()
                                        val orderNo = responseData["orderNo"] as? String
                                        val elderUserId = (responseData["elderUserId"] as? Number)?.toLong()
                                        val destLat = (responseData["destLat"] as? Number)?.toDouble()
                                        val destLng = (responseData["destLng"] as? Number)?.toDouble()
                                        val destAddress = responseData["destAddress"] as? String
                                        val status = (responseData["status"] as? Number)?.toInt()
                                        
                                        Log.d("HomeViewModel", "✅ 代叫车请求已发送")
                                        Log.d("HomeViewModel", "  - 订单ID: $orderId")
                                        Log.d("HomeViewModel", "  - 订单号: $orderNo")
                                        Log.d("HomeViewModel", "  - 长辈ID: $elderUserId")
                                        Log.d("HomeViewModel", "  - 目的地: $destAddress")
                                        Log.d("HomeViewModel", "  - 状态: $status")
                                        
                                        // ⭐ 创建一个临时 Order 对象用于UI显示
                                        val tempOrder = Order(
                                            id = orderId ?: 0L,
                                            orderNo = orderNo ?: "",
                                            userId = elderUserId ?: 0L,
                                            driverId = null,  // ⭐ 修复：添加必需的driverId参数
                                            guardianUserId = userId,  // ⭐ 修复：使用guardianUserId代替proxyUserId
                                            destLat = destLat,
                                            destLng = destLng,
                                            poiName = null,
                                            destAddress = destAddress,
                                            platformUsed = null,
                                            platformOrderId = null,
                                            estimatePrice = null,
                                            status = status ?: 0,
                                            createTime = java.time.LocalDateTime.now().toString()
                                        )
                                        
                                        _orderState.value = OrderState.Success(tempOrder)
                                        _events.send(HomeEvent.OrderCreated(tempOrder))
                                        
                                        // ⭐ 修复：普通用户代叫车后也跳转到行程追踪，显示“等待长辈确认”状态
                                        launch {
                                            kotlinx.coroutines.delay(500)  // 延迟 500ms，让 UI 动画完成
                                            _events.send(HomeEvent.NavigateToOrderTracking(tempOrder.id))
                                            Log.d("HomeViewModel", "🚀 已发送导航事件（亲友代叫车-Map类型）: orderId=${tempOrder.id}")
                                        }
                                        
                                        // ⭐ 修复：直接发送全局事件，不使用嵌套协程
                                        val requesterName = _userNickname.value ?: "亲友"
                                        launch {
                                            MyApplication.sendProxyOrderRequest(
                                                orderId = tempOrder.id,
                                                requesterName = requesterName,
                                                destination = tempOrder.destAddress ?: "未知目的地"
                                            )
                                            Log.d("HomeViewModel", "📢 已发送代叫车请求事件（Map类型）：orderId=${tempOrder.id}, requester=$requesterName")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("HomeViewModel", "❌ 解析 Map 数据失败", e)
                                        _orderState.value = OrderState.Error("订单创建成功，但解析响应失败")
                                    }
                                }
                                is String -> {
                                    // 后端返回字符串（可能是订单ID或消息）
                                    Log.d("HomeViewModel", "✅ 代叫车请求已发送，返回数据: $responseData")
                                    _orderState.value = OrderState.Error("${response.message ?: "订单创建成功"}：$responseData")
                                }
                                else -> {
                                    // 其他情况
                                    Log.w("HomeViewModel", "⚠️ 未知返回类型: ${responseData?.javaClass?.name}")
                                    _orderState.value = OrderState.Error(response.message ?: "订单创建成功，但未返回订单详情")
                                }
                            }
                        } else {
                            val errorMsg = response.message ?: "代叫车失败"
                            Log.e("HomeViewModel", "❌ 代叫车失败：errorMsg=$errorMsg, code=${response.code}")
                            
                            // ⭐ 关键修复：后端返回的 403 错误需要明确提示
                            val userFriendlyMsg = when {
                                response.code == 403 -> "您未绑定该长辈，无法代叫车。请先在个人中心完成绑定。"
                                response.code == 401 -> "登录已过期，请重新登录"
                                response.code == 404 -> "长辈不存在或已解绑"
                                errorMsg.contains("未绑定") || errorMsg.contains("绑定") -> "请先在亲情守护中绑定该长辈"
                                else -> errorMsg
                            }
                            
                            _orderState.value = OrderState.Error(userFriendlyMsg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 代叫车异常", e)
                    withContext(Dispatchers.Main) {
                        _orderState.value = OrderState.Error(e.message ?: "网络错误")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        _isCreatingOrder.value = false
                    }
                }
            }
        }
        
        /**
         * ⭐ 新增：长辈确认代叫车
         * @param orderId 订单ID
         * @param confirmed true-同意，false-拒绝
         * @param rejectReason 拒绝原因（可选）
         */
        fun confirmProxyOrder(
            orderId: Long,
            confirmed: Boolean,
            rejectReason: String? = null
        ) {
            viewModelScope.launch {
                try {
                    val userId = tokenManager.getUserId()
                    if (userId == null) {
                        Log.e("HomeViewModel", "❌ 用户未登录")
                        return@launch
                    }
                    
                    Log.d("HomeViewModel", "📱 长辈确认代叫车：orderId=$orderId, confirmed=$confirmed")
                    Log.d("HomeViewModel", "🔍 [调试] 传入的 orderId 参数值: $orderId")
                    
                    if (orderId == 0L) {
                        Log.e("HomeViewModel", "❌❌❌ 严重错误：orderId 为 0！这将导致后端无法处理")
                        Log.e("HomeViewModel", "❌❌❌ 请检查调用方传递的参数是否正确")
                    }
                    
                    val request = com.example.myapplication.data.model.ConfirmProxyOrderRequest(
                        confirmed = confirmed,
                        rejectReason = rejectReason
                    )
                    
                    Log.d("HomeViewModel", "📤 准备调用 API：userId=$userId, orderId=$orderId")
                    val response = apiService.confirmProxyOrder(userId, orderId, request)
                    Log.d("HomeViewModel", "📥 API 调用完成，response.code=${response.code}")
                    
                    if (response.isSuccess()) {
                        Log.d("HomeViewModel", "✅ 确认成功：${response.message}")
                        
                        // ⭐ 修复：长辈确认后，延迟500ms再跳转，确保UI动画完成
                        if (confirmed) {
                            Log.d("HomeViewModel", "🚀 长辈已同意，准备跳转到行程追踪页: orderId=$orderId")
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(500)  // ⭐ 增加延迟到500ms，让UI动画完成
                                _events.send(HomeEvent.NavigateToOrderTracking(orderId))
                                Log.d("HomeViewModel", "🚀 已发送导航事件（长辈确认）: orderId=$orderId")
                            }
                        } else {
                            Log.d("HomeViewModel", "❌ 长辈已拒绝，原因：$rejectReason")
                        }
                    } else {
                        Log.e("HomeViewModel", "❌ 确认失败：${response.message}")
                        // ⭐ 新增：确认失败时显示错误提示
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                appContext,
                                "确认失败：${response.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 确认异常", e)
                }
            }
        }
        
        /**
         * ⭐ 新增：加载我的长辈列表（用于帮长辈叫车）
         * 注意：只有普通账户才能调用此接口，长辈账户会返回403
         */
        fun loadElderList(forceRefresh: Boolean = false) {
            // ⭐ 修复：长辈模式不需要加载长辈列表
            if (_isElderMode.value) {
                Log.d("HomeViewModel", "⚠️ 当前是长辈模式，跳过加载长辈列表")
                return
            }
            
            // ⭐ 低优先级7：缓存逻辑
            if (!forceRefresh && _elderListLoaded && _elderInfoList.value.isNotEmpty()) {
                Log.d("HomeViewModel", "👴 使用缓存的长辈列表，共 ${_elderInfoList.value.size} 个")
                // ⭐ 新增：打印已绑定的长辈ID列表
                val elderIds = _elderInfoList.value.map { it.userId }
                Log.d("HomeViewModel", "👴 已绑定的长辈ID列表: $elderIds")
                return
            }
            
            viewModelScope.launch {
                try {
                    val userId = tokenManager.getUserId()
                    if (userId == null) {
                        Log.w("HomeViewModel", "用户未登录，无法获取长辈列表")
                        return@launch
                    }
                    
                    // ⭐ 关键修复：先检查用户是否为长辈模式（从服务器实时查询）
                    Log.d("HomeViewModel", "🔍 开始检查用户模式，userId=$userId")
                    val profileResponse = apiService.getUserProfile()
                    Log.d("HomeViewModel", "🔍 getUserProfile 结果：isSuccess=${profileResponse.isSuccess()}, data=${profileResponse.data}")
                    
                    if (profileResponse.isSuccess()) {
                        val profile = profileResponse.data
                        Log.d("HomeViewModel", "🔍 用户信息：guardMode=${profile?.guardMode}")
                        
                        if (profile != null && profile.guardMode == 1) {
                            Log.w("HomeViewModel", "⏭️ 检测到长辈账号(userId=$userId, guardMode=1)，禁止调用 /api/guard/myElders")
                            return@launch
                        }
                    } else {
                        Log.e("HomeViewModel", "❌ 获取用户信息失败：${profileResponse.message}")
                    }
                    
                    Log.d("HomeViewModel", "👴 开始加载长辈列表，userId=$userId")
                    
                    val response = apiService.getMyElders(userId)
                    
                    if (response.isSuccess()) {
                        val elderList = response.data ?: emptyList()
                        _elderInfoList.value = elderList
                        
                        // ⭐ 修复：只有成功加载且有数据时才标记为已加载
                        if (elderList.isNotEmpty()) {
                            _elderListLoaded = true
                            Log.d("HomeViewModel", "✅ 加载成功，共 ${elderList.size} 个长辈")
                        } else {
                            Log.w("HomeViewModel", "⚠️ 加载成功但列表为空，可能是真的没有绑定长辈")
                        }
                    } else {
                        Log.e("HomeViewModel", "❌ 加载失败：${response.message}")
                        _elderInfoList.value = emptyList()
                        _elderListLoaded = false  // ⭐ 修复：失败时重置标志，允许重试
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ loadElderList exception", e)
                    _elderInfoList.value = emptyList()
                    _elderListLoaded = false  // ⭐ 修复：异常时重置标志，允许重试
                }
            }
        }
        
        /**
         * ⭐ 高优先级2：接收代叫车请求（由 WebSocket 调用）
         * @param orderId 订单ID
         * @param requesterName 代叫人姓名
         * @param destination 目的地
         */
        fun onProxyOrderRequestReceived(
            orderId: Long,
            requesterName: String,
            destination: String
        ) {
            Log.d("HomeViewModel", "📩 收到代叫车请求：orderId=$orderId, from=$requesterName, to=$destination")
            
            _proxyOrderRequest.value = ProxyOrderRequest(
                orderId = orderId,
                requesterName = requesterName,
                destination = destination
            )
            
            Log.d("HomeViewModel", "✅ _proxyOrderRequest 已更新")
        }
        
        /**
         * ⭐ 清除代叫车请求通知
         */
        fun clearProxyOrderRequest() {
            _proxyOrderRequest.value = null
        }
        
        /**
         * ⭐ 新增：解析 GUARD_PUSH 消息（代叫车推送）
         */
        private fun parseGuardPushMessage(json: String) {
            viewModelScope.launch {
                try {
                    val jsonFormat = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                    
                    // 先解析外层结构
                    val jsonObject = org.json.JSONObject(json)
                    val type = jsonObject.optString("type", "")
                    
                    // ⭐ 修复：同时处理 GUARD_PUSH、ORDER_CREATED 和直接的订单状态消息
                    if (type.uppercase() != "GUARD_PUSH" && 
                        type.uppercase() != "ORDER_CREATED" &&
                        type.uppercase() != "ORDER_ACCEPTED" &&
                        type.uppercase() != "DRIVER_LOCATION") {
                        // 不是这些类型，忽略（由 ChatViewModel 处理）
                        return@launch
                    }
                    
                    Log.d("HomeViewModel", "📩 [HomeViewModel] 收到 $type 类型消息")
                    
                    // ⭐ 修复：根据消息类型选择不同的解析方式
                    when (type.uppercase()) {
                        "GUARD_PUSH" -> {
                            // GUARD_PUSH 类型：data 字段是 JSON 字符串
                            val dataStr = jsonObject.optString("data", "")
                            if (dataStr.isBlank()) {
                                Log.w("HomeViewModel", "⚠️ [HomeViewModel] data 字段为空")
                                return@launch
                            }
                            
                            val pushMessage = jsonFormat.decodeFromString<com.example.myapplication.data.model.GuardPushMessage>(dataStr)
                            
                            Log.d("HomeViewModel", "📩 收到亲情守护推送：type=${pushMessage.type}")
                            Log.d("HomeViewModel", "📊 推送详情：orderId=${pushMessage.orderId}, requester=${pushMessage.proxyUserName}, dest=${pushMessage.destAddress}")
                            
                            when (pushMessage.type) {
                                "NEW_ORDER" -> {
                                    // ⭐ 代叫车请求通知
                                    Log.d("HomeViewModel", "🚗 收到代叫车请求：orderId=${pushMessage.orderId}, requester=${pushMessage.proxyUserName}, dest=${pushMessage.destAddress}")
                                    
                                    // ⭐ 关键修复：设置 _proxyOrderRequest 触发 UI 弹窗
                                    _proxyOrderRequest.value = ProxyOrderRequest(
                                        orderId = pushMessage.orderId ?: 0L,
                                        requesterName = pushMessage.proxyUserName ?: "亲友",
                                        destination = pushMessage.destAddress ?: "未知目的地"
                                    )
                                    
                                    // ⭐ 无论前台后台，都发送高优先级通知
                                    sendIncomingCallNotification(
                                        orderId = pushMessage.orderId ?: 0L,
                                        requesterName = pushMessage.proxyUserName ?: "亲友",
                                        destination = pushMessage.destAddress ?: "未知目的地"
                                    )
                                }
                                
                                "ORDER_ACCEPTED" -> {
                                    // ⭐ 修复：司机已接单，由 OrderTrackingViewModel 处理，不再这里跳转
                                    val orderId = pushMessage.orderId ?: 0L
                                    Log.d("HomeViewModel", "✅ 收到 ORDER_ACCEPTED（GUARD_PUSH包装），orderId=$orderId")
                                    // 不触发导航，由 OrderTrackingScreen 处理 DRIVER_REQUEST 确认后自动显示
                                }
                                
                                "CHAT_MESSAGE" -> {
                                    // ⭐ 聊天消息通知（订单内聊天）
                                    Log.d("HomeViewModel", "💬 收到订单聊天消息：senderId=${pushMessage.senderId}, content=${pushMessage.content}")
                                }
                                
                                else -> {
                                    Log.w("HomeViewModel", "⚠️ 未知的推送类型：${pushMessage.type}")
                                }
                            }
                        }
                        
                        "ORDER_CREATED" -> {
                            // ORDER_CREATED 类型：data 字段是 JSON 对象
                            Log.d("HomeViewModel", "🚗 收到代叫车创建消息")
                            
                            // ⭐ 关键修复：只有长辈端才需要显示确认弹窗
                            Log.d("HomeViewModel", "🔍 当前 isElderMode=${_isElderMode.value}")
                            if (!_isElderMode.value) {
                                Log.w("HomeViewModel", "⏭️ 当前是普通用户，忽略 ORDER_CREATED 消息（由后端推送给代叫人作为确认）")
                                return@launch
                            }
                            
                            val dataObj = jsonObject.optJSONObject("data")
                            if (dataObj == null) {
                                Log.w("HomeViewModel", "⚠️ [HomeViewModel] data 对象为空")
                                return@launch
                            }
                            
                            val orderId = dataObj.optLong("orderId", 0L)
                            val requesterName = dataObj.optString("requesterName", "亲友")
                            val destAddress = dataObj.optString("destAddress", dataObj.optString("destination", "未知目的地"))
                            
                            Log.d("HomeViewModel", "🚗 收到代叫车请求：orderId=$orderId, requester=$requesterName, dest=$destAddress")
                            
                            // ⭐ 关键修复：设置 _proxyOrderRequest 触发 UI 弹窗
                            _proxyOrderRequest.value = ProxyOrderRequest(
                                orderId = orderId,
                                requesterName = requesterName,
                                destination = destAddress
                            )
                            
                            // ⭐ 发送高优先级通知
                            sendIncomingCallNotification(
                                orderId = orderId,
                                requesterName = requesterName,
                                destination = destAddress
                            )
                        }
                        
                        "ORDER_ACCEPTED" -> {
                            // ⭐ 修复：直接接收的司机接单消息，由 OrderTrackingViewModel 处理
                            val orderId = jsonObject.optLong("orderId", 0L)
                            Log.d("HomeViewModel", "✅ 收到 ORDER_ACCEPTED（直接消息），orderId=$orderId")
                            // 不触发导航，由 OrderTrackingScreen 处理 DRIVER_REQUEST 确认后自动显示
                        }
                        
                        "DRIVER_LOCATION" -> {
                            // 司机位置更新，忽略（由 OrderTrackingViewModel 处理）
                            Log.d("HomeViewModel", "📍 收到司机位置更新")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ 解析亲情守护推送消息失败", e)
                    Log.e("HomeViewModel", "❌ 原始JSON: $json")
                }
            }
        }
        
        /**
         * ⭐ 发送高优先级代叫车通知（类似来电提醒）
         * 即使应用在后台，也能弹出横幅通知
         */
        private fun sendIncomingCallNotification(orderId: Long, requesterName: String, destination: String) {
            val channelId = "proxy_order_channel"
            val channelName = "代叫车请求"
            val notificationId = orderId.hashCode()
            
            // ⭐ 检查通知权限（Android 13+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (!hasPermission) {
                    Log.w("HomeViewModel", "⚠️ 缺少 POST_NOTIFICATIONS 权限，无法发送通知")
                    return
                }
            }
            
            // 创建高优先级通知渠道
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "长辈端代叫车确认请求"
                    setBypassDnd(true)  // 绕过免打扰模式
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                val manager = appContext.getSystemService(android.app.NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
            
            // 创建点击通知后的 Intent
            val intent = android.content.Intent(appContext, com.example.myapplication.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("PROXY_ORDER_ID", orderId)
                putExtra("PROXY_ORDER_REQUESTER", requesterName)
                putExtra("PROXY_ORDER_DESTINATION", destination)
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                appContext,
                orderId.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // 构建通知
            val notification = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)  // ⭐ 使用 Android 系统图标
                .setContentTitle("🚗 新的代叫车请求")
                .setContentText("$requesterName 为你叫了车：$destination")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setFullScreenIntent(pendingIntent, true)  // ⭐ 关键：允许全屏显示（类似来电）
                .build()
            
            try {
                val manager = appContext.getSystemService(android.app.NotificationManager::class.java)
                manager.notify(notificationId, notification)
                Log.d("HomeViewModel", "🔔 已发送高优先级代叫车通知：orderId=$orderId")
            } catch (e: SecurityException) {
                Log.e("HomeViewModel", "❌ 发送通知失败：缺少权限", e)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "❌ 发送通知异常", e)
            }
        }
        
        /**
         * ⭐ 关键修复：长辈模式连接 WebSocket 接收代叫车通知
         */
        private fun connectWebSocketForElderMode() {
            Log.d("HomeViewModel", "🔌 [connectWebSocketForElderMode] 方法被调用")
            viewModelScope.launch {
                Log.d("HomeViewModel", "✅ [connectWebSocketForElderMode] viewModelScope.launch 已启动")
                try {
                    val userId = tokenManager.getUserId()
                    Log.d("HomeViewModel", "🔍 [connectWebSocketForElderMode] userId=$userId")
                    if (userId == null) {
                        Log.w("HomeViewModel", "⚠️ userId 为空，跳过 WebSocket 连接")
                        return@launch
                    }
                    
                    // 检查是否已经连接
                    if (webSocketClient.isConnected()) {
                        Log.d("HomeViewModel", "✅ WebSocket 已连接，跳过重复连接")
                        return@launch
                    }
                    
                    val token = tokenManager.getToken()
                    if (token.isNullOrBlank()) {
                        Log.w("HomeViewModel", "⚠️ Token 为空，跳过 WebSocket 连接")
                        return@launch
                    }
                    
                    // ⭐ 连接 WebSocket，使用 user_{userId} 作为 sessionId
                    val wsSessionId = "user_$userId"
                    Log.d("HomeViewModel", "🔌 开始连接 WebSocket，sessionId=$wsSessionId")
                    webSocketClient.connect(wsSessionId, token)
                    
                    // ⭐ 关键修复：HomeViewModel 也需要监听 WebSocket 消息，处理 GUARD_PUSH 类型
                    // 这样即使没有进入聊天页面，也能收到代叫车推送
                    
                    // ⭐ 修复：使用伴生对象的静态标志避免跨实例重复监听
                    synchronized(HomeViewModel::class.java) {
                        if (!wsMessageListenerStarted) {
                            wsMessageListenerStarted = true
                            Log.d("HomeViewModel", "🚀 启动 WebSocket 消息监听协程...")
                            
                            viewModelScope.launch {
                                try {
                                    Log.d("HomeViewModel", "🔄 HomeViewModel 开始监听 WebSocket 消息流...")
                                    webSocketClient.messages.collect { serverMessage ->
                                        parseGuardPushMessage(serverMessage)
                                    }
                                } catch (e: Exception) {
                                    Log.e("HomeViewModel", "❌ HomeViewModel 消息监听异常", e)
                                }
                            }
                            Log.d("HomeViewModel", "✅ WebSocket 消息监听协程已启动")
                        } else {
                            Log.w("HomeViewModel", "⏭️ WebSocket 消息监听已存在，跳过重复创建")
                        }
                    }
                    
                    Log.d("HomeViewModel", "✅ WebSocket 连接请求已发送（HomeViewModel 和 ChatViewModel 同时监听）")
                    
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "❌ WebSocket 连接异常", e)
                }
            }
        }
        
        /**
         * ⭐ 修复：移除 parseWebSocketMessage，消息监听由 ChatViewModel 统一处理
         * 避免重复监听导致欢迎消息刷屏
         */
        
        override fun onCleared() {
            super.onCleared()
            Log.d("HomeViewModel", "=== HomeViewModel onCleared 被调用 ===")
            
            // ⭐ 修复：不要断开 WebSocket，因为 ChatViewModel 可能还在使用
            // WebSocket 是单例共享资源，由 ChatViewModel 管理生命周期
            // if (webSocketClient.isConnected()) {
            //     Log.d("HomeViewModel", "🔌 断开 WebSocket 连接")
            //     webSocketClient.disconnect()
            // }
            Log.d("HomeViewModel", "✅ 保留 WebSocket 连接（由 ChatViewModel 管理）")
            
            // 清理定位客户端
            stopIndependentLocation()
            
            // 清理语音识别
            speechHelper?.destroy()
            speechHelper = null
            
            // 取消所有协程
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