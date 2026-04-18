package com.example.myapplication.presentation.orderTracking

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.example.myapplication.core.websocket.ChatWebSocketClient
import com.example.myapplication.data.model.Order
import com.example.myapplication.data.model.WsMessage
import com.example.myapplication.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class OrderTrackingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val webSocketClient: ChatWebSocketClient,
    private val tokenManager: com.example.myapplication.core.datastore.TokenManager
) : ViewModel() {

    // ==================== UI State ====================
    
    private val _uiState = MutableStateFlow<OrderTrackingUiState>(OrderTrackingUiState.Loading)
    val uiState: StateFlow<OrderTrackingUiState> = _uiState.asStateFlow()

    private val _driverLocation = MutableStateFlow<LatLng?>(null)
    val driverLocation: StateFlow<LatLng?> = _driverLocation.asStateFlow()

    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

    // ⭐ 新增：事件流（用于UI提示）
    private val _events = MutableSharedFlow<OrderTrackingEvent>()
    val events = _events.asSharedFlow()

    // ==================== WebSocket 监听 ====================
    
    private var wsJob: Job? = null

    /**
     * 初始化行程追踪（传入订单ID）
     */
    fun initTracking(orderId: Long) {
        Log.d("OrderTrackingVM", "🚀 初始化行程追踪，订单ID: $orderId")
        
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = OrderTrackingUiState.Loading
            
            // 1. 获取订单详情
            val result = orderRepository.getOrder(orderId)
            
            if (result.isSuccess()) {
                val order = result.data!!
                Log.d("OrderTrackingVM", "✅ 订单加载成功: ${order.orderNo}")
                
                _uiState.value = OrderTrackingUiState.Success(order)
                
                // 2. 如果有司机位置，立即显示
                if (order.driverLat != null && order.driverLng != null) {
                    _driverLocation.value = LatLng(order.driverLat, order.driverLng)
                }
                
                // 3. 连接 WebSocket 并监听消息
                connectAndListen(orderId)
            } else {
                Log.e("OrderTrackingVM", "❌ 订单加载失败: ${result.message}")
                _uiState.value = OrderTrackingUiState.Error(result.message ?: "订单不存在")
            }
        }
    }

    /**
     * 连接 WebSocket 并监听行程消息
     */
    private fun connectAndListen(orderId: Long) {
        // 取消之前的监听
        wsJob?.cancel()
        
        wsJob = viewModelScope.launch {
            try {
                // ⭐ 修复：检查 WebSocket 是否已连接，如果没有则先连接
                if (!webSocketClient.isConnected()) {
                    Log.d("OrderTrackingVM", "🔌 WebSocket 未连接，开始连接...")
                    val userId = tokenManager.getUserId()
                    val token = tokenManager.getToken()
                    
                    if (userId != null && !token.isNullOrBlank()) {
                        val wsSessionId = "user_$userId"
                        webSocketClient.connect(wsSessionId, token)
                        Log.d("OrderTrackingVM", "✅ WebSocket 连接请求已发送")
                        
                        // 等待连接建立
                        kotlinx.coroutines.delay(1000)
                    } else {
                        Log.e("OrderTrackingVM", "❌ 无法连接 WebSocket：userId 或 token 为空")
                        return@launch
                    }
                } else {
                    Log.d("OrderTrackingVM", "✅ WebSocket 已连接")
                }
                
                // 开始监听消息
                Log.d("OrderTrackingVM", "🚀 开始监听 WebSocket 消息流...")
                webSocketClient.messages.collect { message ->
                    try {
                        // ⭐ 修复：配置 Json 忽略未知字段，避免解析错误
                        val json = Json { ignoreUnknownKeys = true }
                        val wsMessage = json.decodeFromString<WsMessage>(message)
                        handleWsMessage(wsMessage, orderId)
                    } catch (e: Exception) {
                        Log.e("OrderTrackingVM", "❌ 解析 WebSocket 消息失败", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderTrackingVM", "❌ WebSocket 监听异常", e)
            }
        }
    }

    /**
     * 处理 WebSocket 消息
     */
    private fun handleWsMessage(wsMessage: WsMessage, currentOrderId: Long) {
        // 只处理当前订单的消息
        if (wsMessage.orderId != null && wsMessage.orderId != currentOrderId) {
            return
        }

        when (wsMessage.type) {
            WsMessage.TYPE_DRIVER_REQUEST -> {  // ⭐ 新增：司机接单请求
                Log.d("OrderTrackingVM", "🚕 收到 DRIVER_REQUEST")
                handleDriverRequest(wsMessage)
            }
            
            WsMessage.TYPE_ORDER_ACCEPTED -> {
                Log.d("OrderTrackingVM", "🚕 收到 ORDER_ACCEPTED")
                handleOrderAccepted(wsMessage)
            }
            
            WsMessage.TYPE_DRIVER_REJECTED -> {  // ⭐ 新增：用户拒绝接单
                Log.d("OrderTrackingVM", "❌ 收到 DRIVER_REJECTED")
                handleDriverRejected(wsMessage)
            }
            
            WsMessage.TYPE_DRIVER_LOCATION -> {
                Log.d("OrderTrackingVM", "📍 收到 DRIVER_LOCATION")
                handleDriverLocation(wsMessage)
            }
            
            WsMessage.TYPE_DRIVER_ARRIVED -> {
                Log.d("OrderTrackingVM", "🎉 收到 DRIVER_ARRIVED")
                handleDriverArrived(wsMessage)
            }
            
            WsMessage.TYPE_TRIP_STARTED -> {
                Log.d("OrderTrackingVM", "🚀 收到 TRIP_STARTED")
                handleTripStarted(wsMessage)
            }
            
            WsMessage.TYPE_TRIP_COMPLETED -> {
                Log.d("OrderTrackingVM", "🏁 收到 TRIP_COMPLETED")
                handleTripCompleted(wsMessage)
            }
            
            WsMessage.TYPE_TRIP_STARTED -> {  // ⭐ 新增：行程开始
                Log.d("OrderTrackingVM", "🚀 收到 TRIP_STARTED")
                handleTripStarted(wsMessage)
            }
            
            WsMessage.TYPE_TRIP_COMPLETED -> {  // ⭐ 新增：行程完成
                Log.d("OrderTrackingVM", "🏁 收到 TRIP_COMPLETED")
                handleTripCompleted(wsMessage)
            }
            
            WsMessage.TYPE_ORDER_CREATED -> {
                Log.d("OrderTrackingVM", "📦 收到 ORDER_CREATED（代叫车）- 忽略，由 HomeViewModel 处理")
                // ⭐ 修复：不在此处处理，避免与 HomeViewModel 冲突
                // handleOrderCreated(wsMessage) 已移除
            }
            
            WsMessage.TYPE_PROXY_ORDER_CONFIRMED -> {
                Log.d("OrderTrackingVM", "✅ 收到 PROXY_ORDER_CONFIRMED")
                handleProxyOrderConfirmed(wsMessage)
            }
            
            else -> {
                Log.d("OrderTrackingVM", "ℹ️ 忽略消息类型: ${wsMessage.type}")
            }
        }
    }

    /**
     * ⭐ 新增：处理司机接单请求（弹窗确认）
     */
    private fun handleDriverRequest(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            Log.d("OrderTrackingVM", "🚕 收到 DRIVER_REQUEST，显示确认弹窗")
            // 触发事件，让UI显示确认弹窗
            _events.emit(OrderTrackingEvent.DriverRequestReceived(wsMessage))
        }
    }
    
    /**
     * ⭐ 新增:处理用户拒绝接单
     */
    private fun handleDriverRejected(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentState = _uiState.value
            if (currentState is OrderTrackingUiState.Success) {
                // 清空司机信息,订单状态回到待确认
                val updatedOrder = currentState.order.copy(
                    driverName = null,
                    driverPhone = null,
                    driverAvatar = null,
                    carNo = null,
                    carType = null,
                    carColor = null,
                    rating = null,
                    status = 0,  // 回到待确认状态
                    driverLat = null,
                    driverLng = null
                )
                    
                _uiState.value = OrderTrackingUiState.Success(updatedOrder)
                _driverLocation.value = null
                    
                // 显示提示
                _events.emit(OrderTrackingEvent.DriverRejected("您已拒绝该司机,正在为您重新派单..."))
                    
                // ⭐ 新增:启动10秒倒计时,提示用户等待新司机
                viewModelScope.launch {
                    delay(10000)  // 10秒后
                    val currentStateAfterDelay = _uiState.value
                    if (currentStateAfterDelay is OrderTrackingUiState.Success && 
                        currentStateAfterDelay.order.status == 0) {
                        // 如果订单仍未被取消且没有新司机,显示等待提示
                        _events.emit(OrderTrackingEvent.DriverRejected("⏳ 正在为您寻找新的司机,请稍候..."))
                    }
                }
            }
        }
    }

    /**
     * 处理司机接单消息
     */
    private fun handleOrderAccepted(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentState = _uiState.value
            if (currentState is OrderTrackingUiState.Success) {
                // 更新订单中的司机信息
                val updatedOrder = currentState.order.copy(
                    driverName = wsMessage.driverName,
                    driverPhone = wsMessage.driverPhone,
                    driverAvatar = wsMessage.driverAvatar,
                    carNo = wsMessage.carNo,
                    carType = wsMessage.carType,
                    carColor = wsMessage.carColor,
                    rating = wsMessage.rating,
                    status = 3  // 司机已接单
                )
                
                _uiState.value = OrderTrackingUiState.Success(updatedOrder)
                
                // 更新司机位置
                if (wsMessage.driverLat != null && wsMessage.driverLng != null) {
                    _driverLocation.value = LatLng(wsMessage.driverLat, wsMessage.driverLng)
                }
                
                // 设置初始 ETA
                _etaMinutes.value = wsMessage.etaMinutes
            }
        }
    }

    /**
     * 处理司机位置更新
     */
    private fun handleDriverLocation(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            if (wsMessage.driverLat != null && wsMessage.driverLng != null) {
                // 平滑更新司机位置（前端动画处理）
                _driverLocation.value = LatLng(wsMessage.driverLat, wsMessage.driverLng)
            }
            
            // 更新 ETA
            _etaMinutes.value = wsMessage.etaMinutes
        }
    }

    /**
     * 处理司机到达消息
     */
    private fun handleDriverArrived(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentState = _uiState.value
            if (currentState is OrderTrackingUiState.Success) {
                // ⭐ 修复：司机已到达，更新订单状态为 4（司机已到达）
                val updatedOrder = currentState.order.copy(
                    status = 4  // ⭐ 修复：更新为司机已到达状态
                )
                _uiState.value = OrderTrackingUiState.Success(updatedOrder)
                
                _etaMinutes.value = 0
                
                // 更新司机位置到起点
                if (wsMessage.driverLat != null && wsMessage.driverLng != null) {
                    _driverLocation.value = LatLng(wsMessage.driverLat, wsMessage.driverLng)
                }
                
                Log.d("OrderTrackingVM", "✅ 司机已到达上车点，订单状态更新为 4，等待乘客上车")
            }
        }
    }

    /**
     * ⭐ 新增：处理行程开始
     */
    private fun handleTripStarted(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentState = _uiState.value
            if (currentState is OrderTrackingUiState.Success) {
                val updatedOrder = currentState.order.copy(
                    status = 5  // 行程中
                )
                
                _uiState.value = OrderTrackingUiState.Success(updatedOrder)
                Log.d("OrderTrackingVM", "✅ 行程已开始")
            }
        }
    }

    /**
     * ⭐ 新增：处理行程完成
     */
    private fun handleTripCompleted(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentState = _uiState.value
            if (currentState is OrderTrackingUiState.Success) {
                val updatedOrder = currentState.order.copy(
                    status = 6  // 已完成
                )
                
                _uiState.value = OrderTrackingUiState.Success(updatedOrder)
                Log.d("OrderTrackingVM", "✅ 行程已完成")
            }
        }
    }

    /**
     * ⭐ 新增：处理代叫车确认结果（亲友端接收）
     */
    private fun handleProxyOrderConfirmed(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            val confirmed = wsMessage.confirmed
            val rejectReason = wsMessage.rejectReason
                
            if (confirmed == true) {
                Log.d("OrderTrackingVM", "✅ 长辈已同意代叫车")
                // 显示成功提示
                val currentState = _uiState.value
                if (currentState is OrderTrackingUiState.Success) {
                    // 更新订单状态，添加提示信息
                    val updatedOrder = currentState.order.copy(
                        status = 2  // 司机接单中
                    )
                    _uiState.value = OrderTrackingUiState.Success(updatedOrder)
                }
                // 触发事件通知UI显示Toast
                _events.emit(OrderTrackingEvent.ProxyOrderConfirmed(true, null))
            } else {
                Log.d("OrderTrackingVM", "❌ 长辈已拒绝代叫车，原因：$rejectReason")
                // 显示拒绝提示并返回上一页
                _events.emit(OrderTrackingEvent.ProxyOrderConfirmed(false, rejectReason))
            }
        }
    }

    /**
     * 刷新订单信息
     */
    fun refreshOrder(orderId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = orderRepository.getOrder(orderId)
            if (result.isSuccess()) {
                result.data?.let { order ->
                    _uiState.value = OrderTrackingUiState.Success(order)
                }
            }
        }
    }

    /**
     * ⭐ 新增：取消订单
     */
    suspend fun cancelOrder(orderId: Long): Boolean {
        return try {
            val result = orderRepository.cancelOrder(orderId)
            if (result.isSuccess()) {
                Log.d("OrderTrackingVM", "✅ 订单取消成功")
                // 刷新订单状态
                refreshOrder(orderId)
                true
            } else {
                Log.e("OrderTrackingVM", "❌ 订单取消失败: ${result.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("OrderTrackingVM", "❌ 取消订单异常", e)
            false
        }
    }
    
    /**
     * ⭐ 新增：确认/拒绝司机接单
     */
    suspend fun confirmDriverAcceptance(orderId: Long, accepted: Boolean): Boolean {
        return try {
            val apiService = com.example.myapplication.core.network.RetrofitClient.instance
            val request = com.example.myapplication.data.model.ConfirmDriverRequest(
                orderId = orderId,
                accepted = accepted
            )
            
            val result = apiService.confirmDriverAcceptance(request)
            if (result.isSuccess()) {
                Log.d("OrderTrackingVM", "✅ 司机接单${if (accepted) "已同意" else "已拒绝"}")
                true
            } else {
                Log.e("OrderTrackingVM", "❌ 确认失败: ${result.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("OrderTrackingVM", "❌ 确认异常", e)
            false
        }
    }

    /**
     * ⭐ 新增：乘客上车/开始行程
     */
    suspend fun startTrip(orderId: Long): Boolean {
        return try {
            val apiService = com.example.myapplication.core.network.RetrofitClient.instance
            val result = apiService.passengerBoard(orderId)
            if (result.isSuccess()) {
                Log.d("OrderTrackingVM", "✅ 乘客已上车，行程开始")
                // 更新本地状态
                val currentState = _uiState.value
                if (currentState is OrderTrackingUiState.Success) {
                    _uiState.value = OrderTrackingUiState.Success(
                        currentState.order.copy(status = 5)  // 行程中
                    )
                }
                true
            } else {
                Log.e("OrderTrackingVM", "❌ 开始行程失败: ${result.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("OrderTrackingVM", "❌ 开始行程异常", e)
            false
        }
    }

    /**
     * ⭐ 新增：到达目的地/完成行程
     */
    suspend fun finishTrip(orderId: Long): Boolean {
        return try {
            val apiService = com.example.myapplication.core.network.RetrofitClient.instance
            val result = apiService.completeTrip(orderId)
            if (result.isSuccess()) {
                Log.d("OrderTrackingVM", "✅ 行程已完成")
                // 更新本地状态
                val currentState = _uiState.value
                if (currentState is OrderTrackingUiState.Success) {
                    _uiState.value = OrderTrackingUiState.Success(
                        currentState.order.copy(status = 6)  // 已完成
                    )
                }
                true
            } else {
                Log.e("OrderTrackingVM", "❌ 完成行程失败: ${result.message}")
                false
            }
        } catch (e: Exception) {
            Log.e("OrderTrackingVM", "❌ 完成行程异常", e)
            false
        }
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        wsJob?.cancel()
        Log.d("OrderTrackingVM", "🧹 ViewModel 清理完成")
    }
}

/**
 * UI 状态密封类
 */
sealed class OrderTrackingUiState {
    object Loading : OrderTrackingUiState()
    data class Success(val order: Order) : OrderTrackingUiState()
    data class Error(val message: String) : OrderTrackingUiState()
}

/**
 * ⭐ 新增：事件密封类（用于UI提示）
 */
sealed class OrderTrackingEvent {
    /**
     * 代叫车确认结果
     * @param confirmed true-同意，false-拒绝
     * @param rejectReason 拒绝原因
     */
    data class ProxyOrderConfirmed(val confirmed: Boolean, val rejectReason: String?) : OrderTrackingEvent()
    
    /**
     * ⭐ 新增：收到司机接单请求（需要弹窗确认）
     */
    data class DriverRequestReceived(val wsMessage: com.example.myapplication.data.model.WsMessage) : OrderTrackingEvent()
    
    /**
     * ⭐ 新增：用户拒绝司机接单
     */
    data class DriverRejected(val message: String) : OrderTrackingEvent()
}
