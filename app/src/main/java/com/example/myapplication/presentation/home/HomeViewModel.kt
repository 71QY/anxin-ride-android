package com.example.myapplication.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _destination = MutableStateFlow("")
    val destination: StateFlow<String> = _destination

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState

    fun updateDestination(text: String) {
        _destination.value = text
    }

    fun createOrder(destName: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading
            // 模拟网络请求延迟
            delay(1500)
            // 生成模拟订单
            val mockOrder = Order(
                id = 1L,
                orderNo = "AX" + System.currentTimeMillis(),
                userId = 1L,
                driverId = null,
                destLat = lat,
                destLng = lng,
                destAddress = destName,
                status = 0,
                platformUsed = "gaode",
                estimatePrice = 25.0,
                createTime = "2025-03-10 12:00:00"
            )
            _orderState.value = OrderState.Success(mockOrder)
        }
    }

    sealed class OrderState {
        object Idle : OrderState()
        object Loading : OrderState()
        data class Success(val order: Order) : OrderState()
        data class Error(val message: String) : OrderState()
    }
}