package com.example.myapplication.presentation.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.example.myapplication.data.model.GuardianInfo
import com.example.myapplication.map.MapViewComposable
import com.example.myapplication.presentation.chat.ChatViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║  ⚠️⚠️⚠️ 重要标识：这是【长辈精简模式】界面 ⚠️⚠️⚠️   ║
 * ║                                                          ║
 * ║  适用场景：                                               ║
 * ║  - 长辈用户（guardMode=1）的首页                          ║
 * ║  - 简化操作，大按钮设计                                   ║
 * ║  - 主要功能：呼叫亲友、聊天、确认代叫车                   ║
 * ║  - 不支持自己输入目的地叫车                               ║
 * ║                                                          ║
 * ║  特征：                                                   ║
 * ║  - 标题："安心出行·长辈端"（居中）                        ║
 * ║  - 全屏模式切换按钮                                       ║
 * ║  - 大按钮：呼叫亲友、聊天                                 ║
 * ║  - 代叫车确认对话框                                       ║
 * ║  - 底部面板更紧凑                                         ║
 * ║                                                          ║
 * ║  🚫 修改此文件不会影响普通用户界面！                      ║
 * ║  ✅ 普通用户界面在 NormalHomeScreen.kt                    ║
 * ╚══════════════════════════════════════════════════════════╝
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ElderSimplifiedScreen(
    viewModel: HomeViewModel,
    chatViewModel: ChatViewModel,  // ⭐ 新增：从外部传入 ChatViewModel，确保使用同一个实例
    onNavigateToProfile: () -> Unit,
    onNavigateToChat: () -> Unit,
    onLogout: () -> Unit = {},
    onNavigateToOrderTracking: (Long) -> Unit = {}  // ⭐ 新增：跳转到行程追踪页面
) {
    val context = LocalContext.current
    
    // ========== UI状态管理（全部集中在这里）==========
    var isFollowingLocation by remember { mutableStateOf(true) }
    var isMapFullScreen by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var elderAMap by remember { mutableStateOf<AMap?>(null) }
    
    // 呼叫相关状态
    var showCallGuardianDialog by remember { mutableStateOf(false) }
    var guardianPhone by remember { mutableStateOf<String?>(null) }
    var guardianName by remember { mutableStateOf<String?>(null) }
    var showCallDriverDialog by remember { mutableStateOf(false) }
    var driverPhone by remember { mutableStateOf<String?>(null) }
    var driverName by remember { mutableStateOf<String?>(null) }
    
    // ⭐ 修复：代叫车相关状态已移至 MainActivity 全局处理，此处移除
    
    // ========== 从 ViewModel 收集状态 ==========
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val locationAccuracy by viewModel.locationAccuracy.collectAsStateWithLifecycle()
    val orderState by viewModel.orderState.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val guardianInfoList by viewModel.guardianInfoList.collectAsStateWithLifecycle()
    // ⭐ 修复：proxyOrderRequest 已移至 MainActivity 全局监听，此处不再需要
    
    // ========== 权限管理 ==========
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val callPhonePermissionState = rememberPermissionState(permission = Manifest.permission.CALL_PHONE)
    
    // ⭐ 新增：通知权限（Android 13+ 需要）
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    
    // ========== 动画效果 ==========
    val pulseAnimation by animateFloatAsState(
        targetValue = if (isFollowingLocation) 1f else 1.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1000,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAnimation"
    )
    
    // ========== 副作用处理 ==========
    
    // 1. 启动定位
    var hasStartedLocation by remember { mutableStateOf(false) }
    LaunchedEffect(locationPermissionState.status) {
        if (locationPermissionState.status == PermissionStatus.Granted && !hasStartedLocation) {
            Log.d("ElderSimplifiedScreen", "✅ 权限已授予，启动独立定位")
            viewModel.startIndependentLocation()
            
            // ⭐ 新增：启动后台定位追踪服务，确保切换应用后仍能持续获取位置
            viewModel.startBackgroundTracking()
            Log.d("ElderSimplifiedScreen", "🚀 已启动后台定位追踪服务")
            
            hasStartedLocation = true
        } else if (locationPermissionState.status != PermissionStatus.Granted) {
            Log.w("ElderSimplifiedScreen", "⚠️ 定位权限未授予，当前状态: ${locationPermissionState.status}")
        }
    }
    
    // 2. 初始化加载 - 修复：确保进入长辈端时强制重新定位
    LaunchedEffect(Unit) {
        Log.d("ElderSimplifiedScreen", "👴 长辈模式启动")
        Log.d("ElderSimplifiedScreen", "📍 当前权限状态: ${locationPermissionState.status}")
        Log.d("ElderSimplifiedScreen", "📍 当前位置: lat=${currentLocation?.latitude}, lng=${currentLocation?.longitude}")
        
        // ⭐ 修复：移除重复的 checkElderMode 调用（HomeScreen 已经调用过）
        // viewModel.checkElderMode() 已移除，避免触发防抖机制
        
        if (locationPermissionState.status != PermissionStatus.Granted) {
            Log.w("ElderSimplifiedScreen", "⚠️ 权限未授予，请求权限...")
            locationPermissionState.launchPermissionRequest()
        } else {
            Log.d("ElderSimplifiedScreen", "✅ 权限已授予，检查是否需要启动定位")
            if (!hasStartedLocation) {
                Log.d("ElderSimplifiedScreen", "🚀 启动独立定位服务")
                viewModel.startIndependentLocation()
                viewModel.startBackgroundTracking()
                hasStartedLocation = true
            }
        }
        
        viewModel.loadGuardianInfo()
        
        // ⭐ 新增：请求通知权限（用于代叫车通知）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionState?.let { permState ->
                if (permState.status != PermissionStatus.Granted) {
                    Log.d("ElderSimplifiedScreen", "🔔 请求通知权限...")
                    permState.launchPermissionRequest()
                } else {
                    Log.d("ElderSimplifiedScreen", "✅ 通知权限已授予")
                }
            }
        }
    }
    
    // 3. ⭐ 新增：首次定位成功后自动移动相机到街道级别
    var hasInitialLocated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentLocation, elderAMap) {
        if (currentLocation != null && !hasInitialLocated && elderAMap != null) {
            Log.d("ElderSimplifiedScreen", "📍 首次定位成功，自动移动相机到街道级别")
            try {
                // ⭐ 修复：使用 moveCamera 替代 animateCamera，提升初始化速度
                elderAMap?.moveCamera(
                    com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(currentLocation!!, 16.5f)
                )
                hasInitialLocated = true
                Log.d("ElderSimplifiedScreen", "✅ 相机已移动到 zoom=16.5f（街道级），lat=${currentLocation!!.latitude}, lng=${currentLocation!!.longitude}")
            } catch (e: Exception) {
                Log.e("ElderSimplifiedScreen", "❌ 首次定位移动相机失败", e)
            }
        }
    }
    
    // 4. 监听定位变化（⭐ 修复：统一zoom为16.5f街道级别）
    LaunchedEffect(currentLocation) {
        Log.d("ElderSimplifiedScreen", "📍 位置更新: lat=${currentLocation?.latitude}, lng=${currentLocation?.longitude}")
        
        // ⭐ 新增：同步位置到 ChatViewModel，激活 WebSocket 连接
        currentLocation?.let { loc ->
            chatViewModel.syncLocationFromHome(loc.latitude, loc.longitude)
            Log.d("ElderSimplifiedScreen", "✅ 位置已同步到 ChatViewModel")
        }
        
        if (isFollowingLocation) {
            currentLocation?.let { loc ->
                elderAMap?.let { map ->
                    Log.d("ElderSimplifiedScreen", "🎯 [跟随模式] 移动相机到当前位置")
                    try {
                        // ⭐ 修复：统一zoom为16.5f（街道级别）
                        map.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(loc, 16.5f))
                        Log.d("ElderSimplifiedScreen", "✅ 相机移动成功，zoom=16.5f")
                    } catch (e: Exception) {
                        Log.e("ElderSimplifiedScreen", "❌ 移动相机失败", e)
                    }
                } ?: run {
                    Log.w("ElderSimplifiedScreen", "⚠️ AMap实例为null，等待地图初始化")
                }
            }
        } else {
            Log.d("ElderSimplifiedScreen", "⏭️ [自由模式] 不移动相机")
        }
    }
    
    // 5. ⭐ 修复：代叫车请求已移至 MainActivity 全局处理，此处不再监听
    // 避免与全局弹窗冲突
    
    // ⭐ 新增：监听 HomeEvent 事件（处理长辈确认后的跳转）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeViewModel.HomeEvent.NavigateToOrderTracking -> {
                    Log.d("ElderSimplifiedScreen", "🚀 收到导航事件，跳转到行程追踪: orderId=${event.orderId}")
                    onNavigateToOrderTracking(event.orderId)
                }
                else -> {}
            }
        }
    }
    
    // ⭐ 修复：长辈端不监听 orderState，因为长辈不能自己创建订单
    // 订单只能通过代叫车流程产生，由 WebSocket ORDER_CREATED 消息触发
    
    // 6. Toast显示
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            kotlinx.coroutines.delay(2000)
            toastMessage = null
        }
    }
    
    // ========== 工具函数 ==========
    
    fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }
    
    fun makePhoneCall(phoneNumber: String) {
        val phoneRegex = Regex("^1[3-9]\\d{9}$")
        if (!phoneRegex.matches(phoneNumber)) {
            toastMessage = "电话号码格式不正确"
            return
        }
        
        if (callPhonePermissionState.status == PermissionStatus.Granted) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("ElderSimplifiedScreen", "拨打电话失败", e)
                toastMessage = "拨打电话失败：${e.message}"
            }
        } else {
            callPhonePermissionState.launchPermissionRequest()
            toastMessage = "需要电话权限才能拨打"
        }
    }
    
    // ========== UI渲染 ==========
    
    Scaffold(
        topBar = {
            // ⭐ 关键修复：使用自定义 Card 替代 TopAppBar，确保标题真正居中且不被遮挡
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),  // ⭐ 加大高度：72dp → 80dp，避免标题被遮挡
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.98f)  // ⭐ 提高不透明度
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)  // ⭐ 增加阴影
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),  // ⭐ 增加垂直内边距
                    contentAlignment = Alignment.Center
                ) {
                    // ⭐ 居中标题 - 使用 Box 确保真正居中
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "安心出行·长辈端",
                            fontSize = 30.sp,  // ⭐ 加大1字号：29.sp → 30.sp
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = Color(0xFF1677FF)
                        )
                    }
                    
                    // ⭐ 右侧全屏按钮
                    var lastToggleTime by remember { mutableStateOf(0L) }
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastToggleTime < 500) return@IconButton
                            lastToggleTime = currentTime
                            
                            isMapFullScreen = !isMapFullScreen
                            toastMessage = if (isMapFullScreen) {
                                "已切换到全屏模式\n地图将占据整个屏幕，可自由拖拽和缩放"
                            } else {
                                "已退出全屏模式"
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isMapFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isMapFullScreen) "退出全屏" else "全屏",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        // ⭐ 关键修复：只应用底部 padding，让地图紧贴标题栏，卡片不被导航栏遮挡
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())  // ⭐ 只在 Box 上应用底部 padding
        ) {
            // 1. 地图区域 - ⭐ 关键修复：直接填满父容器，紧贴标题栏
            MapViewComposable(
                modifier = Modifier.fillMaxSize(),  // ⭐ 修复：直接填满，不需要 offset
                onMapReady = { map ->
                    Log.d("ElderSimplifiedScreen", "🗺️ onMapReady回调执行")
                    elderAMap = map
                    
                    try {
                        // ⭐ 修改：使用普通地图模式（2D平面，与普通用户一致）
                        map.mapType = AMap.MAP_TYPE_NORMAL
                        map.isMyLocationEnabled = true
                        
                        // ⭐ 关闭3D建筑显示，使用2D平面地图（只调用一次）
                        map.showBuildings(false)
                        // ⭐ 关闭倾斜手势，防止3D视角
                        map.uiSettings.isTiltGesturesEnabled = false
                        
                        // ✅ 修复：使用 LOCATION_TYPE_LOCATION_ROTATE 模式，显示蓝点和精度圈
                        val myLocationStyle = com.amap.api.maps.model.MyLocationStyle()
                        myLocationStyle.myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
                        myLocationStyle.radiusFillColor(android.graphics.Color.argb(100, 66, 133, 244))  // ✅ 加深蓝色填充（透明度100/255）
                        myLocationStyle.strokeColor(android.graphics.Color.argb(220, 66, 133, 244))  // ✅ 加深蓝色边框（透明度220/255）
                        myLocationStyle.strokeWidth(4f)  // ✅ 加粗边框到4px
                        myLocationStyle.interval(3000)  // ✅ 3秒更新一次，防止精度圈消失
                        map.myLocationStyle = myLocationStyle
                        
                        // ✅ 修复：降低防抖阈值，让精度圈更稳定
                        var lastLocationUpdate = 0L
                        var lastLat = 0.0
                        var lastLng = 0.0
                        val MIN_LOCATION_CHANGE_METERS = 3.0  // ✅ 降低到3米（减少消失）
                        
                        map.setOnMyLocationChangeListener { location ->
                            val currentTime = System.currentTimeMillis()
                            val currentLat = location.latitude
                            val currentLng = location.longitude
                            
                            // ✅ 计算距离变化
                            val hasSignificantChange = if (lastLat == 0.0 && lastLng == 0.0) {
                                true  // 首次定位
                            } else {
                                val distance = android.location.Location("temp").apply {
                                    latitude = lastLat
                                    longitude = lastLng
                                }.distanceTo(android.location.Location("temp").apply {
                                    latitude = currentLat
                                    longitude = currentLng
                                })
                                distance >= MIN_LOCATION_CHANGE_METERS
                            }
                            
                            // ✅ 只有位置变化超过3米且间隔超过1秒才更新（更频繁，防止消失）
                            if (hasSignificantChange && (currentTime - lastLocationUpdate > 1000)) {
                                viewModel.updateCurrentLocation(currentLat, currentLng)
                                if (location.accuracy > 0) {
                                    viewModel.updateLocationAccuracy(location.accuracy)
                                }
                                lastLocationUpdate = currentTime
                                lastLat = currentLat
                                lastLng = currentLng
                                Log.d("ElderSimplifiedScreen", "📍 位置更新：lat=$currentLat, lng=$currentLng, accuracy=${location.accuracy}m")
                            }
                        }
                        
                        // ⭐ 隐藏InfoWindow，减少渲染开销
                        map.setInfoWindowAdapter(null)
                        
                        // 监听地图拖动
                        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                            override fun onCameraChangeFinish(p0: com.amap.api.maps.model.CameraPosition) {
                                isFollowingLocation = false
                                Log.d("ElderSimplifiedScreen", "🗺️ 用户拖动地图，切换到自由模式")
                            }
                            override fun onCameraChange(p0: com.amap.api.maps.model.CameraPosition) {}
                        })
                        
                        // ⭐ 关键修复：初始化地图相机 - ⭐ 使用 moveCamera 替代 animateCamera，提升初始化速度
                        val targetLocation = currentLocation
                        if (targetLocation != null) {
                            // 定位已完成，直接移动到当前位置
                            map.moveCamera(
                                com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(targetLocation, 16.5f)
                            )
                            Log.d("ElderSimplifiedScreen", "📍 地图初始化：已有定位，zoom=16.5f（街道级）")
                        } else {
                            // ⭐ 修复：定位未完成时，也使用zoom=16.5f街道级别，避免显示空茫茫的城市级别
                            val defaultLocation = com.amap.api.maps.model.LatLng(23.3549, 116.6819)
                            map.moveCamera(
                                com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(defaultLocation, 16.5f)
                            )
                            Log.d("ElderSimplifiedScreen", "📍 地图初始化：等待定位，zoom=16.5f（街道级）")
                        }
                        
                        // 隐藏地图自带的缩放按钮
                        map.uiSettings.isZoomControlsEnabled = false
                        // 启用所有手势
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = false  // ⭐ 修复：禁用倾斜手势，与上面保持一致
                        map.uiSettings.isCompassEnabled = true
                        
                    } catch (e: Exception) {
                        Log.e("ElderSimplifiedScreen", "❌ 地图初始化失败", e)
                    }
                },
                onMapClick = {},
                onPoiClick = {}
            )
            
            // 2. 自定义缩放按钮 - 紧贴顶部标题栏左下角
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 90.dp, start = 12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 放大按钮
                    Card(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                elderAMap?.let { map ->
                                    map.animateCamera(CameraUpdateFactory.zoomIn())
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "放大",
                                tint = Color(0xFF1677FF),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    // 缩小按钮
                    Card(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                elderAMap?.let { map ->
                                    map.animateCamera(CameraUpdateFactory.zoomOut())
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "缩小",
                                tint = Color(0xFF1677FF),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
            
            // 3. 定位按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 300.dp, end = 16.dp)
            ) {
                if (!isFollowingLocation) {
                    Canvas(
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    ) {
                        val pulseRadius = 32.0f * pulseAnimation
                        drawCircle(color = Color.Red.copy(alpha = 0.3f), radius = pulseRadius)
                    }
                }
                
                FloatingActionButton(
                    onClick = {
                        isFollowingLocation = true
                        currentLocation?.let { loc ->
                            elderAMap?.animateCamera(
                                com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(loc, 16.5f)
                            )
                            toastMessage = "已回到当前位置"
                        } ?: run {
                            toastMessage = "正在获取位置..."
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .scale(if (isFollowingLocation) 1f else pulseAnimation),
                    containerColor = if (isFollowingLocation) MaterialTheme.colorScheme.primary else Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "定位",
                            tint = if (isFollowingLocation) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        
                        if (!isFollowingLocation) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-4).dp)
                            )
                        }
                    }
                }
            }
            
            // 4. 底部面板 - ⭐ 修复：让快捷操作卡片紧贴底部导航栏
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)  // ⭐ 新增：限制最大高度，确保可滚动
                    .align(Alignment.BottomCenter),  // ⭐ 修复：紧贴底部导航栏，无额外间距
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),  // ⭐ 减小圆角：32dp→24dp
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)  // ⭐ 减小内边距：10dp→8dp，更紧贴
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)  // ⭐ 减小间距：10dp→8dp，更紧凑
                ) {
                    // 订单信息 - ⭐ 调高位置，增加顶部边距
                    if (orderState is HomeViewModel.OrderState.Success) {
                        val order = (orderState as HomeViewModel.OrderState.Success).order
                        Spacer(modifier = Modifier.height(16.dp))  // ⭐ 新增：增加顶部间距，调高卡片位置
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE8F5E9)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🚗 行程进行中",
                                        fontSize = 19.sp,  // ⭐ 调小一号：20.sp → 19.sp
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Icon(
                                        Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                Divider(color = Color(0xFFC8E6C9), thickness = 1.dp)
                                
                                Text("目的地：${order.poiName ?: order.destAddress ?: "未知"}", fontSize = 14.sp, color = Color(0xFF1A1A1A))  // ⭐ 缩小1字号：15.sp → 14.sp
                                
                                order.driverName?.let { Text(text = "司机：$it", fontSize = 14.sp, color = Color(0xFF1A1A1A)) }  // ⭐ 缩小1字号
                                order.carNo?.let { Text(text = "车牌：$it", fontSize = 14.sp, color = Color(0xFF1A1A1A)) }  // ⭐ 修复：carPlate → carNo
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = {
                                        if (order.driverPhone != null) {
                                            makePhoneCall(order.driverPhone)
                                        } else {
                                            toastMessage = "暂无司机联系方式"
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2E7D32)
                                    )
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("呼叫司机", fontSize = 14.sp, fontWeight = FontWeight.Bold)  // ⭐ 缩小1字号：15.sp → 14.sp
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        // ⭐ 新增：同步长辈模式状态到 ChatViewModel
                                        chatViewModel.syncElderMode(true)
                                        onNavigateToChat()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF1677FF)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1677FF))
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("聊天", fontSize = 14.sp, fontWeight = FontWeight.Bold)  // ⭐ 缩小1字号：15.sp → 14.sp
                                }
                            }
                        }
                    }
                    
                    // 快捷操作区域标题
                    Text(
                        text = "快捷操作",
                        fontSize = 15.sp,  // ⭐ 加大1字号：14.sp → 15.sp
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(top = 8.dp)  // ⭐ 修复：增加上边距，与上方内容保持合理间距
                    )
                    
                    // 呼叫亲友按钮 - 主要操作
                    Button(
                        onClick = {
                            val guardians = viewModel.guardianInfoList.value
                            if (guardians.isNotEmpty()) {
                                val firstGuardian = guardians.firstOrNull()
                                if (firstGuardian?.phone != null) {
                                    makePhoneCall(firstGuardian.phone)
                                } else {
                                    toastMessage = "暂无亲友联系方式"
                                }
                            } else {
                                toastMessage = "暂无绑定的亲友，请先在个人中心添加"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 12.dp
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)  // ⭐ 缩小红框：48dp → 44dp
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp)),  // ⭐ 圆角同步缩小
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)  // ⭐ 图标缩小：32dp → 28dp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "呼叫亲友",
                                fontSize = 17.sp,  // ⭐ 缩小1字号：18.sp → 17.sp
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    
                    // 智能体对话按钮
                    OutlinedButton(
                        onClick = {
                            // ⭐ 新增：同步长辈模式状态到 ChatViewModel
                            chatViewModel.syncElderMode(true)
                            onNavigateToChat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1677FF)
                        ),
                        border = androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFF1677FF))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)  // ⭐ 缩小蓝框：44dp → 40dp
                                    .background(Color(0xFF1677FF).copy(alpha = 0.1f), RoundedCornerShape(20.dp)),  // ⭐ 圆角同步缩小
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = Color(0xFF1677FF),
                                    modifier = Modifier.size(24.dp)  // ⭐ 图标缩小：28dp → 24dp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "智能体对话",
                                fontSize = 16.sp,  // ⭐ 减小2字号：18.sp → 16.sp
                                fontWeight = FontWeight.Bold
                            )

                        }
                    }
                    
                    // ⭐ 已移除个人中心按钮
                }
            }
        }
    }
    
    // ========== 对话框 ==========
    
    // ⭐ 修复：代叫车确认对话框已移至 MainActivity 全局处理，此处移除
    // 避免与全局弹窗冲突
}
