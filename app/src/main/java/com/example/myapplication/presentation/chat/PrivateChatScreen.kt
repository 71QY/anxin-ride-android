package com.example.myapplication.presentation.chat

import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Mic  // ⭐ 新增
import androidx.compose.material.icons.filled.PlayArrow  // ⭐ 新增
import androidx.compose.material.icons.filled.MyLocation  // ⭐ 新增：定位图标
import androidx.compose.material.icons.filled.Place  // ⭐ 新增：地点图标
import androidx.compose.material.icons.filled.NotificationsActive  // ⭐ 新增：代叫车提示图标
import androidx.compose.material.icons.filled.Close  // ⭐ 新增：关闭图标
import androidx.compose.material.icons.filled.AttachFile  // ⭐ 新增：附件图标
import androidx.compose.material.icons.filled.CheckCircle  // ⭐ 新增：已同意图标
import androidx.compose.material.icons.filled.DirectionsCar  // ⭐ 新增：行程中图标
import androidx.compose.material.icons.filled.EventAvailable  // ⭐ 新增：已结束图标
import androidx.compose.material.icons.filled.Check  // ⭐ 新增：勾选图标
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle  // ⭐ 新增：用于 StateFlow 收集
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.myapplication.data.model.PrivateChatMessage  // ⭐ 新增
import java.text.SimpleDateFormat
import java.util.*

/**
 * 亲友私聊界面（真正的P2P聊天）
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalLayoutApi::class)
@Composable
fun PrivateChatScreen(
    viewModel: PrivateChatViewModel = hiltViewModel(),
    chatViewModel: com.example.myapplication.presentation.chat.ChatViewModel? = null,  // ⭐ 新增：传入 ChatViewModel
    guardianId: Long,
    guardianName: String,
    onBackClick: () -> Unit,
    isElderMode: Boolean = false,  // ⭐ 新增：长辈模式标识
    onNavigateToHomeWithDestination: ((String, Double, Double) -> Unit)? = null,  // ⭐ 新增：跳转到首页叫车
    onNavigateToOrderTracking: ((Long) -> Unit)? = null,  // ⭐ 新增：跳转到行程追踪界面
    onNavigateToElderLocation: ((String, Double, Double) -> Unit)? = null,  // ⭐ 新增：跳转到长辈位置地图
    onConfirmProxyOrder: ((Long, Boolean) -> Unit)? = null  // ⭐ 新增：确认/拒绝代叫车回调
) {
    val context = LocalContext.current
    
    // ⭐ 关键日志：函数入口立即输出
    Log.d("PrivateChatScreen", "🚀 [函数入口] PrivateChatScreen 被调用")
    Log.d("PrivateChatScreen", "🚀 [函数入口] guardianId=$guardianId, guardianName=$guardianName, isElderMode=$isElderMode")
    
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()  // ⭐ 新增：获取当前用户ID
        
    // ⭐ 修复1：使用正确的 collectAsStateWithLifecycle API
    val cardLocation by chatViewModel?.sharedLocation?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    
    // ⭐ 修复31：精简日志，只输出关键信息
    LaunchedEffect(cardLocation) {
        cardLocation?.let { location ->
            Log.d("PrivateChatScreen", "📍 [状态监控] cardLocation变化: ${location.favoriteName}")
        }
    }
        
    // ⭐ 新增：弹窗状态管理
    var showShareDialog by remember { mutableStateOf(false) }
    // ⭐ 修复2：使用语义化的数据类替代 Triple
    data class ShareLocationData(
        val name: String,
        val latitude: Double,
        val longitude: Double
    )
    
    var pendingShareLocation by remember { mutableStateOf<ShareLocationData?>(null) }
    var pendingElderName by remember { mutableStateOf("") }
        
    // ⭐ 新增：标记分享是否已被处理（用户在外层弹窗点击了“立即叫车”）
    var isShareProcessed by remember { mutableStateOf(false) }
        
    // ⭐ 新增：标记是否已经对该分享地点弹过窗（避免重复弹窗）
    var hasShownDialogForCurrentShare by remember { mutableStateOf(false) }
        
    // ⭐ 关键修复：记录上次弹窗的 elderId，只有新的分享才弹窗
    var lastPopupElderId by remember { mutableStateOf<Long?>(null) }
    
    // ⭐ 新增：控制卡片在当前会话中的可见性（不影响缓存）
    var isCardVisible by remember { mutableStateOf(true) }
        
    var inputText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
        
    
    

        
    // ⭐ 修复：监听 cardLocation 的变化，当收到 WebSocket 消息时触发弹窗
    LaunchedEffect(cardLocation) {
        cardLocation?.let { location ->
                // ⭐ 修复11：简化判断逻辑，只检查 elderId 是否匹配当前聊天对象
                val currentUserId = com.example.myapplication.MyApplication.tokenManager.getUserId()
                val shouldShowCard = location.elderId == guardianId
                
                if (shouldShowCard) {
                    Log.d("PrivateChatScreen", "📍 [WebSocket] 收到分享的地点：${location.favoriteName}")
                
                // ⭐ 修复12：只有最新且未弹过窗的消息才弹窗
                val currentTime = System.currentTimeMillis()
                val messageTimestamp = location.elderLocationTimestamp ?: 0L
                val isRecentMessage = messageTimestamp > 0 && (currentTime - messageTimestamp) < 5000
                
                // ⭐ 关键修复：如果是旧消息，不弹窗但仍显示卡片
                if (!isRecentMessage) {
                    Log.d("PrivateChatScreen", "⏭️ [WebSocket] 消息来自缓存，只显示卡片不弹窗")
                    // ⭐ 不标记 hasShownDialogForCurrentShare，允许后续新消息弹窗
                    return@let
                }
                
                // ⭐ 检查是否已经对该 elderId 弹过窗
                if (lastPopupElderId == location.elderId && hasShownDialogForCurrentShare) {
                    Log.d("PrivateChatScreen", "⏭️ [WebSocket] 同一 elderId 已弹过窗，不重复弹窗")
                    return@let
                }
                
                // ⭐ 检查分享是否已被处理
                if (isShareProcessed) {
                    Log.d("PrivateChatScreen", "⏭️ [WebSocket] 分享已被处理，不弹窗")
                    return@let
                }
                    
                // ⭐ 修复16：播放提示音后释放资源
                try {
                    val ringtoneManager = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION
                    )
                    val ringtone = android.media.RingtoneManager.getRingtone(context, ringtoneManager)
                    ringtone.play()
                    // Ringtone 会在播放完成后自动释放，无需手动管理
                        
                    // ⭐ 修复17：检查 Vibrator 可用性
                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(200)
                        }
                        Log.d("PrivateChatScreen", "🔔 [提醒] 已播放提示音和震动")
                    } else {
                        Log.w("PrivateChatScreen", "⚠️ [提醒] 设备不支持震动")
                    }
                } catch (e: Exception) {
                    Log.e("PrivateChatScreen", "❌ 播放提醒失败", e)
                }
                    
                // ⭐ 触发弹窗
                pendingShareLocation = ShareLocationData(
                    name = location.favoriteName,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                pendingElderName = location.elderName
                showShareDialog = true
                hasShownDialogForCurrentShare = true
                lastPopupElderId = location.elderId
                Log.d("PrivateChatScreen", "✅ [WebSocket] 已触发弹窗，等待用户操作")
            } else {
                Log.d("PrivateChatScreen", "⚠️ [WebSocket] 忽略不匹配的分享 - elderId=${location.elderId}, 当前guardianId=$guardianId")
            }
        }
    }
    
    LaunchedEffect(guardianId) {
                    Log.d("PrivateChatScreen", "🔗 [初始化] 私聊界面初始化，guardianId=$guardianId")
        
        // ⭐ 关键修复：每次进入私聊界面时，重置卡片可见性
        isCardVisible = true
        
        // ⭐ 关键修复：同步长辈模式状态到 ChatViewModel
        if (chatViewModel != null) {
            chatViewModel.syncElderMode(isElderMode)
        }
        
        // ⭐ 获取当前用户ID
        val currentUserId = com.example.myapplication.MyApplication.tokenManager.getUserId()
        
        // ⭐ 新增：检查分享是否已被处理（用户在外层弹窗点击了“立即叫车”）
        try {
            val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
            isShareProcessed = prefs.getBoolean("share_processed_$guardianId", false)
            if (isShareProcessed) {
                Log.d("PrivateChatScreen", "✅ [初始化] 检测到分享已被处理")
            }
        } catch (e: Exception) {
            Log.e("PrivateChatScreen", "❌ [初始化] 读取 share_processed 标记失败", e)
        }
        
        if (chatViewModel != null) {
            val currentSharedLocation = chatViewModel.sharedLocation.value
            
            // ⭐ 关键修复：如果 StateFlow 为空，尝试从 SharedPreferences 恢复
            if (currentSharedLocation == null || currentSharedLocation.elderId != guardianId) {
                Log.d("PrivateChatScreen", "⚠️ [初始化] StateFlow 为空或不匹配，尝试从本地缓存恢复...")
                try {
                    val prefs = context.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                    
                    val cacheKey = guardianId
                    val cachedElderId = prefs.getLong("elderId_${cacheKey}", -1L)
                    
                    if (cachedElderId == cacheKey) {
                        val elderName = prefs.getString("elderName_${cacheKey}", "") ?: ""
                        val favoriteName = prefs.getString("favoriteName_${cacheKey}", "") ?: ""
                        val favoriteAddress = prefs.getString("favoriteAddress_${cacheKey}", "") ?: ""
                        val latitude = prefs.getFloat("latitude_${cacheKey}", 0f).toDouble()
                        val longitude = prefs.getFloat("longitude_${cacheKey}", 0f).toDouble()
                        val elderCurrentLat = prefs.getFloat("elderCurrentLat_${cacheKey}", 0f).toDouble()
                        val elderCurrentLng = prefs.getFloat("elderCurrentLng_${cacheKey}", 0f).toDouble()
                        val elderLocationTimestamp = prefs.getLong("elderLocationTimestamp_${cacheKey}", 0L)
                        // ⭐ 关键修复：从缓存恢复 orderId 和 orderStatus
                        val orderId = prefs.getLong("orderId_${cacheKey}", -1L).takeIf { it != -1L }
                        val orderStatus = prefs.getInt("orderStatus_${cacheKey}", 0)  // ⭐ 新增：恢复订单状态
                        
                        if (favoriteName.isNotBlank()) {
                            Log.d("PrivateChatScreen", "✅ [初始化] 从本地缓存恢复分享地点：$favoriteName")
                            
                            val restoredLocation = com.example.myapplication.presentation.chat.ChatViewModel.SharedLocationInfo(
                                elderId = cachedElderId,
                                elderName = elderName,
                                favoriteName = favoriteName,
                                favoriteAddress = favoriteAddress,
                                latitude = latitude,
                                longitude = longitude,
                                elderCurrentLat = if (elderCurrentLat != 0.0) elderCurrentLat else null,
                                elderCurrentLng = if (elderCurrentLng != 0.0) elderCurrentLng else null,
                                elderLocationTimestamp = if (elderLocationTimestamp != 0L) elderLocationTimestamp else null,
                                orderId = orderId,  // ⭐ 关键修复：从缓存恢复订单ID
                                orderStatus = orderStatus  // ⭐ 关键修复：从缓存恢复订单状态（0-待确认 1-已同意）
                            )
                            
                            // ⭐ 关键修复：从缓存恢复时，标记已弹过窗，避免触发 LaunchedEffect 弹窗
                            hasShownDialogForCurrentShare = true
                            
                            // ⭐ 更新 ChatViewModel 的 StateFlow
                            chatViewModel.setSharedLocation(restoredLocation)
                            Log.d("PrivateChatScreen", "✅ [初始化] 已恢复 sharedLocation，卡片将显示（不会弹窗）")
                        } else {
                            Log.d("PrivateChatScreen", "⚠️ [初始化] 本地缓存为空")
                        }
                    } else {
                        Log.d("PrivateChatScreen", "⚠️ [初始化] 没有该用户的分享记录 (cacheKey=$cacheKey)")
                    }
                } catch (e: Exception) {
                    Log.e("PrivateChatScreen", "❌ [初始化] 从缓存恢复失败", e)
                }
            } else {
                Log.d("PrivateChatScreen", "✅ [初始化] 检测到分享地点：${currentSharedLocation.favoriteName}，卡片将自动显示")
            }
        } else {
            Log.e("PrivateChatScreen", "❌ [初始化] chatViewModel 为 null，无法恢复分享地点")
        }
    }
    
    // 录音权限
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    // 初始化聊天
    LaunchedEffect(guardianId) {
        viewModel.initChat(guardianId)
    }
    
    // 滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // 显示错误提示
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()  // ⭐ 新增：输入框随键盘上移
            .navigationBarsPadding()  // ⭐ 新增：适配底部导航栏
    ) {
        // 顶部栏
        TopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = guardianName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "在线",
                        fontSize = 12.sp,
                        color = Color.Green
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        
        // 加载状态
        if (isLoading && messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // ⭐ 消息列表（输入框固定在底部）
            LazyColumn(
                modifier = Modifier
                    .weight(1f)  // ⭐ 占据剩余空间
                    .padding(horizontal = 8.dp),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
    // ⭐ 修复：亲友端和长辈端都应该显示卡片
                val currentCardLocation = cardLocation  // ⭐ 关键修复：先赋值给局部变量
                
                // ⭐ 修复35-36：精简卡片显示检查日志
                if (currentCardLocation != null && guardianId != null) {
                    val currentUserId = com.example.myapplication.MyApplication.tokenManager.getUserId()
                    val shouldShowCard = (currentCardLocation.elderId == guardianId || currentCardLocation.elderId == currentUserId) && isCardVisible
                    
                    if (shouldShowCard) {
                        item {
                            Log.d("PrivateChatScreen", "🎴 [UI] 渲染分享地点卡片：${currentCardLocation.favoriteName}")
                            
                            SharedLocationCard(
                                locationName = currentCardLocation.favoriteName,
                                elderName = guardianName,
                                orderStatus = currentCardLocation.orderStatus,
                                orderId = currentCardLocation.orderId,
                                startAddress = currentCardLocation.elderCurrentLat?.let { 
                                    "当前位置 (${String.format("%.6f", it)}, ${String.format("%.6f", currentCardLocation.elderCurrentLng ?: 0.0)})" 
                                },
                                destAddress = currentCardLocation.favoriteAddress.takeIf { !it.isNullOrBlank() } ?: currentCardLocation.favoriteName,
                                isElderMode = isElderMode,
                                onUseForTaxi = {
                                    Log.d("PrivateChatScreen", "🚕 [用户操作] 点击一键填充到打车界面")
                                    onNavigateToHomeWithDestination?.invoke(currentCardLocation.favoriteName, currentCardLocation.latitude, currentCardLocation.longitude)
                                },
                                onViewOrderDetail = { orderId ->
                                    Log.d("PrivateChatScreen", "📋 [用户操作] 查看订单详情，orderId=$orderId")
                                    onNavigateToOrderTracking?.invoke(orderId)
                                },
                                onDismiss = {
                                    Log.d("PrivateChatScreen", "❌ [用户操作] 用户关闭了分享卡片")
                                    isCardVisible = false
                                },
                                onConfirmOrder = { orderId ->
                                    Log.d("PrivateChatScreen", "✅ [用户操作] 长辈同意代叫车，orderId=$orderId")
                                    onConfirmProxyOrder?.invoke(orderId, true)
                                },
                                onRejectOrder = { orderId ->
                                    Log.d("PrivateChatScreen", "❌ [用户操作] 长辈拒绝代叫车，orderId=$orderId")
                                    onConfirmProxyOrder?.invoke(orderId, false)
                                }
                            )
                        }
                    }
                }
                
                items(messages) { message ->
                    PrivateChatBubble(
                        message = message,
                        currentUserId = currentUserId  // ⭐ 传递当前用户ID
                    )
                }
            }
        }
        
        // ⭐ 输入区域（固定在底部）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ⭐ 修复：长辈模式也支持文字输入（明确设置 enabled = true）
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息...") },
                        singleLine = true,
                        enabled = true,  // ⭐ 明确启用输入框
                        readOnly = false  // ⭐ 明确设置为可编辑
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 发送按钮
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendTextMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "发送")
                    }
                    
                    // 语音按钮
                    IconButton(
                        onClick = {
                            if (audioPermissionState.status.isGranted) {
                                Toast.makeText(context, "语音功能开发中", Toast.LENGTH_SHORT).show()
                            } else {
                                audioPermissionState.launchPermissionRequest()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "语音"
                        )
                    }
                    
                    // ⭐ 新增：多媒体按钮（图片/拍照/视频）
                    var showMediaMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showMediaMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "发送图片/视频"
                            )
                        }
                        
                        // 多媒体选择菜单
                        DropdownMenu(
                            expanded = showMediaMenu,
                            onDismissRequest = { showMediaMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📷 拍照") },
                                onClick = {
                                    showMediaMenu = false
                                    Toast.makeText(context, "拍照功能开发中", Toast.LENGTH_SHORT).show()
                                    // TODO: 实现拍照功能
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🖼️ 选择图片") },
                                onClick = {
                                    showMediaMenu = false
                                    Toast.makeText(context, "选择图片功能开发中", Toast.LENGTH_SHORT).show()
                                    // TODO: 实现选择图片功能
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🎥 录制视频") },
                                onClick = {
                                    showMediaMenu = false
                                    Toast.makeText(context, "录制视频功能开发中", Toast.LENGTH_SHORT).show()
                                    // TODO: 实现录制视频功能
                                }
                            )
                        }
                    }
                    
                    // ⭐ 新增：发送定位按钮（查看长辈位置）- 仅在有长辈时显示
                    if (guardianId != null) {
                        IconButton(
                            onClick = {
                                // ⭐ 关键修复：从 ChatViewModel 获取长辈实时位置
                                val sharedLocation = chatViewModel?.sharedLocation?.value
                                if (sharedLocation != null && sharedLocation.elderCurrentLat != null && sharedLocation.elderCurrentLng != null) {
                                    Log.d("PrivateChatScreen", "📍 [查看位置] 跳转到长辈位置地图 - lat=${sharedLocation.elderCurrentLat}, lng=${sharedLocation.elderCurrentLng}")
                                    
                                    // ⭐ 调用回调，跳转到地图界面显示长辈位置
                                    onNavigateToElderLocation?.invoke(
                                        guardianName,
                                        sharedLocation.elderCurrentLat,
                                        sharedLocation.elderCurrentLng
                                    )
                                } else {
                                    Log.w("PrivateChatScreen", "⚠️ [查看位置] 未收到长辈实时位置")
                                    Toast.makeText(context, "暂未收到长辈位置信息，请稍后再试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "查看长辈位置"
                            )
                        }
                    }
                }
                
                // ⭐ 长辈模式：显示快捷短语按钮
                if (isElderMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("我到了", "请稍等", "我走得慢", "谢谢", "好的").forEach { phrase ->
                            FilterChip(
                                selected = false,
                                onClick = { 
                                    viewModel.sendTextMessage(phrase)
                                },
                                label = { Text(phrase) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // ⭐ 修复19：删除无用的 ElderLocationDialog（从未被调用）
    // 该对话框功能已由 onNavigateToElderLocation 回调实现
    
    // ⭐ 新增：收藏分享弹窗（类似代打车全局弹窗）
    if (showShareDialog && pendingShareLocation != null) {
        AlertDialog(
            onDismissRequest = {
                // 不允许点击外部关闭，必须选择操作
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "📍 收到分享的地点",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${pendingElderName} 分享了以下地点给您：",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // ⭐ 修复7：安全访问 pendingShareLocation
                            pendingShareLocation?.let { location ->
                                Text(
                                    text = location.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "纬度: ${String.format("%.6f", location.latitude)}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "经度: ${String.format("%.6f", location.longitude)}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Divider()
                    
                    Text(
                        "是否立即为她代叫车？",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                // ⭐ 修复6-8：安全解包，避免强制解包崩溃
                pendingShareLocation?.let { location ->
                    pendingShareLocation = null
                    showShareDialog = false
                    isShareProcessed = true
                    
                    Log.d("PrivateChatScreen", "🚕 [弹窗] 用户点击立即叫车：${location.name}")
                    
                    // 跳转到首页叫车
                    onNavigateToHomeWithDestination?.invoke(location.name, location.latitude, location.longitude)
                    
                    // 持久化保存 share_processed 标记
                    try {
                        val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("share_processed_$guardianId", true)
                            .apply()
                        Log.d("PrivateChatScreen", "✅ [弹窗] 已持久化 share_processed 标记")
                    } catch (e: Exception) {
                        Log.e("PrivateChatScreen", "❌ [弹窗] 保存 share_processed 失败", e)
                    }
                }
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("立即叫车", fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        Log.d("PrivateChatScreen", "ℹ️ [弹窗] 用户选择稍后查看")
                        
                        // ⭐ 修复：不再设置 sharedLocation，直接使用 chatViewModel.sharedLocation
                        // sharedLocation = pendingShareLocation  // ❌ 已删除
                        
                        // ⭐ 关键修复：关闭弹窗，但不清除 hasShownDialogForCurrentShare，避免重复弹窗
                        showShareDialog = false
                        // ⭐ 注意：hasShownDialogForCurrentShare 保持为 true，lastPopupElderId 保持不变
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("稍后查看", fontSize = 16.sp)
                }
            }
        )
    }
}

/**
 * ⭐ 新增：分享的地点卡片（带代叫车提示）
 */
@Composable
fun SharedLocationCard(
    locationName: String,
    elderName: String,
    orderStatus: Int? = null,
    orderId: Long? = null,
    startAddress: String? = null,
    destAddress: String? = null,
    isElderMode: Boolean = false,
    onUseForTaxi: () -> Unit,
    onViewOrderDetail: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit = {},
    onConfirmOrder: ((Long) -> Unit)? = null,
    onRejectOrder: ((Long) -> Unit)? = null
) {
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📍 分享的地点",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = locationName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // ⭐ 新增：显示起点和终点信息
            if (!startAddress.isNullOrBlank() || !destAddress.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 起点
                    if (!startAddress.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ⭐ 修复：使用 Box + Canvas 绘制绿色圆点
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "起点：$startAddress",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 2
                            )
                        }
                    }
                    
                    // 终点
                    if (!destAddress.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFFF44336),  // 红色终点
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "终点：$destAddress",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
            
            // ⭐ 根据订单状态显示不同的提示
            Spacer(modifier = Modifier.height(12.dp))
            
            when (orderStatus) {
                0 -> {
                    // 待确认状态：显示代叫车请求
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)  // 淡橙色背景
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$elderName 请求您帮她代叫车",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
                1 -> {
                    // 已同意状态：显示已同意提示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)  // 淡绿色背景
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✅ 您已同意代叫车请求",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                2 -> {
                    // 行程中状态
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)  // 淡蓝色背景
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🚗 行程进行中",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0D47A1)
                            )
                        }
                    }
                }
                3 -> {
                    // 已结束状态
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5)  // 灰色背景
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = Color(0xFF757575),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✅ 行程已结束",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF616161)
                            )
                        }
                    }
                }
                else -> {
                    // 未知状态：不显示额外信息
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // ⭐ 根据订单状态显示不同的按钮
            if (orderStatus == 0 || orderStatus == null) {
                // 待确认或未知状态：显示操作按钮
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ⭐ 第一行：同意和拒绝按钮（长辈端确认订单）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (orderId != null && onConfirmOrder != null) {
                                    onConfirmOrder(orderId)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)  // 绿色
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("同意")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                if (orderId != null && onRejectOrder != null) {
                                    onRejectOrder(orderId)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF44336)  // 红色
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("拒绝")
                        }
                    }
                    
                    // ⭐ 第二行：一键填充到打车界面和稍后再说（仅亲友端显示）
                    if (!isElderMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onUseForTaxi,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("一键填充到打车界面")
                            }
                            
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("稍后再说")
                            }
                        }
                    }
                }
            } else {
                // 已同意/行程中/已结束：只显示一个查看详情的按钮
                Button(
                    onClick = {
                        if (orderId != null && onViewOrderDetail != null) {
                            // ⭐ 关键修复：跳转到行程追踪界面，而不是首页
                            onViewOrderDetail(orderId)
                        } else {
                            // 降级方案：如果没有 orderId，仍然调用 onUseForTaxi
                            onUseForTaxi()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = orderStatus != 3  // 已结束则禁用
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        when (orderStatus) {
                            1 -> "查看订单详情"
                            2 -> "查看行程进度"
                            3 -> "查看历史订单"
                            else -> "查看详情"
                        }
                    )
                }
            }
        }
    }
}

/**
 * 私聊消息气泡（仿微信风格）
 */
@Composable
fun PrivateChatBubble(
    message: PrivateChatMessage,
    currentUserId: Long?  // ⭐ 新增：当前用户ID参数
) {
    // ⭐ 修复：正确判断是否是自己发的消息
    val isSelf = remember(message.senderId, currentUserId) { 
        currentUserId != null && message.senderId == currentUserId
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
    ) {
        // ⭐ 显示时间戳
        Text(
            text = formatTime(message.createdAt),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(
                bottom = 2.dp,
                start = if (!isSelf) 4.dp else 0.dp,
                end = if (isSelf) 4.dp else 0.dp
            )
        )
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelf) {
                    Color.White  // ⭐ 我发的消息：白色背景（与智能体聊天一致）
                } else {
                    MaterialTheme.colorScheme.primary  // ⭐ 对方发的消息：主题色背景（与智能体聊天一致）
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 根据消息类型显示不同内容
                when (message.messageType) {
                    1 -> {
                        // 文字消息
                        Text(
                            text = message.content,
                            color = if (isSelf) Color.Black else Color.White  // ⭐ 我的消息黑色字体，对方消息白色字体
                        )
                    }
                    2 -> {
                        // 语音消息
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isSelf) Color.Black else Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "语音消息",
                                color = if (isSelf) Color.Black else Color.White
                            )
                        }
                    }
                    3 -> {
                        // 图片消息
                        Text(
                            text = "📷 图片",
                            color = if (isSelf) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: String): String {
    return try {
        val instant = java.time.Instant.parse(timestamp)
        val date = Date.from(instant)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(date)
    } catch (e: Exception) {
        "--:--"
    }
}
