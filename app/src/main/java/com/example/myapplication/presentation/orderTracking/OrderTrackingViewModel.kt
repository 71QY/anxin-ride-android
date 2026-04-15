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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class OrderTrackingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val webSocketClient: ChatWebSocketClient
) : ViewModel() {

    // ==================== UI State ====================
    
    private val _uiState = MutableStateFlow<OrderTrackingUiState>(OrderTrackingUiState.Loading)
    val uiState: StateFlow<OrderTrackingUiState> = _uiState.asStateFlow()

    private val _driverLocation = MutableStateFlow<LatLng?>(null)
    val driverLocation: StateFlow<LatLng?> = _driverLocation.asStateFlow()

    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

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
                // 注意：这里假设已经建立了 WebSocket 连接
                // 如果没有连接，需要在调用此方法前确保已连接
                
                webSocketClient.messages.collect { message ->
                    try {
                        val wsMessage = Json.decodeFromString<WsMessage>(message)
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
            WsMessage.TYPE_ORDER_ACCEPTED -> {
                Log.d("OrderTrackingVM", "🚕 收到 ORDER_ACCEPTED")
                handleOrderAccepted(wsMessage)
            }
            
            WsMessage.TYPE_DRIVER_LOCATION -> {
                Log.d("OrderTrackingVM", "📍 收到 DRIVER_LOCATION")
                handleDriverLocation(wsMessage)
            }
            
            WsMessage.TYPE_DRIVER_ARRIVED -> {
                Log.d("OrderTrackingVM", "🎉 收到 DRIVER_ARRIVED")
                handleDriverArrived(wsMessage)
            }
            
            WsMessage.TYPE_ORDER_CREATED -> {
                Log.d("OrderTrackingVM", "📦 收到 ORDER_CREATED（代叫车）")
                handleOrderCreated(wsMessage)
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
                val updatedOrder = currentState.order.copy(
                    status = 4  // 已到达
                )
                
                _uiState.value = OrderTrackingUiState.Success(updatedOrder)
                _etaMinutes.value = 0
                
                // 更新司机位置到起点
                if (wsMessage.driverLat != null && wsMessage.driverLng != null) {
                    _driverLocation.value = LatLng(wsMessage.driverLat, wsMessage.driverLng)
                }
            }
        }
    }

    /**
     * 处理代叫车创建消息
     */
    private fun handleOrderCreated(wsMessage: WsMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            wsMessage.data?.let { data ->
                val tempOrder = Order(
                    id = data.orderId ?: 0L,
                    orderNo = data.orderNo ?: "",
                    userId = data.userId,
                    driverId = null,
                    guardianUserId = data.guardianUserId,
                    destLat = data.destLat,
                    destLng = data.destLng,
                    poiName = data.poiName,
                    destAddress = data.destAddress,
                    platformUsed = null,
                    platformOrderId = null,
                    estimatePrice = data.estimatePrice,
                    actualPrice = null,
                    createTime = data.createTime ?: "",
                    status = data.status ?: 0,
                    driverName = null,
                    driverPhone = null,
                    driverAvatar = null,
                    carNo = null,
                    carType = null,
                    carColor = null,
                    rating = null,
                    eta = null,
                    startLat = null,
                    startLng = null,
                    driverLat = null,
                    driverLng = null
                )
                
                _uiState.value = OrderTrackingUiState.Success(tempOrder)
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
                // TODO: 显示提示“长辈已确认，司机正在赶来”
            } else {
                Log.d("OrderTrackingVM", "❌ 长辈已拒绝代叫车，原因：$rejectReason")
                // TODO: 显示提示“长辈拒绝了代叫车，原因：XXX”
                // 可选：关闭当前页面或返回上一页
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
