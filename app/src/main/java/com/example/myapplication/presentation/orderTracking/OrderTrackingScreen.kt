package com.example.myapplication.presentation.orderTracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log  // ⭐ 新增：导入 Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person  // ⭐ 新增：司机图标
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info  // ⭐ 新增：信息图标
import androidx.compose.material.icons.filled.Check  // ⭐ 新增：确认图标
import androidx.compose.material.icons.filled.Close  // ⭐ 新增：关闭图标
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.example.myapplication.R
import com.example.myapplication.map.MapViewComposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTrackingScreen(
    orderId: Long,
    onBackClick: () -> Unit,
    viewModel: OrderTrackingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val driverLocation by viewModel.driverLocation.collectAsStateWithLifecycle()
    val etaMinutes by viewModel.etaMinutes.collectAsStateWithLifecycle()
    
    // ⭐ 新增：位置权限请求（确保在任何界面都可以请求权限）
    var hasRequestedLocationPermission by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Log.d("OrderTrackingScreen", "✅ 位置权限已授予")
            } else {
                Log.w("OrderTrackingScreen", "⚠️ 位置权限被拒绝")
                Toast.makeText(context, "⚠️ 需要位置权限才能显示地图", Toast.LENGTH_LONG).show()
            }
        }
    )
    
    // ⭐ 新增：首次进入时请求位置权限
    LaunchedEffect(Unit) {
        if (!hasRequestedLocationPermission) {
            hasRequestedLocationPermission = true
            // 检查是否已有权限
            val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                Log.d("OrderTrackingScreen", "🔑 请求位置权限")
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                Log.d("OrderTrackingScreen", "✅ 已有位置权限")
            }
        }
    }
    
    // ⭐ 新增：获取用户角色（长辈端不允许取消订单和选择司机）
    val profileViewModel: com.example.myapplication.presentation.profile.ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val profile by profileViewModel.profile.collectAsStateWithLifecycle()
    val isElderMode = profile?.guardMode == 1
    
    // ⭐ 关键修复：通过订单状态判断是否为代叫车订单
    val isProxyOrder by remember {
        derivedStateOf {
            when (uiState) {
                is OrderTrackingUiState.Success -> {
                    val order = (uiState as OrderTrackingUiState.Success).order
                    // 代叫车订单：status=0(待长辈确认)、1(已确认)、2(寻找司机)
                    // 非代叫车订单：status=3+(司机已接单)
                    order.status <= 2
                }
                else -> false
            }
        }
    }
    
    // ⭐ 修复：在 Composable 顶层创建 scope
    val scope = rememberCoroutineScope()
    
    // ⭐ 新增：取消订单确认对话框状态
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    
    // ⭐ 新增：司机接单确认对话框状态（必须在 LaunchedEffect 之前定义）
    var showDriverAcceptDialog by remember { mutableStateOf<com.example.myapplication.data.model.WsMessage?>(null) }
    
    // ⭐ 新增：监听事件（代叫车确认提示 + 司机接单请求）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OrderTrackingEvent.ProxyOrderConfirmed -> {
                    if (event.confirmed) {
                        Toast.makeText(context, "✅ 长辈已确认，司机正在赶来", Toast.LENGTH_LONG).show()
                    } else {
                        val reason = event.rejectReason ?: "未知原因"
                        Toast.makeText(context, "❌ 长辈拒绝：$reason", Toast.LENGTH_LONG).show()
                        // 延迟返回上一页
                        kotlinx.coroutines.delay(2000)
                        onBackClick()
                    }
                }
                is OrderTrackingEvent.DriverRequestReceived -> {
                    // ⭐ 关键修复：
                    // - 亲友端（isElderMode=false）：显示司机接单确认弹窗，可以选择同意/拒绝
                    // - 长辈端（isElderMode=true）：只刷新订单，查看司机信息，不能选择/拒绝
                    if (!isElderMode) {
                        // 亲友端：显示确认弹窗
                        Log.d("OrderTrackingScreen", "👥 [亲友端] 收到DRIVER_REQUEST，显示确认弹窗")
                        showDriverAcceptDialog = event.wsMessage
                    } else {
                        // 长辈端：只刷新订单以获取司机信息
                        Log.d("OrderTrackingScreen", "👴 [长辈端] 收到DRIVER_REQUEST，刷新订单获取司机信息")
                        scope.launch {
                            kotlinx.coroutines.delay(500)  // 等待后端更新
                            viewModel.refreshOrder(orderId)
                        }
                    }
                }
                is OrderTrackingEvent.DriverRejected -> {
                    Toast.makeText(context, "⚠️ ${event.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 地图相关
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var driverMarker by remember { mutableStateOf<com.amap.api.maps.model.Marker?>(null) }
    var startMarker by remember { mutableStateOf<com.amap.api.maps.model.Marker?>(null) }  // ⭐ 新增：起点标记
    var endMarker by remember { mutableStateOf<com.amap.api.maps.model.Marker?>(null) }  // ⭐ 新增：终点标记
    var routePolyline by remember { mutableStateOf<com.amap.api.maps.model.Polyline?>(null) }
    
    // ⭐ 关键修复：记录上次的订单状态，用于判断是否需要重新绘制
    var lastOrderStatus by remember { mutableStateOf<Int?>(null) }

    // ⭐ 关键修复：初始化追踪（每次进入页面都重新加载）
    LaunchedEffect(Unit) {
        Log.d("OrderTrackingScreen", "🚀 [OrderTrackingScreen] 初始化行程追踪，orderId=$orderId")
        viewModel.initTracking(orderId)
    }

    // ⭐ 关键修复：监听司机位置变化，平滑移动标记（合并到 uiState 的 LaunchedEffect 中）
    // 注意：这个 LaunchedEffect 会在 aMap 初始化后自动执行
    LaunchedEffect(aMap, driverLocation) {
        if (aMap != null && driverLocation != null) {
            val map = aMap!!
            val location = driverLocation!!
            
            if (driverMarker == null) {
                // ⭐ 创建司机标记
                Log.d("OrderTrackingScreen", "🚕 创建司机标记：lat=${location.latitude}, lng=${location.longitude}")
                
                driverMarker = map.addMarker(
                    MarkerOptions()
                        .position(location)
                        .icon(BitmapDescriptorFactory.fromResource(
                            R.drawable.ic_taxi_driver
                        ))
                        .anchor(0.5f, 1.0f)
                        .title("司机位置")
                        .snippet("正在赶来中")
                        .zIndex(10f)
                )
                Log.d("OrderTrackingScreen", "✅ 司机标记创建成功")
            } else {
                // 平滑移动标记（2.5秒动画）
                Log.d("OrderTrackingScreen", "🚕 司机位置更新：lat=${location.latitude}, lng=${location.longitude}")
                animateMarkerSmoothly(driverMarker!!, location, durationMs = 2500)
            }
            
            // 移动相机到司机位置
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))
        }
    }

    // ⭐ 关键修复：绘制路线和标记（监听 uiState 和 aMap）
    LaunchedEffect(aMap, uiState) {
        if (uiState is OrderTrackingUiState.Success && aMap != null) {
            val order = (uiState as OrderTrackingUiState.Success).order
            val map = aMap!!
            
            // ⭐ 关键修复：首次加载或订单状态变化时重新绘制
            val shouldRedraw = lastOrderStatus == null || lastOrderStatus != order.status
            
            if (shouldRedraw) {
                lastOrderStatus = order.status
                
                // 清除旧路线和标记
                routePolyline?.remove()
                startMarker?.remove()
                endMarker?.remove()
                driverMarker?.remove()  // ⭐ 清除旧的司机标记
                
                // 重置标记引用
                routePolyline = null
                startMarker = null
                endMarker = null
                driverMarker = null
                
                // ⭐ 修复：如果有起点和终点坐标，绘制路线
                val startLat = order.startLat
                val startLng = order.startLng
                val destLat = order.destLat
                val destLng = order.destLng
                
                if (startLat != null && startLng != null && destLat != null && destLng != null) {
                    // ⭐ 绘制蓝色路线
                    val polyline = map.addPolyline(
                        PolylineOptions()
                            .add(LatLng(startLat, startLng))
                            .add(LatLng(destLat, destLng))
                            .color(android.graphics.Color.parseColor("#3366FF"))
                            .width(10f)
                            .geodesic(true)
                    )
                    routePolyline = polyline
                    
                    // ⭐ 添加起点标记（绿色）
                    startMarker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(startLat, startLng))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                            .title("上车点")
                            .snippet(order.poiName ?: order.destAddress ?: "起点")
                    )
                    
                    // ⭐ 添加终点标记（红色）
                    endMarker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(destLat, destLng))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            .title("目的地")
                            .snippet(order.poiName ?: order.destAddress ?: "终点")
                    )
                    
                    // ⭐ 关键修复：如果有司机位置，立即重新创建司机标记
                    if (order.driverLat != null && order.driverLng != null) {
                        val driverLatLng = LatLng(order.driverLat, order.driverLng)
                        try {
                            driverMarker = map.addMarker(
                                MarkerOptions()
                                    .position(driverLatLng)
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_taxi_driver))
                                    .anchor(0.5f, 1.0f)
                                    .title("司机位置")
                                    .snippet("正在赶来中")
                                    .zIndex(10f)
                            )
                        } catch (e: Exception) {
                            Log.e("OrderTrackingScreen", "❌ 司机标记创建失败", e)
                        }
                    }
                    
                    // ⭐ 调整地图视野，显示完整路线
                    val bounds = com.amap.api.maps.model.LatLngBounds.builder()
                        .include(LatLng(startLat, startLng))
                        .include(LatLng(destLat, destLng))
                        .build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("行程进行中", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is OrderTrackingUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                is OrderTrackingUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("❌ ${state.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refreshOrder(orderId) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                
                is OrderTrackingUiState.Success -> {
                    OrderTrackingContent(
                        order = state.order,
                        etaMinutes = etaMinutes,
                        isElderMode = isElderMode,  // ⭐ 关键修复：传入长辈模式标识
                        aMap = aMap,
                        onMapReady = { map ->
                            aMap = map
                            // 设置地图初始视图
                            setupInitialMapView(map, state.order)
                        },
                        onCallDriver = {
                            callDriver(context, state.order.driverPhone)
                        },
                        onCancelOrder = {
                            // ⭐ 修复：显示确认对话框
                            showCancelConfirmDialog = true
                        },
                        // ⭐ 新增：乘客上车（触发行程开始）
                        onBoardCar = {
                            scope.launch {
                                val result = viewModel.startTrip(orderId)
                                if (result) {
                                    Toast.makeText(context, "✅ 乘客已上车，行程开始", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "❌ 开始行程失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    // ⭐ 新增：取消订单确认对话框
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("确认取消订单") },
            text = { Text("确定要取消这个订单吗？取消后无法恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        scope.launch {
                            try {
                                val result = viewModel.cancelOrder(orderId)
                                if (result) {
                                    Toast.makeText(context, "✅ 订单已取消", Toast.LENGTH_SHORT).show()
                                    // 延迟返回上一页
                                    kotlinx.coroutines.delay(1000)
                                    onBackClick()
                                } else {
                                    Toast.makeText(context, "❌ 取消失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "❌ 取消订单异常：${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("确认取消")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("我再想想")
                }
            }
        )
    }
    
    // ⭐ 新增：司机接单确认对话框
    showDriverAcceptDialog?.let { wsMessage ->
        AlertDialog(
            onDismissRequest = { },  // ⭐ 禁止点击外部关闭，必须选择同意或拒绝
            title = { 
                Text(
                    text = "🚕 有司机接单",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = wsMessage.message ?: "是否允许该司机接单？",
                        fontSize = 16.sp,
                        color = Color(0xFF333333)
                    )
                    
                    Divider()
                    
                    // 司机信息
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF1677FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "司机：${wsMessage.driverName ?: "未知"}",
                                fontSize = 15.sp
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFF1677FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "车牌：${wsMessage.carNo ?: "未知"}",
                                fontSize = 15.sp
                            )
                        }
                        
                        if (wsMessage.carType != null || wsMessage.carColor != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF1677FF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "车辆：${wsMessage.carColor ?: ""} ${wsMessage.carType ?: ""}",
                                    fontSize = 15.sp
                                )
                            }
                        }
                        
                        if (wsMessage.rating != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "评分：${String.format("%.1f", wsMessage.rating)}",
                                    fontSize = 15.sp
                                )
                            }
                        }
                        
                        if (wsMessage.driverPhone != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "电话：${wsMessage.driverPhone}",
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDriverAcceptDialog = null
                        scope.launch {
                            val result = viewModel.confirmDriverAcceptance(orderId, true)
                            if (result) {
                                Toast.makeText(context, "✅ 已同意司机接单", Toast.LENGTH_SHORT).show()
                                // ⭐ 关键修复：确认后主动刷新订单，获取司机信息
                                kotlinx.coroutines.delay(500)  // 等待后端更新
                                viewModel.refreshOrder(orderId)
                                Log.d("OrderTrackingScreen", "🔄 已刷新订单信息，等待司机数据")
                            } else {
                                Toast.makeText(context, "❌ 操作失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("同意")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDriverAcceptDialog = null
                        scope.launch {
                            val result = viewModel.confirmDriverAcceptance(orderId, false)
                            if (result) {
                                Toast.makeText(context, "⚠️ 已拒绝，正在重新派单...", Toast.LENGTH_LONG).show()
                                // ⭐ 关键修复：拒绝后主动刷新订单，更新状态
                                kotlinx.coroutines.delay(500)  // 等待后端更新
                                viewModel.refreshOrder(orderId)
                                Log.d("OrderTrackingScreen", "🔄 已刷新订单信息，状态应回到待确认")
                            } else {
                                Toast.makeText(context, "❌ 操作失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拒绝")
                }
            }
        )
    }
}

@Composable
private fun OrderTrackingContent(
    order: com.example.myapplication.data.model.Order,
    etaMinutes: Int?,
    isElderMode: Boolean,  // ⭐ 新增：是否为长辈模式
    aMap: AMap?,
    onMapReady: (AMap) -> Unit,
    onCallDriver: () -> Unit,
    onCancelOrder: () -> Unit,
    onBoardCar: () -> Unit  // ⭐ 乘客上车
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. 地图区域（固定权重，让地图有足够空间）
        MapViewComposable(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f),
            onMapReady = onMapReady,
            onMapClick = {},
            onPoiClick = {}
        )

        // 2. 底部信息面板（权重增加，确保内容可完整显示）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 8.dp,
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 32.dp),  // ⭐ 增加底部留白，确保按钮完全可见
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态条
                StatusBanner(order.status, etaMinutes)

                // ⭐ 关键修复：只有订单状态 >= 3（司机已接单）时才显示司机卡片
                if (order.status >= 3 && (order.driverName != null || order.driverPhone != null)) {
                    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    
                    // 司机信息卡片
                    DriverInfoCard(
                        driverName = order.driverName,
                        rating = order.rating,
                        carNo = order.carNo,
                        carType = order.carType,
                        carColor = order.carColor,
                        driverPhone = order.driverPhone,
                        onCallDriver = onCallDriver
                    )
                }

                // 目的地信息
                DestinationInfo(
                    destAddress = order.poiName ?: order.destAddress ?: "收藏地点"
                )

                // 操作按钮
                ActionButtons(
                    orderStatus = order.status,  // ⭐ 新增：传入订单状态
                    etaMinutes = etaMinutes,  // ⭐ 新增：传入 ETA
                    isElderMode = isElderMode,  // ⭐ 关键修复：传入长辈模式标识
                    hasDriverPhone = !order.driverPhone.isNullOrBlank(),  // ⭐ 关键修复：是否有司机电话
                    onCancelOrder = onCancelOrder,
                    onCallDriver = onCallDriver,
                    onBoardCar = onBoardCar  // ⭐ 乘客上车
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(status: Int, etaMinutes: Int?) {
    val (statusText, statusColor, icon) = when (status) {
        0 -> Triple("⏳ 等待长辈确认...", Color(0xFFFF9800), Icons.Default.CarCrash)  // ⭐ 修复：更明确的提示
        1 -> Triple("✅ 已确认，正在寻找司机", Color(0xFF2196F3), Icons.Default.DirectionsCar)  // ⭐ 修复：更准确的描述
        2 -> Triple("🚕 正在为您寻找司机...", Color(0xFF2196F3), Icons.Default.CarCrash)
        3 -> Triple("🚗 司机已接单 - 赶来中", Color(0xFF2196F3), Icons.Default.DirectionsCar)
        4 -> Triple("✅ 司机已到达 - 请上车", Color(0xFF4CAF50), Icons.Default.CheckCircle)
        5 -> Triple("🚀 行程中 - 前往目的地", Color(0xFF9C27B0), Icons.Default.DirectionsCar)
        6 -> Triple("✅ 行程已完成", Color(0xFF2E7D32), Icons.Default.CheckCircle)
        7 -> Triple("❌ 已取消", Color.Gray, Icons.Default.CarCrash)
        8 -> Triple("❌ 已拒绝", Color.Red, Icons.Default.CarCrash)
        else -> Triple("未知状态", Color.Gray, Icons.Default.CarCrash)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                
                if (etaMinutes != null && status in 2..5) {  // ⭐ 修复：状态5（行程中）也显示ETA
                    Text(
                        text = if (etaMinutes <= 0) "即将到达" else "预计${etaMinutes}分钟到达",
                        fontSize = 13.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverInfoCard(
    driverName: String?,
    rating: Double?,
    carNo: String?,
    carType: String?,
    carColor: String?,
    driverPhone: String?,
    onCallDriver: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 司机姓名和评分
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color(0xFF1677FF),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = driverName ?: "司机信息加载中...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                        
                        if (rating != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", rating),
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
            }

            // 车辆信息
            if (carNo != null || carType != null || carColor != null) {
                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    carNo?.let {
                        InfoRow(label = "车牌号", value = it, icon = Icons.Default.DirectionsCar)
                    }
                    carType?.let {
                        InfoRow(label = "车型", value = it, icon = Icons.Default.CarCrash)
                    }
                    carColor?.let {
                        InfoRow(label = "颜色", value = it, icon = Icons.Default.LocationOn)
                    }
                }
            }

            // 联系电话
            if (driverPhone != null) {
                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                
                Button(
                    onClick = onCallDriver,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("联系司机", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(18.dp))
        Text(
            text = "$label：",
            fontSize = 14.sp,
            color = Color(0xFF666666)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A1A)
        )
    }
}

@Composable
private fun DestinationInfo(destAddress: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFF1677FF),
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Text(
                    text = "目的地",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Text(
                    text = destAddress ?: "未知",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A1A)
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    orderStatus: Int,  // ⭐ 修复：传入订单状态
    etaMinutes: Int?,  // ⭐ 新增：预计到达时间
    isElderMode: Boolean,  // ⭐ 新增：是否为长辈模式
    hasDriverPhone: Boolean,  // ⭐ 关键修复：是否有司机电话
    onCancelOrder: () -> Unit,
    onCallDriver: () -> Unit,
    onBoardCar: () -> Unit = {}  // ⭐ 乘客上车
) {
    // ⭐ 根据订单状态显示不同按钮
    when (orderStatus) {
        4 -> {
            // ⭐ 司机已到达：显示"乘客上车"按钮
            Button(
                onClick = onBoardCar,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("乘客上车，开始行程", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        5 -> {
            // ⭐ 行程中：司机载乘客去目的地，乘客端只需等待
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color(0xFF1677FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "🚗 行程进行中，司机正前往目的地",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1677FF)
                        )
                        if (etaMinutes != null && etaMinutes > 0) {
                            Text(
                                text = "预计 ${etaMinutes} 分钟到达",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }
            }
        }
        6 -> {
            // ⭐ 行程已完成：显示总结信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎉 行程已结束，感谢您的使用！",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
        else -> {
            // ⭐ 其他状态（0-待确认、1-已确认、2-寻找司机、3-司机已接单）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ⭐ 关键修复：只有亲友端（非长辈）才能取消订单
                if (!isElderMode && orderStatus < 6) {  
                    OutlinedButton(
                        onClick = onCancelOrder,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Text("取消订单", fontSize = 15.sp)
                    }
                }
                
                // ⭐ 关键修复：
                // - 司机已接单（status>=3）且有司机电话：显示联系司机按钮
                // - 长辈端和亲友端都可以联系司机
                if (orderStatus >= 3 && hasDriverPhone) {
                    Button(
                        onClick = onCallDriver,
                        modifier = Modifier.weight(if (!isElderMode && orderStatus < 6) 1f else 1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1677FF))
                    ) {
                        Text("联系司机", fontSize = 15.sp)
                    }
                }
                
                // ⭐ 新增：长辈端在司机已接单时，显示提示信息
                if (isElderMode && orderStatus == 3 && !hasDriverPhone) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⏳ 司机正在赶来，请稍候...",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 工具函数 ====================

/**
 * 平滑移动 Marker
 */
private fun animateMarkerSmoothly(
    marker: com.amap.api.maps.model.Marker,
    targetPosition: LatLng,
    durationMs: Long = 2500
) {
    val startPosition = marker.position
    val startTime = System.currentTimeMillis()
    
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    val runnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            
            // 线性插值
            val lat = startPosition.latitude + (targetPosition.latitude - startPosition.latitude) * progress
            val lng = startPosition.longitude + (targetPosition.longitude - startPosition.longitude) * progress
            
            marker.position = LatLng(lat, lng)
            
            if (progress < 1f) {
                handler.postDelayed(this, 16)  // 约60fps
            }
        }
    }
    
    handler.post(runnable)
}

/**
 * 设置地图初始视图
 */
private fun setupInitialMapView(map: AMap, order: com.example.myapplication.data.model.Order) {
    // 优先显示司机位置，否则显示起点
    val targetLat = order.driverLat ?: order.startLat ?: order.destLat
    val targetLng = order.driverLng ?: order.startLng ?: order.destLng
    
    if (targetLat != null && targetLng != null) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(targetLat, targetLng), 15f))
    }
}

/**
 * 拨打电话
 */
private fun callDriver(context: Context, phone: String?) {
    if (phone.isNullOrBlank()) {
        Toast.makeText(context, "司机电话未提供", Toast.LENGTH_SHORT).show()
        return
    }
    
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法拨打电话", Toast.LENGTH_SHORT).show()
    }
}
