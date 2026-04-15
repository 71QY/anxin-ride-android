package com.example.myapplication.presentation.chat

import android.util.Log  // ⭐ 新增：用于日志输出
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onSessionSelected: (String) -> Unit = {},
    onNavigateToAgent: () -> Unit = {},  // ⭐ 新增：跳转到智能体聊天
    guardianList: List<com.example.myapplication.data.model.GuardianInfo> = emptyList(),  // ⭐ 长辈端：亲友列表
    elderList: List<com.example.myapplication.data.model.ElderInfo> = emptyList(),  // ⭐ 普通端：长辈列表
    showBackButton: Boolean = true  // ⭐ 新增：是否显示返回键（普通用户不显示）
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    
    // 如果已选择会话，显示聊天详情
    if (selectedSessionId != null) {
        ChatDetailContent(
            viewModel = viewModel,
            onBackClick = {
                viewModel.backToList()
            }
        )
    } else {
        // 否则显示聊天列表
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("消息") },
                    navigationIcon = {
                        if (showBackButton) {  // ⭐ 根据参数决定是否显示返回键
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // ⭐ 智能体助手入口（固定显示在最上方）
                item {
                    AgentChatEntry(
                        onClick = onNavigateToAgent
                    )
                }
                
                // ⭐ 新增：普通端 - 我的长辈列表
                if (elderList.isNotEmpty()) {
                    item {
                        Text(
                            text = "我的长辈",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
                    items(elderList) { elder ->
                        ElderChatItem(
                            elder = elder,
                            onClick = {
                                // ⭐ 点击长辈后进入与该长辈的聊天界面
                                onSessionSelected("elder_${elder.userId}")
                            }
                        )
                    }
                }
                
                // ⭐ 长辈端 - 我的亲友列表
                if (guardianList.isNotEmpty()) {
                    item {
                        Text(
                            text = "我的亲友",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                
                // ⭐ 新增：显示亲友列表（先过滤无效数据）
                val validGuardians = guardianList.filter { 
                    it.userId != 0L && !it.name.isNullOrBlank() 
                }
                
                items(validGuardians) { guardian ->
                    GuardianChatItem(
                        guardian = guardian,
                        onClick = {
                            // ⭐ 点击亲友后进入与该亲友的聊天界面
                            onSessionSelected("guardian_${guardian.userId}")
                        }
                    )
                }
                
                // 原有的会话列表（暂时为空，保留以便后续扩展）
                items(sessions) { session ->
                    ChatSessionItem(
                        session = session,
                        onClick = {
                            viewModel.selectSession(session.id)
                            onSessionSelected(session.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AgentChatEntry(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🤖",
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "智能体助手",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "在线",
                    fontSize = 14.sp,
                    color = Color.Green
                )
            }
        }
    }
}

@Composable
fun GuardianChatItem(
    guardian: com.example.myapplication.data.model.GuardianInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👤",
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = guardian.name ?: "未知亲友",  // ⭐ 防御性处理
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = guardian.phone ?: "暂无联系方式",  // ⭐ 显示手机号
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// ⭐ 新增：长辈聊天项（普通端显示）
@Composable
fun ElderChatItem(
    elder: com.example.myapplication.data.model.ElderInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👴",
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = elder.name ?: "未知长辈",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = elder.phone ?: "暂无联系方式",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ChatSessionItem(
    session: com.example.myapplication.data.model.ChatSession,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = session.avatar ?: "👤",
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 聊天信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = session.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = formatTime(session.lastMessageTime),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.lastMessage,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                    
                    if (session.unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "${session.unreadCount}",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatDetailContent(
    viewModel: ChatListViewModel,
    onBackClick: () -> Unit
) {
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部栏
        TopAppBar(
            title = { Text("聊天") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )
        
        // 消息列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                MessageBubble(message = message)
            }
        }
        
        // 输入框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank()
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
fun MessageBubble(message: com.example.myapplication.data.model.ChatMessage) {
    val isUser = message.isUser
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
