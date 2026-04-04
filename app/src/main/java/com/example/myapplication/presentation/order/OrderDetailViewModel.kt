package com.example.myapplication.presentation.order

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _order = MutableStateFlow<Order?>(null)
    val order: StateFlow<Order?> = _order.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 加载订单详情
     * @param orderId 订单 ID
     */
    fun loadOrder(orderId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                Log.d("OrderDetailViewModel", "开始加载订单详情，orderId=$orderId")
                val response = api.getOrder(orderId)
                if (response.isSuccess()) {
                    _order.value = response.data
                    Log.d("OrderDetailViewModel", "订单加载成功：${response.data?.orderNo}")
                } else {
                    _errorMessage.value = response.message ?: "加载失败"
                    Log.e("OrderDetailViewModel", "订单加载失败：${response.message}")
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "网络错误"
                _errorMessage.value = errorMsg
                Log.e("OrderDetailViewModel", "订单加载异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 取消订单
     * @param orderId 订单 ID
     */
    fun cancelOrder(orderId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                Log.d("OrderDetailViewModel", "开始取消订单，orderId=$orderId")
                val response = api.cancelOrder(orderId)
                if (response.isSuccess()) {
                    Log.d("OrderDetailViewModel", "订单取消成功")
                    // 重新加载订单，刷新状态
                    loadOrder(orderId)
                } else {
                    _errorMessage.value = response.message ?: "取消失败"
                    Log.e("OrderDetailViewModel", "订单取消失败：${response.message}")
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "网络错误"
                _errorMessage.value = errorMsg
                Log.e("OrderDetailViewModel", "订单取消异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 重置订单状态
     */
    fun resetOrder() {
        _order.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("OrderDetailViewModel", "ViewModel 已清理")
    }
}