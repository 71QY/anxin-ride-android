package com.example.myapplication.presentation.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.network.RetrofitClient
import com.example.myapplication.data.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OrderDetailViewModel : ViewModel() {
    private val api = RetrofitClient.instance

    private val _order = MutableStateFlow<Order?>(null)
    val order: StateFlow<Order?> = _order

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun loadOrder(orderId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = api.getOrder(orderId)
                if (response.isSuccess()) {
                    _order.value = response.data
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelOrder(orderId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.cancelOrder(orderId)
                if (response.isSuccess()) {
                    loadOrder(orderId) // 重新加载订单，刷新状态
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}