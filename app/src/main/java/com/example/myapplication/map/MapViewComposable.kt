package com.example.myapplication.map

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView

@Composable
fun MapViewComposable(
    onMapReady: (AMap) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(Unit) {
        mapView.onCreate(Bundle())
        onDispose { mapView.onDestroy() }
    }

    AndroidView(
        factory = { mapView },
        update = { onMapReady(it.map) }
    )
}