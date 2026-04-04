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
import com.example.myapplication.core.utils.SpeechRecognizerHelper
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
    private val agentRepository: AgentRepository  // ⭐ 新增：注入 AgentRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sessionId = MutableStateFlow(UUID.randomUUID().toString())
    val sessionId: StateFlow<String> = _sessionId

    private var speechHelper: SpeechRecognizerHelper? = null

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

    init {
        Log.d("ChatViewModel", "=== ChatViewModel 初始化开始 ===")

        // ⭐ 优化：异步初始化，不阻塞 UI
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) {
                MyApplication.tokenManager.getToken()
            }

            if (!token.isNullOrBlank()) {
                try {
                    // ⭐ 优化：非阻塞连接，立即返回
                    webSocketClient.connect(sessionId.value, token)
                    Log.d("ChatViewModel", "WebSocket 连接请求已发送")
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
                webSocketClient.messages.collect { serverMessage ->
                    parseServerMessage(serverMessage)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "消息监听异常", e)
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
                    Log.d("ChatViewModel", "🛰️ 首次进入，开始定位...")
                    // 这里会通过 Application 级别的共享来获取 HomeViewModel 的位置
                    // 暂时保持等待，实际位置由 HomeScreen 同步

                    // ⭐ 新增：轮询等待 HomeViewModel 同步位置（最多等 5 秒）
                    var waitCount = 0
                    while (currentLat == null && waitCount < 50) {  // 5 秒 = 50 * 100ms
                        delay(100)
                        waitCount++
                    }

                    if (currentLat != null) {
                        Log.d("ChatViewModel", "✅ 位置同步成功：lat=$currentLat, lng=$currentLng")
                    } else {
                        Log.w("ChatViewModel", "⚠️ 位置同步超时，等待 HomeViewModel 唤醒")
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
        } else {
            Log.d("ChatViewModel", "⏭️ 位置变化小于 50 米，跳过更新")
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

        // ⭐ 新增：检测是否是具体地点名称（不包含"我要去"、"带我去"等前缀）
        val isSpecificLocation = !content.contains("我要去") && 
                                 !content.contains("带我去") && 
                                 !content.contains("我想去") &&
                                 !content.contains("导航到") &&
                                 !content.contains("去")
        
        if (isSpecificLocation) {
            Log.d("ChatViewModel", "📍 检测到具体地点名称，使用 HTTP 搜索并自动选择评分最高的")
            searchAndAutoSelect(content)
            return
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
                // ⭐ 修改：使用 HTTP 方式识别图片
                recognizeImageByHttp(it)
            }
        }
    }

    // ⭐ 修改：使用 HTTP 方式发送并识别图片
    fun sendImage(bitmap: Bitmap) {
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.IO) {
                val compressedBitmap = compressImage(bitmap)
                val stream = ByteArrayOutputStream()
                // ⭐ 修改：降低图片质量到 60%，减少内存占用
                compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)

                val byteArray = stream.toByteArray()

                if (byteArray.size > 500 * 1024) {
                    stream.reset()
                    compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
                }

                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }

            val imageMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "📷 [图片]",
                isUser = true,
                timestamp = System.currentTimeMillis(),
                imageBase64 = base64
            )
            _messages.value += imageMessage

            // ⭐ 修改：使用 HTTP API 识别图片
            recognizeImageByHttp(bitmap)
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
                            addSystemMessage("📍 已为您找到：**${bestPlace.name}**\n⭐ 评分：${bestPlace.score ?: "N/A"}")
                            
                            // ⭐ 延迟一点，让用户看到提示，然后自动确认
                            delay(800)
                            
                            // 直接调用 HTTP 确认接口
                            confirmSelectionByHttp(bestPlace.name ?: "")
                        } else {
                            addSystemMessage("😕 未找到相关地点，请尝试其他关键词")
                        }
                    }
                    
                    "ORDER" -> {
                        // ⭐ 修改：移除 route 检查，直接调用 HTTP API 创建订单
                        Log.d("ChatViewModel", "=== 收到 WebSocket ORDER 消息 ===")
                        Log.d("ChatViewModel", "response.message=${response.message}")
                        Log.d("ChatViewModel", "response.poi=${response.poi}")
                        Log.d("ChatViewModel", "response.route=${response.route}")
                        
                        addSystemMessage(response?.message ?: "✅ 已确认目的地，正在创建订单...")
                        
                        // ⭐ 直接从 response 中获取 poi
                        response.poi?.let { poi ->
                            Log.d("ChatViewModel", "🚀 开始调用 createOrder (HTTP API)，poiName=${poi.name}")
                            createOrder(poi.name ?: "", poi.lat, poi.lng, 1, null)
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
                
                if (response?.type == "ORDER" +
                    "") {
                    addSystemMessage(response.message ?: "已确认目的地")
                    // ⭐ 直接从 response 中获取 poi
                    response.poi?.let { poi ->
                        Log.d("ChatViewModel", "POI 信息：name=${poi.name}, lat=${poi.lat}, lng=${poi.lng}")
                        Log.d("ChatViewModel", "🚀 开始调用 createOrder (HTTP 方式)，poiName=${poi.name}")
                        createOrder(poi.name ?: "", poi.lat, poi.lng, 1, null)
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
            
            // ⭐ 图片压缩（对齐后端要求：500KB 以内，最佳 200-500KB）
            val base64 = withContext(Dispatchers.IO) {
                val compressedBitmap = compressImage(bitmap)
                val stream = ByteArrayOutputStream()
                
                // 第一次压缩：质量 60%
                compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                var byteArray = stream.toByteArray()
                
                // 如果超过 500KB，继续压缩到 40%
                if (byteArray.size > 500 * 1024) {
                    stream.reset()
                    compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
                    byteArray = stream.toByteArray()
                }
                
                // ⭐ 添加前缀：data:image/jpeg;base64,
                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64String"
            }
            
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
            Log.d("ChatViewModel", "places count=${result.data?.places?.size}")

            if (result.isSuccess()) {
                val response = result.data
                if (response != null) {
                    Log.d("ChatViewModel", "=== 收到图片识别响应 ===")
                    Log.d("ChatViewModel", "type=${response.type}, message=${response.message}")
                    
                    when (response.type.uppercase()) {
                        "SEARCH" -> {
                            val places = response.places ?: emptyList()
                            if (places.isNotEmpty()) {
                                // ⭐ 解析 POI 列表（包含 score 字段）
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
                                        it.score  // ⭐ 评分字段
                                    )
                                }
                                val suggestions = places.take(3).map { it.name }
                                val chatMessage = ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = response.message,
                                    isUser = false,
                                    timestamp = System.currentTimeMillis(),
                                    suggestions = suggestions
                                )
                                _messages.value += chatMessage
                                
                                // ⭐ 如果需要确认，弹出候选列表
                                if (response.needConfirm) {
                                    Log.d("ChatViewModel", "🔔 需要确认，弹出候选列表对话框")
                                    _showCandidatesDialog.value = true
                                }
                            } else {
                                addSystemMessage("😕 未从图片中识别到有效地址")
                            }
                        }
                        
                        "ORDER" -> {
                            // 直接返回订单信息（精确匹配）
                            addSystemMessage(response.message)
                            response.poi?.let { poi ->
                                createOrder(poi.name ?: "", poi.lat, poi.lng, 1, null)
                            }
                        }
                        
                        "CHAT" -> {
                            // AI 聊天回复
                            addSystemMessage(response.message)
                        }
                        
                        "ERROR" -> {
                            addSystemMessage("❌ ${response.message}")
                        }
                        
                        else -> {
                            addSystemMessage(response.message ?: "✅ 识别完成")
                        }
                    }
                }
            } else {
                // ⭐ 友好的错误提示（对齐后端文档）
                val errorMsg = when {
                    result.message.contains("图片大小") -> "⚠️ 图片太大，请重新选择"
                    result.message.contains("不支持的图片格式") -> "⚠️ 仅支持 JPEG/PNG/BMP 格式"
                    result.message.contains("网络") -> "⚠️ 网络连接失败"
                    else -> "❌ 识别失败：${result.message}"
                }
                addSystemMessage(errorMsg)
            }
        }
    }

    fun startVoiceInput(context: Context) {
        if (speechHelper == null) {
            speechHelper = SpeechRecognizerHelper(context) { result ->
                val text = parseVoiceResult(result)
                sendMessage(text)
            }
        }
        speechHelper?.startListening()
    }

    private fun parseVoiceResult(raw: String): String {
        return try {
            val json = JSONObject(raw)
            val ws = json.getJSONArray("ws")
            val sb = StringBuilder()
            for (i in 0 until ws.length()) {
                val cw = ws.getJSONObject(i).getJSONArray("cw")
                if (cw.length() > 0) {
                    sb.append(cw.getJSONObject(0).getString("w"))
                }
            }
            sb.toString()
        } catch (e: Exception) {
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
            Log.d("ChatViewModel", "参数：poiName=$poiName, poiLat=$poiLat, poiLng=$poiLng")
            
            _orderState.value = OrderState.Loading

            // ⭐ 修改：不再传递起点坐标
            val result = orderRepository.createOrder(
                poiName = poiName,
                poiLat = poiLat,
                poiLng = poiLng,
                passengerCount = passengerCount,
                remark = remark
            )
            
            Log.d("ChatViewModel", "订单创建结果：code=${result.code}, message=${result.message}")
            
            if (result.isSuccess()) {
                result.data?.let { order ->
                    Log.d("ChatViewModel", "✅ 订单创建成功：orderId=${order.id}, orderNo=${order.orderNo}")
                    _orderState.value = OrderState.Success(order)
                    addSystemMessage("✅ 订单创建成功：${order.orderNo}")
                } ?: run {
                    Log.e("ChatViewModel", "❌ 订单数据为 null")
                    _orderState.value = OrderState.Error("返回数据为空")
                }
            } else {
                Log.e("ChatViewModel", "❌ 订单创建失败：${result.message}")
                _orderState.value = OrderState.Error(result.message ?: "未知错误")
                addSystemMessage("❌ 订单创建失败：${result.message}")
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
            val response = jsonFormat.decodeFromString<WebSocketResponse>(json)

            Log.d("ChatViewModel", "=== 收到 WebSocket 消息 ===")
            Log.d("ChatViewModel", "type=${response.type}, message=${response.message}")
            Log.d("ChatViewModel", "data=${response.data}")

            when (response.type?.uppercase()) {
                "SEARCH", "SEARCH_RESULT", "POI_LIST" -> {
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

                "CHAT" -> {
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

                    "ORDER" -> {
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
                                            poiName = orderJson.optString("destAddress", ""),
                                            poiAddress = orderJson.optString("destAddress", ""),
                                            destAddress = orderJson.optString("destAddress", ""),
                                            lat = orderJson.optDouble("destLat", 0.0),
                                            lng = orderJson.optDouble("destLng", 0.0),
                                            passengerCount = 1,
                                            estimatedPrice = orderJson.optDouble("estimatePrice", 0.0),
                                            actualPrice = null,
                                            createTime = orderJson.optString("createTime", ""),
                                            remark = null
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

                "ROUTE" -> {
                    // ⭐ 路线规划响应
                    addSystemMessage(response.message ?: "🗺️ 路线规划完成")
                    // 如果有 route 数据，可以显示详细信息
                }

                "ERROR" -> {
                    // ⭐ 错误响应
                    addSystemMessage("❌ ${response.message}")
                }

                // ⭐ 新增：处理图片识别响应（对齐后端文档）
                "image_recognition" -> {
                    val ocrText = response.ocrText ?: ""
                    addSystemMessage("📷 OCR 识别结果：$ocrText")

                    // ⭐ 如果后端同时返回了 POI 列表
                    val poiList = extractPoiList(response.data)
                    if (poiList.isNotEmpty()) {
                        _poiList.value = poiList
                        val suggestions = poiList.take(3).map { it.name }
                        val chatMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = "根据图片内容，为您找到 ${poiList.size} 个地点",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            suggestions = suggestions
                        )
                        _messages.value += chatMessage
                        
                        // ⭐ 弹出候选列表供用户选择
                        _showCandidatesDialog.value = true
                    } else if (ocrText.isNotBlank()) {
                        addSystemMessage("💡 识别到文字：$ocrText\n但未找到匹配的地址信息")
                    }
                }

                // ⭐ 新增：处理错误响应
                "error" -> {
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
            Log.e("ChatViewModel", "解析消息失败", e)
            val chatMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = json,
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

            // 降级：尝试从 data 字段提取
            val dataObj = response.data?.toString()
            if (dataObj != null && dataObj != "null") {
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

    // ⭐ 新增：图片压缩方法（对齐后端文档：500KB 以内，最佳 200-500KB）
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
}