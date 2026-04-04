package com.example.myapplication.presentation.home

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.example.myapplication.data.model.PoiResponse
import com.example.myapplication.map.MapViewComposable
import com.google.accompanist.permissions.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToProfile: () -> Unit,
    onStartFloatService: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onNavigateToOrder: (String) -> Unit,
    onNavigateToChat: () -> Unit = {}
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val showPoiDetailDialog by viewModel.showPoiDetailDialog.collectAsStateWithLifecycle()
    val poiDetail by viewModel.poiDetail.collectAsStateWithLifecycle()
    val selectedPoiForMap by viewModel.selectedPoiForMap.collectAsStateWithLifecycle()
    val currentLocationState by viewModel.currentLocation.collectAsStateWithLifecycle()
    
    val locationPermissionState = rememberPermissionState(permission = android.Manifest.permission.ACCESS_FINE_LOCATION)
    
    val context = LocalContext.current
    
    LaunchedEffect(locationPermissionState.status) {
        if (locationPermissionState.status == PermissionStatus.Granted) {
            Log.d("HomeScreen", "✅ 定位权限已授予，请求定位（仅一次）")
            viewModel.startLocation(context)
        }
    }
    
    val isGeocoding by viewModel.isGeocoding.collectAsStateWithLifecycle()
    val geocodeError by viewModel.geocodeError.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val backendPoiResults by viewModel.backendPoiResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val clickedLocation by viewModel.clickedLocation.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val locationAccuracy by viewModel.locationAccuracy.collectAsStateWithLifecycle()
    val orderState by viewModel.orderState.collectAsStateWithLifecycle()
    val isCreatingOrder by viewModel.isCreatingOrder.collectAsStateWithLifecycle()

    var showPoiDialog by remember { mutableStateOf(false) }
    var isNationwideSearch by remember { mutableStateOf(false) }
    var showAgentSheet by remember { mutableStateOf(false) }

    val aMapState = remember { mutableStateOf<AMap?>(null) }

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    var showMapHint by remember { mutableStateOf(true) }
    
    // ⭐ 新增：底部面板高度状态，支持拖动调整
    var bottomPanelHeight by remember { mutableStateOf(250.dp) }
    val minPanelHeight = 150.dp
    val maxPanelHeight = 600.dp

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
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
        
        delay(5000)
        showMapHint = false
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            Log.d("HomeScreen", "✅ 权限状态变化：已授予，但不再重复定位")
            // ⭐ 修改：移除重复的定位调用，因为上面已经处理了
        }
    }
    
    LaunchedEffect(backendPoiResults) {
        Log.d("HomeScreen", "=== backendPoiResults 变化 ===")
        Log.d("HomeScreen", "结果数量：${backendPoiResults.size}")
        if (backendPoiResults.isNotEmpty()) {
            showPoiDialog = true
            Log.d("HomeScreen", "显示 POI 选择对话框")
            backendPoiResults.forEachIndexed { index, poi ->
                Log.d("HomeScreen", "[$index] ${poi.name} - ${poi.address} (${poi.distance}m)")
            }
        } else {
            Log.w("HomeScreen", "后端搜索结果为空")
        }
    }

    LaunchedEffect(destination) {
        Log.d("HomeScreen", "=== 目的地输入框变化 ===")
        Log.d("HomeScreen", "输入内容：'$destination'")
        Log.d("HomeScreen", "是否为空：${destination.isBlank()}")
    }

    LaunchedEffect(clickedLocation) {
        Log.d("HomeScreen", "=== clickedLocation 变化 ===")
        Log.d("HomeScreen", "点击位置：$clickedLocation")
        val aMap = aMapState.value
        clickedLocation?.let {
            Log.d("HomeScreen", "在地图上标记位置")
            aMap?.clear()
            aMap?.addMarker(com.amap.api.maps.model.MarkerOptions().position(it))
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
        }
    }

    LaunchedEffect(selectedPoiForMap) {
        Log.d("HomeScreen", "=== selectedPoiForMap 变化 ===")
        val aMap = aMapState.value
        val poi = selectedPoiForMap
        Log.d("HomeScreen", "选中的 POI: $poi")
        if (poi != null) {
            try {
                val latLonPointClass = Class.forName("com.amap.api.services.core.LatLonPoint")
                val getLatLonPointMethod = poi.javaClass.getMethod("getLatLonPoint")
                val latLonPoint = getLatLonPointMethod.invoke(poi)
                val getLatitudeMethod = latLonPointClass.getMethod("getLatitude")
                val getLongitudeMethod = latLonPointClass.getMethod("getLongitude")
                val latitude = getLatitudeMethod.invoke(latLonPoint) as Double
                val longitude = getLongitudeMethod.invoke(latLonPoint) as Double
                
                val getNameMethod = poi.javaClass.getMethod("getName")
                val name = getNameMethod.invoke(poi) as String
                
                val latLng = LatLng(latitude, longitude)
                Log.d("HomeScreen", "POI 坐标：lat=$latitude, lng=$longitude, name=$name")
                aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                aMap?.addMarker(
                    com.amap.api.maps.model.MarkerOptions()
                        .position(latLng)
                        .title(name)
                        .icon(com.amap.api.maps.model.BitmapDescriptorFactory.defaultMarker(com.amap.api.maps.model.BitmapDescriptorFactory.HUE_RED))
                )
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to process POI", e)
            }
        }
    }

    // ⭐ 新增：监听当前位置变化，在地图上显示蓝色定位图标
    LaunchedEffect(currentLocation) {
        Log.d("HomeScreen", "=== currentLocation 变化 ===")
        Log.d("HomeScreen", "当前位置：$currentLocation")
        currentLocation?.let {
            Log.d("HomeScreen", "📍 位置更新：lat=${it.latitude}, lng=${it.longitude}")
            // ⭐ 高德地图会自动显示蓝色定位图标（通过 setMyLocationEnabled）
            // 这里只需要移动相机到当前位置即可
            aMapState.value?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
        }
    }

    LaunchedEffect(orderState) {
        when (val currentState = orderState) {
            is HomeViewModel.OrderState.Success -> {
                val order = currentState.order
                Log.d("HomeScreen", "=== 订单创建成功 ===")
                Log.d("HomeScreen", "订单 ID: ${order.id}")
                Log.d("HomeScreen", "订单号：${order.orderNo}")
                Log.d("HomeScreen", "目的地：${order.poiName}")
                Log.d("HomeScreen", "预估价格：¥${order.estimatedPrice}")
                Log.d("HomeScreen", "跳转到订单详情：${order.id}")
                onNavigateToOrder(order.id.toString())
                viewModel.resetOrderState()
            }
            is HomeViewModel.OrderState.Error -> {
                val errorMsg = currentState.message
                Log.e("HomeScreen", "=== 订单创建失败 ===")
                Log.e("HomeScreen", "错误信息：$errorMsg")
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                viewModel.resetOrderState()
            }
            else -> {
                Log.d("HomeScreen", "订单状态变化：${currentState::class.simpleName}")
            }
        }
    }
    
    LaunchedEffect(geocodeError) {
        geocodeError?.let { error ->
            if (error.isNotBlank()) {
                Log.e("HomeScreen", "=== 逆地理编码错误 ===")
                Log.e("HomeScreen", "错误信息：$error")
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                viewModel.clearGeocodeError()
            }
        }
    }

    if (showPoiDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPoiDialog = false 
                viewModel.clearSearchResults()
            },
            title = { Text("选择目的地") },
            text = {
                LazyColumn {
                    items(backendPoiResults) { poi ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.selectBackendPoi(poi)
                                    showPoiDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = poi.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = poi.address,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            if (poi.distance != null) {
                                Text(
                                    text = "距离：${poi.distance}m",
                                    fontSize = 12.sp,
                                    color = Color.Blue
                                )
                            }
                            Divider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showPoiDialog = false
                        viewModel.clearSearchResults()
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showPoiDetailDialog && poiDetail != null) {
        poiDetail?.let { detail ->
            AlertDialog(
                onDismissRequest = {
                    viewModel.dismissPoiDetailDialog()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text(detail.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = detail.address,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        if (detail.distance != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "距离：${detail.formattedDistance ?: String.format("%.1f 公里", detail.distance!! / 1000)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        
                        if (detail.duration != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "预计：${detail.duration}分钟",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        
                        if (detail.price != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "预估车费：¥${detail.price}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        if (!detail.canOrder) {
                            Text(
                                text = "该地点暂不支持叫车服务",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.confirmPoiSelection()
                        },
                        enabled = detail.canOrder
                    ) {
                        Text("确认选为目的地")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.dismissPoiDetailDialog()
                    }) {
                        Text("取消")
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // ⭐ 地图区域 - 占据整个屏幕
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            MapViewComposable(
                onMapReady = { aMap ->
                    Log.d("HomeScreen", "=== 地图初始化完成 ===")
                    aMapState.value = aMap
                    val style = MyLocationStyle()
                    style.myLocationType(MyLocationStyle.LOCATION_TYPE_SHOW)
                    style.radiusFillColor(android.graphics.Color.argb(80, 0, 0, 255))
                    style.strokeColor(android.graphics.Color.argb(255, 0, 0, 255))
                    style.strokeWidth(2f)
                    aMap.setMyLocationStyle(style)
                    aMap.isMyLocationEnabled = true
                    Log.d("HomeScreen", "启用我的位置图层")

                    aMap.setOnMyLocationChangeListener { location ->
                        Log.d("HomeScreen", "位置变化：lat=${location.latitude}, lng=${location.longitude}")
                        viewModel.updateCurrentLocation(location.latitude, location.longitude)
                    }
                },
                onMapClick = {
                    Log.d("HomeScreen", "=== 地图点击事件 ===")
                    Log.d("HomeScreen", "点击坐标：lat=${it.latitude}, lng=${it.longitude}")
                    vibrate()
                    Toast.makeText(context, "已选择位置", Toast.LENGTH_SHORT).show()
                    viewModel.onMapClick(it)
                },
                onPoiClick = { poi ->
                    Log.d("HomeScreen", "=== POI 点击事件 ===")
                    Log.d("HomeScreen", "POI 名称：${poi.name}")
                    Log.d("HomeScreen", "POI 坐标：lat=${poi.coordinate.latitude}, lng=${poi.coordinate.longitude}")
                    viewModel.onPoiClick(poi)
                }
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

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ⭐ 智能体对话按钮 - 新增
                FloatingActionButton(
                    onClick = {
                        Log.d("HomeScreen", "=== 点击智能体对话按钮 ===")
                        onNavigateToChat()
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "智能体对话",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // ⭐ 定位按钮
                FloatingActionButton(
                    onClick = {
                        val currentLocation = viewModel.currentLocation.value
                        currentLocation?.let { it ->
                            aMapState.value?.animateCamera(
                                com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(it, 17f)
                            )
                        } ?: run {
                            Toast.makeText(context, "🛰️ 正在获取位置...", Toast.LENGTH_SHORT).show()
                            viewModel.startLocation(context)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "定位",
                        tint = if (currentLocation != null) Color.Green else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // ⭐ 新增：显示位置精度信息
                locationAccuracy?.let { accuracy ->
                    Card(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = "±${accuracy.toInt()}m",
                            fontSize = 12.sp,
                            color = if (accuracy < 50) Color.Green else Color(0xFFFF9800),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isGeocoding) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ⭐ 底部可拖动面板 - 使用 Card 实现阴影
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomPanelHeight)
                .pointerInput(bottomPanelHeight) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            Log.d("HomeScreen", "拖动结束，当前高度：$bottomPanelHeight")
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val newHeight = (bottomPanelHeight - dragAmount.dp).coerceIn(minPanelHeight, maxPanelHeight)
                            if (newHeight != bottomPanelHeight) {
                                bottomPanelHeight = newHeight
                                Log.d("HomeScreen", "面板高度：$bottomPanelHeight")
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // ⭐ 拖动手柄 - 美化版
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            RoundedCornerShape(3.dp)
                        )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    // ⭐ 修改：优化搜索框界面，支持自适应
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // 第一行：搜索框和按钮
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color.White.copy(alpha = 0.95f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = destination,
                                onValueChange = { 
                                    viewModel.updateDestination(it)
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("你要去哪？", color = Color.Gray) },
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                                maxLines = 2,  // ⭐ 支持多行输入
                                minLines = 1
                            )
                    
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isSearching && destination.isNotBlank(),
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                IconButton(
                                    onClick = { 
                                        if (currentLocation != null) {
                                            viewModel.searchPoiFromBackend(destination)
                                        } else {
                                            Toast.makeText(context, "请先获取当前位置", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "搜索",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSearching,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Box(
                                    Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                    
                            IconButton(
                                onClick = {
                                    if (audioPermissionState.status.isGranted) {
                                        viewModel.startVoiceInput(context)
                                    } else {
                                        Toast.makeText(context, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                                        audioPermissionState.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isListening) "停止录音" else "语音输入",
                                    tint = if (isListening) Color.Red else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSearching,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isNationwideSearch) "全国搜索中..." else "正在搜索...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { 
                        try {
                            viewModel.createOrder(destination)
                        } catch (e: Exception) {
                            Log.e("HomeScreen", "创建订单时发生异常", e)
                            android.widget.Toast.makeText(context, "创建订单失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = destination.isNotBlank() && (selectedPoiForMap != null || clickedLocation != null) && !isCreatingOrder
                ) {
                    if (isCreatingOrder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("创建中...", fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    } else {
                        Text("确认叫车", fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
            }
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
