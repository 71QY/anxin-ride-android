package com.example.myapplication.presentation.home

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.example.myapplication.data.model.ElderInfo
import com.example.myapplication.data.model.PoiResponse
import com.example.myapplication.map.MapViewComposable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║  ⚠️⚠️⚠️ 重要标识：这是【普通用户模式】界面 ⚠️⚠️⚠️   ║
 * ║                                                          ║
 * ║  适用场景：                                               ║
 * ║  - 普通用户（非长辈）的首页                               ║
 * ║  - 需要输入目的地、叫车下单                               ║
 * ║  - 可以帮长辈代叫车                                       ║
 * ║  - 有智能体对话入口                                       ║
 * ║                                                          ║
 * ║  特征：                                                   ║
 * ║  - 标题："安心出行"（居左）                               ║
 * ║  - 底部有"输入目的地"卡片                                 ║
 * ║  - 地图可点击选点                                         ║
 * ║  - 右侧有定位、智能体按钮                                 ║
 * ║                                                          ║
 * ║  🚫 修改此文件不会影响长辈端界面！                        ║
 * ║  ✅ 长辈端界面在 ElderSimplifiedScreen.kt                 ║
 * ╚══════════════════════════════════════════════════════════╝
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalAnimationApi::class)
@Composable
fun NormalHomeScreen(
    viewModel: HomeViewModel,  // ⭐ 修复：移除默认参数，必须由调用方传入
    onNavigateToProfile: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onNavigateToOrder: (String) -> Unit,
    onNavigateToChat: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToOrderTracking: (Long) -> Unit = {}  // ⭐ 新增：跳转到行程追踪页面
) {
    // ========== 状态收集 ==========
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val showPoiDetailDialog by viewModel.showPoiDetailDialog.collectAsStateWithLifecycle()
    val poiDetail by viewModel.poiDetail.collectAsStateWithLifecycle()
    val selectedPoiForMap by viewModel.selectedPoiForMap.collectAsStateWithLifecycle()
    
    val isProfileLoaded by viewModel.isProfileLoaded.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    
    val isGeocoding by viewModel.isGeocoding.collectAsStateWithLifecycle()
    val geocodeError by viewModel.geocodeError.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val backendPoiResults by viewModel.backendPoiResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val clickedLocation by viewModel.clickedLocation.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val voiceText by viewModel.voiceText.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val locationAccuracy by viewModel.locationAccuracy.collectAsStateWithLifecycle()
    val orderState by viewModel.orderState.collectAsStateWithLifecycle()
    val isCreatingOrder by viewModel.isCreatingOrder.collectAsStateWithLifecycle()
    
    val elderList by viewModel.elderInfoList.collectAsStateWithLifecycle()
    val currentAccent by viewModel.currentAccent.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    
    // ========== UI状态 ==========
    val context = LocalContext.current
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    var showMapHint by remember { mutableStateOf(true) }
    var hasRequestedLocation by remember { mutableStateOf(false) }
    
    // 帮长辈叫车相关
    var showProxyOrderDialog by remember { mutableStateOf(false) }
    var selectedElderForProxy by remember { mutableStateOf<ElderInfo?>(null) }
    var isSubmittingProxyOrder by remember { mutableStateOf(false) }
    var showOrderTypeDialog by remember { mutableStateOf(false) }
    
    var showPoiDialog by remember { mutableStateOf(false) }
    var isNationwideSearch by remember { mutableStateOf(false) }
    var showAgentSheet by remember { mutableStateOf(false) }
    var showDialectDialog by remember { mutableStateOf(false) }
    
    // ⭐ 新增:收藏取名对话框状态
    var showFavoriteNameDialog by remember { mutableStateOf(false) }
    var customFavoriteName by remember { mutableStateOf("") }
    
    val aMapState = remember { mutableStateOf<AMap?>(null) }
    var bottomPanelHeight by rememberSaveable { mutableStateOf(250) }  // ⭐ 修复：固定初始高度250dp，避免盖过地图，用户可以上拉展开
    val minPanelHeight = 120  // ⭐ 同步增大最小高度
    val maxPanelHeight = 600
    
    var showMapSettingsDialog by remember { mutableStateOf(false) }
    var mapTextSizeLevel by remember { mutableStateOf(1) }
    
    // ========== 副作用处理 ==========
    
    // 1. ⭐ 修复：移除自动登录失败检测，由 MainActivity 统一处理
    // 原因：HomeViewModel 的 userId 是异步获取的，这里的同步检查会导致误判
    // 如果 Token 真的失效，AuthInterceptor 会自动清除，下次启动时会跳转到登录页
    
    // 2. 地图提示自动消失
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        showMapHint = false
    }
    
    // 3. 定位权限处理
    var hasStartedLocation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(locationPermissionState.status) {
        if (locationPermissionState.status == PermissionStatus.Granted && !hasStartedLocation) {
            Log.d("NormalHomeScreen", "✅ 权限已授予，启动独立定位")
            viewModel.startIndependentLocation()
            
            // ⭐ 新增：启动后台定位追踪服务，确保切换应用后仍能持续获取位置和维持WebSocket连接
            viewModel.startBackgroundTracking()
            Log.d("NormalHomeScreen", "🚀 已启动后台定位追踪服务")
            
            hasStartedLocation = true
        }
    }
    
    // ⭐ 新增：监听定位变化，首次定位成功后自动移动相机到用户位置
    var hasInitialLocated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentLocation) {
        if (currentLocation != null && !hasInitialLocated) {
            Log.d("NormalHomeScreen", "📍 首次定位成功，自动移动相机到用户位置")
            aMapState.value?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(currentLocation!!, 16.5f)
            )
            hasInitialLocated = true
        }
    }
    
    // 4. POI搜索结果监听 - ⭐ 修复：只要有结果就显示对话框
    LaunchedEffect(backendPoiResults) {
        if (backendPoiResults.isNotEmpty()) {
            Log.d("NormalHomeScreen", "🔍 收到搜索结果，共${backendPoiResults.size}条，显示对话框")
            showPoiDialog = true
        } else {
            // ⭐ 清空结果时关闭对话框
            showPoiDialog = false
        }
    }
    
    // 5. 订单状态监听
    var hasHandledOrderSuccess by remember { mutableStateOf(false) }
    
    // ⭐ 新增：监听导航事件（跳转到行程追踪页面）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeViewModel.HomeEvent.NavigateToOrderTracking -> {
                    Log.d("NormalHomeScreen", "🚀 收到导航事件，跳转到行程追踪: orderId=${event.orderId}")
                    onNavigateToOrderTracking(event.orderId)
                }
                else -> {}
            }
        }
    }
    
    // ⭐ 新增：监听长辈列表变化
    LaunchedEffect(elderList) {
        Log.d("NormalHomeScreen", "👴 elderList变化，当前大小: ${elderList.size}")
        if (elderList.isNotEmpty()) {
            Log.d("NormalHomeScreen", "✅ 长辈列表已加载：${elderList.map { it.name }}")
        } else {
            Log.w("NormalHomeScreen", "⚠️ 长辈列表为空，可能未绑定长辈或加载失败")
        }
    }
    LaunchedEffect(orderState) {
        when (val currentState = orderState) {
            is HomeViewModel.OrderState.Success -> {
                if (hasHandledOrderSuccess) return@LaunchedEffect
                
                val order = currentState.order
                Log.d("NormalHomeScreen", "✅ 订单创建成功: ${order.id}")
                hasHandledOrderSuccess = true
                // ⭐ 修复：不再在这里跳转，由 NavigateToOrderTracking 事件统一处理
                viewModel.resetOrderState()
            }
            is HomeViewModel.OrderState.Error -> {
                Toast.makeText(context, currentState.message, Toast.LENGTH_SHORT).show()
                viewModel.resetOrderState()
                hasHandledOrderSuccess = false
                isSubmittingProxyOrder = false
            }
            is HomeViewModel.OrderState.Idle -> {
                hasHandledOrderSuccess = false
            }
            else -> {}
        }
    }
    
    // 6. 地理编码错误处理
    LaunchedEffect(geocodeError) {
        geocodeError?.let { error ->
            if (error.isNotBlank()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                viewModel.clearGeocodeError()
            }
        }
    }
    
    // 7. 首次进入时加载长辈列表
    LaunchedEffect(Unit) {
        // ⭐ 修复：移除重复的定位启动，由 LaunchedEffect #3 统一处理
        // startIndependentLocation() 和 startBackgroundTracking() 已在权限授予时启动
        
        // 如果还没有权限，请求权限（LaunchedEffect #3 会在权限授予后自动启动定位）
        if (locationPermissionState.status != PermissionStatus.Granted) {
            locationPermissionState.launchPermissionRequest()
        }
        
        // 加载长辈列表
        viewModel.loadElderList()
        Log.d("NormalHomeScreen", "👴 已触发加载长辈列表")
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
    
    // ========== UI渲染 ==========
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()  // ⭐ 关键修复：确保键盘弹出时输入框可见
    ) {
        // 1. 地图区域 - ⭐ 优化：地图占据全屏
        MapViewComposable(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { aMap ->
                aMapState.value = aMap
                aMap.isMyLocationEnabled = true
                
                // ✅ 修复：使用 LOCATION_TYPE_LOCATION_ROTATE 模式，显示蓝点和精度圈
                val myLocationStyle = com.amap.api.maps.model.MyLocationStyle()
                myLocationStyle.myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
                myLocationStyle.radiusFillColor(android.graphics.Color.argb(100, 66, 133, 244))  // ✅ 加深蓝色填充（透明度100/255）
                myLocationStyle.strokeColor(android.graphics.Color.argb(220, 66, 133, 244))  // ✅ 加深蓝色边框（透明度220/255）
                myLocationStyle.strokeWidth(4f)  // ✅ 加粗边框到4px
                myLocationStyle.interval(3000)  // ✅ 3秒更新一次，防止精度圈消失
                myLocationStyle.showMyLocation(true)
                aMap.myLocationStyle = myLocationStyle
                
                // ✅ 修复：降低防抖阈值，让精度圈更稳定
                var lastLocationUpdate = 0L
                var lastLat = 0.0
                var lastLng = 0.0
                val MIN_LOCATION_CHANGE_METERS = 3.0  // ✅ 降低到3米（减少消失）
                
                aMap.setOnMyLocationChangeListener { location ->
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
                        Log.d("NormalHomeScreen", "📍 位置更新：lat=$currentLat, lng=$currentLng, accuracy=${location.accuracy}m")
                    }
                }
                
                // ⭐ 隐藏地图自带的缩放按钮
                aMap.uiSettings.isZoomControlsEnabled = false
                
                // ⭐ 修复：初始化地图相机 - 统一zoom为16.5f街道级别，避免显示空茫茫的城市级别
                val targetLocation = currentLocation
                if (targetLocation != null) {
                    // 定位已完成，直接移动到当前位置
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 16.5f))
                    Log.d("NormalHomeScreen", "📍 地图初始化：已有定位，zoom=16.5f（街道级）")
                } else {
                    // ⭐ 修复：定位未完成时，也使用zoom=16.5f街道级别，避免显示空茫茫的城市级别
                    val defaultLocation = LatLng(23.3549, 116.6819)
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 16.5f))
                    Log.d("NormalHomeScreen", "📍 地图初始化：等待定位，zoom=16.5f（街道级）")
                }
            },
            onMapClick = {
                vibrate()
                Toast.makeText(context, "已选择位置", Toast.LENGTH_SHORT).show()
                viewModel.onMapClick(it)
            },
            onPoiClick = { poi ->
                viewModel.onPoiClick(poi)
            }
        )
        
        // 2. 顶部标题栏 - ⭐ 优化：浮动在地图上方，半透明背景，文字居中
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 8.dp),  // ⭐ 修复：左右padding改为0，增加卡面宽度约2cm
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)  // ⭐ 修复：从44.dp增加到80.dp，增加约2cm高度
            ) {
                // ⭐ 居左标题
                Text(
                    text = "安心出行",
                    fontSize = 21.sp,  // ⭐ 加大1字号：20.sp → 21.sp
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)  // ⭐ 左侧留白16dp
                )
                
                // ⭐ 右侧按钮
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "个人中心", tint = Color(0xFF165DFF), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        
        // 3. 地图提示 - ⭐ 优化：提示用户点击地点可以叫车
        AnimatedVisibility(visible = showMapHint) {
            Card(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 104.dp, start = 12.dp, end = 12.dp),  // ⭐ 增加右边距
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF165DFF).copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("💡 小提示", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "点击地图上的地点，即可为自己或长辈叫车",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        
        // 4. 自定义缩放按钮 - 紧贴顶部标题栏左下角
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 96.dp, start = 12.dp),  // ⭐ 紧贴标题卡片下方
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
                        aMapState.value?.animateCamera(CameraUpdateFactory.zoomIn())
                    }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "放大",
                        tint = Color(0xFF165DFF),
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
                        aMapState.value?.animateCamera(CameraUpdateFactory.zoomOut())
                    }
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "缩小",
                        tint = Color(0xFF165DFF),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        
        // 5. 右侧按钮组 - ⭐ 修复：上移1cm（约38dp），329-38=291dp
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 291.dp, end = 12.dp),  // ⭐ 上移1cm
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // 定位按钮
                FloatingActionButton(
                    onClick = {
                        currentLocation?.let {
                            aMapState.value?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 16.5f))
                        } ?: run {
                            Toast.makeText(context, "🛰️ 正在获取位置...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(52.dp),
                    containerColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "定位",
                        tint = if (currentLocation != null) Color(0xFF165DFF) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // 智能体按钮
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FloatingActionButton(
                        onClick = { onNavigateToChat() },
                        modifier = Modifier.size(52.dp),
                        containerColor = Color(0xFF165DFF),
                        elevation = FloatingActionButtonDefaults.elevation(6.dp)
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = "智能体对话",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "智能体",
                        fontSize = 11.sp,
                        color = Color(0xFF165DFF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        
        // 5. 加载遮罩
        if (isGeocoding) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            }
        }
        
        // 6. 底部面板 - ⭐ 优化：添加平滑动画和更好的拖动响应
        val animatedBottomPanelHeight by animateIntAsState(
            targetValue = bottomPanelHeight,
            label = "bottomPanelHeight"
        )
        
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(animatedBottomPanelHeight.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            // ⭐ 优化：增加灵敏度，直接更新高度
                            val newHeight = (bottomPanelHeight - dragAmount.toInt()).coerceIn(minPanelHeight, maxPanelHeight)
                            if (newHeight != bottomPanelHeight) {
                                bottomPanelHeight = newHeight
                            }
                        },
                        onDragEnd = {
                            // ⭐ 优化：拖动结束时自动吸附到合适的位置
                            when {
                                bottomPanelHeight < 200 -> bottomPanelHeight = minPanelHeight
                                bottomPanelHeight > 450 -> bottomPanelHeight = maxPanelHeight
                                else -> bottomPanelHeight = 350  // 中间位置
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)  // ⭐ 优化：增加阴影提升层次感
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState())
            ) {
                // 拖动手柄 - ⭐ 优化：更明显的视觉反馈
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)  // ⭐ 增加可点击区域
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color(0xFFCCCCCC), RoundedCornerShape(2.dp))
                    )
                }
                    
                    // 标题 - ⭐ 优化：加大字号
                    Text(
                        text = "输入目的地",
                        fontSize = 16.sp,  // ⭐ 修复：从14.sp增加到16.sp
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(bottom = 4.dp)  // ⭐ 优化：减小间距，使卡片更紧凑
                    )
                    
                    // ⭐ 新增：语音输入实时显示（仿搜狗输入法）
                    AnimatedVisibility(
                        visible = isListening,
                        enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4FF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ⭐ 录音动画图标
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF165DFF),
                                        strokeWidth = 2.dp
                                    )
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color(0xFF165DFF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                // ⭐ 实时文字显示
                                Text(
                                    text = if (voiceText.isNotBlank()) voiceText else "正在听您说话...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF1A1A1A),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                // ⭐ 清空按钮
                                if (voiceText.isNotBlank()) {
                                    IconButton(
                                        onClick = { viewModel.updateDestination("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "清空",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 目的地输入框 - ⭐ 优化：添加防抖和更好的交互反馈
                    var isSearchFieldFocused by remember { mutableStateOf(false) }
                    val focusRequester = FocusRequester()
                    var lastSearchTime by remember { mutableStateOf(0L) }
                    
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { 
                            viewModel.updateDestination(it)
                            // ⭐ 优化：输入时清空之前的搜索结果，避免显示过时数据
                            if (it.isBlank()) {
                                viewModel.clearSearchResults()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).focusRequester(focusRequester).onFocusChanged { isSearchFieldFocused = it.isFocused },
                        placeholder = { Text("你要去哪？", color = Color(0xFF999999), fontSize = 16.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF5F7FA),
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color(0xFF165DFF),
                            cursorColor = Color(0xFF165DFF)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Color(0xFF1A1A1A)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                // ⭐ 优化：点击键盘搜索按钮时触发搜索
                                Log.d("NormalHomeScreen", "🔍 键盘搜索按钮被点击")
                                Log.d("NormalHomeScreen", "  - destination=$destination")
                                Log.d("NormalHomeScreen", "  - currentLocation=$currentLocation")
                                
                                if (destination.isBlank()) {
                                    Toast.makeText(context, "请输入目的地", Toast.LENGTH_SHORT).show()
                                    return@KeyboardActions
                                }
                                
                                if (currentLocation == null) {
                                    Toast.makeText(context, "请先获取当前位置", Toast.LENGTH_SHORT).show()
                                    return@KeyboardActions
                                }
                                
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastSearchTime < 500) {
                                    Log.d("NormalHomeScreen", "⚠️ 防抖：搜索过于频繁")
                                    return@KeyboardActions
                                }
                                lastSearchTime = currentTime
                                
                                Log.d("NormalHomeScreen", "✅ 开始搜索：$destination")
                                viewModel.searchPoiFromBackend(destination)
                            }
                        ),
                        trailingIcon = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // ⭐ 优化：搜索按钮始终可见，但禁用状态时有视觉反馈
                                IconButton(
                                    onClick = {
                                        Log.d("NormalHomeScreen", "🔍 搜索按钮被点击")
                                        Log.d("NormalHomeScreen", "  - destination=$destination")
                                        Log.d("NormalHomeScreen", "  - currentLocation=$currentLocation")
                                        
                                        if (destination.isBlank()) {
                                            Toast.makeText(context, "请输入目的地", Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        
                                        if (currentLocation == null) {
                                            Toast.makeText(context, "请先获取当前位置", Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastSearchTime < 500) {
                                            Log.d("NormalHomeScreen", "⚠️ 防抖：搜索过于频繁")
                                            return@IconButton
                                        }
                                        lastSearchTime = currentTime
                                        
                                        Log.d("NormalHomeScreen", "✅ 开始搜索：$destination")
                                        viewModel.searchPoiFromBackend(destination)
                                    },
                                    modifier = Modifier.size(40.dp),
                                    enabled = !isSearching && destination.isNotBlank()  // ⭐ 优化：搜索中时禁用
                                ) {
                                    if (isSearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF165DFF),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = "搜索", tint = if (destination.isNotBlank()) Color(0xFF165DFF) else Color.Gray, modifier = Modifier.size(24.dp))
                                    }
                                }
                                
                                IconButton(onClick = {
                                    if (audioPermissionState.status == PermissionStatus.Granted) {
                                        viewModel.startVoiceInput(context)
                                    } else {
                                        Toast.makeText(context, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                                        audioPermissionState.launchPermissionRequest()
                                    }
                                }, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic, 
                                        contentDescription = if (isListening) "停止录音（再次点击）" else "语音输入", 
                                        tint = if (isListening) Color.Red else Color(0xFF165DFF), 
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                IconButton(onClick = { showDialectDialog = true }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Language, contentDescription = "选择方言", tint = Color(0xFF165DFF), modifier = Modifier.size(24.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))  // ⭐ 修复：搜索框和按钮之间的合理间距，12dp→6dp
                    
                    // 为自己叫车按钮 - ⭐ 修复：恢复独立按钮
                    Button(
                        onClick = {
                            if (destination.isBlank()) {
                                Toast.makeText(context, "请输入目的地", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (currentLocation == null) {
                                Toast.makeText(context, "请等待定位完成", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.createOrder(destination)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),  // ⭐ 修复：从42.dp增加到48.dp
                        enabled = !isCreatingOrder && destination.isNotBlank() && currentLocation != null,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF165DFF)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 10.dp
                        )
                    ) {
                        if (isCreatingOrder) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("为自己叫车", fontSize = 16.sp, fontWeight = FontWeight.Bold)  // ⭐ 修复：改为"为自己叫车"
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))  // ⭐ 修复：两个按钮之间的间距缩小
                    
                    // 帮长辈叫车按钮 - ⭐ 新增：恢复此按钮
                    OutlinedButton(
                        onClick = {
                            if (destination.isBlank()) {
                                Toast.makeText(context, "请先输入目的地", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            if (elderList.isEmpty()) {
                                Toast.makeText(context, "您还没有绑定长辈，请先在个人中心绑定", Toast.LENGTH_LONG).show()
                            } else {
                                showProxyOrderDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF165DFF)
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = Color(0xFF165DFF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("帮长辈叫车", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    
    // ========== 对话框 ==========
    
    // POI选择对话框 - ⭐ 优化：使用AnimatedVisibility和更好的列表项样式
    if (showPoiDialog && backendPoiResults.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showPoiDialog = false; viewModel.clearSearchResults() },
            title = { 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("选择目的地", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),  // ⭐ 限制最大高度
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(backendPoiResults) { poi ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vibrate()  // ⭐ 优化:点击时震动反馈
                                    viewModel.selectBackendPoi(poi)
                                    showPoiDialog = false
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = Color(0xFF165DFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = poi.name ?: "未知位置",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1A1A1A),
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = poi.address ?: "未知地址",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    maxLines = 2
                                )
                                if (poi.distance != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = Color(0xFF1677FF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "距离：${String.format("%.1f", poi.distance / 1000.0)}公里",
                                            fontSize = 12.sp,
                                            color = Color(0xFF1677FF),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPoiDialog = false; viewModel.clearSearchResults() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // POI详情对话框
    if (showPoiDetailDialog && poiDetail != null) {
        poiDetail?.let { detail ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPoiDetailDialog() },
                icon = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(detail.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = detail.address, style = MaterialTheme.typography.bodyMedium)
                        if (detail.distance != null) {
                            Text(text = "距离：${detail.formattedDistance ?: String.format("%.1f 公里", detail.distance / 1000)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (detail.duration != null) {
                            val minutes = detail.duration / 60
                            Text(text = "预计时长：${minutes} 分钟", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (detail.price != null) {
                            Text(text = "预估车费：¥${detail.price}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        // ⭐ 修改：收藏按钮 - 弹出取名对话框
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    // ⭐ 弹出取名对话框
                                    customFavoriteName = detail.name
                                    showFavoriteNameDialog = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = "收藏",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("收藏", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.dismissPoiDetailDialog()  // ⭐ 关键修复：先关闭 POI 详情弹窗
                        showOrderTypeDialog = true
                    }, enabled = detail.canOrder) {
                        Text("确认目的地")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPoiDetailDialog() }) {
                        Text("取消")
                    }
                }
            )
        }
    }
    
    // ⭐ 新增:收藏取名对话框(用于POI详情弹窗中的收藏按钮)
    if (showFavoriteNameDialog && poiDetail != null) {
        poiDetail?.let { detail ->
            AlertDialog(
                onDismissRequest = { showFavoriteNameDialog = false },
                title = { Text("给这个地点取个名字") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = detail.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(text = detail.address, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                        OutlinedTextField(
                            value = customFavoriteName,
                            onValueChange = { customFavoriteName = it },
                            label = { Text("自定义名称") },
                            placeholder = { Text("如：看病的地方、买菜的地方") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(text = "💡 提示：取一个你容易记住的名字", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customFavoriteName.isBlank()) return@Button
                            viewModel.addFavorite(
                                name = customFavoriteName,
                                address = detail.address,
                                latitude = detail.lat,
                                longitude = detail.lng,
                                onSuccess = {
                                    showFavoriteNameDialog = false
                                    viewModel.dismissPoiDetailDialog()
                                    Toast.makeText(context, "已收藏该地点", Toast.LENGTH_SHORT).show()
                                },
                                onError = { errorMsg ->
                                    Toast.makeText(context, "收藏失败：$errorMsg", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = customFavoriteName.isNotBlank()
                    ) { Text("保存") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showFavoriteNameDialog = false }) { Text("取消") }
                }
            )
        }
    }
    
    // 选择下单类型对话框
    if (showOrderTypeDialog) {
        Log.d("NormalHomeScreen", "📍 显示选择下单类型对话框，elderList.size=${elderList.size}")
        AlertDialog(
            onDismissRequest = { showOrderTypeDialog = false },
            title = { Text("选择下单类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            showOrderTypeDialog = false
                            viewModel.createOrder(destination)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("为自己叫车")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            Log.d("NormalHomeScreen", "👴 点击帮长辈叫车，elderList.size=${elderList.size}")
                            showOrderTypeDialog = false
                            if (elderList.isEmpty()) {
                                Log.w("NormalHomeScreen", "⚠️ 长辈列表为空，无法代叫车")
                                Toast.makeText(context, "您还没有绑定长辈，请先在个人中心绑定", Toast.LENGTH_LONG).show()
                            } else {
                                Log.d("NormalHomeScreen", "✅ 显示选择长辈对话框，共${elderList.size}个长辈")
                                showProxyOrderDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("帮长辈叫车")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOrderTypeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 选择长辈对话框
    if (showProxyOrderDialog) {
        AlertDialog(
            onDismissRequest = { showProxyOrderDialog = false },
            title = { Text("选择长辈") },
            text = {
                LazyColumn {
                    items(elderList) { elder ->
                        ListItem(
                            headlineContent = { Text(elder.name) },
                            supportingContent = { Text(elder.phone ?: "无电话") },
                            modifier = Modifier.clickable {
                                selectedElderForProxy = elder
                                
                                // ⭐ 关键修复：优先使用 selectedPoiForMap，其次使用 poiDetail
                                val targetPoiRaw = selectedPoiForMap ?: viewModel.poiDetail.value
                                
                                // ⭐ 转换为 PoiDetail 类型（支持多种 POI 类型）
                                val targetPoi = when (targetPoiRaw) {
                                    is com.example.myapplication.data.model.PoiDetail -> targetPoiRaw
                                    is com.example.myapplication.data.model.PoiResponse -> {
                                        // 将 PoiResponse 转换为 PoiDetail
                                        com.example.myapplication.data.model.PoiDetail(
                                            name = targetPoiRaw.name ?: "未知位置",
                                            address = targetPoiRaw.address ?: "未知地址",
                                            lat = targetPoiRaw.lat,
                                            lng = targetPoiRaw.lng,
                                            distance = null,
                                            duration = null,
                                            price = null,
                                            canOrder = true,
                                            sessionId = null
                                        )
                                    }
                                    is com.amap.api.maps.model.Poi -> {
                                        // 将高德地图 POI 转换为 PoiDetail
                                        com.example.myapplication.data.model.PoiDetail(
                                            name = targetPoiRaw.name ?: "未知位置",
                                            address = targetPoiRaw.name ?: "未知地址",  // ⭐ 修复：高德Poi没有snippet属性，使用name代替
                                            lat = targetPoiRaw.coordinate.latitude,
                                            lng = targetPoiRaw.coordinate.longitude,
                                            distance = null,
                                            duration = null,
                                            price = null,
                                            canOrder = true,
                                            sessionId = null
                                        )
                                    }
                                    else -> null
                                }
                                
                                if (targetPoi != null && elder.userId != null) {
                                    Log.d("NormalHomeScreen", "🚗 代叫车：长辈=${elder.name}, 目的地=${targetPoi.name}, destAddress=${targetPoi.address}, lat=${targetPoi.lat}, lng=${targetPoi.lng}")
                                    viewModel.createProxyOrderForElder(
                                        elderUserId = elder.userId,
                                        poiName = targetPoi.address ?: targetPoi.name,  // ⭐ 修复：优先使用完整地址
                                        destLat = targetPoi.lat,
                                        destLng = targetPoi.lng
                                    )
                                    showProxyOrderDialog = false
                                    Toast.makeText(context, "已向${elder.name}发送叫车请求，请等待确认", Toast.LENGTH_LONG).show()
                                } else {
                                    val errorMsg = when {
                                        targetPoi == null -> "请先选择目的地"
                                        elder.userId == null -> "无法获取长辈信息"
                                        else -> "未知错误"
                                    }
                                    Log.e("NormalHomeScreen", "❌ 代叫车失败：$errorMsg")
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProxyOrderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 方言选择对话框
    if (showDialectDialog) {
        AlertDialog(
            onDismissRequest = { showDialectDialog = false },
            title = { Text("选择方言") },
            text = {
                Column {
                    listOf(
                        "mandarin" to "普通话",
                        "cantonese" to "粤语",
                        "sichuan" to "四川话",
                        "henan" to "河南话"
                    ).forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDialect("zh", code)
                                    showDialectDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentAccent == code,
                                onClick = {
                                    viewModel.setDialect("zh", code)
                                    showDialectDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialectDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
