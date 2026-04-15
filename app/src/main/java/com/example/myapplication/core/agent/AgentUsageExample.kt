package com.example.myapplication.core.agent

/**
 * 智能体模块使用示例
 * 
 * 本文件展示了如何在现有项目中使用新创建的模块化组件
 * 注意：这只是示例代码，不会自动集成到项目中
 */

/*
// ========================================
// 示例 1: 在 ChatViewModel 中使用智能体模块
// ========================================

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val webSocketClient: ChatWebSocketClient,
    private val orderRepository: IOrderRepository,
    private val agentRepository: AgentRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    
    // ⭐ 新增：使用智能体模块
    private val agentModule = createAgentModule()
    
    // ... 其他代码保持不变 ...
    
    /**
     * 发送用户消息（使用新的请求构建器）
     */
    fun sendMessage(content: String) {
        viewModelScope.launch {
            // 更新状态管理器
            agentModule.stateManager.onUserMessage(content)
            
            // 使用请求构建器生成符合后端规范的 JSON
            val json = agentModule.requestBuilder.buildUserMessage(
                sessionId = sessionId.value,
                content = content,
                lat = currentLat,
                lng = currentLng,
                dialectType = _currentAccent.value
            )
            
            // 发送消息
            if (webSocketClient.isConnected()) {
                webSocketClient.sendRaw(json)
            } else {
                Log.e("ChatViewModel", "WebSocket 未连接")
            }
        }
    }
    
    /**
     * 解析服务端消息（使用新的消息处理器）
     */
    private fun parseServerMessage(json: String) {
        // 使用消息处理器解析
        val response = agentModule.messageHandler.parseMessage(json) ?: return
        
        when (response.type?.uppercase()) {
            "SEARCH" -> {
                // 处理搜索结果
                val result = agentModule.messageHandler.handleSearchResponse(response)
                
                // 更新状态管理器
                val pois = result.places.map {
                    AgentStateManager.PoiItem(
                        id = it.id,
                        name = it.name,
                        address = it.address,
                        lat = it.lat,
                        lng = it.lng,
                        distance = it.distance,
                        type = it.type,
                        duration = it.duration,
                        price = it.price,
                        score = it.score
                    )
                }
                agentModule.stateManager.onSearchResult(pois, result.needConfirm)
                
                // 显示 POI 列表
                _poiList.value = result.places
                _candidates.value = result.places
                
                // 显示提示消息
                val chatMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = result.message,
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    suggestions = result.suggestions
                )
                _messages.value += chatMessage
                
                // 如果需要确认，弹出对话框
                if (result.needConfirm) {
                    _showCandidatesDialog.value = true
                }
            }
            
            "CHAT" -> {
                // 处理聊天回复
                val chatMessage = agentModule.messageHandler.handleChatResponse(response)
                if (chatMessage != null) {
                    _messages.value += chatMessage
                }
            }
            
            "ORDER" -> {
                // 处理订单创建
                val orderResult = agentModule.messageHandler.handleOrderResponse(response)
                if (orderResult != null) {
                    agentModule.stateManager.onOrderCreated()
                    // 处理订单结果...
                }
            }
            
            "IMAGE_RESULT" -> {
                // 处理图片识别结果
                val imageResult = agentModule.messageHandler.handleImageResult(response)
                
                // 更新状态管理器
                val pois = imageResult.places.map {
                    AgentStateManager.PoiItem(
                        id = it.id,
                        name = it.name,
                        address = it.address,
                        lat = it.lat,
                        lng = it.lng,
                        distance = it.distance,
                        type = it.type,
                        duration = it.duration,
                        price = it.price,
                        score = it.score
                    )
                }
                agentModule.stateManager.onImageRecognized(
                    imageResult.ocrText,
                    pois,
                    imageResult.needConfirm
                )
                
                // 显示识别结果...
            }
            
            "ERROR" -> {
                // 处理错误
                val error = agentModule.messageHandler.handleErrorResponse(response)
                agentModule.stateManager.onError(error.message)
                
                // 显示友好错误提示
                addSystemMessage(error.userFriendlyMessage)
            }
        }
    }
    
    /**
     * 用户选择候选地点（使用请求构建器）
     */
    fun selectCandidate(poi: PoiData) {
        viewModelScope.launch {
            // 验证坐标
            if (!agentModule.requestBuilder.isValidCoordinate(poi.lat, poi.lng)) {
                addSystemMessage("❌ 位置信息无效")
                return@launch
            }
            
            // 更新状态
            val selectedPoi = agentModule.stateManager.onConfirmSelection(poi.name)
            if (selectedPoi == null) {
                addSystemMessage("❌ 未找到该地点，请重新选择")
                return@launch
            }
            
            // 构建确认消息
            val json = agentModule.requestBuilder.buildConfirmMessage(
                sessionId = sessionId.value,
                poiName = poi.name,
                lat = currentLat ?: return@launch,
                lng = currentLng ?: return@launch
            )
            
            // 发送确认消息
            if (webSocketClient.isConnected()) {
                webSocketClient.sendRaw(json)
            }
        }
    }
    
    /**
     * 发送心跳（使用请求构建器）
     */
    private fun sendPing() {
        viewModelScope.launch {
            try {
                val pingMessage = agentModule.requestBuilder.buildPingMessage(sessionId.value)
                webSocketClient.sendRaw(pingMessage)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "发送心跳失败", e)
            }
        }
    }
    
    /**
     * 发送图片（使用请求构建器验证）
     */
    fun sendImage(imageBase64: String) {
        viewModelScope.launch {
            // 验证图片格式
            if (!agentModule.requestBuilder.isValidImageBase64(imageBase64)) {
                addSystemMessage("❌ 图片格式不正确，请使用 JPG/PNG 格式，大小不超过 5MB")
                return@launch
            }
            
            // 构建图片消息
            val json = agentModule.requestBuilder.buildImageMessage(
                sessionId = sessionId.value,
                imageBase64 = imageBase64,
                lat = currentLat,
                lng = currentLng
            )
            
            // 发送消息
            if (webSocketClient.isConnected()) {
                webSocketClient.sendRaw(json)
            }
        }
    }
}


// ========================================
// 示例 2: 验证 SessionId 和坐标
// ========================================

fun validateSessionAndLocation() {
    val requestBuilder = AgentRequestBuilder()
    
    // 验证 SessionId
    val sessionId = requestBuilder.generateSessionId(userId = 123L)
    if (requestBuilder.isValidSessionId(sessionId)) {
        Log.d("Validation", "✅ SessionId 有效: $sessionId")
    }
    
    // 验证坐标
    val lat = 23.653927
    val lng = 116.677026
    if (requestBuilder.isValidCoordinate(lat, lng)) {
        Log.d("Validation", "✅ 坐标有效: lat=$lat, lng=$lng")
    }
}


// ========================================
// 示例 3: 检查当前状态
// ========================================

fun checkCurrentState() {
    val stateManager = AgentStateManager()
    
    // 获取当前状态
    val currentState = stateManager.getCurrentState()
    Log.d("State", "当前状态: ${stateManager.getStateDescription()}")
    
    // 检查是否可以发送消息
    if (stateManager.canSendMessage()) {
        Log.d("State", "✅ 可以发送消息")
    } else {
        Log.d("State", "❌ 当前状态不允许发送消息")
    }
    
    // 检查是否需要确认
    if (stateManager.isWaitingForConfirmation()) {
        Log.d("State", "⏳ 等待用户确认")
    }
}


// ========================================
// 示例 4: 批量图片识别
// ========================================

fun sendBatchImages(mainImage: String, additionalImages: List<String>) {
    val requestBuilder = AgentRequestBuilder()
    
    // 验证所有图片
    val allImages = listOf(mainImage) + additionalImages
    for ((index, image) in allImages.withIndex()) {
        if (!requestBuilder.isValidImageBase64(image)) {
            Log.e("BatchImages", "❌ 第 ${index + 1} 张图片格式无效")
            return
        }
    }
    
    // 构建批量图片消息
    val json = requestBuilder.buildImageMessage(
        sessionId = "user_123",
        imageBase64 = mainImage,
        additionalImages = additionalImages,
        lat = 23.653927,
        lng = 116.677026
    )
    
    // 发送...
    Log.d("BatchImages", "✅ 批量图片消息已构建，共 ${allImages.size} 张")
}
*/
