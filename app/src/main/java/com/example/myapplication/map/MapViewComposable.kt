package com.example.myapplication.map

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Poi

@Composable
fun MapViewComposable(
    modifier: Modifier = Modifier,
    onMapReady: (AMap) -> Unit,
    onMapClick: (LatLng) -> Unit,
    onPoiClick: (Poi) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    var aMap by remember { mutableStateOf<AMap?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    mapView.onCreate(Bundle())
                    Log.d("MapViewComposable", "MapView onCreate")
                }
                // ⭐ 修改：移除 onStart 和 onStop，MapView 不需要这些方法
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    Log.d("MapViewComposable", "MapView onResume")
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                    Log.d("MapViewComposable", "MapView onPause")
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    Log.d("MapViewComposable", "MapView onDestroy")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
            Log.d("MapViewComposable", "DisposableEffect disposed")
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            if (aMap == null) {
                aMap = view.map
                aMap?.let { map ->
                    onMapReady(map)
                    map.setOnMapClickListener { onMapClick(it) }
                    map.setOnPOIClickListener { poi -> onPoiClick(poi) }
                    Log.d("MapViewComposable", "地图加载完成")
                }
            }
        }
    )
}