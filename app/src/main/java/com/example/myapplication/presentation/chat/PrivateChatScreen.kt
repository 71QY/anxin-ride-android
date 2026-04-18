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
    guardianId: Long,
    guardianName: String,
    onBackClick: () -> Unit,
    isElderMode: Boolean = false,  // ⭐ 新增：长辈模式标识
    onNavigateToHomeWithDestination: ((String, Double, Double) -> Unit)? = null  // ⭐ 新增：跳转到首页叫车
) {
    val context = LocalContext.current
    
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()  // ⭐ 新增：获取当前用户ID
    
    var inputText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    
    // ⭐ 新增：读取分享的地点信息
    var sharedLocation by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }  // name, lat, lng
    
    LaunchedEffect(guardianId) {
        // 读取 SharedPreferences 中的地点信息
        val prefs = context.getSharedPreferences("chat_location_$guardianId", Context.MODE_PRIVATE)
        val locationName = prefs.getString("location_name", null)
        val locationAddress = prefs.getString("location_address", null)
        val locationLat = prefs.getFloat("location_lat", 0f).toDouble()
        val locationLng = prefs.getFloat("location_lng", 0f).toDouble()
        val timestamp = prefs.getLong("timestamp", 0L)
        
        // 如果地点信息存在且在 5 分钟内，则显示
        if (locationName != null && locationAddress != null && locationLat != 0.0 && locationLng != 0.0) {
            val age = System.currentTimeMillis() - timestamp
            if (age < 5 * 60 * 1000) {  // 5 分钟内有效
                sharedLocation = Triple(locationName, locationLat, locationLng)
                Log.d("PrivateChatScreen", "📍 显示分享地点：$locationName")
            } else {
                // 过期清除
                prefs.edit().clear().apply()
            }
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
                // ⭐ 新增：如果有分享的地点，先显示地点卡片
                sharedLocation?.let { (name, lat, lng) ->
                    item {
                        SharedLocationCard(
                            locationName = name,
                            onUseForTaxi = {
                                // ⭐ 一键填充到打车界面
                                onNavigateToHomeWithDestination?.invoke(name, lat, lng)
                                // 清除地点信息
                                val prefs = context.getSharedPreferences("chat_location_$guardianId", Context.MODE_PRIVATE)
                                prefs.edit().clear().apply()
                                sharedLocation = null
                            }
                        )
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
}

/**
 * ⭐ 新增：分享的地点卡片
 */
@Composable
fun SharedLocationCard(
    locationName: String,
    onUseForTaxi: () -> Unit
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
                    imageVector = Icons.Default.Send,  // 使用Send图标作为地点标识
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onUseForTaxi,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("一键填充到打车界面")
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
