package com.example.myapplication.presentation.orderTracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
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
import com.example.myapplication.map.MapViewComposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    
    // ⭐ 修复：在 Composable 顶层创建 scope
    val scope = rememberCoroutineScope()

    // 地图相关
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var driverMarker by remember { mutableStateOf<com.amap.api.maps.model.Marker?>(null) }
    var routePolyline by remember { mutableStateOf<com.amap.api.maps.model.Polyline?>(null) }

    // 初始化追踪
    LaunchedEffect(orderId) {
        viewModel.initTracking(orderId)
    }

    // 监听司机位置变化，平滑移动标记
    LaunchedEffect(driverLocation) {
        driverLocation?.let { location ->
            aMap?.let { map ->
                if (driverMarker == null) {
                    // 创建司机标记
                    driverMarker = map.addMarker(
                        MarkerOptions()
                            .position(location)
                            .icon(BitmapDescriptorFactory.fromResource(
                                android.R.drawable.ic_menu_mylocation  // 使用系统图标，可替换为自定义小车图标
                            ))
                            .title("司机位置")
                    )
                } else {
                    // 平滑移动标记（2.5秒动画）
                    animateMarkerSmoothly(driverMarker!!, location, durationMs = 2500)
                }
                
                // 移动相机到司机位置
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))
            }
        }
    }

    // 绘制路线
    LaunchedEffect(uiState) {
        if (uiState is OrderTrackingUiState.Success) {
            val order = (uiState as OrderTrackingUiState.Success).order
            aMap?.let { map ->
                // 清除旧路线
                routePolyline?.remove()
                
                // 如果有起点和终点坐标，绘制路线
                val startLat = order.startLat ?: order.destLat
                val startLng = order.startLng ?: order.destLng
                val destLat = order.destLat
                val destLng = order.destLng
                
                if (startLat != null && startLng != null && destLat != null && destLng != null) {
                    val polyline = map.addPolyline(
                        PolylineOptions()
                            .add(LatLng(startLat, startLng))
                            .add(LatLng(destLat, destLng))
                            .color(Color(0xFF3366FF).hashCode())
                            .width(8f)
                    )
                    routePolyline = polyline
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
                            // ⭐ 修复：使用顶层创建的 scope
                            scope.launch {
                                try {
                                    val result = viewModel.cancelOrder(orderId)
                                    if (result) {
                                        Toast.makeText(context, "订单已取消", Toast.LENGTH_SHORT).show()
                                        // 可选：返回上一页
                                        onBackClick()
                                    } else {
                                        Toast.makeText(context, "取消失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "取消订单异常：${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderTrackingContent(
    order: com.example.myapplication.data.model.Order,
    etaMinutes: Int?,
    aMap: AMap?,
    onMapReady: (AMap) -> Unit,
    onCallDriver: () -> Unit,
    onCancelOrder: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. 地图区域（占70%）
        MapViewComposable(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
            onMapReady = onMapReady,
            onMapClick = {},
            onPoiClick = {}
        )

        // 2. 底部信息面板（占30%）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 8.dp,
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态条
                StatusBanner(order.status, etaMinutes)

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

                // 目的地信息
                DestinationInfo(
                    destAddress = order.poiName ?: order.destAddress
                )

                // 操作按钮
                ActionButtons(
                    canCancel = order.status <= 1,
                    onCancelOrder = onCancelOrder,
                    onCallDriver = onCallDriver
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(status: Int, etaMinutes: Int?) {
    val (statusText, statusColor, icon) = when (status) {
        0 -> Triple("派单中", Color(0xFFFF9800), Icons.Default.CarCrash)
        1 -> Triple("待接单", Color(0xFF2196F3), Icons.Default.CarCrash)
        2, 3 -> Triple("已接单 - 司机赶来中", Color(0xFF2196F3), Icons.Default.DirectionsCar)
        4 -> Triple("已到达 - 请上车", Color(0xFF4CAF50), Icons.Default.CheckCircle)
        5 -> Triple("行程中", Color(0xFF9C27B0), Icons.Default.DirectionsCar)
        6 -> Triple("已取消", Color.Gray, Icons.Default.CarCrash)
        7 -> Triple("已拒绝", Color.Red, Icons.Default.CarCrash)
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
                
                if (etaMinutes != null && status in 2..3) {
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
    canCancel: Boolean,
    onCancelOrder: () -> Unit,
    onCallDriver: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (canCancel) {
            OutlinedButton(
                onClick = onCancelOrder,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("取消订单", fontSize = 15.sp)
            }
        }
        
        Button(
            onClick = onCallDriver,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1677FF))
        ) {
            Text("联系司机", fontSize = 15.sp)
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
    val targetLat = order.driverLat ?: order.startLat ?: order.destLat ?: return
    val targetLng = order.driverLng ?: order.startLng ?: order.destLng ?: return
    
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(targetLat, targetLng), 15f))
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
