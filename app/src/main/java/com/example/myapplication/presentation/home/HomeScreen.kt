package com.example.myapplication.presentation.home

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.example.myapplication.map.MapViewComposable
import com.google.accompanist.permissions.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToOrder: (Long) -> Unit
) {
    val context = LocalContext.current

    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val isGeocoding by viewModel.isGeocoding.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val selectedPoiForMap by viewModel.selectedPoiForMap.collectAsStateWithLifecycle()
    val clickedLocation by viewModel.clickedLocation.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val orderState by viewModel.orderState.collectAsStateWithLifecycle()  // 新增：监听订单状态

    val aMapState = remember { mutableStateOf<AMap?>(null) }

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    var showMapHint by remember { mutableStateOf(true) }

    fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }

    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) audioPermissionState.launchPermissionRequest()
        if (!locationPermissionState.status.isGranted) locationPermissionState.launchPermissionRequest()

        delay(5000)
        showMapHint = false
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            viewModel.startLocation(context)
        }
    }

    LaunchedEffect(clickedLocation) {
        val aMap = aMapState.value
        clickedLocation?.let {
            aMap?.clear()
            aMap?.addMarker(com.amap.api.maps.model.MarkerOptions().position(it))
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
        }
    }

    LaunchedEffect(selectedPoiForMap) {
        val aMap = aMapState.value
        selectedPoiForMap?.let {
            val latLng = LatLng(it.latLonPoint.latitude, it.latLonPoint.longitude)
            aMap?.clear()
            aMap?.addMarker(com.amap.api.maps.model.MarkerOptions().position(latLng))
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        }
    }

    // 🔧 新增：监听订单状态，成功后导航，失败时显示提示
    LaunchedEffect(orderState) {
        when (orderState) {
            is HomeViewModel.OrderState.Success -> {
                val order = (orderState as HomeViewModel.OrderState.Success).order
                onNavigateToOrder(order.id)
                viewModel.resetOrderState()
            }
            is HomeViewModel.OrderState.Error -> {
                Toast.makeText(context, (orderState as HomeViewModel.OrderState.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.resetOrderState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                MapViewComposable(
                    onMapReady = { aMap ->
                        aMapState.value = aMap
                        val style = MyLocationStyle()
                        style.myLocationType(MyLocationStyle.LOCATION_TYPE_SHOW)
                        aMap.setMyLocationStyle(style)
                        aMap.isMyLocationEnabled = true

                        aMap.setOnMyLocationChangeListener { location ->
                            viewModel.updateCurrentLocation(location.latitude, location.longitude)
                        }
                    },
                    onMapClick = {
                        vibrate()
                        Toast.makeText(context, "已选择位置", Toast.LENGTH_SHORT).show()
                        viewModel.onMapClick(it)
                    },
                    onPoiClick = { poi -> viewModel.onPoiClick(poi) }
                )

                androidx.compose.animation.AnimatedVisibility(visible = showMapHint) {
                    Text(
                        "点击地图选点",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(8.dp)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        currentLocation?.let {
                            aMapState.value?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
                        } ?: Toast.makeText(context, "正在获取位置...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    containerColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "定位", tint = Color.Black)
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isGeocoding,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = destination,
                        onValueChange = { viewModel.updateDestination(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("你要去哪？") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isSearching,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { viewModel.searchPoi(destination) }) {
                            Icon(Icons.Default.Search, null)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSearching,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = {
                            if (audioPermissionState.status.isGranted) {
                                viewModel.startVoiceInput(context)
                            } else {
                                Toast.makeText(context, "需要录音权限", Toast.LENGTH_SHORT).show()
                                audioPermissionState.launchPermissionRequest()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Mic, null)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    items(searchResults) { poi ->
                        Text(
                            text = poi.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPoi(poi) }
                                .padding(12.dp)
                        )
                        Divider()
                    }
                }
            }

            // 🔧 新增：确认叫车按钮
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.createOrder(destination) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = destination.isNotBlank() && currentLocation != null
            ) {
                Text("确认叫车")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = isListening,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("正在聆听...", color = Color.White, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.stopVoiceInput() }) {
                        Text("结束")
                    }
                }
            }
        }
    }
}