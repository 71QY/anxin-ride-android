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
                Log.d("MapViewComposable", "⏳ 尝试获取 AMap 实例...")
                aMap = view.map
                
                if (aMap != null) {
                    Log.d("MapViewComposable", "✅ AMap 实例获取成功")
                    Log.d("MapViewComposable", "🔥🔥🔥 即将调用 onMapReady 回调 🔥🔥🔥")
                    Log.d("MapViewComposable", "🔥 onMapReady 参数是否为 null: ${onMapReady == null}")
                    try {
                        onMapReady(aMap!!)
                        Log.d("MapViewComposable", "🔥🔥🔥 onMapReady 回调已调用完成 🔥🔥🔥")
                    } catch (e: Exception) {
                        Log.e("MapViewComposable", "❌ onMapReady 回调执行失败", e)
                        e.printStackTrace()
                    }
                    aMap!!.setOnMapClickListener { onMapClick(it) }
                    aMap!!.setOnPOIClickListener { poi -> onPoiClick(poi) }
                } else {
                    Log.w("MapViewComposable", "⚠️ AMap 实例为 null，等待下次 update")
                }
            }
        }
    )
}