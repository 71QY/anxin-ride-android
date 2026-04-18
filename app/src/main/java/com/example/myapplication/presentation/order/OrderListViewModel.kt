package com.example.myapplication.presentation.order

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
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
    private val orderRepository: IOrderRepository,
    private val apiService: ApiService,  // ⭐ 新增：用于获取代叫订单
    private val tokenManager: TokenManager  // ⭐ 新增：用于获取用户ID
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
        // ⭐ 修复：不在 init 中自动加载，等待 OrderListScreen 传入 isElderMode 参数
        // loadOrders()
    }

    /**
     * 加载订单列表
     * @param loadMore 是否加载更多（上拉分页）
     * @param isElderMode 是否为长辈模式（长辈不加载代叫订单）
     */
    fun loadOrders(loadMore: Boolean = false, isElderMode: Boolean = false) {
        if (_isLoading.value) return
        if (loadMore && !hasMore) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _errorMessage.value = "用户未登录"
                    _isLoading.value = false
                    return@launch
                }
                
                Log.d("OrderListViewModel", "📦 开始加载订单列表，userId=$userId, isElderMode=$isElderMode")
                
                // ⭐ 新增：同时加载普通订单和代叫订单（长辈模式跳过代叫订单）
                val page = if (loadMore) currentPage + 1 else 1
                
                // 1. 加载普通订单
                val orderResult = orderRepository.getOrderList(page = page, size = pageSize)
                val normalOrders = if (orderResult.isSuccess()) {
                    val pageData = orderResult.data
                    if (pageData != null) {
                        currentPage = pageData.page
                        hasMore = currentPage * pageData.size < pageData.total
                        pageData.list
                    } else {
                        emptyList()
                    }
                } else {
                    Log.e("OrderListViewModel", "❌ 加载普通订单失败：${orderResult.message}")
                    emptyList()
                }
                
                // 2. 加载代叫订单（仅普通账户，长辈账户跳过此步骤避免 403）
                val proxyOrders = if (!isElderMode) {
                    val proxyResult = apiService.getProxyOrders(userId)
                    if (proxyResult.isSuccess() && proxyResult.data != null) {
                        Log.d("OrderListViewModel", "✅ 加载代叫订单成功，数量=${proxyResult.data!!.size}")
                        proxyResult.data!!
                    } else {
                        Log.w("OrderListViewModel", "⚠️ 加载代叫订单失败：${proxyResult.message}")
                        emptyList()
                    }
                } else {
                    Log.d("OrderListViewModel", "⚠️ 长辈模式，跳过加载代叫订单")
                    emptyList()
                }
                
                // 3. 合并订单列表（去重，按创建时间排序）
                val allOrders = (normalOrders + proxyOrders)
                    .distinctBy { it.id }  // 去重
                    .sortedByDescending { it.createTime }  // 按时间倒序
                
                if (loadMore) {
                    // 加载更多：追加到列表末尾
                    _orders.value = _orders.value + allOrders
                } else {
                    // 首次加载或刷新：替换列表
                    _orders.value = allOrders
                }
                
                Log.d("OrderListViewModel", "📊 订单列表加载完成：普通=${normalOrders.size}, 代叫=${proxyOrders.size}, 总计=${_orders.value.size}")
                
            } catch (e: Exception) {
                Log.e("OrderListViewModel", "❌ 加载订单异常", e)
                _errorMessage.value = "加载失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
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