package com.example.myapplication.core.utils

import android.util.Log
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局位置管理器（单例）
 * 用于在多个 ViewModel 之间共享当前位置
 */
object LocationManager {
    private const val TAG = "LocationManager"
    
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()
    
    /**
     * 更新当前位置
     */
    fun updateLocation(lat: Double, lng: Double) {
        val newLocation = LatLng(lat, lng)
        _currentLocation.value = newLocation
        Log.d(TAG, "📍 更新全局位置: lat=$lat, lng=$lng")
    }
    
    /**
     * 获取当前位置
     */
    fun getLocation(): LatLng? {
        return _currentLocation.value
    }
    
    /**
     * 清除位置
     */
    fun clearLocation() {
        _currentLocation.value = null
        Log.d(TAG, "🗑️ 清除全局位置")
    }
}
