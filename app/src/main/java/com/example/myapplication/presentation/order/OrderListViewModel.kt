package com.example.myapplication.presentation.order

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

@HiltViewModel
class OrderListViewModel @Inject constructor(
    private val orderRepository: IOrderRepository
) : ViewModel() {

    // 订单列表数据
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 分页相关
    private var currentPage = 1
    private var hasMore = true
    private val pageSize = 10

    init {
        loadOrders()
    }

    /**
     * 加载订单列表
     * @param loadMore 是否加载更多（上拉分页）
     */
    fun loadOrders(loadMore: Boolean = false) {
        if (_isLoading.value) return
        if (loadMore && !hasMore) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val page = if (loadMore) currentPage + 1 else 1
            val result = orderRepository.getOrderList(page = page, size = pageSize)
            if (result.isSuccess()) {
                val pageData = result.data
                if (pageData != null) {
                    if (loadMore) {
                        // 加载更多：追加到列表末尾
                        _orders.value = _orders.value + pageData.list
                    } else {
                        // 首次加载或刷新：替换列表
                        _orders.value = pageData.list
                    }
                    currentPage = pageData.page
                    hasMore = currentPage * pageData.size < pageData.total
                }
            } else {
                _errorMessage.value = result.message
            }
            _isLoading.value = false
        }
    }

    /**
     * 刷新列表（重置分页，重新加载第一页）
     */
    fun refresh() {
        currentPage = 1
        hasMore = true
        loadOrders()
    }
}