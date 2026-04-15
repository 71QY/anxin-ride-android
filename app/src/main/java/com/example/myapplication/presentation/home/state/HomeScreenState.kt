package com.example.myapplication.presentation.home.state

import androidx.compose.runtime.Immutable
import com.amap.api.maps.model.LatLng
import com.example.myapplication.data.model.ElderInfo
import com.example.myapplication.data.model.GuardianInfo
import com.example.myapplication.data.model.PoiDetail
import com.example.myapplication.presentation.home.HomeViewModel

/**
 * 长辈端UI状态
 * 
 * 职责：封装长辈模式的所有UI状态，与NormalUiState完全隔离
 */
@Immutable
data class ElderUiState(
    val currentLocation: LatLng? = null,
    val locationAccuracy: Float? = null,
    val orderState: HomeViewModel.OrderState = HomeViewModel.OrderState.Idle,
    val guardianInfoList: List<GuardianInfo> = emptyList(),
    val isFollowingLocation: Boolean = true,
    val isMapFullScreen: Boolean = false,
    val toastMessage: String? = null,
    
    // 代叫车相关
    val showProxyOrderConfirmDialog: Boolean = false,
    val pendingProxyOrderId: Long? = null,
    val proxyOrderRequesterName: String? = null,
    val proxyOrderDestination: String? = null,
    
    // 呼叫相关
    val showCallGuardianDialog: Boolean = false,
    val guardianPhone: String? = null,
    val guardianName: String? = null,
    val showCallDriverDialog: Boolean = false,
    val driverPhone: String? = null,
    val driverName: String? = null
) {
    companion object {
        val Initial = ElderUiState()
    }
}

/**
 * 普通用户UI状态
 * 
 * 职责：封装普通模式的所有UI状态，与ElderUiState完全隔离
 */
@Immutable
data class NormalUiState(
    val destination: String = "",
    val currentLocation: LatLng? = null,
    val locationAccuracy: Float? = null,
    val clickedLocation: LatLng? = null,
    val selectedPoiForMap: Any? = null,
    val poiDetail: PoiDetail? = null,
    val showPoiDetailDialog: Boolean = false,
    
    // 搜索相关
    val searchResults: List<Any> = emptyList(),
    val backendPoiResults: List<com.example.myapplication.data.model.PoiResponse> = emptyList(),
    val isSearching: Boolean = false,
    val isGeocoding: Boolean = false,
    val geocodeError: String? = null,
    
    // 语音相关
    val isListening: Boolean = false,
    val voiceText: String = "",
    val showDialectDialog: Boolean = false,
    val currentAccent: String = "mandarin",
    val currentLanguage: String = "zh_cn",
    
    // 订单相关
    val orderState: HomeViewModel.OrderState = HomeViewModel.OrderState.Idle,
    val isCreatingOrder: Boolean = false,
    
    // 帮长辈叫车相关
    val elderList: List<ElderInfo> = emptyList(),
    val showProxyOrderDialog: Boolean = false,
    val selectedElderForProxy: ElderInfo? = null,
    val isSubmittingProxyOrder: Boolean = false,
    val showOrderTypeDialog: Boolean = false,
    
    // UI状态
    val bottomPanelHeight: Int = 250,
    val showMapHint: Boolean = true,
    val showMapSettingsDialog: Boolean = false,
    val mapTextSizeLevel: Int = 1,
    val showPoiDialog: Boolean = false,
    val isNationwideSearch: Boolean = false,
    val showAgentSheet: Boolean = false
) {
    companion object {
        val Initial = NormalUiState()
    }
}
