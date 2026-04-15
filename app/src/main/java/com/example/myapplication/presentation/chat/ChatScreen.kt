package com.example.myapplication.presentation.chat

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize  // ⭐ 新增：内容变化动画
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState  // ⭐ 新增
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration  // ⭐ 新增：用于机型自适应
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight  // ⭐ 新增：用于字体粗细
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage  // ⭐ 新增：Coil 图片加载
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.PoiData  // ⭐ 新增：导入 PoiData
import com.google.accompanist.permissions.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch  // ⭐ 新增：用于 viewModelScope.launch
import java.util.UUID

// ⭐ 聊天模式枚举
enum class ChatMode {
    AGENT,      // 智能体聊天
    FRIEND,     // 好友聊天
    DRIVER      // 司机联系
}

// ⭐ 简单的 PopupMenuButton 实现
@Composable
fun PopupMenuButton(
    items: List<String>,
    onItemSelected: (Int) -> Unit,
    icon: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(onClick = { expanded = true }) {
            icon()
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalLayoutApi::class)  // ⭐ 新增：ExperimentalLayoutApi for FlowRow
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,  // ⭐ 关键修复：移除默认参数，强制外部传入
    onNavigateToOrder: (Long) -> Unit,
    chatMode: ChatMode = ChatMode.AGENT,  // ⭐ 新增：聊天模式参数
    isElderMode: Boolean = false,  // ⭐ 新增：长辈模式标识
    onSwitchChatMode: ((ChatMode) -> Unit)? = null,  // ⭐ 新增：切换聊天模式的回调
    onBackClick: (() -> Unit)? = null  // ⭐ 新增：返回按钮回调
) {
    val context = LocalContext.current

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val poiList by viewModel.poiList.collectAsStateWithLifecycle()  // ⭐ 新增
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()  // ⭐ 新增：监听候选列表
    val showCandidatesDialog by viewModel.showCandidatesDialog.collectAsStateWithLifecycle()  // ⭐ 新增
    val orderState by viewModel.orderState.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()  // ⭐ 新增：监听语音输入状态
    val voiceInputText by viewModel.voiceInputText.collectAsStateWithLifecycle()  // ⭐ 新增：监听实时语音文本
    var inputText by remember { mutableStateOf("") }
    
    // ⭐ 优化：待发送的图片预览列表（最多3张，支持删除）
    data class PendingImage(val uri: Uri? = null, val bitmap: Bitmap? = null)
    var pendingImages by remember { mutableStateOf<List<PendingImage>>(emptyList()) }
    val MAX_IMAGES = 3
    
    // ⭐ 优化：图片预览状态（用于动画控制）
    var isImagePreviewVisible by remember { mutableStateOf(false) }
    
    // ⭐ 优化：显示 Toast 提示
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(toastMessage) {
        toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }
    
    // ⭐ 优化：记录是否已经显示过教程（每个用户只发一次）
    var hasShownTutorial by remember { mutableStateOf(false) }
    
    // ⭐ 新增：LazyListState for auto-scroll
    val lazyListState = rememberLazyListState()
    
    // ⭐ 新增：图片选择器对话框状态
    var showImagePickerDialog by remember { mutableStateOf(false) }
    
    // ⭐ 新增：查看大图对话框状态
    var showImageDialog by remember { mutableStateOf(false) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val mediaPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    // ⭐ 新增：订单详情对话框状态（必须在 LaunchedEffect 之前声明）
    var showOrderDetailDialog by remember { mutableStateOf(false) }
    
    // ⭐ 根据模式显示不同的标题
    val title = when (chatMode) {
        ChatMode.AGENT -> "智能体助手"
        ChatMode.FRIEND -> "好友聊天"
        ChatMode.DRIVER -> "司机联系"
    }
    
    // ⭐ 辅助函数：从 PendingImage 列表加载 Bitmap
    suspend fun loadBitmapsFromPendingImages(context: android.content.Context, pendingImages: List<PendingImage>): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        pendingImages.forEach { pendingImage ->
            if (pendingImage.bitmap != null) {
                bitmaps.add(pendingImage.bitmap)
            } else if (pendingImage.uri != null) {
                var inputStream: java.io.InputStream? = null
                try {
                    inputStream = context.contentResolver.openInputStream(pendingImage.uri)
                    android.graphics.BitmapFactory.decodeStream(inputStream)?.let { bitmaps.add(it) }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "❌ 读取图片失败: ${e.message}")
                } finally {
                    inputStream?.close()
                }
            }
        }
        return bitmaps
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            if (pendingImages.size >= MAX_IMAGES) {
                toastMessage = "最多只能上传${MAX_IMAGES}张图片"
                return@let
            }
            pendingImages = pendingImages + PendingImage(bitmap = it)
            Log.d("ChatScreen", "📷 已选择拍照图片，当前: ${pendingImages.size}张")
        }
    }

    // ⭐ 修复：使用 PickMultipleVisualMedia 支持多选图片
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remainingSlots = MAX_IMAGES - pendingImages.size
            val toAdd = uris.take(remainingSlots)
            
            toAdd.forEach { uri ->
                pendingImages = pendingImages + PendingImage(uri = uri)
            }
            
            Log.d("ChatScreen", "🖼️ 已选择相册图片，当前: ${pendingImages.size}张")
            
            if (uris.size > remainingSlots) {
                toastMessage = "最多只能上传${MAX_IMAGES}张图片，已自动截取前${remainingSlots}张"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) {
            audioPermissionState.launchPermissionRequest()
        }
    }
    
    // ⭐ 新增：调试日志，检查长辈模式状态
    LaunchedEffect(isElderMode) {
        Log.d("ChatScreen", "👴 长辈模式状态: isElderMode=$isElderMode, chatMode=$chatMode")
    }
    
    // ⭐ 修改：移除自动重连逻辑，避免重复连接
    // ChatViewModel 已经在 init 中处理了 WebSocket 连接和重连
    // LaunchedEffect(Unit) {
    //     viewModel.reconnectWebSocket()
    // }

    LaunchedEffect(orderState) {
        if (orderState is ChatViewModel.OrderState.Success) {
            val order = (orderState as ChatViewModel.OrderState.Success).order
            // ⭐ 弹出订单详情对话框而不是直接跳转
            showOrderDetailDialog = true
            // ⭐ 不在这里重置状态，等待用户操作后再重置
        }
    }
    
    // ⭐ 新增：当消息列表变化时，自动滚动到最新消息（底部）
    LaunchedEffect(messages.size) {
        Log.d("ChatScreen", "📊 消息列表大小变化: ${messages.size}")
        Log.d("ChatScreen", "📊 消息列表引用: ${System.identityHashCode(messages)}")
        if (messages.isNotEmpty()) {
            Log.d("ChatScreen", "📊 最后一条消息: ${messages.last().content.take(50)}...")
            // ⭐ 优化：延迟一小段时间，确保布局完成后再滚动
            delay(100)
            lazyListState.animateScrollToItem(messages.size - 1)
            Log.d("ChatScreen", "✅ 已滚动到最后一条消息")
        }
    }
    
    // ⭐ 新增：首次打开智能体时发送使用教程（只发一次）
    LaunchedEffect(Unit) {
        // ⭐ 关键修复：只有当消息列表真正为空时才发送教程
        // 避免在用户已经发送消息后才添加教程，导致时序问题
        if (!hasShownTutorial && chatMode == ChatMode.AGENT) {
            // ⭐ 延迟检查，给用户操作留出时间
            delay(1500)
            
            // ⭐ 再次检查消息列表是否为空（用户可能在这1.5秒内发送了消息）
            if (messages.isEmpty()) {
                hasShownTutorial = true
                Log.d("ChatScreen", "📚 发送教程消息")
                val tutorialContent = if (isElderMode) {
                    // 长辈端教程
                    """👋 欢迎使用智能体助手！我是您的智能出行助手。

【📷 拍照功能】
📍 拍街道、商店 → 告诉我怎么去
🍜 拍菜单、餐厅 → 推荐好吃的
📄 拍路牌、说明书 → 翻译文字
🛍️ 拍商品 → 查询价格
🚌 拍车站 → 查看交通信息
🏞️ 拍风景 → 了解景点历史

【🎤 语音功能】
点击麦克风按钮，直接说话即可

【💬 聊天功能】
有任何问题都可以问我，比如：
• "我想去火车站"
• "附近有什么好吃的"
• "帮我叫车"

💡 温馨提示：长辈模式下无法直接叫车，但可以告诉我您的需求，我会通知您的亲友为您代叫车辆！""".trimIndent()
                } else {
                    // 普通用户教程
                    """👋 欢迎使用智能体助手！我是您的智能出行助手。

【🚗 叫车功能】
• 直接说：“我要去火车站”
• 或输入目的地后点击“为自己叫车”
• 也可以“帮长辈叫车”

【📷 拍照功能】
📍 拍街道、商店 → 导航去那
🍜 拍菜单 → 查看评价
📄 拍路牌 → 翻译理解
🛍️ 拍商品 → 比价购物
🚌 拍车站 → 查询班次
🏞️ 拍风景 → 景点介绍

【🎤 语音功能】
点击麦克风，直接说出目的地

【💬 智能对话】
有任何问题都可以问我，比如：
• “附近的餐厅推荐”
• “怎么去机场最快”
• “帮我查一下公交车”""".trimIndent()
                }
                
                // 添加教程消息
                val tutorialMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = tutorialContent,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                viewModel.addMessage(tutorialMessage)
            }
        }
    }
    
    // ⭐ 新增：实时同步语音识别结果到输入框
    LaunchedEffect(voiceInputText) {
        if (voiceInputText.isNotBlank()) {
            inputText = voiceInputText  // ⭐ 像微信一样实时更新输入框
        }
    }

    if (showImagePickerDialog) {
        if (pendingImages.size >= MAX_IMAGES) {
            // ⭐ 优化：超过额度时显示提醒
            AlertDialog(
                onDismissRequest = { showImagePickerDialog = false },
                title = { Text("⚠️ 提示") },
                text = { Text("最多只能上传${MAX_IMAGES}张图片，请先删除部分图片后再添加") },
                confirmButton = {
                    TextButton(onClick = { showImagePickerDialog = false }) {
                        Text("知道了")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showImagePickerDialog = false },
                title = { Text("选择图片来源") },
                text = { 
                    Text(
                        if (isElderMode) {
                            "📷 长按图片可多选，最多${MAX_IMAGES}张\n（还可选择 ${MAX_IMAGES - pendingImages.size} 张）"
                        } else {
                            "请选择拍照或从相册选取（还可选择 ${MAX_IMAGES - pendingImages.size} 张）"
                        }
                    ) 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showImagePickerDialog = false
                            if (cameraPermissionState.status.isGranted) {
                                takePictureLauncher.launch(null)
                            } else {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        }
                    ) { Text("拍照") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showImagePickerDialog = false
                            if (mediaPermissionState.status.isGranted) {
                                // ⭐ 修复：使用 PickVisualMediaRequest 替代字符串
                                pickImageLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            } else {
                                mediaPermissionState.launchPermissionRequest()
                            }
                        }
                    ) { Text("相册") }
                }
            )
        }
    }
    
    // ⭐ 新增：候选地点选择对话框
    if (showCandidatesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCandidatesDialog() },
            title = { Text("选择地点") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates) { candidate ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.selectCandidate(candidate)
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = candidate.address ?: "未知地址",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (candidate.distance != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "距离：${String.format("%.1f", candidate.distance / 1000)}公里",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCandidatesDialog() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // ⭐ 新增：订单详情对话框
    if (showOrderDetailDialog) {
        val order = (orderState as? ChatViewModel.OrderState.Success)?.order
        if (order != null) {
            AlertDialog(
                onDismissRequest = { 
                    showOrderDetailDialog = false
                    viewModel.resetOrderState()  // ⭐ 用户关闭对话框时重置状态
                },
                title = { Text("✅ 订单创建成功") },
                text = {
                    Column {
                        Text(
                            text = "订单号：${order.orderNo}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "目的地：${order.destAddress ?: order.poiName ?: "未知"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "预估价格：¥${order.estimatePrice ?: 0.0}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "状态：${getStatusText(order.status)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!order.remark.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "备注：${order.remark}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showOrderDetailDialog = false
                            viewModel.resetOrderState()  // ⭐ 跳转前重置状态
                            onNavigateToOrder(order.id)
                        }
                    ) {
                        Text("查看详情")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showOrderDetailDialog = false
                            viewModel.resetOrderState()  // ⭐ 关闭时重置状态
                        }
                    ) {
                        Text("关闭")
                    }
                }
            )
        } else {
            // ⭐ order 为 null 时重置状态并关闭对话框
            showOrderDetailDialog = false
            viewModel.resetOrderState()
        }
    }
    
    // ⭐ 新增：大图查看对话框
    if (showImageDialog) {
        ImageDialog(
            imageBase64 = selectedImageBase64,
            onDismiss = {
                showImageDialog = false
                selectedImageBase64 = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()  // ⭐ 关键：自动适配输入法高度
            .navigationBarsPadding()  // ⭐ 新增：适配底部导航栏
    ) {
        // ⭐ 顶部栏 - 使用更紧凑的布局
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),  // 适配状态栏
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),  // 更紧凑的内边距
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮
                IconButton(
                    onClick = { 
                        if (onBackClick != null) {
                            onBackClick()  // ⭐ 使用自定义返回逻辑
                        } else {
                            onNavigateToOrder(-1L)  // ⭐ 兼容旧逻辑
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                
                // 标题区域
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title, 
                        fontSize = if (LocalConfiguration.current.screenWidthDp < 360) 16.sp else 18.sp,  // 机型自适应
                        fontWeight = FontWeight.Bold
                    )
                    if (chatMode == ChatMode.AGENT) {
                        Text(
                            text = "在线", 
                            fontSize = if (LocalConfiguration.current.screenWidthDp < 360) 10.sp else 12.sp,  // 机型自适应
                            color = Color.Green
                        )
                    }
                }
                
                // 更多按钮
                PopupMenuButton(
                    items = listOf("智能体助手", "好友聊天", "司机联系"),
                    onItemSelected = { index ->
                        when (index) {
                            0 -> {
                                // 切换到智能体
                                if (chatMode != ChatMode.AGENT) {
                                    onSwitchChatMode?.invoke(ChatMode.AGENT)
                                }
                            }
                            1 -> {
                                // 切换到好友（跳转到聊天列表选择亲友）
                                onSwitchChatMode?.invoke(ChatMode.FRIEND)
                            }
                            2 -> {
                                // 切换到司机（暂未实现）
                                Toast.makeText(context, "司机联系功能开发中", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // ⭐ 修改：正常顺序显示（最新消息在底部，类似微信）
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            state = lazyListState,  // ⭐ 使用自定义状态
            reverseLayout = false,  // ⭐ 正常布局，第一条消息在顶部
            contentPadding = PaddingValues(vertical = 8.dp)  // ⭐ 添加内边距，确保内容不被遮挡
        ) {
            // ⭐ 正常顺序显示消息
            items(messages) { msg ->
                ChatBubble(
                    message = msg,
                    poiList = poiList,  // ⭐ 新增
                    onCreateOrder = { destAddress, lat, lng -> viewModel.createOrderViaWebSocket(destAddress, lat, lng) },  // ⭐ 修改
                    onImageClick = { imageBase64 ->  // ⭐ 新增：图片点击回调
                        selectedImageBase64 = imageBase64
                        showImageDialog = true
                    },
                    isElderMode = isElderMode,  // ⭐ 传递长辈模式标识
                    onSpeakText = { text -> viewModel.speakText(text) }  // ⭐ 传递语音朗读回调
                )
            }
        }

        // ⭐ 优化：输入区域,添加语音输入动画和图片预览
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // ⭐ 优化：多图片预览区域（横向滚动的小方块）
            androidx.compose.animation.AnimatedVisibility(
                visible = pendingImages.isNotEmpty(),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingImages) { pendingImage ->
                            // ⭐ 每个图片显示为小方块
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(80.dp)
                            ) {
                                // 显示图片
                                if (pendingImage.bitmap != null) {
                                    Image(
                                        bitmap = pendingImage.bitmap.asImageBitmap(),
                                        contentDescription = "待发送的图片",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (pendingImage.uri != null) {
                                    coil.compose.AsyncImage(
                                        model = pendingImage.uri,
                                        contentDescription = "待发送的图片",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                                
                                // 删除按钮（右上角 X）
                                IconButton(
                                    onClick = {
                                        pendingImages = pendingImages.filter { it != pendingImage }
                                        Log.d("ChatScreen", "❌ 已删除一张图片，剩余: ${pendingImages.size}张")
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除图片",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // ⭐ 新增：语音输入状态提示和实时文本显示
            androidx.compose.animation.AnimatedVisibility(
                visible = isListening,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ⭐ 录音动画指示器
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.size(24.dp)
                            ) {
                                val radius = size.minDimension / 2
                                drawCircle(
                                    color = Color.Red.copy(alpha = 0.6f),
                                    radius = radius
                                )
                            }
                            Text(
                                text = "正在识别...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // ⭐ 新增：实时显示识别结果（逐字填入）
                        if (voiceInputText.isNotBlank()) {
                            Text(
                                text = voiceInputText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "请说话...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // ⭐ 优化：输入框行 - 添加垂直间距和对齐
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),  // ⭐ 新增：内容变化时平滑动画
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)  // ⭐ 按钮之间的间距
            ) {
                // ⭐ 修复：长辈模式也支持打字输入（明确设置 enabled = true）
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    // ⭐ 优化：单行显示，自动滚动
                    singleLine = true,
                    maxLines = 1,
                    enabled = true,  // ⭐ 明确启用输入框
                    readOnly = false,  // ⭐ 明确设置为可编辑
                    shape = RoundedCornerShape(20.dp),  // ⭐ 更圆润的输入框
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                IconButton(
                    onClick = {
                        // ⭐ 优化：支持多图片发送（一次批量发送）
                        when {
                            inputText.isNotBlank() && pendingImages.isNotEmpty() -> {
                                // 情况1：有文字 + 有图片 → 发送图文混合消息（多张合并）
                                Log.d("ChatScreen", "🚀 发送图文消息，图片数: ${pendingImages.size}")
                                // 一次性发送所有图片和文字
                                val imagesToSend = pendingImages
                                val textToSend = inputText
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    try {
                                        Log.d("ChatScreen", "⏳ 开始加载Bitmap...")
                                        val bitmaps = loadBitmapsFromPendingImages(context, imagesToSend)
                                        Log.d("ChatScreen", "✅ 加载完成，Bitmap数量: ${bitmaps.size}")
                                        if (bitmaps.isNotEmpty()) {
                                            Log.d("ChatScreen", "📤 调用 sendMultipleImagesWithText...")
                                            viewModel.sendMultipleImagesWithText(bitmaps, textToSend)
                                            // ⭐ 成功发送后才清空
                                            inputText = ""
                                            pendingImages = emptyList()
                                        } else {
                                            Log.e("ChatScreen", "❌ Bitmap加载失败")
                                            android.widget.Toast.makeText(context, "图片加载失败", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ChatScreen", "❌ 发送图片异常: ${e.message}", e)
                                        android.widget.Toast.makeText(context, "发送失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            inputText.isNotBlank() -> {
                                // 情况2：只有文字 → 只发送文字
                                Log.d("ChatScreen", "🚀 发送文字消息")
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                            pendingImages.isNotEmpty() -> {
                                // 情况3：只有图片 → 批量发送所有图片
                                Log.d("ChatScreen", "🚀 批量发送图片消息，图片数: ${pendingImages.size}")
                                val imagesToSend = pendingImages
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    try {
                                        Log.d("ChatScreen", "⏳ 开始加载Bitmap...")
                                        val bitmaps = loadBitmapsFromPendingImages(context, imagesToSend)
                                        Log.d("ChatScreen", "✅ 加载完成，Bitmap数量: ${bitmaps.size}")
                                        if (bitmaps.isNotEmpty()) {
                                            Log.d("ChatScreen", "📤 调用 sendMultipleImages...")
                                            viewModel.sendMultipleImages(bitmaps)
                                            // ⭐ 成功发送后才清空
                                            pendingImages = emptyList()
                                        } else {
                                            Log.e("ChatScreen", "❌ Bitmap加载失败")
                                            android.widget.Toast.makeText(context, "图片加载失败", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ChatScreen", "❌ 发送图片异常: ${e.message}", e)
                                        android.widget.Toast.makeText(context, "发送失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }

                        }
                    },
                    enabled = inputText.isNotBlank() || pendingImages.isNotEmpty(),
                    modifier = Modifier.size(48.dp)  // ⭐ 固定按钮大小
                ) {
                    Icon(
                        Icons.Default.Send, 
                        contentDescription = "发送",
                        tint = if (inputText.isNotBlank() || pendingImages.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }

                IconButton(
                    onClick = {
                        Log.d("ChatScreen", "🎤 麦克风按钮被点击！")
                        Log.d("ChatScreen", "当前权限状态: ${audioPermissionState.status}")
                        if (audioPermissionState.status.isGranted) {
                            Log.d("ChatScreen", "✅ 权限已授予，开始语音识别")
                            viewModel.startVoiceInput(context)
                        } else {
                            Log.w("ChatScreen", "❌ 权限未授予，请求权限")
                            Toast.makeText(context, "需要录音权限", Toast.LENGTH_SHORT).show()
                            audioPermissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.size(48.dp)  // ⭐ 固定按钮大小
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "语音",
                        tint = if (isListening) Color.Red else MaterialTheme.colorScheme.onSurface  // ⭐ 录音中显示红色
                    )
                }

                // ⭐ 修改：长辈模式也支持图片发送（只禁用下单功能）
                IconButton(
                    onClick = { showImagePickerDialog = true },
                    modifier = Modifier.size(48.dp)  // ⭐ 固定按钮大小
                ) {
                    Icon(Icons.Default.Image, contentDescription = "图片")
                }
            }
            
            // ⭐ 已移除：图片识别快捷提示卡片（改为首次打开时发送教程消息）
            
            // ⭐ 移除：智能体聊天不需要快捷短语（快捷短语是给真人亲友的）
            // 如果需要，可以在这里添加适合 AI 交互的快捷操作
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    poiList: List<PoiData>,  // ⭐ 新增：传入 POI 列表
    onCreateOrder: (String, Double, Double) -> Unit,  // ⭐ 修改：接受经纬度和地址
    onImageClick: (String) -> Unit,  // ⭐ 新增：图片点击回调
    isElderMode: Boolean = false,  // ⭐ 新增：长辈模式标识
    onSpeakText: ((String) -> Unit)? = null  // ⭐ 新增：语音朗读回调
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        // ⭐ 显示时间戳（可选）
        if (!message.isUser) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            )
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    Color.White  // ⭐ 用户消息：白色背景
                } else {
                    MaterialTheme.colorScheme.primary  // ⭐ AI消息：深色背景（主题色）
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .clickable(enabled = isElderMode && !message.isUser && onSpeakText != null) {
                    // ⭐ 长辈模式下，点击司机/AI消息自动朗读
                    onSpeakText?.invoke(message.content)
                }
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // ⭐ 显示消息内容
                Text(
                    text = message.content,
                    color = if (message.isUser) Color.Black else Color.White,  // ⭐ 用户消息黑色字体，AI消息白色字体
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // ⭐ 如果有图片，显示图片（支持多张）
                if (!message.imageBase64.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ⭐ 显示第一张图片
                        DisplayImageWithClick(
                            imageBase64 = message.imageBase64,
                            onImageClick = onImageClick
                        )
                        
                        // ⭐ 显示额外的图片
                        message.additionalImages?.forEach { additionalImage ->
                            DisplayImageWithClick(
                                imageBase64 = additionalImage,
                                onImageClick = onImageClick
                            )
                        }
                    }
                }
            }
        }
        
        // ⭐ 用户消息也显示时间戳
        if (message.isUser) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        }

        // ⭐ 显示智能体回复的建议按钮
        if (!message.isUser && !message.suggestions.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = if (isElderMode) 75.dp else 0.dp)  // ⭐ 长辈模式整体下移2cm（75dp），普通模式不变
                    .padding(start = 4.dp, top = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                message.suggestions.forEach { suggestion ->
                    // ⭐ 查找对应的 POI 坐标
                    val poi = poiList.find { it.name == suggestion }
                    
                    // ⭐ 长辈模式：禁用下单按钮，仅显示提示
                    if (isElderMode) {
                        OutlinedButton(
                            onClick = { 
                                // ⭐ 长辈模式下点击建议按钮，明确提示无法下单的原因
                                onSpeakText?.invoke("长辈模式下已禁用下单功能。您的亲友可以为您代叫车辆，如需叫车请切换到普通模式")
                            },
                            modifier = Modifier.padding(end = 4.dp, top = 4.dp),
                            enabled = false,  // ⭐ 禁用按钮
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = suggestion,
                                maxLines = 1
                            )
                        }
                    } else {
                        // ⭐ 普通模式：正常显示下单按钮
                        OutlinedButton(
                            onClick = { 
                                if (poi != null) {
                                    onCreateOrder(poi.name, poi.lat, poi.lng)
                                } else {
                                    // 如果找不到 POI，使用默认坐标（北京）
                                    onCreateOrder(suggestion, 39.9042, 116.4074)
                                }
                            },
                            modifier = Modifier.padding(end = 4.dp, top = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = suggestion,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ⭐ 新增：格式化时间戳
private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// ⭐ 新增：显示单张图片并支持点击
@Composable
private fun DisplayImageWithClick(
    imageBase64: String,
    onImageClick: (String) -> Unit
) {
    // ⭐ 解码并显示 Base64 图片
    val bitmap = remember(imageBase64) {
        try {
            // 移除 data:image/jpeg;base64, 前缀（如果存在）
            val base64Data = if (imageBase64.contains(",")) {
                imageBase64.substringAfter(",")
            } else {
                imageBase64
            }
            
            val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("ChatScreen", "❌ 图片解码失败: ${e.message}")
            null
        }
    }
    
    if (bitmap != null) {
        // ⭐ 添加点击事件，支持查看大图
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "发送的图片（点击查看大图）",
            modifier = Modifier
                .width(180.dp)
                .heightIn(max = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onImageClick(imageBase64)
                },
            contentScale = ContentScale.Crop
        )
    } else {
        Text(
            text = "⚠️ 图片加载失败",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red
        )
    }
}

// ⭐ 新增：状态码转文字函数
fun getStatusText(status: Int): String {
    return when (status) {
        0 -> "待接单"
        1 -> "已接单"
        2 -> "进行中"
        3 -> "已完成"
        4 -> "已取消"
        else -> "未知状态 ($status)"
    }
}

// ⭐ 新增：大图查看对话框
@Composable
fun ImageDialog(
    imageBase64: String?,
    onDismiss: () -> Unit
) {
    if (imageBase64 == null) return
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查看图片") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 解码并显示图片
                val bitmap = remember(imageBase64) {
                    try {
                        val base64Data = if (imageBase64.contains(",")) {
                            imageBase64.substringAfter(",")
                        } else {
                            imageBase64
                        }
                        
                        val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    } catch (e: Exception) {
                        Log.e("ImageDialog", "❌ 图片解码失败: ${e.message}")
                        null
                    }
                }
                
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "大图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)  // ⭐ 更大的最大高度
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit  // ⭐ 使用 Fit 保持完整显示
                    )
                } else {
                    Text(
                        text = "⚠️ 图片加载失败",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Red
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

// ⭐ 新增：图片场景提示卡片
@Composable
fun ImageScenarioChip(
    icon: String,
    label: String,
    hint: String
) {
    Card(
        modifier = Modifier.widthIn(max = 120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}