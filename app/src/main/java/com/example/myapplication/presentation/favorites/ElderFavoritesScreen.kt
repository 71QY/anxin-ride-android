package com.example.myapplication.presentation.favorites

import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.model.LatLng
import com.example.myapplication.data.model.FavoriteLocation
import com.example.myapplication.data.model.GuardianInfo
import java.util.Locale

/**
 * ⭐ 长辈端收藏页面 - 适老化设计
 * 功能特点:
 * 1. 超大按钮、大字体
 * 2. 点击收藏项自动语音播报
 * 3. 一键发送给亲友(快速指定目的地)
 * 4. 一键确认到达(通知亲友)
 * 5. 纯查看模式(显示地址、电话、简介)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderFavoritesScreen(
    viewModel: FavoritesViewModel,
    homeViewModel: com.example.myapplication.presentation.home.HomeViewModel,
    onNavigateToHomeWithDestination: (String, Double, Double) -> Unit = { _, _, _ -> },
    onNavigateToChat: (Long, String) -> Unit = { _, _ -> }  // ⭐ 新增：跳转到聊天界面
) {
    val context = LocalContext.current
    val favorites by viewModel.favorites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // 监听 HomeViewModel 的当前位置
    val currentLocation by homeViewModel.currentLocation.collectAsState()
    val globalLocation by com.example.myapplication.core.utils.LocationManager.currentLocation.collectAsState()
    
    // 对话框状态
    var favoriteToShare by remember { mutableStateOf<FavoriteLocation?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }
    var favoriteToView by remember { mutableStateOf<FavoriteLocation?>(null) }
    
    // TTS 语音播报
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.refreshFavorites()
        
        // 初始化 TTS
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.setLanguage(Locale.CHINESE)
                Log.d("ElderFavoritesScreen", "✅ TTS 初始化成功")
            } else {
                Log.e("ElderFavoritesScreen", "❌ TTS 初始化失败")
            }
        }
    }
    
    // 清理 TTS
    DisposableEffect(Unit) {
        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
    }
    
    // 同步位置
    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            viewModel.updateCurrentLocation(location.latitude, location.longitude)
        }
    }
    
    LaunchedEffect(globalLocation) {
        globalLocation?.let { location ->
            viewModel.updateCurrentLocation(location.latitude, location.longitude)
        }
    }
    
    // 错误提示
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "我的收藏地点",
                        fontSize = 24.sp,  // ⭐ 长辈端大字体
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        if (isLoading && favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 4.dp)
            }
        } else if (favorites.isEmpty()) {
            // ⭐ 长辈端空状态 - 简化版
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Text(
                        text = "暂无收藏地点",
                        fontSize = 18.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "请联系亲友帮您添加",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)  // ⭐ 更大间距
            ) {
                items(favorites) { favorite ->
                    ElderFavoriteItemCard(
                        favorite = favorite,
                        onClick = {
                            // ⭐ 修复：点击收藏项只语音播报，不跳转
                            speakLocationInfo(textToSpeech, favorite)
                        },
                        onSendToGuardian = {
                            favoriteToShare = favorite
                            showShareDialog = true
                        },
                        onViewDetails = {
                            favoriteToView = favorite
                        }
                    )
                }
            }
        }
    }
    
    // 分享给亲友对话框
    if (showShareDialog && favoriteToShare != null) {
        ElderShareFavoriteDialog(
            favorite = favoriteToShare!!,
            viewModel = viewModel,
            homeViewModel = homeViewModel,
            context = context,  // ⭐ 传入context
            onDismiss = {
                showShareDialog = false
                favoriteToShare = null
            },
            onSuccess = { guardianId ->
                showShareDialog = false
                favoriteToShare = null
                Toast.makeText(context, "✅ 已发送给亲友", Toast.LENGTH_LONG).show()
                
                // ⭐ 修复：分享成功后自动跳转到与该亲友的聊天界面
                val guardianName = homeViewModel.guardianInfoList.value.find { it.userId == guardianId }?.name ?: "亲友"
                onNavigateToChat(guardianId, guardianName)
            }
        )
    }
    
    // 查看详情对话框
    favoriteToView?.let { favorite ->
        ElderFavoriteDetailDialog(
            favorite = favorite,
            onDismiss = { favoriteToView = null }
        )
    }
}

/**
 * ⭐ 长辈端收藏项卡片 - 超大按钮设计
 */
@Composable
private fun ElderFavoriteItemCard(
    favorite: FavoriteLocation,
    onClick: () -> Unit,
    onSendToGuardian: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),  // ⭐ 更大内边距
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部:图标 + 名称
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (favorite.type) {
                        "HOME" -> Icons.Default.Home
                        "COMPANY" -> Icons.Default.Business
                        "HOSPITAL" -> Icons.Default.LocalHospital
                        else -> Icons.Default.Place
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)  // ⭐ 更大图标
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = favorite.name,
                    fontSize = 22.sp,  // ⭐ 超大字体
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 地址
            Text(
                text = favorite.address,
                fontSize = 16.sp,  // ⭐ 大字体
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            
            // 电话(如果有)
            favorite.phone?.let { phone ->
                Text(
                    text = "📞 $phone",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 简介(如果有)
            favorite.description?.let { desc ->
                Text(
                    text = desc,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
            
            // 底部操作按钮区 - ⭐ 横向排列,超大按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 发送给亲友
                Button(
                    onClick = onSendToGuardian,
                    modifier = Modifier.weight(1f).height(56.dp),  // ⭐ 超大按钮
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1677FF)
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("发给亲友", fontSize = 16.sp)
                }
                
                // 查看详情
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("查看详情", fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * ⭐ 语音播报地点信息
 */
private fun speakLocationInfo(tts: TextToSpeech?, favorite: FavoriteLocation) {
    if (tts == null) return
    
    val speakText = if (favorite.description != null) {
        "这是${favorite.name},${favorite.description},地址:${favorite.address}"
    } else {
        "这是${favorite.name},地址:${favorite.address}"
    }
    
    tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, null)
    Log.d("ElderFavoritesScreen", "🔊 语音播报: $speakText")
}

/**
 * ⭐ 长辈端分享收藏对话框
 */
@Composable
private fun ElderShareFavoriteDialog(
    favorite: FavoriteLocation,
    viewModel: FavoritesViewModel,
    homeViewModel: com.example.myapplication.presentation.home.HomeViewModel,
    context: android.content.Context,  // ⭐ 新增：传入context
    onDismiss: () -> Unit,
    onSuccess: (Long) -> Unit = {}  // ⭐ 修改：返回guardianId用于跳转
) {
    val guardianList by homeViewModel.guardianInfoList.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "发送给哪位亲友?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (guardianList.isEmpty()) {
                Text(
                    "暂无绑定的亲友,请联系子女帮您绑定",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(guardianList) { guardian ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val guardianUserId = guardian.userId
                                    // ⭐ 修复：先关闭对话框，再发起分享请求
                                    onDismiss()
                                    
                                    viewModel.shareFavoriteToGuardian(
                                        favoriteId = favorite.id!!,
                                        guardianUserId = guardianUserId,
                                        onSuccess = {
                                            Log.d("ElderFavoritesScreen", "✅ [分享成功] 已发送 WebSocket 消息给亲友")
                                            // ⭐ 修复：分享成功后自动跳转到与该亲友的聊天界面
                                            onSuccess(guardianUserId)
                                        },
                                        onError = { error: String ->
                                            Toast.makeText(context, "❌ $error", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = guardian.name ?: "亲友",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = guardian.realName ?: "",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 16.sp)
            }
        }
    )
}

/**
 * ⭐ 长辈端查看详情对话框
 */
@Composable
private fun ElderFavoriteDetailDialog(
    favorite: FavoriteLocation,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                favorite.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 类型标签
                Row {
                    Surface(
                        modifier = Modifier,
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = when (favorite.type) {
                                "HOME" -> "🏠 家"
                                "COMPANY" -> "🏢 公司"
                                "HOSPITAL" -> "🏥 医院"
                                else -> "📍 其他"
                            },
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                
                // 地址
                Column {
                    Text("详细地址", fontSize = 14.sp, color = Color.Gray)
                    Text(favorite.address, fontSize = 16.sp)
                }
                
                // 电话
                favorite.phone?.let { phone ->
                    Column {
                        Text("联系电话", fontSize = 14.sp, color = Color.Gray)
                        Text(phone, fontSize = 18.sp, color = Color(0xFF1677FF), fontWeight = FontWeight.Bold)
                    }
                }
                
                // 简介
                favorite.description?.let { desc ->
                    Column {
                        Text("简介说明", fontSize = 14.sp, color = Color.Gray)
                        Text(desc, fontSize = 16.sp)
                    }
                }
                
                // 坐标
                Column {
                    Text("经纬度坐标", fontSize = 14.sp, color = Color.Gray)
                    Text("纬度: ${favorite.latitude}\n经度: ${favorite.longitude}", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("关闭", fontSize = 16.sp)
            }
        }
    )
}
