package com.example.myapplication.presentation.home

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Poi
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.example.myapplication.core.utils.SpeechRecognizerHelper
import com.example.myapplication.data.model.Order
import com.example.myapplication.domain.repository.IOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject  // ⭐ 新增导入
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orderRepository: IOrderRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _destination = MutableStateFlow("")
    val destination: StateFlow<String> = _destination.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _orderState = MutableStateFlow<OrderState>(OrderState.Idle)
    val orderState: StateFlow<OrderState> = _orderState.asStateFlow()

    private val _isGeocoding = MutableStateFlow(false)
    val isGeocoding: StateFlow<Boolean> = _isGeocoding.asStateFlow()

    private val _geocodeError = MutableStateFlow<String?>(null)
    val geocodeError: StateFlow<String?> = _geocodeError.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PoiItem>>(emptyList())
    val searchResults: StateFlow<List<PoiItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedPoiForMap = MutableStateFlow<PoiItem?>(null)
    val selectedPoiForMap: StateFlow<PoiItem?> = _selectedPoiForMap.asStateFlow()

    private val _clickedLocation = MutableStateFlow<LatLng?>(null)
    val clickedLocation: StateFlow<LatLng?> = _clickedLocation.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var speechHelper: SpeechRecognizerHelper? = null
    private val addressCache = LruCache<Long, String>(20)

    private val requestId = AtomicLong(0)

    private fun getCacheKey(latLng: LatLng): Long {
        return (latLng.latitude * 10_000).toLong() * 10_000 + (latLng.longitude * 10_000).toLong()
    }

    fun updateDestination(text: String) {
        _destination.value = text
        _geocodeError.value = null
    }

    // ⭐ 修改：语音输入回调中解析JSON
    fun startVoiceInput(context: Context) {
        if (speechHelper == null) {
            speechHelper = SpeechRecognizerHelper(context) { result ->
                val text = parseVoiceResult(result)  // 解析原始结果
                updateDestination(text)
                searchPoi(text)
                _isListening.value = false
            }
        }
        _isListening.value = true
        speechHelper?.startListening()
    }

    // ⭐ 新增：解析科大讯飞格式的JSON，提取文字
    private fun parseVoiceResult(raw: String): String {
        return try {
            val json = JSONObject(raw)
            val ws = json.getJSONArray("ws")
            val sb = StringBuilder()
            for (i in 0 until ws.length()) {
                val cw = ws.getJSONObject(i).getJSONArray("cw")
                if (cw.length() > 0) {
                    sb.append(cw.getJSONObject(0).getString("w"))
                }
            }
            sb.toString()
        } catch (e: Exception) {
            // 如果解析失败，直接返回原字符串（可能已经是纯文本）
            raw
        }
    }

    fun stopVoiceInput() {
        speechHelper?.destroy()
        _isListening.value = false
    }

    fun startLocation(context: Context) {
        val locationClient = AMapLocationClient(context)
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
        }
        locationClient.setLocationOption(option)
        locationClient.setLocationListener { aMapLocation: AMapLocation ->
            if (aMapLocation.errorCode == 0) {
                val latLng = LatLng(aMapLocation.latitude, aMapLocation.longitude)
                _currentLocation.value = latLng
                Log.d("HomeViewModel", "定位成功: $latLng")
            } else {
                Log.e("HomeViewModel", "定位失败: ${aMapLocation.errorInfo}")
            }
            locationClient.onDestroy()
        }
        locationClient.startLocation()
    }

    fun updateCurrentLocation(lat: Double, lng: Double) {
        _currentLocation.value = LatLng(lat, lng)
    }

    fun onMapClick(latLng: LatLng) {
        Log.d("HomeViewModel", "onMapClick called, latLng=$latLng")
        _clickedLocation.value = latLng

        val currentReqId = requestId.incrementAndGet()
        val cacheKey = getCacheKey(latLng)
        val cachedAddress = addressCache.get(cacheKey)

        if (cachedAddress != null) {
            Log.d("HomeViewModel", "缓存命中: $cachedAddress")
            updateDestination(cachedAddress)
            return
        }

        Log.d("HomeViewModel", "缓存未命中，开始逆地理编码，请求ID=$currentReqId")
        _destination.value = ""
        updateDestination("正在获取地址...")
        _isGeocoding.value = true
        _geocodeError.value = null

        val startTime = System.currentTimeMillis()
        val minDisplayMs = 500L

        viewModelScope.launch {
            var address: String? = null
            var errorMsg: String? = null
            try {
                address = withTimeout(8000) {
                    suspendGeocode(latLng, currentReqId)
                }
                if (address != null) {
                    addressCache.put(cacheKey, address)
                    Log.d("HomeViewModel", "逆地理编码成功: $address")
                } else {
                    errorMsg = "获取地址失败"
                    Log.e("HomeViewModel", "逆地理编码失败，地址为空")
                }
            } catch (e: TimeoutCancellationException) {
                errorMsg = "获取地址超时"
                Log.e("HomeViewModel", "逆地理编码超时")
            } catch (e: Exception) {
                errorMsg = "获取地址失败"
                Log.e("HomeViewModel", "逆地理编码异常", e)
            } finally {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < minDisplayMs) {
                    delay(minDisplayMs - elapsed)
                }
                _isGeocoding.value = false
                if (address != null) {
                    updateDestination(address)
                } else {
                    updateDestination("")
                    _geocodeError.value = errorMsg
                }
            }
        }
    }

    private suspend fun suspendGeocode(latLng: LatLng, reqId: Long): String? = suspendCancellableCoroutine { continuation ->
        val geocodeSearch = GeocodeSearch(appContext)
        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                if (reqId != requestId.get()) {
                    Log.d("HomeViewModel", "忽略旧请求回调，reqId=$reqId, current=${requestId.get()}")
                    return
                }
                if (rCode == AMapException.CODE_AMAP_SUCCESS) {
                    val address = result?.regeocodeAddress?.formatAddress
                    continuation.resume(address)
                } else {
                    continuation.resume(null)
                }
            }
            override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
        })
        val query = RegeocodeQuery(LatLonPoint(latLng.latitude, latLng.longitude), 200f, GeocodeSearch.AMAP)
        geocodeSearch.getFromLocationAsyn(query)

        continuation.invokeOnCancellation {
            Log.d("HomeViewModel", "逆地理编码协程被取消")
        }
    }

    fun onPoiClick(poi: Poi) {
        val latLng = LatLng(poi.coordinate.latitude, poi.coordinate.longitude)
        _clickedLocation.value = latLng
        updateDestination(poi.name)
    }

    fun searchPoi(keyword: String) {
        if (keyword.isBlank()) {
            Log.e("HomeViewModel", "搜索关键词为空")
            return
        }
        _isSearching.value = true
        _searchResults.value = emptyList()
        Log.d("HomeViewModel", "开始搜索关键词：$keyword")

        val location = _currentLocation.value
        Log.d("HomeViewModel", "当前定位：$location")

        val query = PoiSearch.Query(keyword, "", "")
        query.pageSize = 20

        val poiSearch = PoiSearch(appContext, query)

        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                _isSearching.value = false
                if (rCode == AMapException.CODE_AMAP_SUCCESS) {
                    var pois = result?.pois ?: emptyList()
                    val location = _currentLocation.value
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        pois = pois.sortedBy { poi ->
                            val poiLatLng = LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude)
                            AMapUtils.calculateLineDistance(currentLatLng, poiLatLng)
                        }
                        Log.d("HomeViewModel", "按距离排序完成")
                    }
                    _searchResults.value = pois
                    Log.d("HomeViewModel", "搜索到 ${pois.size} 个地点")
                } else {
                    Log.e("HomeViewModel", "POI搜索失败，错误码：$rCode")
                    _geocodeError.value = "搜索失败"
                }
            }
            override fun onPoiItemSearched(poiItem: PoiItem?, rCode: Int) {}
        })
        poiSearch.searchPOIAsyn()
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun selectPoi(poi: PoiItem) {
        updateDestination(poi.title)
        clearSearchResults()
        _selectedPoiForMap.value = poi
    }

    fun clearSelectedPoi() {
        _selectedPoiForMap.value = null
    }

    fun createOrder(destName: String) {
        viewModelScope.launch {
            _orderState.value = OrderState.Loading
            val start = _currentLocation.value
            if (start == null) {
                _orderState.value = OrderState.Error("无法获取当前位置，请稍后重试")
                return@launch
            }
            val destLat = 30.243
            val destLng = 120.15
            val result = orderRepository.createOrder(destName, destLat, destLng)
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

    fun resetOrderState() {
        _orderState.value = OrderState.Idle
    }

    override fun onCleared() {
        speechHelper?.destroy()
        super.onCleared()
    }

    sealed class OrderState {
        object Idle : OrderState()
        object Loading : OrderState()
        data class Success(val order: Order) : OrderState()
        data class Error(val message: String) : OrderState()
    }
}