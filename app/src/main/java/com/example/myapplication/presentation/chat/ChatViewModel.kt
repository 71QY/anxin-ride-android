package com.example.myapplication.presentation.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
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

    init {
        Log.d("ChatViewModel", "=== ChatViewModel 初始化开始 ===")

        // ⭐ 优化：异步初始化，不阻塞 UI
        viewModelScope.launch {
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
                Log.e("ChatViewModel", "❌ Token 为空，无法连接 WebSocket")
                addSystemMessage("⚠️ 请先登录，再进行对话")
            }
        }

        // ⭐ 优化：立即启动消息监听，不等待连接完成
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "🔄 开始监听 WebSocket 消息流...")
                webSocketClient.messages.collect { serverMessage ->
                    Log.d("ChatViewModel", "📥 ViewModel 收到消息: ${serverMessage.take(100)}...")
                    parseServerMessage(serverMessage)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ 消息监听异常", e)
                addSystemMessage("⚠️ 消息接收异常：${e.message}")
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
            lng = currentLng,
            page = 1,
            pageSize = 20,
            sortByDistance = true
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
                    addSystemMessage("❌ 网络连接已断开，请检查网络")
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
                // ⭐ 修改：使用 WebSocket 方式识别图片（后端推荐）
                sendImage(it)
            }
        }
    }

    // ⭐ 修改：使用 HTTP API 方式发送并识别图片（WebSocket 缓冲区限制太严格）
    fun sendImage(bitmap: Bitmap) {
        Log.d("ChatViewModel", "=== 开始图片识别 (HTTP API) ===")
        recognizeImageByHttp(bitmap)
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
            
            // ⭐ 图片压缩（对齐后端要求：最大支持 10MB，建议压缩到 3MB 以内）
            val base64 = withContext(Dispatchers.IO) {
                compressAndToBase64(bitmap, maxSizeKB = 3072)  // 3MB = 3072KB
            }
            
            // ⭐ 发送前校验大小（最大 10MB）
            val base64SizeKB = base64.length / 1024
            if (base64SizeKB > 10240) {  // 10MB = 10240KB
                Log.e("ChatViewModel", "❌ 图片压缩后仍然过大：${base64SizeKB}KB")
                addSystemMessage("⚠️ 图片太大（超过 10MB），请重新选择更小的图片")
                return@launch
            } else if (base64SizeKB > 3072) {
                Log.w("ChatViewModel", "⚠️ 图片较大：${base64SizeKB}KB，可能影响识别速度")
            }
            
            Log.d("ChatViewModel", "✅ 图片压缩成功：原始=${bitmap.byteCount / 1024}KB, 压缩后=${base64SizeKB}KB")
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

    // ⭐ 新增：处理 IMAGE_RECOGNITION 响应（旧版本兼容）
    private fun handleImageRecognitionResponse(data: AgentSearchResponse) {
        val places = data.places ?: data.candidates ?: emptyList()
        Log.d("ChatViewModel", "📸 IMAGE_RECOGNITION 类型，places count=${places.size}")
        
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
                        _voiceInputText.value = finalText  // ⭐ 设置到输入框，等待用户手动发送
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
                        _voiceInputText.value = partialText  // ⭐ 实时更新 UI
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

    private fun addSystemMessage(content: String) {
        val systemMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        _messages.value += systemMessage
    }

    private fun parseServerMessage(json: String) {
        Log.d("ChatViewModel", "📥 收到原始消息: ${json.take(200)}...")  // ⭐ 新增：记录原始消息
        
        try {
            // ⭐ 新增：消息去重，避免重复显示相同的消息
            if (processedMessages.contains(json)) {
                Log.d("ChatViewModel", "⚠️ 检测到重复消息，已跳过")
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
                                     json.contains("ocrText") || 
                                     (json.contains("success") && response.success != null)
            
            when {
                isImageRecognition -> {
                    // ⭐ 处理图片识别响应
                    Log.d("ChatViewModel", "=== 收到图片识别响应 ===")
                    Log.d("ChatViewModel", "type=${response.type}, success=${response.success}, message=${response.message}")
                    Log.d("ChatViewModel", "data=${response.data}")
                    
                    // ⭐ 检查是否识别失败（message 中包含 "失败" 或 "异常"）
                    val isErrorMessage = response.message?.contains("失败") == true || 
                                         response.message?.contains("异常") == true ||
                                         response.message?.contains("Cannot invoke") == true ||
                                         response.message?.contains("null") == true
                    
                    if (response.success == true && !isErrorMessage) {
                        // ⭐ 成功：解析 OCR 文本和 POI 列表
                        val ocrText = response.ocrText ?: response.message ?: ""
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

    // ⭐ 修改：用户选择候选地点（使用 HTTP API）
    fun selectCandidate(poi: PoiData) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "=== 用户选择候选地点 ===")
            Log.d("ChatViewModel", "选择：${poi.name}")

            // 添加用户确认消息到聊天历史
            val userConfirmMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "✅ 确认选择：${poi.name}",
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
                            Log.d("ChatViewModel", "创建订单：${poi.name}")
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
                Log.e("ChatViewModel", "确认失败：${result.message}")
                addSystemMessage("❌ 确认失败：${result.message}")
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
    private suspend fun compressAndToBase64(source: Bitmap, maxSizeKB: Int = 500): String {
        return withContext(Dispatchers.IO) {
            var bitmap = source
            
            Log.d("ImageCompression", "========== 图片压缩信息 ==========")
            Log.d("ImageCompression", "原始文件大小: ${source.byteCount / 1024} KB")
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
    
    // ⭐ 新增：辅助函数 - 语音播报（预留接口）
    private fun speak(text: String) {
        // TODO: 集成 TTS 语音播报功能
        Log.d("ChatViewModel", "🔊 语音播报: $text")
        // 未来可以集成百度 TTS 或系统 TTS
        // ttsHelper?.speak(text)
    }
}