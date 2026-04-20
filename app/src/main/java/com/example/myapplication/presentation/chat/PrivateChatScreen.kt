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
    onNavigateToElderLocation: ((String, Double, Double) -> Unit)? = null  // ⭐ 新增：跳转到长辈位置地图
) {
    val context = LocalContext.current
    
    // ⭐ 关键日志：函数入口立即输出
    Log.d("PrivateChatScreen", "🚀 [函数入口] PrivateChatScreen 被调用")
    Log.d("PrivateChatScreen", "🚀 [函数入口] guardianId=$guardianId, guardianName=$guardianName, isElderMode=$isElderMode")
    
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()  // ⭐ 新增：获取当前用户ID
        
    // ⭐ 修复：在函数顶部收集 sharedLocation（单一数据源）
    val cardLocation by chatViewModel?.sharedLocation?.collectAsState() ?: remember { mutableStateOf(null) }
    
    // ⭐ 新增：日志 - 监控 cardLocation 变化
    LaunchedEffect(cardLocation) {
        Log.d("PrivateChatScreen", "🔍 [状态监控] === cardLocation 变化 ===")
        Log.d("PrivateChatScreen", "🔍 [状态监控] cardLocation != null: ${cardLocation != null}")
        if (cardLocation != null) {
            Log.d("PrivateChatScreen", "🔍 [状态监控] elderId=${cardLocation!!.elderId}, guardianId=$guardianId")
            Log.d("PrivateChatScreen", "🔍 [状态监控] favoriteName=${cardLocation!!.favoriteName}")
            Log.d("PrivateChatScreen", "🔍 [状态监控] lat=${cardLocation!!.latitude}, lng=${cardLocation!!.longitude}")
            Log.d("PrivateChatScreen", "🔍 [状态监控] elderCurrentLat=${cardLocation!!.elderCurrentLat}, elderCurrentLng=${cardLocation!!.elderCurrentLng}")
            Log.d("PrivateChatScreen", "🔍 [状态监控] orderId=${cardLocation!!.orderId}, orderStatus=${cardLocation!!.orderStatus}")
        } else {
            Log.w("PrivateChatScreen", "⚠️ [状态监控] cardLocation 为 null，卡片不会显示！")
            Log.w("PrivateChatScreen", "⚠️ [状态监控] 可能原因：1) WebSocket未推送 2) SharedPreferences缓存为空 3) StateFlow被清除")
        }
    }
        
    // ⭐ 新增：弹窗状态管理
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareLocation by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }
    var pendingElderName by remember { mutableStateOf("") }
        
    // ⭐ 新增：标记分享是否已被处理（用户在外层弹窗点击了“立即叫车”）
    var isShareProcessed by remember { mutableStateOf(false) }
        
    // ⭐ 新增：标记是否已经对该分享地点弹过窗（避免重复弹窗）
    var hasShownDialogForCurrentShare by remember { mutableStateOf(false) }
        
    // ⭐ 关键修复：记录上次弹窗的 elderId，只有新的分享才弹窗
    var lastPopupElderId by remember { mutableStateOf<Long?>(null) }
        
    var inputText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
        
    // ⭐ 新增：长辈位置信息（用于发送定位功能）
    var showElderLocationDialog by remember { mutableStateOf(false) }
    var elderLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }  // lat, lng
    
    // ⭐ 新增：多媒体选择对话框
    var showMediaPickerDialog by remember { mutableStateOf(false) }
        
    // ⭐ 修复：监听 cardLocation 的变化，当收到 WebSocket 消息时触发弹窗
    LaunchedEffect(cardLocation) {
        cardLocation?.let { location ->
            // ⭐ 关键修复：亲友端和长辈端都应该显示卡片
            // 亲友端：location.elderId == guardianId (分享者是当前聊天的长辈)
            // 长辈端：location.elderId == currentUserId (自己是分享者)
            val currentUserId = com.example.myapplication.MyApplication.tokenManager.getUserId()
            val shouldShowCard = location.elderId == guardianId || location.elderId == currentUserId
            
            if (shouldShowCard) {
                Log.d("PrivateChatScreen", "📍 [WebSocket] 收到分享的地点：${location.favoriteName}, elderId=${location.elderId}, guardianId=$guardianId")
                
                // ⭐ 关键修复1：如果是同一个 elderId 且已经弹过窗，不再弹窗
                if (lastPopupElderId == location.elderId && hasShownDialogForCurrentShare) {
                    Log.d("PrivateChatScreen", "⏭️ [WebSocket] 同一 elderId 已弹过窗，不重复弹窗")
                    return@let
                }
                
                // ⭐ 关键修复2：如果分享已被处理（用户已点击立即叫车），不再弹窗
                if (isShareProcessed) {
                    Log.d("PrivateChatScreen", "⏭️ [WebSocket] 分享已被处理，不弹窗")
                    return@let
                }
                
                // ⭐ 关键修复3：检查是否是刚收到的 WebSocket 消息（时间戳在 5 秒内）
                val currentTime = System.currentTimeMillis()
                val messageTimestamp = location.elderLocationTimestamp ?: 0L
                val isRecentMessage = messageTimestamp > 0 && (currentTime - messageTimestamp) < 5000
                
                if (!isRecentMessage) {
                    Log.d("PrivateChatScreen", "⏭️ [WebSocket] 消息不是最新的（来自缓存），不弹窗")
                    // ⭐ 关键修复：即使是旧消息，也标记为已弹窗，避免后续重复
                    lastPopupElderId = location.elderId
                    hasShownDialogForCurrentShare = true
                    return@let
                }
                    
                // ⭐ 新增：播放提示音和震动
                try {
                    val ringtoneManager = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION
                    )
                    val ringtone = android.media.RingtoneManager.getRingtone(context, ringtoneManager)
                    ringtone.play()
                        
                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(200)
                    }
                    Log.d("PrivateChatScreen", "🔔 [提醒] 已播放提示音和震动")
                } catch (e: Exception) {
                    Log.e("PrivateChatScreen", "❌ 播放提醒失败", e)
                }
                    
                // ⭐ 触发弹窗
                pendingShareLocation = Triple(
                    location.favoriteName,
                    location.latitude,
                    location.longitude
                )
                pendingElderName = location.elderName
                showShareDialog = true
                hasShownDialogForCurrentShare = true  // ⭐ 标记已弹窗
                lastPopupElderId = location.elderId  // ⭐ 记录弹窗的 elderId
                Log.d("PrivateChatScreen", "✅ [WebSocket] 已触发弹窗，等待用户操作")
            } else {
                Log.d("PrivateChatScreen", "⚠️ [WebSocket] 忽略不匹配的分享 - elderId=${location.elderId}, 当前guardianId=$guardianId")
            }
        }
    }
    
    LaunchedEffect(guardianId) {
        Log.d("PrivateChatScreen", "🔗 [初始化] 私聊界面初始化，guardianId=$guardianId, guardianName=$guardianName")
        Log.d("PrivateChatScreen", "🔗 [初始化] chatViewModel 是否存在: ${chatViewModel != null}")
        Log.d("PrivateChatScreen", "🔗 [初始化] isElderMode: $isElderMode")
        
        // ⭐ 关键修复：同步长辈模式状态到 ChatViewModel
        if (chatViewModel != null) {
            chatViewModel.syncElderMode(isElderMode)
        }
        
        // ⭐ 新增：获取当前用户ID
        val currentUserId = com.example.myapplication.MyApplication.tokenManager.getUserId()
        Log.d("PrivateChatScreen", "🔗 [初始化] currentUserId: $currentUserId")
        
        // ⭐ 新增：检查分享是否已被处理（用户在外层弹窗点击了“立即叫车”）
        try {
            val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
            isShareProcessed = prefs.getBoolean("share_processed_$guardianId", false)
            if (isShareProcessed) {
                Log.d("PrivateChatScreen", "✅ [初始化] 检测到分享已被处理，卡片将显示为已同意状态")
                // ⭐ 关键修复：如果分享已被处理，则标记已弹过窗，避免重复弹窗
                hasShownDialogForCurrentShare = true
            }
        } catch (e: Exception) {
            Log.e("PrivateChatScreen", "❌ [初始化] 读取 share_processed 标记失败", e)
        }
        
        if (chatViewModel != null) {
            val currentSharedLocation = chatViewModel.sharedLocation.value
            Log.d("PrivateChatScreen", "🔗 [初始化] 当前 sharedLocation: ${currentSharedLocation?.favoriteName ?: "null"}")
            Log.d("PrivateChatScreen", "🔗 [初始化] currentSharedLocation?.elderId: ${currentSharedLocation?.elderId}, guardianId: $guardianId")
            
            // ⭐ 关键修复：如果 StateFlow 为空，尝试从 SharedPreferences 恢复
            if (currentSharedLocation == null || currentSharedLocation.elderId != guardianId) {
                Log.d("PrivateChatScreen", "⚠️ [初始化] StateFlow 为空或不匹配，尝试从本地缓存恢复...")
                try {
                    val prefs = context.getSharedPreferences("shared_location_cache", android.content.Context.MODE_PRIVATE)
                    
                    // ⭐ 关键修复：长辈端应该使用 currentUserId 作为缓存 key
                    val cacheKey = if (isElderMode) currentUserId else guardianId
                    val cachedElderId = prefs.getLong("elderId_${cacheKey}", -1L)
                    
                    Log.d("PrivateChatScreen", "🔍 [缓存恢复] cacheKey=$cacheKey, cachedElderId=$cachedElderId, isElderMode=$isElderMode, currentUserId=$currentUserId")
                    
                    // ⭐ 新增：打印所有缓存的 key，用于调试
                    val allEntries = prefs.all
                    Log.d("PrivateChatScreen", "🔍 [缓存恢复] SharedPreferences 中所有的 keys: ${allEntries.keys}")
                    Log.d("PrivateChatScreen", "🔍 [缓存恢复] 所有 entries: $allEntries")
                    
                    if (cachedElderId == cacheKey) {
                        val elderName = prefs.getString("elderName_${cacheKey}", "") ?: ""
                        val favoriteName = prefs.getString("favoriteName_${cacheKey}", "") ?: ""
                        val favoriteAddress = prefs.getString("favoriteAddress_${cacheKey}", "") ?: ""
                        val latitude = prefs.getFloat("latitude_${cacheKey}", 0f).toDouble()
                        val longitude = prefs.getFloat("longitude_${cacheKey}", 0f).toDouble()
                        val elderCurrentLat = prefs.getFloat("elderCurrentLat_${cacheKey}", 0f).toDouble()
                        val elderCurrentLng = prefs.getFloat("elderCurrentLng_${cacheKey}", 0f).toDouble()
                        val elderLocationTimestamp = prefs.getLong("elderLocationTimestamp_${cacheKey}", 0L)
                        // ⭐ 关键修复：从缓存恢复 orderId
                        val orderId = prefs.getLong("orderId_${cacheKey}", -1L).takeIf { it != -1L }
                        
                        if (favoriteName.isNotBlank()) {
                            Log.d("PrivateChatScreen", "✅ [初始化] 从本地缓存恢复分享地点：$favoriteName, orderId=$orderId")
                            
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
                                orderStatus = if (isShareProcessed) 1 else 0  // ⭐ 如果已处理，则设为已同意状态
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
                
                // ⭐ 关键日志：详细记录卡片显示判断逻辑
                Log.d("PrivateChatScreen", "🔍 [卡片显示检查] === 开始检查 ===")
                Log.d("PrivateChatScreen", "🔍 [卡片显示检查] currentCardLocation != null: ${currentCardLocation != null}")
                Log.d("PrivateChatScreen", "🔍 [卡片显示检查] guardianId != null: ${guardianId != null}")
                Log.d("PrivateChatScreen", "🔍 [卡片显示检查] isElderMode: $isElderMode")
                
                if (currentCardLocation == null) {
                    Log.w("PrivateChatScreen", "⚠️ [卡片显示检查] currentCardLocation 为 null，跳过卡片渲染")
                    Log.w("PrivateChatScreen", "⚠️ [卡片显示检查] 请检查：1) ChatViewModel.sharedLocation 是否有值 2) SharedPreferences 缓存是否恢复成功")
                }
                
                if (currentCardLocation != null && guardianId != null) {
                    val currentUserId = com.example.myapplication.MyApplication.tokenManager.getUserId()
                    Log.d("PrivateChatScreen", "🔍 [卡片显示检查] currentUserId: $currentUserId")
                    Log.d("PrivateChatScreen", "🔍 [卡片显示检查] cardLocation.elderId: ${currentCardLocation.elderId}")
                    Log.d("PrivateChatScreen", "🔍 [卡片显示检查] guardianId: $guardianId")
                    
                    val shouldShowCard = currentCardLocation.elderId == guardianId || currentCardLocation.elderId == currentUserId
                    Log.d("PrivateChatScreen", "🔍 [卡片显示检查] elderId == guardianId: ${currentCardLocation.elderId == guardianId}")
                    Log.d("PrivateChatScreen", "🔍 [卡片显示检查] elderId == currentUserId: ${currentCardLocation.elderId == currentUserId}")
                    Log.d("PrivateChatScreen", "🔍 [卡片显示检查] shouldShowCard: $shouldShowCard")
                    
                    if (!shouldShowCard) {
                        Log.w("PrivateChatScreen", "⚠️ [卡片显示检查] shouldShowCard 为 false，卡片不会显示")
                        Log.w("PrivateChatScreen", "⚠️ [卡片显示检查] elderId (${currentCardLocation.elderId}) 既不等于 guardianId ($guardianId) 也不等于 currentUserId ($currentUserId)")
                    }
                    
                    if (shouldShowCard) {
                        item {
                            Log.d("PrivateChatScreen", "🎴 [UI] 渲染分享地点卡片：${currentCardLocation.favoriteName}, 订单状态: ${currentCardLocation.orderStatus}")
                            
                            SharedLocationCard(
                                locationName = currentCardLocation.favoriteName,
                                elderName = guardianName,
                                orderStatus = currentCardLocation.orderStatus,  // ⭐ 新增：传递订单状态
                                orderId = currentCardLocation.orderId,  // ⭐ 新增：传递订单ID
                                startAddress = currentCardLocation.elderCurrentLat?.let { 
                                    "当前位置 (${String.format("%.6f", it)}, ${String.format("%.6f", currentCardLocation.elderCurrentLng ?: 0.0)})" 
                                },  // ⭐ 新增：起点（长辈当前位置）
                                destAddress = currentCardLocation.favoriteAddress.takeIf { !it.isNullOrBlank() } ?: currentCardLocation.favoriteName,  // ⭐ 新增：终点（收藏地点）
                                onUseForTaxi = {
                                    Log.d("PrivateChatScreen", "🚕 [用户操作] 点击一键填充到打车界面")
                                    onNavigateToHomeWithDestination?.invoke(currentCardLocation.favoriteName, currentCardLocation.latitude, currentCardLocation.longitude)
                                    Log.d("PrivateChatScreen", "✅ [用户操作] 已跳转到打车界面，卡片保持显示")
                                },
                                onViewOrderDetail = { orderId ->
                                    // ⭐ 关键修复：已确认订单，直接跳转到行程追踪界面
                                    Log.d("PrivateChatScreen", "📋 [用户操作] 查看订单详情，orderId=$orderId")
                                    onNavigateToOrderTracking?.invoke(orderId)
                                },
                                onDismiss = {
                                    Log.d("PrivateChatScreen", "❌ [用户操作] 用户关闭了分享卡片")
                                    // ⭐ 修复：清除 ChatViewModel 中的 sharedLocation
                                    chatViewModel?.clearSharedLocation()
                                    Log.d("PrivateChatScreen", "✅ [用户操作] 已清除 sharedLocation，卡片消失")
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
    
    // ⭐ 新增：长辈位置查看对话框
    if (showElderLocationDialog) {
        ElderLocationDialog(
            elderName = guardianName,
            elderLocation = elderLocation,
            onDismiss = { 
                showElderLocationDialog = false
                elderLocation = null
            }
        )
    }
    
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
                        "$pendingElderName 分享了以下地点给您：",
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
                            Text(
                                text = pendingShareLocation!!.first,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "纬度: ${String.format("%.6f", pendingShareLocation!!.second)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "经度: ${String.format("%.6f", pendingShareLocation!!.third)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        val (name, lat, lng) = pendingShareLocation!!
                        Log.d("PrivateChatScreen", "🚕 [弹窗] 用户点击立即叫车：$name")
                        
                        // ⭐ 修复：不再设置 sharedLocation，直接使用 chatViewModel.sharedLocation
                        // sharedLocation = pendingShareLocation  // ❌ 已删除
                        
                        // 跳转到首页叫车
                        onNavigateToHomeWithDestination?.invoke(name, lat, lng)
                        
                        // ⭐ 关键修复：关闭弹窗，并标记分享已被处理
                        showShareDialog = false
                        isShareProcessed = true  // ⭐ 标记分享已被处理
                        
                        // ⭐ 持久化保存 share_processed 标记
                        try {
                            val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("share_processed_$guardianId", true)
                                .apply()
                            Log.d("PrivateChatScreen", "✅ [弹窗] 已持久化 share_processed 标记")
                        } catch (e: Exception) {
                            Log.e("PrivateChatScreen", "❌ [弹窗] 保存 share_processed 失败", e)
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
    elderName: String,  // ⭐ 新增：长辈姓名
    orderStatus: Int? = null,  // ⭐ 新增：订单状态（0-待确认 1-已同意 2-行程中 3-已结束）
    orderId: Long? = null,  // ⭐ 新增：订单ID
    startAddress: String? = null,  // ⭐ 新增：起点地址
    destAddress: String? = null,  // ⭐ 新增：终点地址
    onUseForTaxi: () -> Unit,
    onViewOrderDetail: ((Long) -> Unit)? = null,  // ⭐ 新增：查看订单详情回调
    onDismiss: () -> Unit = {}  // ⭐ 新增：关闭回调
) {
    Log.d("PrivateChatScreen", "🎴 [SharedLocationCard] 渲染卡片 - 地点: $locationName, 长辈: $elderName, 订单状态: $orderStatus")
    Log.d("PrivateChatScreen", "🎴 [SharedLocationCard] 起点: $startAddress, 终点: $destAddress")
    
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

/**
 * ⭐ 新增：长辈位置查看对话框（显示长辈实时位置）
 */
@Composable
fun ElderLocationDialog(
    elderName: String,
    elderLocation: Pair<Double, Double>?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("查看 $elderName 的位置")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (elderLocation != null) {
                    val (lat, lng) = elderLocation
                    
                    // 位置信息卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)  // 淡蓝色背景
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "位置坐标",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1976D2)
                                )
                            }
                            
                            Text(
                                text = "纬度: ${String.format("%.6f", lat)}",
                                fontSize = 14.sp,
                                color = Color(0xFF424242)
                            )
                            Text(
                                text = "经度: ${String.format("%.6f", lng)}",
                                fontSize = 14.sp,
                                color = Color(0xFF424242)
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 更新时间提示
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "实时更新中",
                                    fontSize = 12.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 提示文字
                    Text(
                        text = "💡 提示：在首页地图中可以查看更详细的路线和位置信息",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    // 加载状态
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在获取位置信息...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // ⭐ TODO: 跳转到首页并聚焦长辈位置
                    onDismiss()
                }
            ) {
                Text("在地图中查看")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
