package com.example.myapplication.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.Order
import com.example.myapplication.domain.repository.IOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orderRepository: IOrderRepository
) : ViewModel() {

    private val _destination = MutableStateFlow("")
    val destination: StateFlow<String> = _destination.asStateFlow()

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState.asStateFlow()

    fun updateDestination(text: String) {
        _destination.value = text
    }

    fun createOrder(destName: String, lat: Double, lng: Double) {
        Log.d("HomeViewModel", "创建订单，目的地：$destName, 坐标：$lat, $lng")
        viewModelScope.launch {
            _orderState.value = OrderState.Loading
            val result = orderRepository.createOrder(destName, lat, lng)
            if (result.isSuccess()) {
                result.data?.let { order ->
                    _orderState.value = OrderState.Success(order)
                } ?: run {
                    _orderState.value = OrderState.Error("返回数据为空")
                }
            } else {
                _orderState.value = OrderState.Error(result.message ?: "未知错误")
            }
        }
    }

    sealed class OrderState {
        object Idle : OrderState()
        object Loading : OrderState()
        data class Success(val order: Order) : OrderState()
        data class Error(val message: String) : OrderState()
    }
}