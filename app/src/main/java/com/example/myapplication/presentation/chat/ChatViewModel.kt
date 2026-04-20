@file:Suppress("DEPRECATION", "NewApi")

package com.example.myapplication.presentation.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.core.utils.BaiduSpeechRecognizerHelper
import com.example.myapplication.core.websocket.ChatWebSocketClient
import com.example.myapplication.data.model.AgentSearchResponse  // ⭐ 新增
import com.example.myapplication.data.repository.AgentRepository  // ⭐ 新增
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.PoiData
import com.example.myapplication.data.model.PoiResponse  // ⭐ 新增：导入 PoiResponse
import com.example.myapplication.data.model.WebSocketRequest
import com.example.myapplication.data.model.WebSocketResponse
import com.example.myapplication.data.model.GuardPushMessage  // ⭐ 新增：亲情守护推送消息
import com.example.myapplication.domain.repository.IOrderRepository
import com.example.myapplication.core.datastore.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject  // ⭐ 新增：导入 JsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val webSocketClient: ChatWebSocketClient,
    private val orderRepository: IOrderRepository,
    private val agentRepository: AgentRepository,  // ⭐ 新增：注入 AgentRepository
    private val tokenManager: TokenManager  // ⭐ 新增：用于获取 userId
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sessionId = MutableStateFlow(UUID.randomUUID().toString())
    val sessionId: StateFlow<String> = _sessionId

    private var speechHelper: BaiduSpeechRecognizerHelper? = null
    
    // ⭐ 新增：TTS语音播报
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    private val _poiList = MutableStateFlow<List<PoiData>>(emptyList())
    val poiList: StateFlow<List<PoiData>> = _poiList

    // ⭐ 新增：候选列表 StateFlow
    private val _candidates = MutableStateFlow<List<PoiData>>(emptyList())
    val candidates: StateFlow<List<PoiData>> = _candidates.asStateFlow()

    // ⭐ 新增：显示候选列表对话框的状态
    private val _showCandidatesDialog = MutableStateFlow(false)
    val showCandidatesDialog: StateFlow<Boolean> = _showCandidatesDialog.asStateFlow()

    private var currentLat: Double? = null
    private var currentLng: Double? = null

    // ⭐ 新增：记录上次同步的位置，用于距离判定
    private var lastSyncedLat: Double? = null
    private var lastSyncedLng: Double? = null

    // ⭐ 新增：记录是否已经初始化过位置（仅首次进入时定位）
    private var hasInitializedLocation = false

    // ⭐ 新增：防抖 Job，避免频繁搜索
    private var searchDebounceJob: Job? = null
    private val SEARCH_DEBOUNCE_DELAY = 1000L  // 1 秒防抖

    // ⭐ 新增：记录已处理的消息，避免重复显示
    private val processedMessages = mutableSetOf<String>()

    // ⭐ 新增：语音输入状态
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    // ⭐ 新增：实时语音文本（用于显示正在识别的内容）
    private val _voiceInputText = MutableStateFlow("")
    val voiceInputText: StateFlow<String> = _voiceInputText.asStateFlow()
    
    // ⭐ 新增：方言选择（默认普通话）
    private var _currentAccent = MutableStateFlow("mandarin")  // mandarin: 普通话
    val currentAccent = _currentAccent.asStateFlow()
    
    // ⭐ 新增：支持的方言列表
    data class DialectOption(val name: String, val language: String, val accent: String)
    
    val supportedDialects = listOf(
        DialectOption("普通话", "zh_cn", "mandarin"),
        DialectOption("粤语", "zh_cn", "cantonese"),
        DialectOption("四川话", "zh_cn", "sichuan")
    )
    
    // ⭐ 新增：切换方言
    fun setDialect(accent: String) {
        _currentAccent.value = accent
        Log.d("ChatViewModel", "🌍 切换方言: accent=$accent")
    }
    
    // ⭐ 新增：待发送图片列表（最多3张）
    private val _pendingImages = mutableStateListOf<String>()  // Base64 字符串
    val pendingImages: List<String> = _pendingImages
    
    // ⭐ 新增：是否显示图片数量限制对话框
    private val _showImageLimitDialog = mutableStateOf(false)
    val showImageLimitDialog: Boolean = _showImageLimitDialog.value
    
    // ⭐ 新增：是否为长辈模式（用于禁用下单功能）
    private val _isElderMode = MutableStateFlow(false)
    val isElderMode: StateFlow<Boolean> = _isElderMode.asStateFlow()
    
    /**
     * ⭐ 新增：根据长辈模式生成友好的提示文本
     */
    private fun getFriendlyMessage(normalMsg: String, elderMsg: String): String {
        return if (_isElderMode.value) elderMsg else normalMsg
    }
    
    // ⭐ 新增：分享的地点信息（用于私聊界面显示）
    data class SharedLocationInfo(
        val elderId: Long,
        val elderName: String,
        val favoriteName: String,
        val favoriteAddress: String,
        val latitude: Double,              // 收藏地点纬度（目的地）
        val longitude: Double,             // 收藏地点经度（目的地）
        val timestamp: Long = System.currentTimeMillis(),
        
        // ⭐ 新增：长辈实时位置（作为代叫车起点）
        val elderCurrentLat: Double? = null,   // 长辈当前纬度
        val elderCurrentLng: Double? = null,   // 长辈当前经度
        val elderLocationTimestamp: Long? = null,  // 位置更新时间戳
        
        // ⭐ 新增：订单状态（用于卡片显示）
        val orderId: Long? = null,  // 关联的订单ID
        val orderStatus: Int? = null  // 订单状态：0-待确认 1-已同意 2-行程中 3-已结束
    )
    private val _sharedLocation = MutableStateFlow<SharedLocationInfo?>(null)
    val sharedLocation: StateFlow<SharedLocationInfo?> = _sharedLocation.asStateFlow()

    init {
        Log.d("ChatViewModel", "=== ChatViewModel 初始化开始 ===")

        // ⭐ 优化：异步初始化，不阻塞 UI
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    MyApplication.tokenManager.getToken()
                }
                
                // ⭐ 获取 userId，用于生成 sessionId
                val userId = withContext(Dispatchers.IO) {
                    tokenManager.getUserId()
                }

                if (!token.isNullOrBlank() && userId != null) {
                    // ⭐ sessionId 格式：user_{userId}
                    val wsSessionId = "user_$userId"
                    // ⭐ 同步更新 sessionId StateFlow
                    _sessionId.value = wsSessionId
                    
                    try {
                        // ⭐ 优化：非阻塞连接，立即返回
                        webSocketClient.connect(wsSessionId, token)
                        Log.d("ChatViewModel", "WebSocket 连接请求已发送，sessionId=$wsSessionId")
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "WebSocket 连接异常", e)
                        addSystemMessage("⚠️ 连接异常：${e.message}")
                    }
                } else {
                    Log.w("ChatViewModel", "⚠️ Token 为空或 userId 为 null，跳过 WebSocket 连接")
                    // ⭐ 不显示错误消息，因为可能还未登录
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ ChatViewModel 初始化异常", e)
                // ⭐ 不抛出异常，避免应用崩溃
            }
        }

        // ⭐ 优化：立即启动消息监听，不等待连接完成
        viewModelScope.launch {
            try {
                webSocketClient.messages.collect { serverMessage ->
                    parseServerMessage(serverMessage)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ 消息监听异常", e)
                addSystemMessage("⚠️ 消息接收异常：${e.message}")
            }
        }
        
        // ⭐ 新增：监听订单创建事件，更新 sharedLocation 中的 orderId
        viewModelScope.launch {
            try {
                com.example.myapplication.MyApplication.orderCreatedEvent.collect { event ->
                    Log.d("ChatViewModel", "📩 收到订单创建事件：orderId=${event.orderId}, elderId=${event.elderId}")
                    
                    val currentSharedLocation = _sharedLocation.value
                    if (currentSharedLocation != null && currentSharedLocation.elderId == event.elderId) {
                        val updatedLocation = currentSharedLocation.copy(
                            orderId = event.orderId,
                            orderStatus = 0  // 0-待确认
                        )
                        _sharedLocation.value = updatedLocation
                        Log.d("ChatViewModel", "✅ 已更新 sharedLocation 的 orderId: ${event.orderId}")
                    } else {
                        Log.w("ChatViewModel", "⚠️ 未找到匹配的 sharedLocation，elderId=${event.elderId}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ 订单创建事件监听异常", e)
            }
        }

        // ⭐ 获取初始位置
        updateLocationFromGPS()

        // ⭐ 优化：提前启动定时检查（10 秒后），更快发现问题
        viewModelScope.launch {
            delay(10000)  // 10 秒后开始检查
            checkWebSocketConnection()
        }

        Log.d("ChatViewModel", "=== ChatViewModel 初始化完成 ===")
        
        // ⭐ 新增：从 SharedPreferences 恢复 sharedLocation（长辈端必备）
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId != null && _isElderMode.value) {
                    Log.d("ChatViewModel", "🔄 [初始化] 尝试从本地缓存恢复 sharedLocation...")
                    val prefs = MyApplication.instance.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                    val cachedElderId = prefs.getLong("elderId_${userId}", -1L)
                    
                    if (cachedElderId == userId) {
                        val elderName = prefs.getString("elderName_${userId}", "") ?: ""
                        val favoriteName = prefs.getString("favoriteName_${userId}", "") ?: ""
                        val favoriteAddress = prefs.getString("favoriteAddress_${userId}", "") ?: ""
                        val latitude = prefs.getFloat("latitude_${userId}", 0f).toDouble()
                        val longitude = prefs.getFloat("longitude_${userId}", 0f).toDouble()
                        val elderCurrentLat = prefs.getFloat("elderCurrentLat_${userId}", 0f).toDouble().takeIf { it != 0.0 }
                        val elderCurrentLng = prefs.getFloat("elderCurrentLng_${userId}", 0f).toDouble().takeIf { it != 0.0 }
                        val elderLocationTimestamp = prefs.getLong("elderLocationTimestamp_${userId}", 0L).takeIf { it != 0L }
                        val orderId = prefs.getLong("orderId_${userId}", -1L).takeIf { it != -1L }
                        
                        if (favoriteName.isNotBlank()) {
                            val restoredLocation = SharedLocationInfo(
                                elderId = cachedElderId,
                                elderName = elderName.ifBlank { "亲友" },
                                favoriteName = favoriteName,
                                favoriteAddress = favoriteAddress,
                                latitude = latitude,
                                longitude = longitude,
                                elderCurrentLat = elderCurrentLat,
                                elderCurrentLng = elderCurrentLng,
                                elderLocationTimestamp = elderLocationTimestamp,
                                orderId = orderId,
                                orderStatus = 0  // 默认待确认
                            )
                            _sharedLocation.value = restoredLocation
                            Log.d("ChatViewModel", "✅ [初始化] 成功恢复 sharedLocation: favoriteName=$favoriteName, orderId=$orderId")
                        } else {
                            Log.d("ChatViewModel", "⚠️ [初始化] 本地缓存为空")
                        }
                    } else {
                        Log.d("ChatViewModel", "⚠️ [初始化] 没有该用户的分享记录 (userId=$userId)")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ [初始化] 恢复 sharedLocation 失败", e)
            }
        }
    }

    // ⭐ 定时检查 WebSocket 连接状态
    private fun checkWebSocketConnection() {
        viewModelScope.launch {
            while (true) {
                delay(30000)  // 每 30 秒检查一次
                if (!webSocketClient.isConnected()) {
                    Log.w("ChatViewModel", "WebSocket 已断开，尝试重连...")
                    reconnectWebSocket()
                } else {
                    // ⭐ 优化：减少日志输出，仅发送心跳
                    sendPing()
                }
            }
        }
    }

    // ⭐ 新增：发送心跳消息
    private fun sendPing() {
        viewModelScope.launch {
            try {
                val pingMessage = JSONObject().apply {
                    put("type", "ping")
                    put("sessionId", sessionId.value)
                    put("timestamp", System.currentTimeMillis())
                }
                Log.d("ChatViewModel", "发送心跳：${pingMessage.toString()}")
                webSocketClient.sendRaw(pingMessage.toString())
            } catch (e: Exception) {
                Log.e("ChatViewModel", "发送心跳失败", e)
            }
        }
    }

    // ⭐ 手动重连方法
    fun reconnectWebSocket() {
        viewModelScope.launch {
            // ⭐ 优化：缩短延迟到 1 秒
            delay(1000)

            // 如果已经连接，不需要重连
            if (webSocketClient.isConnected()) {
                return@launch
            }

            val token = withContext(Dispatchers.IO) {
                MyApplication.tokenManager.getToken()
            }

            if (!token.isNullOrBlank()) {
                try {
                    webSocketClient.disconnect()
                    delay(200)
                    webSocketClient.connect(sessionId.value, token)

                    // ⭐ 优化：缩短等待时间到 500ms
                    delay(500)

                    if (!webSocketClient.isConnected()) {
                        addSystemMessage("⚠️ 重连失败，请检查网络")
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "重连过程异常", e)
                    addSystemMessage("❌ 重连异常：${e.message}")
                }
            } else {
                addSystemMessage("⚠️ Token 已过期，请重新登录")
            }
        }
    }

    // ⭐ 修改：从 GPS 获取位置（使用高德 SDK）- 仅在首次进入时调用
    private fun updateLocationFromGPS() {
        viewModelScope.launch {
            try {
                // ⭐ 仅首次进入时定位
                if (!hasInitializedLocation) {
                    Log.d("ChatViewModel", "🛰️ 首次进入，等待位置同步...")
                    // ⭐ 不再阻塞等待，只是标记需要初始化
                    // 实际位置由 MainActivity 的 LaunchedEffect 异步同步
                    
                    // ⭐ 优化：轮询等待 HomeViewModel 同步位置（最多等 10 秒）
                    var waitCount = 0
                    while ((currentLat == null || currentLat == 0.0) && waitCount < 100) {  // 10 秒 = 100 * 100ms
                        delay(100)
                        waitCount++
                    }

                    if (currentLat != null && currentLat != 0.0) {
                        Log.d("ChatViewModel", "✅ 位置同步成功：lat=$currentLat, lng=$currentLng")
                    } else {
                        Log.w("ChatViewModel", "⚠️ 位置同步超时，当前位置：lat=$currentLat, lng=$currentLng")
                        addSystemMessage("⚠️ 位置获取失败，请检查定位权限")
                    }
                } else {
                    Log.d("ChatViewModel", "✅ 已经初始化过位置，跳过定位")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "获取位置失败", e)
            }
        }
    }

    // ⭐ 修改：接收 HomeScreen 同步的位置（供 MainActivity 调用）
    fun syncLocationFromHome(lat: Double, lng: Double) {
        // ⭐ 过滤无效位置（0.0, 0.0）
        if (lat == 0.0 || lng == 0.0) {
            Log.w("ChatViewModel", "⚠️ 收到无效位置：lat=$lat, lng=$lng，忽略")
            return
        }
        
        // ⭐ 标记已经初始化过位置
        hasInitializedLocation = true

        // ⭐ 优化：距离判定（小于 50 米不更新），减少计算
        val shouldUpdate = lastSyncedLat?.let { prevLat ->
            lastSyncedLng?.let { prevLng ->
                val distance = calculateDistance(prevLat, prevLng, lat, lng)
                distance >= 50.0 // 超过 50 米才更新
            } ?: true
        } ?: true

        if (shouldUpdate) {
            currentLat = lat
            currentLng = lng
            lastSyncedLat = lat
            lastSyncedLng = lng
            Log.d("ChatViewModel", "📍 位置已更新：lat=$lat, lng=$lng")
            
            // ⭐ 新增：同步位置到 WebSocket 客户端
            webSocketClient.updateLocation(lat, lng)
            
            // ⭐ 新增：上报位置到后端
            reportLocationToBackend(lat, lng)
        } else {
            Log.d("ChatViewModel", "⏭️ 位置变化小于 50 米，跳过更新")
        }
    }
    
    /**
     * ⭐ 新增：同步长辈模式状态
     */
    fun syncElderMode(isElder: Boolean) {
        _isElderMode.value = isElder
        Log.d("ChatViewModel", "👴 同步长辈模式: isElder=$isElder")
    }
    
    // ⭐ 新增：上报位置到后端
    private fun reportLocationToBackend(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    Log.w("ChatViewModel", "⚠️ 用户未登录，无法上报位置")
                    return@launch
                }
                
                // ⭐ sessionId 格式：user_{userId}
                val sessionId = "user_$userId"
                Log.d("ChatViewModel", "📍 开始上报位置到后端：lat=$lat, lng=$lng, sessionId=$sessionId")
                
                val result = agentRepository.updateLocation(sessionId, lat, lng)
                if (result.code == 200) {
                    Log.d("ChatViewModel", "✅ 位置上报成功")
                } else {
                    Log.e("ChatViewModel", "❌ 位置上报失败：${result.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "上报位置异常", e)
            }
        }
    }

    // ⭐ 新增：计算两点之间的距离（Haversine 公式）
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // 地球半径，单位：米

        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    fun sendMessage(content: String) {
        Log.d("ChatViewModel", "=== sendMessage 被调用 ===")
        Log.d("ChatViewModel", "发送内容：$content")
        Log.d("ChatViewModel", "当前位置：currentLat=$currentLat, currentLng=$currentLng")
        
        // ⭐ 新增：长辈端检测下单意图并提示
        if (_isElderMode.value) {
            val orderKeywords = listOf("叫车", "打车", "下单", "订车", "派车", "约车")
            if (orderKeywords.any { content.contains(it) }) {
                Log.d("ChatViewModel", "👴 长辈端检测到下单意图，拦截并提示")
                addSystemMessage(
                    "😊 温馨提示：\n" +
                    "长辈端不能直接叫车哦~\n" +
                    "\n" +
                    "如需叫车，您可以：\n" +
                    "1. 联系您的亲友帮忙代叫\n" +
                    "2. 让亲友使用他们的账号为您叫车\n" +
                    "\n" +
                    "💡 我还可以帮您：\n" +
                    "• 查询地点信息\n" +
                    "• 识别图片中的地址\n" +
                    "• 提供路线建议"
                )
                return  // ⭐ 拦截，不发送到后端
            }
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            imageBase64 = null
        )
        _messages.value += userMessage

        // ⭐ 新增：智能意图识别系统（像真正的 AI 一样处理多种需求）
        val intent = detectIntent(content)
        Log.d("ChatViewModel", "🧠 意图识别结果：${intent.type}, confidence=${intent.confidence}")
        
        when (intent.type) {
            IntentType.SPECIFIC_LOCATION -> {
                // 📍 明确地点名称 → HTTP API 搜索并自动选择
                Log.d("ChatViewModel", "📍 检测到具体地点名称，使用 HTTP 搜索并自动选择评分最高的")
                searchAndAutoSelect(intent.extractedKeyword ?: content)
                return
            }
            
            IntentType.NAVIGATION_REQUEST -> {
                // 🚗 导航请求（"我要去..."、"带我去..."）→ WebSocket 让 AI 解析
                Log.d("ChatViewModel", "🚗 检测到导航请求，通过 WebSocket 发送给 AI 助手")
            }
            
            IntentType.NEARBY_SEARCH -> {
                // 🔍 附近搜索（"附近的餐厅"、"有什么好吃的"）→ WebSocket 让 AI 推荐
                Log.d("ChatViewModel", "🔍 检测到附近搜索，通过 WebSocket 发送给 AI 助手")
            }
            
            IntentType.QUESTION -> {
                // ❓ 问题咨询（"怎么去..."、"如何到达..."）→ WebSocket 让 AI 回答
                Log.d("ChatViewModel", "❓ 检测到问题咨询，通过 WebSocket 发送给 AI 助手")
            }
            
            IntentType.GENERAL_CHAT -> {
                // 💬 普通聊天 → WebSocket 让 AI 对话
                Log.d("ChatViewModel", "💬 检测到普通聊天，通过 WebSocket 发送给 AI 助手")
            }
        }

        // ⭐ 修改：添加 type 字段，与后端对齐
        val wsMessage = WebSocketRequest(
            sessionId = sessionId.value,
            type = "user_message",  // ⭐ 新增：消息类型
            content = content,
            lat = currentLat,
            lng = currentLng
        )
        val json = Json.encodeToString<WebSocketRequest>(wsMessage)
        Log.d("ChatViewModel", "发送 WebSocket 消息：$json")

        // ⭐ 修改：检查连接状态后再发送
        if (webSocketClient.isConnected()) {
            webSocketClient.sendRaw(json)
        } else {
            Log.e("ChatViewModel", "WebSocket 未连接，尝试重连...")
            reconnectWebSocket()
            // ⭐ 延迟发送，等待重连
            viewModelScope.launch {
                delay(2000)
                if (webSocketClient.isConnected()) {
                    webSocketClient.sendRaw(json)
                } else {
                    val msg = getFriendlyMessage(
                        "❌ 网络连接已断开，请检查网络",
                        "😊 网络有点不稳定，请稍等一会儿再试哦~"
                    )
                    addSystemMessage(msg)
                }
            }
        }
    }
    
    // ⭐ 新增：智能意图识别数据类
    data class UserIntent(
        val type: IntentType,
        val confidence: Float,  // 置信度 0.0-1.0
        val extractedKeyword: String? = null  // 提取的关键词
    )
    
    enum class IntentType {
        SPECIFIC_LOCATION,   // 明确地点名称（如"北京站"）
        NAVIGATION_REQUEST,  // 导航请求（如"我要去机场"）
        NEARBY_SEARCH,       // 附近搜索（如"附近的餐厅"）
        QUESTION,            // 问题咨询（如"怎么去火车站"）
        GENERAL_CHAT         // 普通聊天（如"你好"）
    }
    
    // ⭐ 新增：智能意图识别引擎（优化版）
    private fun detectIntent(content: String): UserIntent {
        val trimmedContent = content.trim()
        
        // 🛡️ 边界检查
        if (trimmedContent.isEmpty()) {
            return UserIntent(IntentType.GENERAL_CHAT, 0.0f)
        }
        
        // 1️⃣ 检测导航请求（最高优先级）
        val navigationKeywords = listOf("我要去", "带我去", "我想去", "导航到", "打车去", "送我去")
        if (navigationKeywords.any { trimmedContent.contains(it) }) {
            Log.d("ChatViewModel", "🚗 匹配导航关键词: ${navigationKeywords.first { trimmedContent.contains(it) }}")
            return UserIntent(IntentType.NAVIGATION_REQUEST, 0.95f)
        }
        
        // 2️⃣ 检测问题咨询（高优先级）
        val questionKeywords = listOf("怎么去", "如何到达", "怎么到", "路线", "怎么走", "多远", "多久")
        val hasQuestionMark = trimmedContent.contains("?") || trimmedContent.contains("？")
        if (questionKeywords.any { trimmedContent.contains(it) } || hasQuestionMark) {
            val matchedKeyword = questionKeywords.firstOrNull { trimmedContent.contains(it) } ?: "?"
            Log.d("ChatViewModel", "❓ 匹配问题关键词: $matchedKeyword")
            return UserIntent(IntentType.QUESTION, 0.9f)
        }
        
        // 3️⃣ 检测附近搜索（中优先级）
        val nearbyKeywords = listOf("附近", "周边", "有什么", "有哪些", "推荐", "找一下", "找个")
        if (nearbyKeywords.any { trimmedContent.contains(it) }) {
            val matchedKeyword = nearbyKeywords.first { trimmedContent.contains(it) }
            Log.d("ChatViewModel", "🔍 匹配附近搜索关键词: $matchedKeyword")
            return UserIntent(IntentType.NEARBY_SEARCH, 0.85f)
        }
        
        // 4️⃣ 检测明确地点名称（综合判断）
        val isShortText = trimmedContent.length <= 20
        val hasVerb = trimmedContent.contains("去") || trimmedContent.contains("到") || 
                      trimmedContent.contains("找") || trimmedContent.contains("查") ||
                      trimmedContent.contains("看") || trimmedContent.contains("搜")
        val hasQuestionWord = trimmedContent.contains("怎么") || trimmedContent.contains("如何") ||
                              trimmedContent.contains("哪里") || trimmedContent.contains("什么") ||
                              trimmedContent.contains("谁") || trimmedContent.contains("何时")
        
        // ⭐ 扩展地点关键词库
        val locationKeywords = listOf(
            "站", "机场", "医院", "学校", "酒店", "商场", "公园", "广场",
            "中心", "大厦", "小区", "地铁", "公交", "火车站", "高铁",
            "大学", "中学", "小学", "幼儿园", "超市", "市场", "银行"
        )
        val hasLocationKeyword = locationKeywords.any { trimmedContent.contains(it) }
        val matchedLocationKeyword = locationKeywords.firstOrNull { trimmedContent.contains(it) }
        
        if (isShortText && !hasVerb && !hasQuestionWord) {
            // 短文本 + 无动词 + 无疑问词 → 很可能是地点名称
            val confidence = if (hasLocationKeyword) {
                Log.d("ChatViewModel", "📍 匹配地点关键词: $matchedLocationKeyword")
                0.9f
            } else {
                Log.d("ChatViewModel", "📍 短文本无动词，推测为地点名称")
                0.7f
            }
            return UserIntent(IntentType.SPECIFIC_LOCATION, confidence, trimmedContent)
        }
        
        // 5️⃣ 默认：普通聊天
        Log.d("ChatViewModel", "💬 未匹配特定意图，归类为普通聊天")
        return UserIntent(IntentType.GENERAL_CHAT, 0.5f)
    }

    fun sendLocationRequest() {
        val wsMessage = WebSocketRequest(
            sessionId = sessionId.value,
            content = "获取当前位置"
        )
        val json = Json.encodeToString<WebSocketRequest>(wsMessage)
        webSocketClient.sendRaw(json)
    }

    fun sendImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                var inputStream: InputStream? = null
                try {
                    inputStream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                    addSystemMessage("❌ 读取图片失败：${e.message}")
                    null
                } finally {
                    inputStream?.close()
                }
            }
            bitmap?.let {
                // ⭐ 修改：压缩并添加到待发送列表
                val base64 = withContext(Dispatchers.IO) {
                    compressAndToBase64(it, maxSizeKB = 800)
                }
                addPendingImage(base64)
            }
        }
    }

    // ⭐ 使用 WebSocket 方式发送并识别图片（后端推荐）
    fun sendImage(bitmap: Bitmap) {
        Log.d("ChatViewModel", "=== 开始图片识别 (WebSocket) ===")
        recognizeImageByWebSocket(bitmap)
    }
    
    // ⭐ 新增：添加图片到待发送列表（最多3张）
    fun addPendingImageFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val base64 = compressAndToBase64(bitmap, maxSizeKB = 800)
            addPendingImage(base64)
        }
    }
    
    // ⭐ 新增：添加图片到待发送列表（最多3张）
    fun addPendingImage(base64: String) {
        if (_pendingImages.size >= 3) {
            _showImageLimitDialog.value = true
            Log.w("ChatViewModel", "⚠️ 图片数量已达上限（3张）")
            return
        }
        _pendingImages.add(base64)
        Log.d("ChatViewModel", "✅ 添加待发送图片，当前数量：${_pendingImages.size}")
    }
    
    // ⭐ 新增：删除指定索引的图片
    fun removePendingImage(index: Int) {
        if (index in _pendingImages.indices) {
            _pendingImages.removeAt(index)
            Log.d("ChatViewModel", "🗑️ 删除图片，剩余数量：${_pendingImages.size}")
        }
    }
    
    // ⭐ 新增：清空所有待发送图片
    fun clearPendingImages() {
        _pendingImages.clear()
        Log.d("ChatViewModel", "🗑️ 清空所有待发送图片")
    }
    
    // ⭐ 新增：隐藏图片数量限制对话框
    fun dismissImageLimitDialog() {
        _showImageLimitDialog.value = false
    }
    
    // ⭐ 新增：批量发送所有待发送图片
    fun sendAllPendingImages() {
        sendAllPendingImagesWithText(null)
    }
    
    /**
     * ⭐ 新增：批量发送图片（支持附带文字说明）
     * @param text 可选的文字说明，如果为 null 则只发送图片
     */
    fun sendAllPendingImagesWithText(text: String?) {
        if (_pendingImages.isEmpty()) {
            Log.w("ChatViewModel", "⚠️ 没有待发送的图片")
            return
        }
        
        viewModelScope.launch {
            Log.d("ChatViewModel", "📤 开始批量发送 ${_pendingImages.size} 张图片")
            
            // 位置信息检查
            if (currentLat == null || currentLng == null) {
                addSystemMessage("🛰️ 正在获取您的位置...")
                delay(3000)
                if (currentLat == null || currentLng == null) {
                    addSystemMessage("⚠️ 位置获取失败，请检查是否授予定位权限")
                    return@launch
                }
            }
            
            // ⭐ 修复：先复制图片列表，避免 ConcurrentModificationException
            val imagesCopy = _pendingImages.toList()
            
            // 构建多图片请求（对齐后端文档 1.2.2）
            val imageRequest = JSONObject().apply {
                put("type", "image")
                put("sessionId", sessionId.value)
                put("imageBase64", imagesCopy[0])  // 主图片
                
                if (imagesCopy.size > 1) {
                    // 额外图片数组
                    val additionalImages = JSONArray()
                    for (i in 1 until imagesCopy.size) {
                        additionalImages.put(imagesCopy[i])
                    }
                    put("additionalImages", additionalImages)
                    put("imageCount", imagesCopy.size)
                } else {
                    put("imageCount", 1)
                }
                
                put("lat", currentLat!!)
                put("lng", currentLng!!)
                
                // ⭐ 新增：如果有文字说明，添加到请求中
                if (!text.isNullOrBlank()) {
                    put("content", text)
                }
            }
            
            Log.d("ChatViewModel", "发送多图片 WebSocket 消息：type=image, count=${imagesCopy.size}")
            
            // ⭐ 显示用户发送的图片消息（支持多张 + 文字说明）
            val content = if (!text.isNullOrBlank()) {
                "$text\n📷 [${imagesCopy.size}张图片]"
            } else {
                "📷 [${imagesCopy.size}张图片]"
            }
            
            // ⭐ 修复：使用复制的列表，而不是 subList
            val additionalImagesCopy = if (imagesCopy.size > 1) {
                imagesCopy.subList(1, imagesCopy.size).toList()  // 转为普通 List
            } else {
                null
            }
            
            val imageMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = content,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                imageBase64 = imagesCopy[0],  // 主图
                additionalImages = additionalImagesCopy  // ⭐ 额外图片（已复制）
            )
            _messages.value += imageMessage
            
            // 发送 WebSocket 消息
            if (webSocketClient.isConnected()) {
                webSocketClient.sendRaw(imageRequest.toString())
                Log.d("ChatViewModel", "✅ 多图片消息已发送")
            } else {
                Log.e("ChatViewModel", "❌ WebSocket 未连接，尝试重连...")
                reconnectWebSocket()
                delay(2000)
                if (webSocketClient.isConnected()) {
                    webSocketClient.sendRaw(imageRequest.toString())
                    Log.d("ChatViewModel", "✅ 重连后多图片消息已发送")
                } else {
                    addSystemMessage("❌ 网络连接已断开，请检查网络")
                }
            }
            
            // 清空待发送列表
            clearPendingImages()
        }
    }

    // ⭐ 新增：WebSocket 方式图片识别（后端推荐）
    private fun recognizeImageByWebSocket(bitmap: Bitmap) {
        viewModelScope.launch {
            // ⭐ 位置信息检查（必须参数）
            if (currentLat == null || currentLng == null) {
                addSystemMessage("🛰️ 正在获取您的位置...")
                delay(3000)
                if (currentLat == null || currentLng == null) {
                    addSystemMessage("⚠️ 位置获取失败，请检查是否授予定位权限")
                    return@launch
                }
            }
            
            Log.d("ChatViewModel", "=== 开始 WebSocket 图片识别 ===")
            Log.d("ChatViewModel", "sessionId=${sessionId.value}")
            Log.d("ChatViewModel", "lat=$currentLat, lng=$currentLng")
            
            // ⭐ 图片压缩（对齐后端要求：最大支持 10MB，建议压缩到 800KB - 1MB）
            val base64 = withContext(Dispatchers.IO) {
                compressAndToBase64(bitmap, maxSizeKB = 800)  // 800KB，平衡清晰度和速度
            }
            
            // ⭐ 发送前校验大小（后端限制：10MB = 10240KB）
            val base64SizeKB = base64.length / 1024
            if (base64SizeKB > 10240) {  // 10MB = 10240KB
                Log.e("ChatViewModel", "❌ 图片压缩后仍然过大：${base64SizeKB}KB")
                addSystemMessage("⚠️ 图片太大（超过 10MB），请重新选择更小的图片")
                return@launch
            } else if (base64SizeKB > 3072) {
                Log.w("ChatViewModel", "⚠️ 图片较大：${base64SizeKB}KB，可能影响识别速度")
            }
            
            // 计算Bitmap大小（兼容所有API级别）
            val bitmapSizeKB = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                bitmap.allocationByteCount / 1024
            } else {
                bitmap.byteCount / 1024
            }
            Log.d("ChatViewModel", "✅ 图片压缩成功：原始=${bitmapSizeKB}KB, 压缩后=${base64SizeKB}KB")
            Log.d("ChatViewModel", "imageBase64 length=${base64.length}")

            // ⭐ 显示用户发送的图片消息
            val imageMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "📷 [图片]",
                isUser = true,
                timestamp = System.currentTimeMillis(),
                imageBase64 = base64
            )
            _messages.value += imageMessage

            // ⭐ 构建 WebSocket 消息（对齐后端文档）
            val imageRequest = JSONObject().apply {
                put("type", "image")
                put("sessionId", sessionId.value)
                put("imageBase64", base64)  // ⭐ Base64已包含 data:image/jpeg;base64, 前缀
                put("lat", currentLat!!)
                put("lng", currentLng!!)
            }
            
            Log.d("ChatViewModel", "发送 WebSocket 图片消息：type=image, sessionId=${sessionId.value}")
            
            // ⭐ 检查 WebSocket 连接状态并发送
            if (webSocketClient.isConnected()) {
                webSocketClient.sendRaw(imageRequest.toString())
                Log.d("ChatViewModel", "✅ 图片消息已发送")
            } else {
                Log.e("ChatViewModel", "❌ WebSocket 未连接，尝试重连...")
                reconnectWebSocket()
                // ⭐ 延迟发送，等待重连
                delay(2000)
                if (webSocketClient.isConnected()) {
                    webSocketClient.sendRaw(imageRequest.toString())
                    Log.d("ChatViewModel", "✅ 重连后图片消息已发送")
                } else {
                    addSystemMessage("❌ 网络连接已断开，请检查网络")
                }
            }
        }
    }

    // ⭐ 修改：HTTP 方式智能搜索目的地（主入口）- 带防抖和友好提示
    fun searchDestinationByHttp(keyword: String) {
        // ⭐ 取消之前的搜索请求
        searchDebounceJob?.cancel()

        searchDebounceJob = viewModelScope.launch {
            Log.d("ChatViewModel", "=== HTTP 方式智能搜索 ===")
            Log.d("ChatViewModel", "keyword=$keyword, sessionId=${sessionId.value}")
            Log.d("ChatViewModel", "currentLat=$currentLat, currentLng=$currentLng")

            // ⭐ 优化：如果还没有位置信息，提示用户
            if (currentLat == null || currentLng == null) {
                addSystemMessage("🛰️ 正在获取您的位置...")
                // ⭐ 延迟等待位置同步（最多等 3 秒）
                delay(3000)
                if (currentLat == null || currentLng == null) {
                    addSystemMessage("⚠️ 位置获取失败，请检查是否授予定位权限")
                    return@launch
                }
            }

            // ⭐ 添加用户消息到聊天历史
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = keyword,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            _messages.value += userMessage

            val result = agentRepository.searchDestination(
                sessionId = sessionId.value,
                keyword = keyword,
                lat = currentLat!!,
                lng = currentLng!!
            )

            if (result.isSuccess()) {
                val response = result.data
                Log.d("ChatViewModel", "=== 收到搜索响应 ===")
                Log.d("ChatViewModel", "type=${response?.type}, message=${response?.message}")
                Log.d(
                    "ChatViewModel",
                    "places count=${response?.places?.size}, needConfirm=${response?.needConfirm}"
                )

                when (response?.type?.uppercase()) {
                    "SEARCH" -> {
                        val places = response.places ?: emptyList()
                        Log.d("ChatViewModel", "=== SEARCH 响应处理 ===")
                        Log.d("ChatViewModel", "地点数量：${places.size}")
                        Log.d("ChatViewModel", "needConfirm=${response.needConfirm}")

                        if (places.isNotEmpty()) {
                            // ⭐ 解析 POI 列表（包含 score 字段）
                            val poiList = places.map {
                                PoiData(
                                    id = it.id,
                                    name = it.name,
                                    address = it.address,
                                    lat = it.lat,
                                    lng = it.lng,
                                    distance = it.distance,
                                    type = it.type ?: "",
                                    duration = it.duration,
                                    price = it.price,
                                    score = it.score  // ⭐ 新增：评分字段
                                )
                            }
                            _poiList.value = poiList
                            _candidates.value = poiList

                            val suggestions = places.take(3).map { it.name }

                            // ⭐ 优化：显示更友好的消息
                            val chatMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                content = buildSearchMessage(places, response.message),
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                suggestions = suggestions
                            )
                            _messages.value += chatMessage

                            // ⭐ 如果需要确认，弹出候选列表
                            if (response.needConfirm) {
                                Log.d("ChatViewModel", "🔔 需要确认，弹出候选列表对话框")
                                _showCandidatesDialog.value = true
                            } else {
                                Log.d("ChatViewModel", "✅ 不需要确认，用户可直接选择")
                            }
                        } else {
                            addSystemMessage("😕 未找到相关地点，请尝试其他关键词")
                        }
                    }

                    "ORDER" -> {
                        // 直接返回订单信息
                        addSystemMessage(response?.message ?: "✅ 已确认目的地，正在创建订单...")
                        response?.poi?.let { poi ->
                            response?.route?.let { route ->
                                createOrder(poi.name ?: "", poi.lat, poi.lng, 1, null)
                            }
                        }
                    }

                    "CHAT" -> {
                        // AI 聊天回复
                        addSystemMessage(
                            response?.message ?: "您好！我是您的智能出行助手，请问有什么可以帮您？"
                        )
                    }

                    else -> {
                        addSystemMessage(response?.message ?: "✅ 搜索完成")
                    }
                }
            } else {
                Log.e("ChatViewModel", "搜索失败：${result.message}")
                // ⭐ 优化：友好的错误提示
                val errorMsg = when {
                    result.message.contains("EXCEEDED") || result.message.contains("限流") ->
                        "⚠️ 系统繁忙，请稍后再试"

                    result.message.contains("网络") || result.message.contains("连接") ->
                        "⚠️ 网络连接失败，请检查网络"

                    else -> "❌ 搜索失败：${result.message}"
                }
                addSystemMessage(errorMsg)
            }
        }
    }

    // ⭐ 新增：构建友好的搜索响应消息
    private fun buildSearchMessage(places: List<PoiResponse>, defaultMessage: String?): String {
        val size = places.size
        val firstPlace = places.firstOrNull()

        return buildString {
            append(defaultMessage ?: "为您找到 $size 个地点\n\n")

            // ⭐ 只显示前 3 个推荐
            places.take(3).forEachIndexed { index, place ->
                append("${index + 1}. 📍 ${place.name}\n")
                if (place.distance != null) {
                    append("   📏 距离：${String.format("%.1f", place.distance / 1000)}公里\n")
                }
                if (place.duration != null && place.duration > 0) {
                    append("   ⏱️ 预计：${place.duration / 60}分钟\n")
                }
                if (place.price != null && place.price > 0) {
                    append("   💰 预估：¥${String.format("%.2f", place.price)}\n")
                }
                if (index < 2) append("\n")
            }

            if (size > 3) {
                append("\n💡 还有 ${size - 3} 个结果，请点击查看详情")
            }
        }
    }

    // ⭐ 新增：搜索并自动选择评分最高的地点（用于具体地点名称）
    private fun searchAndAutoSelect(keyword: String) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "=== 搜索并自动选择评分最高的 ===")
            Log.d("ChatViewModel", "keyword=$keyword")

            // ⭐ 检查位置信息
            if (currentLat == null || currentLng == null) {
                addSystemMessage("🛰️ 正在获取您的位置...")
                delay(3000)
                if (currentLat == null || currentLng == null) {
                    addSystemMessage("⚠️ 位置获取失败，请检查是否授予定位权限")
                    return@launch
                }
            }

            // ⭐ 使用 HTTP API 搜索
            val result = agentRepository.searchDestination(
                sessionId = sessionId.value,
                keyword = keyword,
                lat = currentLat!!,
                lng = currentLng!!
            )

            if (result.isSuccess()) {
                val response = result.data
                Log.d("ChatViewModel", "搜索响应：type=${response?.type}, places count=${response?.places?.size}")

                when (response?.type?.uppercase()) {
                    "SEARCH" -> {
                        val places = response.places ?: emptyList()
                        
                        if (places.isNotEmpty()) {
                            // ⭐ 按评分排序，选择评分最高的
                            val sortedPlaces = places.sortedByDescending { it.score ?: 0.0 }
                            val bestPlace = sortedPlaces.first()
                            
                            Log.d("ChatViewModel", "✅ 自动选择评分最高的：${bestPlace.name}, score=${bestPlace.score}")
                            
                            // 显示系统消息
                            addSystemMessage("📍 已为您找到：**${bestPlace.name}**")
                            
                            // ⭐ 延迟一点，让用户看到提示，然后自动确认
                            delay(800)
                            
                            // 直接调用 HTTP 确认接口
                            confirmSelectionByHttp(bestPlace.name ?: "")
                        } else {
                            addSystemMessage("😕 未找到相关地点，请尝试其他关键词")
                        }
                    }
                    
                    "ORDER" -> {
                        // ⭐ 修改:移除 route 检查,直接调用 HTTP API 创建订单,并校验 POI 名称
                        Log.d("ChatViewModel", "=== 收到 WebSocket ORDER 消息 ===")
                        Log.d("ChatViewModel", "response.message=${response.message}")
                        Log.d("ChatViewModel", "response.poi=${response.poi}")
                        Log.d("ChatViewModel", "response.route=${response.route}")
                                            
                        addSystemMessage(response?.message ?: "✅ 已确认目的地,正在创建订单...")
                                            
                        // ⭐ 直接从 response 中获取 poi,并校验名称
                        response.poi?.let { poi ->
                            val poiName = poi.name
                            if (poiName.isNullOrBlank()) {
                                Log.e("ChatViewModel", "❌ POI 名称为空,无法创建订单")
                                addSystemMessage("⚠️ 目的地名称缺失,请重新选择")
                                return@let
                            }
                            Log.d("ChatViewModel", "🚀 开始调用 createOrder (HTTP API),poiName=$poiName")
                            createOrder(poiName, poi.lat, poi.lng, 1, null)
                        } ?: run {
                            Log.e("ChatViewModel", "❌ poi 为 null")
                            addSystemMessage("⚠️ 目的地信息缺失")
                        }
                    }
                    
                    else -> {
                        addSystemMessage(response?.message ?: "✅ 搜索完成")
                    }
                }
            } else {
                Log.e("ChatViewModel", "搜索失败：${result.message}")
                addSystemMessage("❌ 搜索失败：${result.message}")
            }
        }
    }

    // ⭐ 新增：HTTP 方式确认选择
    fun confirmSelectionByHttp(selectedPoiName: String) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "=== HTTP 方式确认选择 ===")
            Log.d("ChatViewModel", "selectedPoiName=$selectedPoiName")

            // ⭐ 检查位置信息
            val lat = currentLat
            val lng = currentLng
            if (lat == null || lng == null) {
                addSystemMessage("⚠️ 位置信息获取中...")
                return@launch
            }

            val result = agentRepository.confirmSelection(
                sessionId = sessionId.value,
                selectedPoiName = selectedPoiName,
                lat = lat,
                lng = lng
            )

            if (result.isSuccess()) {
                val response = result.data
                Log.d("ChatViewModel", "确认响应：type=${response?.type}, message=${response?.message}")
                
                if (response?.type == "ORDER") {
                    addSystemMessage(response.message ?: "已确认目的地")
                    // ⭐ 直接从 response 中获取 poi,并校验名称
                    response.poi?.let { poi ->
                        val poiName = poi.name
                        if (poiName.isNullOrBlank()) {
                            Log.e("ChatViewModel", "❌ POI 名称为空,无法创建订单")
                            addSystemMessage("⚠️ 目的地名称缺失,请重新选择")
                            return@let
                        }
                        Log.d("ChatViewModel", "POI 信息：name=$poiName, lat=${poi.lat}, lng=${poi.lng}")
                        Log.d("ChatViewModel", "🚀 开始调用 createOrder (HTTP 方式)，poiName=$poiName")
                        createOrder(poiName, poi.lat, poi.lng, 1, null)
                    } ?: run {
                        Log.e("ChatViewModel", "❌ poi 为 null")
                        addSystemMessage("⚠️ 目的地信息缺失")
                    }
                } else {
                    addSystemMessage(response?.message ?: "确认成功")
                }
            } else {
                Log.e("ChatViewModel", "确认失败：${result.message}")
                addSystemMessage("❌ 确认失败：${result.message}")
            }
        }
    }

    // ⭐ 新增：HTTP 方式图片识别（完全对齐后端文档）
    fun recognizeImageByHttp(bitmap: Bitmap) {
        viewModelScope.launch {
            // ⭐ 位置信息检查（必须参数）
            if (currentLat == null || currentLng == null) {
                addSystemMessage("🛰️ 正在获取您的位置...")
                delay(3000)
                if (currentLat == null || currentLng == null) {
                    addSystemMessage("⚠️ 位置获取失败，请检查是否授予定位权限")
                    return@launch
                }
            }
            
            Log.d("ChatViewModel", "=== 开始图片识别 ===")
            Log.d("ChatViewModel", "sessionId=${sessionId.value}")
            Log.d("ChatViewModel", "lat=$currentLat, lng=$currentLng")
            
            // ⭐ 图片压缩（对齐后端要求：最大支持 10MB，建议压缩到 800KB - 1MB）
            val base64 = withContext(Dispatchers.IO) {
                compressAndToBase64(bitmap, maxSizeKB = 800)  // 800KB，平衡清晰度和速度
            }
            
            // ⭐ 发送前校验大小（后端限制：10MB = 10240KB）
            val base64SizeKB = base64.length / 1024
            if (base64SizeKB > 10240) {  // 10MB = 10240KB
                Log.e("ChatViewModel", "❌ 图片压缩后仍然过大：${base64SizeKB}KB")
                addSystemMessage("⚠️ 图片太大（超过 10MB），请重新选择更小的图片")
                return@launch
            } else if (base64SizeKB > 3072) {
                Log.w("ChatViewModel", "⚠️ 图片较大：${base64SizeKB}KB，可能影响识别速度")
            }
            
            // 计算Bitmap大小（兼容所有API级别）
            val bitmapSizeKB = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                bitmap.allocationByteCount / 1024
            } else {
                bitmap.byteCount / 1024
            }
            Log.d("ChatViewModel", "✅ 图片压缩成功：原始=${bitmapSizeKB}KB, 压缩后=${base64SizeKB}KB")
            Log.d("ChatViewModel", "imageBase64 length=${base64.length}")

            // ⭐ 显示用户发送的图片消息
            val imageMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "📷 [图片]",
                isUser = true,
                timestamp = System.currentTimeMillis(),
                imageBase64 = base64
            )
            _messages.value += imageMessage

            // ⭐ 调用 HTTP API 识别图片
            val result = agentRepository.recognizeImage(
                sessionId = sessionId.value,
                imageBase64 = base64,  // ⭐ 已经包含前缀
                lat = currentLat!!,
                lng = currentLng!!
            )
            
            Log.d("ChatViewModel", "识别结果：code=${result.code}, message=${result.message}")
            Log.d("ChatViewModel", "data type=${result.data?.type}, places count=${result.data?.places?.size ?: 0}")

            // ⭐ 对齐后端标准响应格式
            when (result.code) {
                200 -> {
                    // ✅ 成功响应
                    val data = result.data
                    if (data == null) {
                        Log.e("ChatViewModel", "❌ code=200 但 data 为 null")
                        addSystemMessage("❌ 服务器返回数据异常")
                        return@launch
                    }
                    
                    Log.d("ChatViewModel", "=== 收到图片识别响应 ===")
                    Log.d("ChatViewModel", "type=${data.type}, message=${data.message}")
                    Log.d("ChatViewModel", "places=${data.places?.size ?: 0}, candidates=${data.candidates?.size ?: 0}")
                    
                    // ⭐ 新增：打印完整的响应数据，便于调试
                    if (data.places.isNullOrEmpty() && data.candidates.isNullOrEmpty()) {
                        Log.w("ChatViewModel", "⚠️ 未识别到地点，原始消息：${data.message}")
                        Log.w("ChatViewModel", "⚠️ 请检查：1.图片是否清晰 2.是否包含地址文字 3.地址是否在当前位置附近")
                    }
                    
                    // ⭐ 按照后端文档标准处理响应（兼容新旧版本）
                    when (data.type?.uppercase()) {
                        "SEARCH" -> {
                            // 📍 新版本：识别到地址，显示 POI 列表
                            handleSearchResponse(data)
                        }
                        
                        "IMAGE_RECOGNITION" -> {
                            // 📸 旧版本：兼容旧的图片识别类型
                            handleImageRecognitionResponse(data)
                        }
                        
                        "ORDER" -> {
                            // 🚗 直接返回订单信息(精确匹配)
                            handleOrderResponse(data)
                        }
                        
                        "CHAT" -> {
                            // 💬 普通聊天回复或未识别到地址
                            handleChatResponse(data)
                        }
                        
                        else -> {
                            Log.w("ChatViewModel", "⚠️ 未知的响应类型: ${data.type}")
                            showError("未知的响应类型")
                        }
                    }
                }
                
                400 -> {
                    // ❌ 参数错误
                    showError("图片格式不正确：${result.message}")
                }
                
                422 -> {
                    // ❌ 未识别到内容
                    showError("图片中没有识别到地址信息，请换一张清晰的图片")
                }
                
                503 -> {
                    // ❌ 服务不可用
                    showError("图片识别服务暂时不可用，请稍后重试")
                }
                
                500 -> {
                    // ❌ 系统错误
                    showError("系统繁忙，请稍后重试")
                }
                
                else -> {
                    // ❌ 其他错误
                    showError("识别失败：${result.message}（错误码：${result.code}）")
                }
            }
        }
    }

    // ⭐ 新增：处理 SEARCH 响应（新版本）
    private fun handleSearchResponse(data: AgentSearchResponse) {
        val places = data.places ?: emptyList()
        Log.d("ChatViewModel", "📍 SEARCH 类型，places count=${places.size}")
        
        if (places.isNotEmpty()) {
            _poiList.value = places.map {
                PoiData(
                    it.id ?: "",
                    it.name ?: "",
                    it.address ?: "",
                    it.lat,
                    it.lng,
                    it.distance,
                    it.type ?: "",
                    it.duration,
                    it.price,
                    it.score
                )
            }
            
            val suggestions = places.take(3).map { it.name }
            val chatMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = data.message ?: "为你找到以下地点",
                isUser = false,
                timestamp = System.currentTimeMillis(),
                suggestions = suggestions
            )
            _messages.value += chatMessage
            
            if (data.needConfirm) {
                Log.d("ChatViewModel", "🔔 需要确认，弹出候选列表对话框")
                _showCandidatesDialog.value = true
            }
            
            speak("找到 ${places.size} 个相关地点")
        } else {
            addSystemMessage("😕 未从图片中识别到有效地址")
        }
    }

    // ⭐ 新增：处理 IMAGE_RECOGNITION 响应（对齐后端文档）
    private fun handleImageRecognitionResponse(data: AgentSearchResponse) {
        Log.d("ChatViewModel", "📸 IMAGE_RECOGNITION 类型")
        
        // ⭐ 检查是否直接返回订单信息
        val orderInfo = data.data?.order
        if (orderInfo != null) {
            Log.d("ChatViewModel", "🚀 图片识别直接下单，orderId=${orderInfo.orderId}")
            addSystemMessage(data.message ?: "✅ 已确认目的地，正在创建订单...")
            
            // ⭐ 直接创建订单
            createOrder(
                poiName = orderInfo.destName ?: "未知位置",
                poiLat = orderInfo.destLat ?: 0.0,
                poiLng = orderInfo.destLng ?: 0.0,
                passengerCount = 1,
                remark = null
            )
            return
        }
        
        // ⭐ 优先从嵌套的 data.data.places 获取，其次从顶层 data.places 获取
        val nestedPlaces = data.data?.places
        val topLevelPlaces = data.places ?: data.candidates
        val places = nestedPlaces ?: topLevelPlaces ?: emptyList()
        
        Log.d("ChatViewModel", "nested places count=${nestedPlaces?.size}, top-level places count=${topLevelPlaces?.size}")
        Log.d("ChatViewModel", "最终 places count=${places.size}")
        
        if (places.isNotEmpty()) {
            _poiList.value = places.map {
                PoiData(
                    it.id ?: "",
                    it.name ?: "",
                    it.address ?: "",
                    it.lat,
                    it.lng,
                    it.distance,
                    it.type ?: "",
                    it.duration,
                    it.price,
                    it.score
                )
            }
            
            val suggestions = places.take(3).map { it.name }
            val chatMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = data.message ?: "找到 ${places.size} 个相关地点",
                isUser = false,
                timestamp = System.currentTimeMillis(),
                suggestions = suggestions
            )
            _messages.value += chatMessage
            
            if (data.needConfirm) {
                _showCandidatesDialog.value = true
            }
            
            speak("找到 ${places.size} 个相关地点")
            
            // ⭐ 重要：调用搜索接口保存会话状态，让后端知道已经搜索过
            viewModelScope.launch {
                val firstPlace = places.firstOrNull()
                if (firstPlace != null && currentLat != null && currentLng != null) {
                    Log.d("ChatViewModel", "🔄 调用搜索接口保存会话状态：${firstPlace.name}")
                    agentRepository.searchDestination(
                        sessionId = sessionId.value,
                        keyword = firstPlace.name,
                        lat = currentLat!!,
                        lng = currentLng!!
                    )
                }
            }
        } else {
            val errorMsg = if (!data.message.isNullOrBlank()) {
                "😕 ${data.message}\n\n💡 建议：\n• 确保图片清晰，光线充足\n• 拍摄招牌、路牌等包含地址的文字\n• 尝试拍摄更近的视角"
            } else {
                "😕 未从图片中识别到有效地址\n\n💡 建议：\n• 确保图片清晰，光线充足\n• 拍摄招牌、路牌等包含地址的文字\n• 尝试拍摄更近的视角"
            }
            addSystemMessage(errorMsg)
        }
    }

    // ⭐ 新增：处理 ORDER 响应
    private fun handleOrderResponse(data: AgentSearchResponse) {
        data.message?.let { addSystemMessage(it) }
        data.poi?.let { poi ->
            val poiName = poi.name
            if (poiName.isNullOrBlank()) {
                Log.e("ChatViewModel", "❌ POI 名称为空,无法创建订单")
                addSystemMessage("⚠️ 目的地名称缺失")
                return@let
            }
            Log.d("ChatViewModel", "🚀 图片识别 ORDER: poiName=$poiName")
            createOrder(poiName, poi.lat, poi.lng, 1, null)
        }
    }

    // ⭐ 新增：处理 CHAT 响应
    private fun handleChatResponse(data: AgentSearchResponse) {
        showMessage(data.message)
    }

    fun startVoiceInput(context: Context) {
        Log.d("ChatViewModel", "=== startVoiceInput 被调用 ===")
        
        // ⭐ 如果正在录音，则停止
        if (_isListening.value) {
            Log.d("ChatViewModel", "停止语音识别")
            speechHelper?.destroy()
            speechHelper = null
            _isListening.value = false
            _voiceInputText.value = ""  // ⭐ 清空实时文本
            return
        }
        
        Log.d("ChatViewModel", "开始语音识别")
        
        if (speechHelper == null) {
            // ⭐ 修改：使用百度语音识别，支持方言
            val baiduLanguage = when (_currentAccent.value) {
                "mandarin" -> "zh-CN"
                "cantonese" -> "zh-HK"
                "sichuan" -> "zh-SICHUAN"
                else -> "zh-CN"
            }
            
            speechHelper = BaiduSpeechRecognizerHelper(
                context = context,
                onResult = { finalText ->
                    // 最终结果：更新输入框，但不自动发送
                    Log.d("ChatViewModel", "✅ 收到百度语音最终结果: $finalText")
                    if (finalText.isNotBlank() && !finalText.contains("配置错误")) {
                        // ⭐ 新增：应用敏感词过滤
                        val filteredText = com.example.myapplication.core.utils.SensitiveWordFilter.filterText(finalText)
                        _voiceInputText.value = filteredText  // ⭐ 设置到输入框，等待用户手动发送
                        
                        // ⭐ 如果检测到敏感词，提示用户
                        if (filteredText != finalText) {
                            Log.w("ChatViewModel", "⚠️ 检测到敏感词，已过滤")
                        }
                    } else {
                        Log.w("ChatViewModel", "⚠️ 语音识别结果为空或配置错误")
                        // ⭐ 不显示提示，避免干扰用户
                        // addSystemMessage("⚠️ 未识别到语音内容")
                    }
                    _isListening.value = false
                    speechHelper = null
                },
                onPartialResult = { partialText ->
                    // ⭐ 实时部分结果：像微信一样逐字显示
                    Log.d("ChatViewModel", "🔄 收到百度语音实时结果: $partialText")
                    if (partialText.isNotBlank()) {
                        // ⭐ 实时结果也应用敏感词过滤
                        val filteredText = com.example.myapplication.core.utils.SensitiveWordFilter.filterText(partialText)
                        _voiceInputText.value = filteredText  // ⭐ 实时更新 UI
                    }
                },
                language = baiduLanguage  // ⭐ 传入方言参数
            )
        }
        _isListening.value = true
        _voiceInputText.value = ""  // ⭐ 开始录音时清空
        speechHelper?.startListening()
        Log.d("ChatViewModel", "✅ 语音识别已启动")
    }

    private fun parseVoiceResult(raw: String): String {
        return try {
            Log.d("ChatViewModel", "🔄 开始解析语音 JSON: ${raw.take(100)}...")
            val json = JSONObject(raw)
            val ws = json.optJSONArray("ws")
            if (ws == null || ws.length() == 0) {
                Log.w("ChatViewModel", "⚠️ JSON 中没有 ws 字段")
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
            Log.d("ChatViewModel", "✅ 语音解析完成: '$result'")
            result.ifEmpty { raw }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "❌ 解析语音结果异常: ${e.message}", e)
            raw
        }
    }

    fun createOrderViaWebSocket(destAddress: String, destLat: Double, destLng: Double) {
        // ⭐ 修改：不再通过 WebSocket 创建订单，改用 HTTP API
        Log.d("ChatViewModel", "=== 开始通过 HTTP API 创建订单 ===")
        Log.d("ChatViewModel", "目的地：$destAddress, lat=$destLat, lng=$destLng")
        
        // ⭐ 新增:长辈端拦截,不允许下单
        if (_isElderMode.value) {
            Log.d("ChatViewModel", "👴 长辈端检测到下单操作,立即拦截")
            addSystemMessage(
                "😊 温馨提示:\n" +
                "长辈端暂不支持直接下单叫车哦~\n" +
                "\n" +
                "如需叫车,您可以:\n" +
                "• 联系您的亲友帮忙代叫\n" +
                "• 让亲友使用他们的账号为您叫车\n" +
                "\n" +
                "💡 我还可以帮您:\n" +
                "• 查询地点信息\n" +
                "• 识别图片中的地址\n" +
                "• 提供路线建议"
            )
            return
        }
        
        viewModelScope.launch {
            _orderState.value = OrderState.Loading
            
            // ⭐ 使用 HTTP API 创建订单
            val result = orderRepository.createOrder(
                poiName = destAddress,
                poiLat = destLat,
                poiLng = destLng,
                passengerCount = 1,
                remark = null
            )
            
            Log.d("ChatViewModel", "订单创建结果：code=${result.code}, message=${result.message}")
            
            if (result.isSuccess()) {
                result.data?.let { order ->
                    Log.d("ChatViewModel", "✅ 订单创建成功：orderId=${order.id}, orderNo=${order.orderNo}")
                    _orderState.value = OrderState.Success(order)
                    addSystemMessage("✅ 订单创建成功：${order.orderNo}")
                    
                    // ⭐ 延迟后自动跳转到订单详情
                    delay(1000)
                    // 注意：这里不直接跳转，由 UI 层监听 orderState 后自动跳转
                } ?: run {
                    Log.e("ChatViewModel", "❌ 订单数据为 null")
                    _orderState.value = OrderState.Error("返回数据为空")
                    addSystemMessage("❌ 订单创建失败：返回数据为空")
                }
            } else {
                Log.e("ChatViewModel", "❌ 订单创建失败：${result.message}")
                _orderState.value = OrderState.Error(result.message ?: "未知错误")
                addSystemMessage("❌ 订单创建失败：${result.message}")
            }
        }
    }

    fun createOrder(
        poiName: String,
        poiLat: Double,
        poiLng: Double,
        passengerCount: Int = 1,
        remark: String? = null
    ) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "=== createOrder 被调用 ===")
            Log.d("ChatViewModel", "参数:poiName=$poiName, poiLat=$poiLat, poiLng=$poiLng")
            
            // ⭐ 新增:长辈端二次防护,防止绕过前置检查
            if (_isElderMode.value) {
                Log.e("ChatViewModel", "❌ 长辈端尝试调用 createOrder,拒绝执行")
                _orderState.value = OrderState.Error("长辈端不支持下单功能")
                addSystemMessage(
                    "😊 温馨提醒:\n" +
                    "长辈端无法直接下单叫车哦~\n" +
                    "请联系您的亲友帮忙代叫 🙏"
                )
                return@launch
            }
                
            // ⭐ 新增:校验 poiName 不能为空
            if (poiName.isBlank()) {
                Log.e("ChatViewModel", "❌ POI 名称为空,拒绝创建订单")
                _orderState.value = OrderState.Error("目的地名称不能为空")
                addSystemMessage("⚠️ 目的地名称缺失,请重新选择")
                return@launch
            }
                
            _orderState.value = OrderState.Loading
    
            // ⭐ 修改:不再传递起点坐标
            val result = orderRepository.createOrder(
                poiName = poiName,
                poiLat = poiLat,
                poiLng = poiLng,
                passengerCount = passengerCount,
                remark = remark
            )
                
            Log.d("ChatViewModel", "订单创建结果:code=${result.code}, message=${result.message}")
                
            if (result.isSuccess()) {
                result.data?.let { order ->
                    Log.d("ChatViewModel", "✅ 订单创建成功:orderId=${order.id}, orderNo=${order.orderNo}")
                    _orderState.value = OrderState.Success(order)
                    addSystemMessage("✅ 订单创建成功:${order.orderNo}")
                } ?: run {
                    Log.e("ChatViewModel", "❌ 订单数据为 null")
                    _orderState.value = OrderState.Error("返回数据为空")
                }
            } else {
                Log.e("ChatViewModel", "❌ 订单创建失败:${result.message}")
                _orderState.value = OrderState.Error(result.message ?: "未知错误")
                addSystemMessage("❌ 订单创建失败:${result.message}")
            }
        }
    }

    fun resetOrderState() {
        _orderState.value = OrderState.Idle
    }

    fun clearPoiList() {
        _poiList.value = emptyList()
    }

    /**
     * ⭐ 新增：添加系统消息（公开方法，供外部调用）
     */
    fun addSystemMessage(content: String) {
        val systemMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        _messages.value += systemMessage
    }

    private fun parseServerMessage(json: String) {
        try {
            // ⭐ 新增：消息去重，避免重复显示相同的消息
            if (processedMessages.contains(json)) {
                // Log.d("ChatViewModel", "⚠️ 检测到重复消息，已跳过")  // ⭐ 移除：减少日志噪音
                return
            }
            processedMessages.add(json)

            // ⭐ 限制缓存大小，避免内存泄漏
            if (processedMessages.size > 100) {
                val iterator = processedMessages.iterator()
                if (iterator.hasNext()) {
                    iterator.remove()
                }
            }

            // ⭐ 使用 ignoreUnknownKeys = true 配置来忽略未知字段
            val jsonFormat = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            
            Log.d("ChatViewModel", "🔄 开始解析 JSON...")
            val response = jsonFormat.decodeFromString<WebSocketResponse>(json)
            Log.d("ChatViewModel", "✅ JSON 解析成功")

            Log.d("ChatViewModel", "=== 收到 WebSocket 消息 ===")
            Log.d("ChatViewModel", "type=${response.type}, message=${response.message}")
            Log.d("ChatViewModel", "data=${response.data}")
            
            // ⭐ 特殊处理：图片识别响应（支持 type=image_recognition）
            val isImageRecognition = response.type?.uppercase() == "IMAGE_RECOGNITION" ||
                                     response.type?.uppercase() == "IMAGE"
            
            when {
                isImageRecognition -> {
                    // ⭐ 处理图片识别响应
                    Log.d("ChatViewModel", "=== 收到图片识别响应 ===")
                    Log.d("ChatViewModel", "type=${response.type}, success=${response.success}, message=${response.message}")
                    Log.d("ChatViewModel", "data=${response.data}")
                    
                    // ⭐ 检查是否识别失败（优先检查 success 字段）
                    val isSuccess = response.success == true
                    val hasErrorMessage = response.message?.let { msg ->
                        msg.contains("失败") || msg.contains("异常") || 
                        msg.contains("Cannot invoke") || msg.contains("null pointer")
                    } ?: false
                    
                    if (isSuccess && !hasErrorMessage) {
                        // ⭐ 成功：解析 OCR 文本和 POI 列表
                        val ocrText = response.message ?: ""
                        Log.d("ChatViewModel", "OCR 文本: $ocrText")
                        
                        // ⭐ 解析 places 数组
                        val places = extractPlaces(response, rawJson = json)
                        val poiList = if (places.isNotEmpty()) {
                            places
                        } else {
                            extractPoiList(response.data)
                        }
                        
                        if (poiList.isNotEmpty()) {
                            Log.d("ChatViewModel", "✅ 找到 ${poiList.size} 个地点")
                            _poiList.value = poiList
                            _candidates.value = poiList
                            
                            val suggestions = poiList.take(3).map { it.name }
                            val chatMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                content = response.message ?: "📷 识别成功，为您找到 ${poiList.size} 个地点",
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                suggestions = suggestions
                            )
                            _messages.value += chatMessage
                            
                            // ⭐ 弹出候选列表供用户选择
                            Log.d("ChatViewModel", "🔔 弹出候选列表对话框")
                            _showCandidatesDialog.value = true
                        } else {
                            // 未找到地址信息
                            Log.w("ChatViewModel", "⚠️ 未从图片中识别到有效地址")
                            addSystemMessage("😕 未能从图片中识别到有效地址信息")
                            if (ocrText.isNotBlank() && ocrText != "success" && !ocrText.contains("失败")) {
                                addSystemMessage("💡 识别到文字：$ocrText")
                            }
                        }
                    } else {
                        // ⭐ 失败：显示错误消息
                        Log.e("ChatViewModel", "❌ 图片识别失败: ${response.message}")
                        
                        // ⭐ 友好的错误提示（对齐后端文档 FAQ）
                        val errorMsg = when {
                            response.message?.contains("系统繁忙") == true || 
                            response.message?.contains("EXCEEDED") == true ->
                                "⚠️ 系统繁忙，请稍后再试（可能触发了频率限制）"
                            response.message?.contains("图片大小") == true ->
                                "⚠️ 图片太大，请重新选择"
                            response.message?.contains("不支持的图片格式") == true ->
                                "⚠️ 仅支持 JPEG/PNG/BMP 格式"
                            response.message?.contains("未能从图片中识别") == true ->
                                "😕 未识别到地址，请拍摄清晰的招牌或路牌"
                            response.message?.contains("Cannot invoke") == true ||
                            response.message?.contains("null") == true ->
                                "❌ 后端服务异常，请稍后再试"
                            response.message?.contains("图片识别失败") == true ->
                                "❌ 图片识别失败，请稍后再试或重新拍摄"
                            else -> "❌ ${response.message ?: "图片识别失败，请重新拍摄"}"
                        }
                        addSystemMessage(errorMsg)
                    }
                }
                                
                response.type?.uppercase() == "SEARCH" ||
                response.type?.uppercase() == "SEARCH_RESULT" ||
                response.type?.uppercase() == "POI_LIST" -> {
                    // ⭐ 修改：传递原始 JSON 以便正确解析 FastJSON 引用格式
                    val places = extractPlaces(response, rawJson = json)
                    val poiList = if (places.isNotEmpty()) {
                        places
                    } else {
                        extractPoiList(response.data)
                    }
                
                    if (poiList.isNotEmpty()) {
                        _poiList.value = poiList
                        // ⭐ 修改：同时设置候选列表
                        _candidates.value = poiList
                
                        val suggestions = poiList.take(3).map { it.name }
                        val chatMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = response.message ?: "为您找到 ${poiList.size} 个地点",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            suggestions = suggestions
                        )
                        _messages.value += chatMessage
                
                        // ⭐ 新增：弹出候选列表对话框
                        Log.d("ChatViewModel", "🔔 parseServerMessage: 需要确认，弹出候选列表")
                        _showCandidatesDialog.value = true
                    } else {
                        // ⭐ 无结果时的友好提示
                        addSystemMessage("😕 未找到相关地点，建议您换一种说法或提供更详细的信息")
                    }
                }
                
                // ⭐ 新增：处理图片识别结果（image_result）
                response.type?.uppercase() == "IMAGE_RESULT" ||
                response.type?.uppercase() == "IMAGE_RECOGNITION" -> {
                    Log.d("ChatViewModel", "📸 收到 IMAGE_RESULT/IMAGE_RECOGNITION 类型消息")
                    Log.d("ChatViewModel", "message=${response.message}")
                    Log.d("ChatViewModel", "data=${response.data}")
                    
                    // ⭐ 提取 OCR 文本
                    val ocrText = response.data?.let { dataObj ->
                        try {
                            val jsonObj = JSONObject(dataObj.toString())
                            jsonObj.optString("ocrText", null)
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "❌ 提取 OCR 文本失败", e)
                            null
                        }
                    }
                    
                    // ⭐ 提取地点列表
                    val places = extractPlaces(response, rawJson = json)
                    val poiList = if (places.isNotEmpty()) {
                        places
                    } else {
                        extractPoiList(response.data)
                    }
                    
                    // ⭐ 构建回复消息
                    val replyMessage = buildString {
                        if (!ocrText.isNullOrBlank()) {
                            append("📷 **图片识别结果：**\n")
                            append("识别文字：$ocrText\n\n")
                        }
                        
                        if (!response.message.isNullOrBlank()) {
                            append("${response.message}\n\n")
                        }
                        
                        if (poiList.isNotEmpty()) {
                            append("📍 找到 ${poiList.size} 个相关地点：\n\n")
                            poiList.take(3).forEachIndexed { index, poi ->
                                append("${index + 1}. **${poi.name}**\n")
                                if (!poi.address.isNullOrBlank()) {
                                    append("   🏠 地址：${poi.address}\n")
                                }
                                if (poi.distance != null) {
                                    append("   📏 距离：${String.format("%.1f", poi.distance / 1000)}公里\n")
                                }
                                if (index < 2) append("\n")
                            }
                            
                            if (poiList.size > 3) {
                                append("\n💡 还有 ${poiList.size - 3} 个结果，请点击查看详情")
                            }
                        } else {
                            append("💡 您可以：\n")
                            append("• 点击输入框上方的地点名称直接下单\n")
                            append("• 或者继续提问，我会帮您查找")
                        }
                    }
                    
                    // ⭐ 显示消息
                    val chatMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = replyMessage,
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        suggestions = poiList.take(3).map { it.name }
                    )
                    _messages.value += chatMessage
                    
                    // ⭐ 更新 POI 列表和候选列表
                    if (poiList.isNotEmpty()) {
                        _poiList.value = poiList
                        _candidates.value = poiList
                        
                        // ⭐ 修复：图片识别有多个结果时，始终弹出候选列表对话框供用户选择
                        Log.d("ChatViewModel", "🔔 图片识别结果：${poiList.size} 个地点，弹出候选列表对话框")
                        _showCandidatesDialog.value = true
                    }
                    
                    // ⭐ 语音播报
                    speak(if (poiList.isNotEmpty()) "找到 ${poiList.size} 个相关地点" else "识别完成")
                }
                
                response.type?.uppercase() == "CHAT" -> {
                    // ⭐ AI 聊天回复
                    if (!response.message.isNullOrBlank()) {
                        // ⭐ 新增：检测是否是沉默提示消息（重复内容不显示）
                        val isSilencePrompt = response.message.contains("我可以帮你找") ||
                                response.message.contains("请告诉我目的地")
                
                        if (isSilencePrompt) {
                            // 检查最近一条消息是否相同
                            val lastMessage = _messages.value.lastOrNull()
                            if (lastMessage?.content == response.message) {
                                Log.d("ChatViewModel", "⚠️ 检测到重复的沉默提示，已跳过")
                                return  // 不显示重复的提示
                            }
                        }
                
                        val chatMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = response.message,
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _messages.value += chatMessage
                    }
                }
                
                response.type?.uppercase() == "ORDER" -> {
                    // ⭐ 订单创建响应
                    Log.d("ChatViewModel", "=== 收到 ORDER 类型消息 ===")
                    Log.d("ChatViewModel", "response.message=${response.message}")
                    Log.d("ChatViewModel", "response.data=${response.data}")
                                    
                    addSystemMessage(response.message ?: "✅ 订单创建成功")
                
                    try {
                        val dataObj = response.data?.toString()
                        if (dataObj != null) {
                            val orderJson = JSONObject(dataObj)
                            val orderNo = orderJson.optString("orderNo", null)
                            val orderId = orderJson.optLong("orderId", -1L)
                            if (orderId != -1L) {
                                // ⭐ 触发跳转订单详情
                                Log.d("ChatViewModel", "✅ 订单创建成功，orderId=$orderId, orderNo=$orderNo")
                                _orderState.value = OrderState.Success(
                                    Order(
                                        id = orderId,
                                        orderNo = orderNo ?: "",
                                        status = orderJson.optInt("status", 0),
                                        userId = orderJson.optLong("userId", 0),
                                        driverId = if (orderJson.isNull("driverId")) null else orderJson.optLong("driverId"),
                                        destLat = orderJson.optDouble("destLat", 0.0),
                                        destLng = orderJson.optDouble("destLng", 0.0),
                                        poiName = orderJson.optString("poiName", ""),
                                        destAddress = orderJson.optString("destAddress", ""),
                                        platformUsed = orderJson.optString("platformUsed", null),
                                        platformOrderId = if (orderJson.isNull("platformOrderId")) null else orderJson.optString("platformOrderId"),
                                        estimatePrice = orderJson.optDouble("estimatePrice", 0.0),
                                        actualPrice = if (orderJson.isNull("actualPrice")) null else orderJson.optDouble("actualPrice"),
                                        createTime = orderJson.optString("createTime", ""),
                                        remark = if (orderJson.isNull("remark")) null else orderJson.optString("remark")
                                    )
                                )
                            } else {
                                Log.e("ChatViewModel", "❌ orderId 无效或不存在")
                            }
                        } else {
                            Log.e("ChatViewModel", "❌ response.data 为 null")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "❌ 提取订单信息失败", e)
                        addSystemMessage("⚠️ 订单数据处理失败：${e.message}")
                    }
                    
                    // ⭐ 重要：ORDER 类型处理完成后直接返回，避免进入 else 分支
                    return
                }
                
                response.type?.uppercase() == "ROUTE" -> {
                    // ⭐ 路线规划响应
                    addSystemMessage(response.message ?: "🗺️ 路线规划完成")
                    // 如果有 route 数据，可以显示详细信息
                }
                
                response.type?.uppercase() == "ERROR" -> {
                    // ⭐ 错误响应
                    addSystemMessage("❌ ${response.message}")
                }
                
                response.type?.lowercase() == "error" -> {
                    addSystemMessage("❌ 错误：${response.message}")
                }
                
                response.type?.uppercase() == "GUARD_PUSH" -> {
                    // ⭐ 亲情守护推送（代叫车通知等）
                    Log.d("ChatViewModel", "📩 收到 GUARD_PUSH 类型消息")
                    Log.d("ChatViewModel", "原始JSON: $json")
                    handleGuardPushMessage(json)
                    // ⭐ 重要：处理完成后直接返回，不进入 else 分支
                    return
                }
                
                // ⭐ 修复：直接检测亲情守护推送类型（后端可能直接发送 FAVORITE_SHARED/NEW_ORDER 等）
                response.type?.uppercase() == "FAVORITE_SHARED" ||
                response.type?.uppercase() == "NEW_ORDER" ||
                response.type?.uppercase() == "ORDER_CREATED" ||
                response.type?.uppercase() == "CHAT_MESSAGE" ||
                response.type?.uppercase() == "ORDER_ACCEPTED" ||
                response.type?.uppercase() == "PROXY_ORDER_CONFIRMED" -> {
                    Log.d("ChatViewModel", "📩 收到亲情守护推送（直接类型）：${response.type}")
                    Log.d("ChatViewModel", "原始JSON: $json")
                    handleGuardPushMessage(json)
                    return
                }

                else -> {
                    // ⭐ 其他类型消息（如 ping 响应、未知类型等）
                    // 仅在 message 非空且不是心跳响应时显示
                    if (!response.message.isNullOrBlank() &&
                        response.type?.lowercase() != "pong"
                    ) {
                        val chatMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = response.message,
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _messages.value += chatMessage
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "❌ 解析消息失败: ${e.message}", e)
            Log.e("ChatViewModel", "原始消息: $json")
            
            // ⭐ 显示错误消息给用户
            addSystemMessage("⚠️ 消息解析失败：${e.message}")
            
            // ⭐ 仍然将原始消息显示出来，方便调试
            val chatMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "[解析失败] $json",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            _messages.value += chatMessage
        }
    }

    // ⭐ 修改:用户选择候选地点(使用 HTTP API)
    fun selectCandidate(poi: PoiData) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "=== 用户选择候选地点 ===")
            Log.d("ChatViewModel", "选择:${poi.name}")
    
            // ⭐ 新增:长辈端直接拦截,不允许进入下单流程
            if (_isElderMode.value) {
                Log.d("ChatViewModel", "👴 长辈端检测到下单操作,立即拦截")
                addSystemMessage(
                    "😊 温馨提示:\n" +
                    "长辈端暂不支持直接下单叫车哦~\n" +
                    "\n" +
                    "如需叫车,您可以:\n" +
                    "• 联系您的亲友帮忙代叫\n" +
                    "• 让亲友使用他们的账号为您叫车\n" +
                    "\n" +
                    "💡 我还可以帮您:\n" +
                    "• 查询地点信息\n" +
                    "• 识别图片中的地址\n" +
                    "• 提供路线建议"
                )
                _showCandidatesDialog.value = false
                return@launch
            }
    
            // 添加用户确认消息到聊天历史
            val userConfirmMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "✅ 确认选择:${poi.name}",
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            _messages.value += userConfirmMessage
    
            // ⭐ 关闭对话框
            _showCandidatesDialog.value = false
            _candidates.value = emptyList()
    
            // ⭐ 使用 HTTP API 确认选择
            val lat = currentLat
            val lng = currentLng
            if (lat == null || lng == null) {
                addSystemMessage("⚠️ 位置信息获取中...")
                return@launch
            }
    
            val result = agentRepository.confirmSelection(
                sessionId = sessionId.value,
                selectedPoiName = poi.name,
                lat = lat,
                lng = lng
            )
    
            if (result.isSuccess()) {
                val response = result.data
                Log.d("ChatViewModel", "=== 收到确认响应 ===")
                Log.d("ChatViewModel", "type=${response?.type}, message=${response?.message}")
                                
                when (response?.type) {
                    "ORDER" -> {
                        addSystemMessage(response.message ?: "已确认目的地")
                        // ⭐ 直接从 response 中获取 poi 信息
                        response.poi?.let { poi ->
                            Log.d("ChatViewModel", "POI: name=${poi.name}, lat=${poi.lat}, lng=${poi.lng}")
                            Log.d("ChatViewModel", "创建订单:${poi.name}")
                            createOrder(poi.name ?: "", poi.lat, poi.lng, 1, null)
                        } ?: run {
                            Log.e("ChatViewModel", "❌ poi 为 null")
                            addSystemMessage("⚠️ 目的地信息缺失")
                        }
                    }
                                    
                    else -> {
                        addSystemMessage(response?.message ?: "确认成功")
                    }
                }
            } else {
                Log.e("ChatViewModel", "确认失败:${result.message}")
                addSystemMessage("❌ 确认失败:${result.message}")
            }
        }
    }

    // ⭐ 新增：关闭候选列表对话框
    fun dismissCandidatesDialog() {
        _showCandidatesDialog.value = false
    }

    // ⭐ 修改：增强 JSON 解析的健壮性 - 静默处理，不弹错误提示
    private fun extractPoiList(data: JsonElement?): List<PoiData> {
        return try {
            // ⭐ 新增：空值检查
            if (data == null || data.toString() == "null") {
                Log.d("ChatViewModel", "收到 null 数据，返回空列表")
                return emptyList()
            }

            val jsonString = data.toString()

            // ⭐ 新增：格式检查（不是数组则直接返回空）
            if (!jsonString.startsWith("[")) {
                Log.d("ChatViewModel", "数据格式不是数组，将使用其他方法解析")
                return emptyList()
            }

            val jsonArray = JSONArray(jsonString)
            val resultList = mutableListOf<PoiData>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val item = jsonArray.getJSONObject(i)
                    val poiData = PoiData(
                        id = item.optString(
                            "id",
                            "poi_${System.currentTimeMillis()}_$i"
                        ),  // ⭐ 新增：生成或获取 ID
                        name = item.optString("name", "未知地点"),
                        lat = item.optDouble("lat", 0.0),
                        lng = item.optDouble("lng", 0.0),
                        address = item.optString("address", ""),
                        distance = item.optDouble("distance", 0.0),
                        type = item.optString("type", null),  // ⭐ 新增：类型
                        duration = item.optInt("duration", 0).takeIf { it > 0 },  // ⭐ 新增：耗时
                        price = item.optDouble("price", 0.0).takeIf { it > 0 }  // ⭐ 新增：价格
                    )
                    resultList.add(poiData)
                } catch (e: Exception) {
                    // ⭐ 静默处理单个 POI 解析失败
                    Log.d("ChatViewModel", "解析单个 POI 失败，跳过第 $i 项")
                }
            }

            Log.d("ChatViewModel", "解析到 ${resultList.size} 个 POI")
            resultList

        } catch (e: Exception) {
            // ⭐ 捕获所有异常，避免崩溃，返回空列表
            Log.e("ChatViewModel", "解析 POI 列表失败", e)
            emptyList()
        }
    }

    // ⭐ 修改：从 places 字段提取 POI 列表（对齐后端文档）- 静默处理，不弹错误提示
    private fun extractPlaces(response: WebSocketResponse, rawJson: String? = null): List<PoiData> {
        return try {
            // ⭐ 新增：优先从顶层 places 字段提取（图片识别 API v2.0）
            if (response.places != null && response.places.isNotEmpty()) {
                Log.d("ChatViewModel", "✅ 从顶层 places 字段提取到 ${response.places.size} 个 POI")
                return response.places.map { poi ->
                    PoiData(
                        id = poi.id ?: "",
                        name = poi.name ?: "",
                        address = poi.address ?: "",
                        lat = poi.lat,
                        lng = poi.lng,
                        distance = poi.distance,
                        type = poi.type ?: "",
                        duration = poi.duration,
                        price = poi.price,
                        score = poi.score
                    )
                }
            }
            
            // ⭐ 修改：优先从原始 JSON 消息中提取 places 字段（处理 FastJSON 引用格式）
            if (rawJson != null && rawJson.contains("\$ref")) {
                Log.d("ChatViewModel", "🔍 检测到 JSON 引用格式，尝试从原始 JSON 提取 places")

                try {
                    val jsonFormat = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }

                    // 将原始 JSON 解析为 JsonObject
                    val fullJson =
                        jsonFormat.decodeFromString<kotlinx.serialization.json.JsonObject>(rawJson)

                    // ⭐ 直接从根级别获取 places 数组（FastJSON/Jackson 的引用格式）
                    val placesArray = fullJson.get("places")
                    if (placesArray != null) {
                        Log.d("ChatViewModel", "✅ 从原始 JSON 的 places 字段提取到数据")
                        val poiList =
                            jsonFormat.decodeFromString<List<PoiResponse>>(placesArray.toString())
                        if (poiList.isNotEmpty()) {
                            return poiList.map { poi ->
                                PoiData(
                                    id = poi.id ?: "",
                                    name = poi.name ?: "",
                                    address = poi.address ?: "",
                                    lat = poi.lat,
                                    lng = poi.lng,
                                    distance = poi.distance,
                                    type = poi.type ?: "",
                                    duration = poi.duration,
                                    price = poi.price,
                                    score = poi.score
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("ChatViewModel", "从原始 JSON 提取 places 失败：${e.message}")
                }
            }

            // ⭐ 新增：从 data.places 提取（后端文档格式）
            try {
                val dataObj = response.data?.toString()
                if (dataObj != null && dataObj != "null" && dataObj != "{}") {
                    Log.d("ChatViewModel", "🔍 解析 data 字段：$dataObj")

                    val jsonFormat = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }

                    // 解析为 JsonObject 获取 places 数组
                    val dataJson = jsonFormat.decodeFromString<kotlinx.serialization.json.JsonObject>(dataObj)
                    val placesArray = dataJson.get("places")
                    
                    if (placesArray != null) {
                        Log.d("ChatViewModel", "✅ 从 data.places 提取到数据")
                        val poiList = jsonFormat.decodeFromString<List<PoiResponse>>(placesArray.toString())
                        if (poiList.isNotEmpty()) {
                            return poiList.map { poi ->
                                PoiData(
                                    id = poi.id ?: "",
                                    name = poi.name ?: "",
                                    address = poi.address ?: "",
                                    lat = poi.lat,
                                    lng = poi.lng,
                                    distance = poi.distance,
                                    type = poi.type ?: "",
                                    duration = poi.duration,
                                    price = poi.price,
                                    score = poi.score
                                )
                            }
                        }
                    } else if (dataObj.contains("ocrText") && !dataObj.contains("places")) {
                        Log.d("ChatViewModel", "⚠️ data 中包含 ocrText 但没有 places 字段")
                        // 只有 OCR 文本，没有识别到地址
                        return emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.d("ChatViewModel", "解析 data.places 失败：${e.message}")
            }

            // 降级：尝试从 data 字段提取
            val dataObj = response.data?.toString()
            if (dataObj != null && dataObj != "null" && dataObj != "{}") {
                Log.d("ChatViewModel", "🔍 降级解析 data 字段：$dataObj")

                val jsonFormat = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }

                // 尝试解析为 AgentSearchResponse 格式
                try {
                    val agentResponse = jsonFormat.decodeFromString<AgentSearchResponse>(dataObj)
                    val places = agentResponse.places ?: emptyList()
                    Log.d("ChatViewModel", "从 AgentSearchResponse 提取到 ${places.size} 个 POI")
                    return places.map { poi ->
                        PoiData(
                            id = poi.id ?: "",
                            name = poi.name ?: "",
                            address = poi.address ?: "",
                            lat = poi.lat,
                            lng = poi.lng,
                            distance = poi.distance,
                            type = poi.type ?: "",
                            duration = poi.duration,
                            price = poi.price,
                            score = poi.score
                        )
                    }
                } catch (e: Exception) {
                    Log.d("ChatViewModel", "解析为 AgentSearchResponse 失败")
                }

                // 尝试直接将 data 解析为 POI 列表
                try {
                    val poiList = jsonFormat.decodeFromString<List<PoiResponse>>(dataObj)
                    if (poiList.isNotEmpty()) {
                        Log.d("ChatViewModel", "✅ 直接解析 data 到 ${poiList.size} 个 POI")
                        return poiList.map { poi ->
                            PoiData(
                                id = poi.id ?: "",
                                name = poi.name ?: "",
                                address = poi.address ?: "",
                                lat = poi.lat,
                                lng = poi.lng,
                                distance = poi.distance,
                                type = poi.type ?: "",
                                duration = poi.duration,
                                price = poi.price,
                                score = poi.score
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.d("ChatViewModel", "直接解析 data 失败")
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "提取 places 异常", e)
            emptyList()
        }
    }

    // ⭐ 保持 WebSocket 长连接
    private var disconnectJob: Job? = null

    override fun onCleared() {
        Log.d("ChatViewModel", "=== onCleared() 被调用 ===")
        Log.d("ChatViewModel", "保持 WebSocket 连接")

        // 不清断 WebSocket，保持连接
        disconnectJob?.cancel()

        // 只清理语音助手
        speechHelper?.destroy()
        
        // ⭐ 清理 TTS
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onCleared()
    }

    // ⭐ 新增：显式的断开方法，供退出登录时调用
    fun disconnectWebSocket() {
        Log.d("ChatViewModel", "=== 主动断开 WebSocket ===")
        disconnectJob?.cancel()
        webSocketClient.disconnect()
        speechHelper?.destroy()
    }

    sealed class OrderState {
        object Idle : OrderState()
        object Loading : OrderState()
        data class Success(val order: Order) : OrderState()
        data class Error(val message: String) : OrderState()
    }

    // ⭐ 新增：图片压缩函数（强制压缩到指定大小以内）
    // ⭐ 修改：公开方法，供 ChatScreen 调用
    suspend fun compressAndToBase64(source: Bitmap, maxSizeKB: Int = 500): String {
        return withContext(Dispatchers.IO) {
            var bitmap = source
            
            Log.d("ImageCompression", "========== 图片压缩信息 ==========")
            // 计算Bitmap大小（兼容所有API级别）
            val sourceSizeKB = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                source.allocationByteCount / 1024
            } else {
                source.byteCount / 1024
            }
            Log.d("ImageCompression", "原始文件大小: ${sourceSizeKB} KB")
            Log.d("ImageCompression", "原始分辨率: ${source.width}x${source.height}")
            
            // 第 1 步：缩小分辨率（最大 1280x1280，提升清晰度）
            val maxWidth = 1280
            val maxHeight = 1280
            var width = bitmap.width
            var height = bitmap.height
            
            if (width > maxWidth || height > maxHeight) {
                val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
                width = (width * ratio).toInt()
                height = (height * ratio).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                Log.d("ImageCompression", "缩放分辨率：${source.width}x${source.height} -> ${width}x${height}")
            }
            
            // 第 2 步：降低 JPEG 质量，直到满足大小要求
            var quality = 85  // ⭐ 提升初始质量（从 70 提升到 85）
            var outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            while (outputStream.toByteArray().size > maxSizeKB * 1024 && quality > 30) {  // ⭐ 最低质量从 10 提升到 30
                outputStream.reset()
                quality -= 5  // ⭐ 每次降低 5（更精细的控制）
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                Log.d("ImageCompression", "降低质量：quality=$quality, size=${outputStream.size() / 1024}KB")
            }
            
            // 打印调试信息
            val finalSize = outputStream.toByteArray().size
            Log.d("ImageCompression", "最终大小: ${finalSize / 1024} KB, 质量: $quality")
            
            // 第 3 步：转 Base64
            val byteArray = outputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            
            Log.d("ImageCompression", "压缩后 Base64 长度: ${base64.length}")
            Log.d("ImageCompression", "压缩后 Base64 大小: ${base64.length / 1024} KB")
            Log.d("ImageCompression", "====================================")
            
            "data:image/jpeg;base64,$base64"
        }
    }
    
    // ⭐ 保留旧方法用于兼容
    private fun compressImage(source: Bitmap): Bitmap {
        // ⭐ OCR 场景：最大 1920x1080，保持清晰度
        val maxWidth = 1920
        val maxHeight = 1080
            
        var width = source.width
        var height = source.height
            
        // ⭐ 计算缩放比例
        var scale = 1.0f
        if (width > maxWidth || height > maxHeight) {
            scale = minOf(
                maxWidth.toFloat() / width,
                maxHeight.toFloat() / height
            )
            width = (width * scale).toInt()
            height = (height * scale).toInt()
        }
            
        // ⭐ 缩放图片
        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(source, width, height, true)
        } else {
            source
        }
            
        return scaledBitmap
    }
    
    // ⭐ 新增：辅助函数 - 显示消息
    private fun showMessage(message: String?) {
        if (!message.isNullOrBlank()) {
            addSystemMessage(message)
        }
    }
    
    // ⭐ 新增：辅助函数 - 显示错误
    private fun showError(message: String) {
        addSystemMessage("❌ $message")
    }
    
    // ⭐ 新增：辅助函数 - 语音播报（使用系统 TTS）
    fun initTTS(context: Context) {
        if (textToSpeech != null) return
        
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("ChatViewModel", "❌ TTS 不支持中文")
                    ttsInitialized = false
                } else {
                    Log.d("ChatViewModel", "✅ TTS 初始化成功")
                    ttsInitialized = true
                }
            } else {
                Log.e("ChatViewModel", "❌ TTS 初始化失败")
                ttsInitialized = false
            }
        }
    }
    
    private fun speak(text: String) {
        if (!ttsInitialized || textToSpeech == null) {
            Log.w("ChatViewModel", "⚠️ TTS 未初始化，跳过语音播报")
            return
        }
        
        try {
            // ⭐ 长辈模式下才播报
            if (_isElderMode.value) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                Log.d("ChatViewModel", "🔊 语音播报: $text")
            } else {
                Log.d("ChatViewModel", "ℹ️ 非长辈模式，跳过语音播报")
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "❌ 语音播报异常", e)
        }
    }
    
    /**
     * ⭐ 处理亲情守护推送消息（NEW_ORDER / CHAT_MESSAGE）
     */
    private fun handleGuardPushMessage(json: String) {
        viewModelScope.launch {
            try {
                val jsonFormat = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
                
                // ⭐ 关键修复：先解析外层结构，提取 data 字段
                val outerJson = jsonFormat.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject
                if (outerJson == null) {
                    Log.e("ChatViewModel", "❌ JSON 解析失败：不是 JsonObject 类型")
                    return@launch
                }
                
                val type = outerJson["type"]?.let { 
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" 
                } ?: ""
                val dataElement = outerJson["data"]
                
                Log.d("ChatViewModel", "📩 收到亲情守护推送：type=$type")
                Log.d("ChatViewModel", "🔍 [调试] data元素存在: ${dataElement != null}")
                
                // ⭐ 如果有 data 字段，需要将 data 中的字段合并到顶层
                val pushMessage = if (dataElement != null && dataElement is kotlinx.serialization.json.JsonObject) {
                    // 将 data 对象和 type 合并为一个新的 JsonObject
                    val mutableMap = dataElement.toMutableMap()
                    mutableMap["type"] = kotlinx.serialization.json.JsonPrimitive(type)
                    
                    // 添加其他顶层字段（如果有的话）
                    outerJson["message"]?.let { msg -> mutableMap["message"] = msg }
                    outerJson["success"]?.let { success -> mutableMap["success"] = success }
                    
                    val mergedJson = kotlinx.serialization.json.JsonObject(mutableMap).toString()
                    Log.d("ChatViewModel", "🔍 [调试] 合并后的JSON前100字符: ${mergedJson.take(100)}...")
                    
                    // 解析合并后的 JSON
                    jsonFormat.decodeFromString<GuardPushMessage>(mergedJson)
                } else {
                    // 没有 data 字段，直接解析（兼容旧格式）
                    Log.d("ChatViewModel", "🔍 [调试] 没有 data 字段，直接解析")
                    jsonFormat.decodeFromString<GuardPushMessage>(json)
                }
                
                Log.d("ChatViewModel", "📩 解析结果：orderId=${pushMessage.orderId}, requester=${pushMessage.proxyUserName}, dest=${pushMessage.destAddress}")
                
                when (pushMessage.type) {
                    "NEW_ORDER", "ORDER_CREATED" -> {  // ⭐ 修复：兼容两种消息类型
                        // ⭐ 代叫车请求通知
                        Log.d("ChatViewModel", "🚗 收到代叫车请求：orderId=${pushMessage.orderId}, requester=${pushMessage.proxyUserName}, dest=${pushMessage.destAddress}")
                        
                        // ⭐ 关键修复：更新 sharedLocation 中的 orderId
                        val currentSharedLocation = _sharedLocation.value
                        if (currentSharedLocation != null && pushMessage.orderId != null) {
                            val updatedLocation = currentSharedLocation.copy(
                                orderId = pushMessage.orderId
                            )
                            _sharedLocation.value = updatedLocation
                            Log.d("ChatViewModel", "✅ 已更新 sharedLocation 的 orderId: ${pushMessage.orderId}")
                            
                            // ⭐ 同时持久化保存 orderId 到 SharedPreferences
                            try {
                                viewModelScope.launch {
                                    val appInstance = MyApplication.instance
                                    val prefs = appInstance.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putLong("orderId_${currentSharedLocation.elderId}", pushMessage.orderId)
                                        .apply()
                                    Log.d("ChatViewModel", "💾 [持久化] 已保存 orderId 到本地缓存")
                                }
                            } catch (e: Exception) {
                                Log.e("ChatViewModel", "❌ [持久化] 保存 orderId 失败", e)
                            }
                        } else {
                            Log.w("ChatViewModel", "⚠️ 无法更新 orderId: sharedLocation=$currentSharedLocation, orderId=${pushMessage.orderId}")
                        }
                        
                        // ⭐ 修复：在协程中发送全局事件
                        viewModelScope.launch {
                            MyApplication.sendProxyOrderRequest(
                                orderId = pushMessage.orderId ?: 0L,
                                requesterName = pushMessage.proxyUserName ?: "亲友",
                                destination = pushMessage.destAddress ?: "未知目的地"
                            )
                            Log.d("ChatViewModel", "✅ 已发送全局代叫车请求事件")
                        }
                        
                        // ⭐ 新增：如果是长辈端，也需要更新 sharedLocation（用于卡片显示）
                        if (_isElderMode.value) {
                            Log.d("ChatViewModel", "👴 长辈端收到 NEW_ORDER，更新 sharedLocation")
                            
                            // ⭐ 关键修复：从 pushMessage.userId 获取长辈ID（后端返回的字段）
                            val elderId = pushMessage.userId ?: com.example.myapplication.MyApplication.tokenManager.getUserId() ?: 0L
                            
                            // ⭐ 关键修复：创建 SharedLocationInfo 并更新 StateFlow
                            val sharedInfo = SharedLocationInfo(
                                elderId = elderId,
                                elderName = pushMessage.proxyUserName ?: "亲友",
                                favoriteName = pushMessage.poiName ?: pushMessage.destAddress ?: "未知目的地",
                                favoriteAddress = pushMessage.destAddress ?: "",
                                latitude = pushMessage.destLat ?: 0.0,
                                longitude = pushMessage.destLng ?: 0.0,
                                elderCurrentLat = pushMessage.startLat,  // ⭐ 长辈当前位置作为起点
                                elderCurrentLng = pushMessage.startLng,
                                elderLocationTimestamp = System.currentTimeMillis(),
                                orderId = pushMessage.orderId,  // ⭐ 保存订单 ID
                                orderStatus = 0  // 0-待确认
                            )
                            _sharedLocation.value = sharedInfo
                            
                            Log.d("ChatViewModel", "✅ [长辈端] sharedLocation 已更新：orderId=${pushMessage.orderId}")
                            Log.d("ChatViewModel", "✅ [长辈端] 目的地：${sharedInfo.favoriteName}")
                            Log.d("ChatViewModel", "✅ [长辈端] 起点：lat=${sharedInfo.elderCurrentLat}, lng=${sharedInfo.elderCurrentLng}")
                            
                            // ⭐ 关键修复：发送 orderCreatedEvent，确保其他组件也能收到通知
                            viewModelScope.launch {
                                val elderId = sharedInfo.elderId
                                val orderId = pushMessage.orderId ?: 0L
                                MyApplication.sendOrderCreatedEvent(orderId, elderId)
                                Log.d("ChatViewModel", "📤 [长辈端] 已发送 orderCreatedEvent: orderId=$orderId, elderId=$elderId")
                            }
                            
                            // ⭐ 持久化保存
                            viewModelScope.launch {
                                try {
                                    Log.d("ChatViewModel", "💾 [长辈端] 开始持久化保存...")
                                    val prefs = MyApplication.instance.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                                    
                                    prefs.edit()
                                        .putLong("elderId_${sharedInfo.elderId}", sharedInfo.elderId)
                                        .putString("elderName_${sharedInfo.elderId}", sharedInfo.elderName)
                                        .putString("favoriteName_${sharedInfo.elderId}", sharedInfo.favoriteName)
                                        .putString("favoriteAddress_${sharedInfo.elderId}", sharedInfo.favoriteAddress)
                                        .putFloat("latitude_${sharedInfo.elderId}", sharedInfo.latitude.toFloat())
                                        .putFloat("longitude_${sharedInfo.elderId}", sharedInfo.longitude.toFloat())
                                        .apply()
                                    
                                    Log.d("ChatViewModel", "💾 [长辈端] 基本信息保存成功")
                                    
                                    if (sharedInfo.elderCurrentLat != null && sharedInfo.elderCurrentLng != null) {
                                        prefs.edit()
                                            .putFloat("elderCurrentLat_${sharedInfo.elderId}", sharedInfo.elderCurrentLat.toFloat())
                                            .putFloat("elderCurrentLng_${sharedInfo.elderId}", sharedInfo.elderCurrentLng.toFloat())
                                            .putLong("elderLocationTimestamp_${sharedInfo.elderId}", sharedInfo.elderLocationTimestamp ?: 0L)
                                            .putLong("orderId_${sharedInfo.elderId}", sharedInfo.orderId ?: 0L)
                                            .apply()
                                        Log.d("ChatViewModel", "💾 [长辈端] 长辈位置和 orderId 保存成功")
                                    }
                                    
                                    // ⭐ 验证保存结果
                                    val verifyPrefs = MyApplication.instance.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                                    val allKeys = verifyPrefs.all.keys
                                    Log.d("ChatViewModel", "💾 [长辈端] 验证：SharedPreferences 中共有 ${allKeys.size} 个键")
                                    Log.d("ChatViewModel", "💾 [长辈端] 验证：所有键 = $allKeys")
                                    
                                    Log.d("ChatViewModel", "✅ [长辈端] 已持久化保存 sharedLocation")
                                } catch (e: Exception) {
                                    Log.e("ChatViewModel", "❌ [长辈端] 持久化失败", e)
                                }
                            }
                        }
                    }
                    
                    "ORDER_ACCEPTED", "PROXY_ORDER_CONFIRMED" -> {
                        // ⭐ 新增：长辈同意代叫车请求
                        val orderId = pushMessage.orderId
                        Log.d("ChatViewModel", "✅ 收到订单确认消息：type=${pushMessage.type}, orderId=$orderId")
                        
                        // ⭐ 关键修复：如果是长辈端，需要创建 sharedLocation 并持久化
                        if (_isElderMode.value && orderId != null) {
                            Log.d("ChatViewModel", "👴 [长辈端] 收到 PROXY_ORDER_CONFIRMED，创建 sharedLocation")
                            
                            // 尝试从之前的 NEW_ORDER 消息中获取目的地信息
                            val currentSharedLocation = _sharedLocation.value
                            
                            if (currentSharedLocation != null && currentSharedLocation.orderId == orderId) {
                                // 已有 sharedLocation，只更新状态
                                val updatedLocation = currentSharedLocation.copy(
                                    orderStatus = 1  // 1-已同意
                                )
                                _sharedLocation.value = updatedLocation
                                Log.d("ChatViewModel", "✅ 已更新订单状态为：已同意")
                            } else {
                                // 没有 sharedLocation，创建一个基本的（用于显示卡片）
                                Log.w("ChatViewModel", "⚠️ 未找到 sharedLocation，创建基本记录")
                                
                                // ⭐ 关键修复：从 pushMessage.userId 获取长辈ID
                                val elderId = pushMessage.userId ?: com.example.myapplication.MyApplication.tokenManager.getUserId() ?: 0L
                                val elderName = "我"
                                
                                val sharedInfo = SharedLocationInfo(
                                    elderId = elderId,
                                    elderName = elderName,
                                    favoriteName = "代叫车行程",  // 默认名称
                                    favoriteAddress = "",
                                    latitude = 0.0,
                                    longitude = 0.0,
                                    elderCurrentLat = null,
                                    elderCurrentLng = null,
                                    elderLocationTimestamp = null,
                                    orderId = orderId,
                                    orderStatus = 1  // 已同意
                                )
                                _sharedLocation.value = sharedInfo
                                Log.d("ChatViewModel", "✅ [长辈端] 已创建 sharedLocation: orderId=$orderId")
                                
                                // ⭐ 持久化保存
                                viewModelScope.launch {
                                    try {
                                        val prefs = MyApplication.instance.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putLong("elderId_${elderId}", elderId)
                                            .putString("elderName_${elderId}", elderName)
                                            .putString("favoriteName_${elderId}", "代叫车行程")
                                            .putLong("orderId_${elderId}", orderId)
                                            .apply()
                                        Log.d("ChatViewModel", "💾 [长辈端] 已持久化保存 sharedLocation")
                                    } catch (e: Exception) {
                                        Log.e("ChatViewModel", "❌ [长辈端] 持久化失败", e)
                                    }
                                }
                            }
                        } else {
                            // 亲友端：只更新状态
                            val currentSharedLocation = _sharedLocation.value
                            if (currentSharedLocation != null && currentSharedLocation.orderId == orderId) {
                                val updatedLocation = currentSharedLocation.copy(
                                    orderStatus = 1  // 1-已同意
                                )
                                _sharedLocation.value = updatedLocation
                                Log.d("ChatViewModel", "✅ 已更新订单状态为：已同意")
                            } else {
                                Log.w("ChatViewModel", "⚠️ 未找到对应的 sharedLocation，orderId=$orderId")
                            }
                        }
                        
                        // ⭐ 关键修复：亲友端收到确认后，跳转到行程追踪界面
                        if (!_isElderMode.value && orderId != null) {
                            Log.d("ChatViewModel", "🚀 [亲友端] 长辈已确认，准备跳转到行程追踪: orderId=$orderId")
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(500)  // 延迟 500ms，让 UI 动画完成
                                MyApplication.sendNavigateToOrderTracking(orderId)
                                Log.d("ChatViewModel", "🚀 [亲友端] 已发送导航事件: orderId=$orderId")
                            }
                        } else {
                            Log.d("ChatViewModel", "⏭️ [长辈端] 忽略 PROXY_ORDER_CONFIRMED（长辈不需要跳转）")
                        }
                    }
                    
                    "CHAT_MESSAGE" -> {
                        // ⭐ 聊天消息通知（订单内聊天）
                        Log.d("ChatViewModel", "💬 收到订单聊天消息：senderId=${pushMessage.senderId}, content=${pushMessage.content}")
                        
                        // TODO: 如果当前在订单追踪页面，可以显示聊天消息
                        // 目前先记录日志
                    }
                    
                    "FAVORITE_SHARED" -> {
                        // ⭐ 新增：处理长辈分享的收藏地点
                        Log.d("ChatViewModel", "📍 收到长辈分享的收藏地点：${pushMessage.favoriteName}")
                        
                        // ⭐ 关键修复：优先使用 userId，兼容后端字段名
                        val elderId = pushMessage.userId ?: pushMessage.elderUserId ?: pushMessage.senderId ?: 0L
                        val elderName = pushMessage.proxyUserName ?: "长辈"
                        val favoriteName = pushMessage.favoriteName ?: "未知地点"
                        val favoriteAddress = pushMessage.favoriteAddress ?: ""
                        val favoriteLat = pushMessage.favoriteLatitude ?: 0.0
                        val favoriteLng = pushMessage.favoriteLongitude ?: 0.0
                        
                        // ⭐ 新增：获取长辈实时位置（作为代叫车起点）
                        val elderCurrentLat = pushMessage.elderCurrentLat
                        val elderCurrentLng = pushMessage.elderCurrentLng
                        val elderLocationTimestamp = pushMessage.elderLocationTimestamp
                        
                        if (elderCurrentLat != null && elderCurrentLng != null) {
                            Log.d("ChatViewModel", "✅ 收到长辈实时位置：lat=$elderCurrentLat, lng=$elderCurrentLng")
                            Log.d("ChatViewModel", "⏰ 位置时间戳：$elderLocationTimestamp")
                        } else {
                            Log.w("ChatViewModel", "⚠️ 未收到长辈实时位置，将使用默认起点")
                        }
                        
                        // ⭐ 更新 StateFlow，让 PrivateChatScreen 可以实时接收
                        val sharedInfo = SharedLocationInfo(
                            elderId = elderId,
                            elderName = elderName,
                            favoriteName = favoriteName,
                            favoriteAddress = favoriteAddress,
                            latitude = favoriteLat,
                            longitude = favoriteLng,
                            elderCurrentLat = elderCurrentLat,
                            elderCurrentLng = elderCurrentLng,
                            elderLocationTimestamp = elderLocationTimestamp,
                            orderId = null,  // 初始状态没有订单ID
                            orderStatus = 0  // 0-待确认
                        )
                        _sharedLocation.value = sharedInfo
                        
                        Log.d("ChatViewModel", "✅ sharedLocation StateFlow 已更新：$favoriteName")
                        Log.d("ChatViewModel", "🔔 [卡片状态] sharedLocation.value != null: ${_sharedLocation.value != null}")
                        Log.d("ChatViewModel", "🔔 [卡片数据] elderId=$elderId, elderName=$elderName, favoriteName=$favoriteName")
                        Log.d("ChatViewModel", "🔔 [卡片数据] lat=$favoriteLat, lng=$favoriteLng, elderCurrentLat=$elderCurrentLat, elderCurrentLng=$elderCurrentLng")
                        
                        // ⭐ 关键修复：持久化保存到 SharedPreferences，确保进入私聊界面时能恢复
                        try {
                            viewModelScope.launch {
                                try {
                                    Log.d("ChatViewModel", "💾 [持久化] 开始保存分享地点...")
                                    val appInstance = MyApplication.instance
                                    Log.d("ChatViewModel", "💾 [持久化] MyApplication.instance 获取成功")
                                    
                                    val prefs = appInstance.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                                    Log.d("ChatViewModel", "💾 [持久化] SharedPreferences 获取成功")
                                    
                                    prefs.edit()
                                        .putLong("elderId_${elderId}", elderId)
                                        .putString("elderName_${elderId}", elderName)
                                        .putString("favoriteName_${elderId}", favoriteName)
                                        .putString("favoriteAddress_${elderId}", favoriteAddress)
                                        .putFloat("latitude_${elderId}", favoriteLat.toFloat())
                                        .putFloat("longitude_${elderId}", favoriteLng.toFloat())
                                        .apply()
                                    Log.d("ChatViewModel", "💾 [持久化] 基本信息保存成功")
                                    
                                    if (elderCurrentLat != null && elderCurrentLng != null) {
                                        prefs.edit()
                                            .putFloat("elderCurrentLat_${elderId}", elderCurrentLat.toFloat())
                                            .putFloat("elderCurrentLng_${elderId}", elderCurrentLng.toFloat())
                                            .putLong("elderLocationTimestamp_${elderId}", elderLocationTimestamp ?: 0L)
                                            .apply()
                                        Log.d("ChatViewModel", "💾 [持久化] 长辈位置保存成功")
                                    }
                                    
                                    Log.d("ChatViewModel", "✅ [持久化] 已保存分享地点到本地缓存：elderId=$elderId")
                                } catch (e: Exception) {
                                    Log.e("ChatViewModel", "❌ [持久化] 保存失败", e)
                                    e.printStackTrace()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "❌ [持久化] 启动协程失败", e)
                            e.printStackTrace()
                        }
                        
                        // ⭐ 发送通知提醒普通用户（如果不在聊天界面）
                        sendFavoriteSharedNotification(
                            elderId = elderId,
                            elderName = elderName,
                            favoriteName = favoriteName,
                            favoriteAddress = favoriteAddress,
                            favoriteLat = favoriteLat,
                            favoriteLng = favoriteLng
                        )
                        
                        Log.d("ChatViewModel", "✅ 已发送收藏分享通知")
                    }
                    
                    else -> {
                        Log.w("ChatViewModel", "⚠️ 未知的推送类型：${pushMessage.type}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ 解析亲情守护推送消息失败", e)
            }
        }
    }
    
    /**
     * ⭐ 新增：发送收藏分享通知
     */
    private fun sendFavoriteSharedNotification(
        elderId: Long,
        elderName: String,
        favoriteName: String,
        favoriteAddress: String,
        favoriteLat: Double = 0.0,  // ⭐ 新增：纬度
        favoriteLng: Double = 0.0   // ⭐ 新增：经度
    ) {
        val appContext = MyApplication.instance  // ⭐ 修复：使用 MyApplication.instance 获取 Context
        val channelId = "favorite_shared_channel"
        val channelName = "收藏地点分享"
        val notificationId = (elderId + System.currentTimeMillis()).toInt()
        
        // 检查通知权限（Android 13+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                Log.w("ChatViewModel", "⚠️ 缺少 POST_NOTIFICATIONS 权限，无法发送通知")
                return
            }
        }
        
        // 创建通知渠道
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "长辈分享的收藏地点"
                setBypassDnd(true)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = appContext.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        // ⭐ 创建点击通知后的 Intent - 跳转到与该长辈的聊天界面
        val intent = android.content.Intent(appContext, com.example.myapplication.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("FAVORITE_SHARED_ELDER_ID", elderId)
            putExtra("FAVORITE_SHARED_ELDER_NAME", elderName)
            putExtra("FAVORITE_NAME", favoriteName)
            putExtra("FAVORITE_ADDRESS", favoriteAddress)
            putExtra("FAVORITE_LAT", favoriteLat)
            putExtra("FAVORITE_LNG", favoriteLng)
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            appContext,
            notificationId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // 构建通知
        val notification = androidx.core.app.NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📍 $elderName 分享了一个地点")
            .setContentText(favoriteName)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("$favoriteName\n$favoriteAddress"))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .build()
        
        try {
            val manager = appContext.getSystemService(android.app.NotificationManager::class.java)
            manager.notify(notificationId, notification)
            Log.d("ChatViewModel", "🔔 已发送收藏分享通知：elderId=$elderId, favorite=$favoriteName")
        } catch (e: SecurityException) {
            Log.e("ChatViewModel", "❌ 发送通知失败：缺少权限", e)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "❌ 发送通知异常", e)
        }
    }
    
    /**
     * ⭐ 新增：清除 sharedLocation，避免重复弹窗
     */
    fun clearSharedLocation() {
        _sharedLocation.value = null
        Log.d("ChatViewModel", "✅ 已清除 sharedLocation")
    }
    
    /**
     * ⭐ 新增：设置 sharedLocation（用于从缓存恢复）
     */
    fun setSharedLocation(location: SharedLocationInfo) {
        _sharedLocation.value = location
        Log.d("ChatViewModel", "✅ 已设置 sharedLocation：${location.favoriteName}")
    }
}