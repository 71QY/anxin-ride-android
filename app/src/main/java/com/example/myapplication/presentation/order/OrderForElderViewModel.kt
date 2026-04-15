package com.example.myapplication.presentation.order

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.CreateOrderForElderRequest
import com.example.myapplication.data.model.ElderInfo
import com.example.myapplication.data.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 代叫车 ViewModel（亲友操作）
 */
@HiltViewModel
class OrderForElderViewModel @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    // 长辈列表
    private val _elders = MutableStateFlow<List<ElderInfo>>(emptyList())
    val elders: StateFlow<List<ElderInfo>> = _elders.asStateFlow()

    // 选中的长辈
    private val _selectedElder = MutableStateFlow<ElderInfo?>(null)
    val selectedElder: StateFlow<ElderInfo?> = _selectedElder.asStateFlow()

    // 目的地信息
    private val _poiName = MutableStateFlow("")
    val poiName: StateFlow<String> = _poiName.asStateFlow()

    private val _destLat = MutableStateFlow(0.0)
    val destLat: StateFlow<Double> = _destLat.asStateFlow()

    private val _destLng = MutableStateFlow(0.0)
    val destLng: StateFlow<Double> = _destLng.asStateFlow()

    // 乘客数量
    private val _passengerCount = MutableStateFlow(1)
    val passengerCount: StateFlow<Int> = _passengerCount.asStateFlow()

    // 备注
    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 订单创建结果
    private val _orderResult = MutableStateFlow<Order?>(null)
    val orderResult: StateFlow<Order?> = _orderResult.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadElders()
    }

    /**
     * 加载我的长辈列表
     */
    fun loadElders() {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _errorMessage.value = "用户未登录"
                    return@launch
                }

                Log.d("OrderForElderViewModel", "开始加载长辈列表")

                val response = api.getMyElders(userId)
                if (response.isSuccess()) {
                    _elders.value = response.data ?: emptyList()
                    Log.d("OrderForElderViewModel", "加载成功，共 ${_elders.value.size} 个长辈")
                } else {
                    _errorMessage.value = response.message ?: "加载失败"
                    Log.e("OrderForElderViewModel", "加载失败：${response.message}")
                }
            } catch (e: Exception) {
                Log.e("OrderForElderViewModel", "loadElders exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            }
        }
    }

    /**
     * 选择长辈
     */
    fun selectElder(elder: ElderInfo) {
        _selectedElder.value = elder
        Log.d("OrderForElderViewModel", "选中长辈：${elder.name}, ${elder.phone}")
    }

    /**
     * 更新目的地信息
     */
    fun updateDestination(poiName: String, lat: Double, lng: Double) {
        _poiName.value = poiName
        _destLat.value = lat
        _destLng.value = lng
    }

    /**
     * 更新乘客数量
     */
    fun updatePassengerCount(count: Int) {
        if (count in 1..6) {
            _passengerCount.value = count
        }
    }

    /**
     * 更新备注
     */
    fun updateRemark(remark: String) {
        _remark.value = remark
    }

    /**
     * 为长辈叫车
     */
    fun createOrderForElder() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // 校验输入
            if (_selectedElder.value == null) {
                _errorMessage.value = "请选择长辈"
                _isLoading.value = false
                return@launch
            }

            if (_poiName.value.isBlank()) {
                _errorMessage.value = "请输入目的地"
                _isLoading.value = false
                return@launch
            }

            if (_destLat.value == 0.0 || _destLng.value == 0.0) {
                _errorMessage.value = "目的地坐标无效"
                _isLoading.value = false
                return@launch
            }

            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _errorMessage.value = "用户未登录"
                    _isLoading.value = false
                    return@launch
                }

                // ⭐ 关键修复：按照后端API文档构造请求
                val request = CreateOrderForElderRequest(
                    elderId = _selectedElder.value!!.userId ?: 0L,      // ⭐ 必填：长辈的用户ID
                    startLat = null,                                     // ⭐ 起点纬度（可选，后端会自动获取）
                    startLng = null,                                     // ⭐ 起点经度（可选，后端会自动获取）
                    destLat = _destLat.value,                            // 终点纬度
                    destLng = _destLng.value,                            // 终点经度
                    destAddress = _poiName.value.trim(),                 // ⭐ 必填：目的地名称
                    needConfirm = true                                   // ⭐ 默认需要长辈确认
                )

                Log.d("OrderForElderViewModel", "📤 开始代叫车：${request.destAddress}")
                Log.d("OrderForElderViewModel", "📤 请求详情：elderId=${request.elderId}, destAddress=${request.destAddress}")

                val response = api.createOrderForElder(userId, request)
                if (response.isSuccess()) {
                    // ⭐ 修复：后端可能返回字符串或对象
                    val responseData = response.data
                    when (responseData) {
                        is Order -> {
                            _orderResult.value = responseData
                            Log.d("OrderForElderViewModel", "✅ 代叫车成功，订单ID：${responseData.id}")
                            clearForm()
                        }
                        is Map<*, *> -> {
                            // ⭐ 修复：后端返回 Map 类型（LinkedTreeMap），需要转换
                            Log.d("OrderForElderViewModel", "⚠️ 后端返回 Map 类型，尝试解析...")
                            try {
                                val orderId = (responseData["id"] as? Number)?.toLong()
                                val orderNo = responseData["orderNo"] as? String
                                val elderUserId = (responseData["elderUserId"] as? Number)?.toLong()
                                val destLat = (responseData["destLat"] as? Number)?.toDouble()
                                val destLng = (responseData["destLng"] as? Number)?.toDouble()
                                val destAddress = responseData["destAddress"] as? String
                                val status = (responseData["status"] as? Number)?.toInt()
                                
                                Log.d("OrderForElderViewModel", "✅ 代叫车成功")
                                Log.d("OrderForElderViewModel", "  - 订单ID: $orderId")
                                Log.d("OrderForElderViewModel", "  - 订单号: $orderNo")
                                Log.d("OrderForElderViewModel", "  - 长辈ID: $elderUserId")
                                Log.d("OrderForElderViewModel", "  - 目的地: $destAddress")
                                
                                // 创建一个临时 Order 对象用于UI显示
                                val tempOrder = Order(
                                    id = orderId ?: 0L,
                                    orderNo = orderNo ?: "",
                                    userId = elderUserId ?: 0L,
                                    driverId = null,
                                    guardianUserId = userId,
                                    destLat = destLat ?: 0.0,
                                    destLng = destLng ?: 0.0,
                                    poiName = null,
                                    destAddress = destAddress,
                                    platformUsed = null,
                                    platformOrderId = null,
                                    estimatePrice = null,
                                    status = status ?: 0,
                                    createTime = java.time.LocalDateTime.now().toString()
                                )
                                
                                _orderResult.value = tempOrder
                                clearForm()
                            } catch (e: Exception) {
                                Log.e("OrderForElderViewModel", "❌ 解析 Map 数据失败", e)
                                _errorMessage.value = "订单创建成功，但解析响应失败"
                            }
                        }
                        is String -> {
                            _errorMessage.value = "${response.message ?: "订单创建成功"}：$responseData"
                            Log.d("OrderForElderViewModel", "⚠️ 代叫车成功，但返回字符串：$responseData")
                        }
                        else -> {
                            _errorMessage.value = response.message ?: "订单创建成功，但未返回订单详情"
                            Log.w("OrderForElderViewModel", "⚠️ 代叫车成功，但返回未知类型：${responseData?.javaClass?.name}")
                        }
                    }
                } else {
                    // ⭐ 关键修复：后端返回的 403 错误需要明确提示
                    val errorMsg = response.message ?: "下单失败"
                    val userFriendlyMsg = when {
                        response.code == 403 -> "您未绑定该长辈，无法代叫车。请先在个人中心完成绑定。"
                        response.code == 401 -> "登录已过期，请重新登录"
                        response.code == 404 -> "长辈不存在或已解绑"
                        errorMsg.contains("未绑定") || errorMsg.contains("绑定") -> "请先在亲情守护中绑定该长辈"
                        else -> errorMsg
                    }
                    _errorMessage.value = userFriendlyMsg
                    Log.e("OrderForElderViewModel", "❌ 下单失败：code=${response.code}, message=$userFriendlyMsg")
                }
            } catch (e: Exception) {
                Log.e("OrderForElderViewModel", "createOrderForElder exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 清空表单
     */
    private fun clearForm() {
        _selectedElder.value = null
        _poiName.value = ""
        _destLat.value = 0.0
        _destLng.value = 0.0
        _passengerCount.value = 1
        _remark.value = ""
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 清除订单结果
     */
    fun clearOrderResult() {
        _orderResult.value = null
    }
}
