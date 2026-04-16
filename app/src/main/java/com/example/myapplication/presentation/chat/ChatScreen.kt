package com.example.myapplication.presentation.chat

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState  // ⭐ 新增
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable  // ⭐ 新增
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
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.PoiData  // ⭐ 新增：导入 PoiData
import com.google.accompanist.permissions.*
import kotlinx.coroutines.delay

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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToOrder: (Long) -> Unit,
    chatMode: ChatMode = ChatMode.AGENT,  // ⭐ 新增：聊天模式参数
    isElderMode: Boolean = false,  // ⭐ 新增：长辈模式标识
    showBackButton: Boolean = true,  // ⭐ 新增：是否显示返回按钮
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
    val pendingImages = viewModel.pendingImages  // ⭐ 新增：待发送图片列表（直接访问，不使用 by）
    val showImageLimitDialog by remember { derivedStateOf { viewModel.showImageLimitDialog } }  // ⭐ 新增：图片数量限制对话框
    val isElderModeState by viewModel.isElderMode.collectAsStateWithLifecycle()  // ⭐ 新增：收集长辈模式状态
    
    // ⭐ 使用传入的参数或 ViewModel 中的状态（优先使用传入的参数）
    val effectiveElderMode = if (chatMode == ChatMode.AGENT) isElderMode else false
    var inputText by remember { mutableStateOf("") }
    
    // ⭐ 新增：用于自动滚动到最新消息
    val lazyListState = rememberLazyListState()
    
    // ⭐ 新增：是否已发送欢迎教程
    var hasSentWelcomeTutorial by rememberSaveable { mutableStateOf(false) }

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

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            // ⭐ 修改：直接传入 Bitmap，在 ViewModel 中异步压缩
            viewModel.addPendingImageFromBitmap(it)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.sendImageFromUri(context, it)
        }
    }

    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) {
            audioPermissionState.launchPermissionRequest()
        }
        
        // ⭐ 新增：进入时发送使用教程（仅首次）
        if (!hasSentWelcomeTutorial && chatMode == ChatMode.AGENT) {
            hasSentWelcomeTutorial = true
            val tutorialMessage = if (effectiveElderMode) {
                "👴 长辈端使用指南：\n" +
                "\n" +
                "✅ 您可以：\n" +
                "• 语音输入：点击🎤按钮说话\n" +
                "• 图片识别：点击📷上传照片\n" +
                "• 文字聊天：直接输入问题\n" +
                "\n" +
                "❌ 温馨提示：\n" +
                "• 长辈端暂不支持下单叫车功能\n" +
                "• 如需叫车，请联系您的亲友代劳\n" +
                "\n" +
                "💡 试试说：'附近的医院在哪里'"
            } else {
                "🤖 智能体助手使用指南：\n" +
                "\n" +
                "✅ 功能说明：\n" +
                "• 🎤 语音输入 - 按住说话，自动识别\n" +
                "• 📷 图片识别 - 上传照片，AI 帮您分析\n" +
                "• 💬 智能对话 - 问路、查路线、找地点\n" +
                "• 🚗 一键叫车 - 点击建议按钮快速下单\n" +
                "\n" +
                "💡 试试说：\n" +
                "• '我要去北京站'\n" +
                "• '附近的餐厅有哪些'\n" +
                "• '怎么去机场'"
            }
            
            // 延迟发送，确保 WebSocket 连接成功
            kotlinx.coroutines.delay(1000)
            viewModel.addSystemMessage(tutorialMessage)
        }
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
        if (messages.isNotEmpty()) {
            // ⭐ 修改：滚动到最后一条消息（索引为 messages.size - 1）
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // ⭐ 新增：实时同步语音识别结果到输入框
    LaunchedEffect(voiceInputText) {
        if (voiceInputText.isNotBlank()) {
            inputText = voiceInputText  // ⭐ 像微信一样实时更新输入框
        }
    }

    if (showImagePickerDialog) {
        AlertDialog(
            onDismissRequest = { showImagePickerDialog = false },
            title = { Text("选择图片来源") },
            text = { Text("请选择拍照或从相册选取") },
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
                            pickImageLauncher.launch("image/*")
                        } else {
                            mediaPermissionState.launchPermissionRequest()
                        }
                    }
                ) { Text("相册") }
            }
        )
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
    
    // ⭐ 新增：图片数量限制对话框
    if (showImageLimitDialog) {
        ImageLimitDialog(
            maxCount = 3,
            onDismiss = { viewModel.dismissImageLimitDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()  // ⭐ 关键修复：确保键盘弹出时输入框可见
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
                if (showBackButton) {
                    IconButton(
                        onClick = { 
                            onBackClick?.invoke() ?: onNavigateToOrder(-1L)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
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
                            0 -> { /* 切换到智能体 */ }
                            1 -> { /* 切换到好友 */ }
                            2 -> { /* 切换到司机 */ }
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
            reverseLayout = false  // ⭐ 正常布局，第一条消息在顶部
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
                    isElderMode = effectiveElderMode  // ⭐ 新增：传递长辈模式标识
                )
            }
        }

        // ⭐ 优化：输入区域，添加语音输入动画
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // ⭐ 新增：图片预览栏（在输入框上方）
            if (pendingImages.isNotEmpty()) {
                ImagePreviewBar(
                    pendingImages = pendingImages,
                    onRemoveImage = { index -> viewModel.removePendingImage(index) }
                )
                
                // ⭐ 发送按钮（当有待发送图片时显示）
                Button(
                    onClick = {
                        viewModel.sendAllPendingImages()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    enabled = pendingImages.isNotEmpty()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("发送 ${pendingImages.size}/3 张图片")  // ⭐ 修改：显示当前数量/上限
                }
            }
            
            // ⭐ 新增：语音输入状态提示
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
                    Row(
                        modifier = Modifier.padding(12.dp),
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
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    // ⭐ 优化：多行显示，最多3行，自动滚动
                    singleLine = false,
                    maxLines = 3,
                    minLines = 1
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            // ⭐ 修复：如果有图片，先发送图片（带文字），否则只发送文字
                            if (pendingImages.isNotEmpty()) {
                                // 图文混发：将文字作为图片的说明一起发送
                                viewModel.sendAllPendingImagesWithText(inputText)
                                inputText = ""  // 发送后清空
                            } else {
                                // 纯文字发送
                                viewModel.sendMessage(inputText)
                                inputText = ""  // 发送后清空
                            }
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
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
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "语音",
                        tint = if (isListening) Color.Red else MaterialTheme.colorScheme.onSurface  // ⭐ 录音中显示红色
                    )
                }
            }
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
    isElderMode: Boolean = false  // ⭐ 新增：长辈模式标识
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
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                
                // ⭐ 如果有图片，显示图片（支持多张，竖向排列）
                val allImages = buildList {
                    if (!message.imageBase64.isNullOrBlank()) {
                        add(message.imageBase64)
                    }
                    if (!message.additionalImages.isNullOrEmpty()) {
                        addAll(message.additionalImages)
                    }
                }
                
                if (allImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // ⭐ 竖向排列所有图片
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allImages.forEachIndexed { index, imageBase64 ->
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
                                    contentDescription = "发送的图片 ${index + 1}/${allImages.size}（点击查看大图）",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onImageClick(imageBase64)  // ⭐ 使用回调函数
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = "⚠️ 图片 ${index + 1} 加载失败",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red
                                )
                            }
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
                    .padding(top = 8.dp, start = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                message.suggestions.forEach { suggestion ->
                    // ⭐ 长辈模式：不显示下单建议按钮
                    if (!isElderMode) {
                        // ⭐ 查找对应的 POI 坐标
                        val poi = poiList.find { it.name == suggestion }
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