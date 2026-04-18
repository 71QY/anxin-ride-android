package com.example.myapplication.presentation.favorites

import android.Manifest
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng
import com.example.myapplication.data.model.FavoriteLocation

/**
 * 收藏常用地点页面
 * 适老化设计：大按钮、大字体、二次确认删除
 * ⭐ 新增：搜索 POI 功能，无需手动输入地址坐标
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,  // ⭐ 修复：移除默认参数，必须由调用方传入
    homeViewModel: com.example.myapplication.presentation.home.HomeViewModel,  // ⭐ 修复：移除默认参数
    onNavigateToHomeWithDestination: (String, Double, Double) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val favorites by viewModel.favorites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // ⭐ 新增：搜索相关状态
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var searchKeyword by remember { mutableStateOf("") }
    
    // ⭐ 新增：监听 HomeViewModel 的当前位置
    val currentLocation by homeViewModel.currentLocation.collectAsState()
    
    // ⭐ 修复：同时监听全局位置管理器（解决 hiltViewModel 创建新实例的问题）
    val globalLocation by com.example.myapplication.core.utils.LocationManager.currentLocation.collectAsState()

    // 对话框状态
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<FavoriteLocation?>(null) }
    var favoriteToDelete by remember { mutableStateOf<FavoriteLocation?>(null) }
    
    // ⭐ 新增：搜索选择对话框
    var selectedPoiForAdd by remember { mutableStateOf<com.example.myapplication.data.model.PoiResponse?>(null) }
    
    // ⭐ 新增：分享目的地给亲友对话框
    var favoriteToShare by remember { mutableStateOf<FavoriteLocation?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }
    
    // ⭐ 新增：查看详情对话框
    var favoriteToView by remember { mutableStateOf<FavoriteLocation?>(null) }

    // ⭐ 定位权限请求（用于地图选点）
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(context, "需要定位权限才能使用地图选点功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 进入页面时自动刷新
    LaunchedEffect(Unit) {
        viewModel.refreshFavorites()
        // ⭐ 初始化TTS语音播报
        viewModel.initTTS(context)
    }
    
    // ⭐ 新增：监听位置变化，同步到 FavoritesViewModel
    LaunchedEffect(currentLocation) {
        currentLocation?.let { location ->
            android.util.Log.d("FavoritesScreen", "📍 位置同步 (HomeViewModel): lat=${location.latitude}, lng=${location.longitude}")
            viewModel.updateCurrentLocation(location.latitude, location.longitude)
        }
    }
    
    // ⭐ 修复：监听全局位置管理器
    LaunchedEffect(globalLocation) {
        globalLocation?.let { location ->
            android.util.Log.d("FavoritesScreen", "📍 位置同步 (Global): lat=${location.latitude}, lng=${location.longitude}")
            viewModel.updateCurrentLocation(location.latitude, location.longitude)
        } ?: run {
            android.util.Log.w("FavoritesScreen", "⚠️ 全局位置为 null")
        }
    }
    
    // ⭐ 修复：在打开对话框时，强制同步一次最新位置
    LaunchedEffect(showAddDialog) {
        if (showAddDialog) {
            // ⭐ 优先使用全局位置管理器（解决 ViewModel 作用域问题）
            val globalLoc = com.example.myapplication.core.utils.LocationManager.getLocation()
            val homeLoc = homeViewModel.currentLocation.value
            val location = globalLoc ?: homeLoc
            
            if (location != null) {
                android.util.Log.d("FavoritesScreen", "🔓 打开搜索对话框，同步位置: lat=${location.latitude}, lng=${location.longitude}")
                viewModel.updateCurrentLocation(location.latitude, location.longitude)
            } else {
                android.util.Log.w("FavoritesScreen", "⚠️ 打开搜索对话框时位置仍为 null")
                // ⭐ 提示用户：需要先回到首页等待定位完成
                Toast.makeText(context, "⚠️ 定位未完成，请先回到首页等待定位成功后再试", Toast.LENGTH_LONG).show()
                showAddDialog = false  // 关闭对话框
            }
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
                        "收藏常用地点",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加收藏")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading && favorites.isEmpty()) {
            // 加载状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (favorites.isEmpty()) {
            // 空状态
            EmptyFavoritesView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onAddClick = { showAddDialog = true }
            )
        } else {
            // 收藏列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favorites) { favorite ->
                    FavoriteItemCard(
                        favorite = favorite,
                        onClick = {
                            // ⭐ 点击收藏项，语音播报地点信息
                            viewModel.speakLocationInfo(
                                name = favorite.name,
                                address = favorite.address,
                                phone = favorite.phone
                            )
                        },
                        onSendToGuardian = {
                            // ⭐ 发送目的地给亲友
                            favoriteToShare = favorite
                            showShareDialog = true
                        },
                        onViewDetails = {
                            // ⭐ 查看详情
                            favoriteToView = favorite
                        },
                        onConfirmArrival = {
                            // ⭐ 确认到达
                            viewModel.confirmArrival(
                                favoriteId = favorite.id ?: return@FavoriteItemCard,
                                onSuccess = {
                                    Toast.makeText(context, "✅ 已通知亲友您已到达", Toast.LENGTH_LONG).show()
                                },
                                onError = { error ->
                                    Toast.makeText(context, "❌ $error", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onEdit = { showEditDialog = favorite },
                        onDelete = { favoriteToDelete = favorite }
                    )
                }
            }
        }
    }

    // 添加收藏对话框 - ⭐ 改为搜索选择模式
    if (showAddDialog) {
        AddFavoriteSearchDialog(
            viewModel = viewModel,
            searchKeyword = searchKeyword,
            onSearchKeywordChange = { searchKeyword = it },
            searchResults = searchResults,
            isSearching = isSearching,
            currentLocation = globalLocation ?: currentLocation,  // ⭐ 优先使用全局位置
            onDismiss = { 
                showAddDialog = false
                viewModel.clearSearchResults()
                searchKeyword = ""
            },
            onSelectPoi = { poi ->
                selectedPoiForAdd = poi
            }
        )
    }
    
    // ⭐ 新增：选择 POI 后取名对话框
    selectedPoiForAdd?.let { poi ->
        AddFavoriteNameDialog(
            poi = poi,
            onDismiss = { selectedPoiForAdd = null },
            onSave = { customName ->
                viewModel.addFavorite(
                    name = customName,
                    address = poi.address ?: poi.name ?: "未知地址",
                    latitude = poi.lat,
                    longitude = poi.lng,
                    type = "CUSTOM",
                    onSuccess = {
                        selectedPoiForAdd = null
                        showAddDialog = false
                        viewModel.clearSearchResults()
                        searchKeyword = ""
                        Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // 编辑收藏对话框
    showEditDialog?.let { favorite ->
        AddEditFavoriteDialog(
            favorite = favorite,
            onDismiss = { showEditDialog = null },
            onSave = { name, address, lat, lng, type ->
                favorite.id?.let { id ->
                    viewModel.updateFavorite(
                        id = id,
                        name = name,
                        address = address,
                        latitude = lat,
                        longitude = lng,
                        type = type,
                        onSuccess = {
                            showEditDialog = null
                            Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }

    // 删除确认对话框
    favoriteToDelete?.let { favorite ->
        AlertDialog(
            onDismissRequest = { favoriteToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除\"${favorite.name}\"吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        favorite.id?.let { id ->
                            viewModel.deleteFavorite(
                                favoriteId = id,
                                onSuccess = {
                                    favoriteToDelete = null
                                    Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { favoriteToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
    
    // ⭐ 新增：分享目的地给亲友对话框
    if (showShareDialog && favoriteToShare != null) {
        ShareToGuardianDialog(
            favorite = favoriteToShare!!,
            homeViewModel = homeViewModel,
            onDismiss = { 
                showShareDialog = false
                favoriteToShare = null
            },
            onShareSuccess = {
                showShareDialog = false
                favoriteToShare = null
                Toast.makeText(context, "✅ 已发送给亲友，等待代叫车", Toast.LENGTH_LONG).show()
            },
            onShareError = { error ->
                Toast.makeText(context, "❌ $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    // ⭐ 新增：查看详情对话框
    favoriteToView?.let { favorite ->
        ViewFavoriteDetailsDialog(
            favorite = favorite,
            onDismiss = { favoriteToView = null },
            onSpeakAgain = {
                // 再次语音播报
                viewModel.speakLocationInfo(
                    name = favorite.name,
                    address = favorite.address,
                    phone = favorite.phone
                )
            }
        )
    }
}

/**
 * ⭐ 新增：分享目的地给亲友对话框
 */
@Composable
private fun ShareToGuardianDialog(
    favorite: FavoriteLocation,
    homeViewModel: com.example.myapplication.presentation.home.HomeViewModel,
    onDismiss: () -> Unit,
    onShareSuccess: () -> Unit,
    onShareError: (String) -> Unit
) {
    val context = LocalContext.current
    val guardians by homeViewModel.guardians.collectAsState()
    var selectedGuardianId by remember { mutableStateOf<Long?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要发送给哪位亲友") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 显示目的地信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📍 ${favorite.name}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = favorite.address,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 亲友列表
                if (guardians.isEmpty()) {
                    Text(
                        text = "暂无亲友，请先绑定亲友关系",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(guardians) { guardian ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGuardianId = guardian.userId },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedGuardianId == guardian.userId) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedGuardianId == guardian.userId,
                                        onClick = { selectedGuardianId = guardian.userId }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = guardian.realName.ifEmpty { guardian.name },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = guardian.phone,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedGuardianId == null) {
                        Toast.makeText(context, "请选择一位亲友", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    favorite.id?.let { favId ->
                        // TODO: 调用API分享
                        onShareSuccess()
                    }
                },
                enabled = selectedGuardianId != null && guardians.isNotEmpty()
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * ⭐ 新增：查看收藏地点详情对话框
 */
@Composable
private fun ViewFavoriteDetailsDialog(
    favorite: FavoriteLocation,
    onDismiss: () -> Unit,
    onSpeakAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("详细信息") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 地点名称
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = favorite.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // 地址
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "地址",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = favorite.address,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // 电话（如果有）
                favorite.phone?.let { phone ->
                    if (phone.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "电话",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = phone,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                
                // 简介（如果有）
                favorite.description?.let { desc ->
                    if (desc.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "简介",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = desc,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                
                // 最后访问时间（如果有）
                favorite.lastVisitedAt?.let { lastVisited ->
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "上次访问",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastVisited,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSpeakAgain) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("再次播报")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 空状态视图
 */
@Composable
private fun EmptyFavoritesView(
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无收藏",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右下角按钮添加常用地点",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddClick,
            modifier = Modifier.height(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加收藏", fontSize = 16.sp)
        }
    }
}

/**
 * 收藏项卡片
 * 适老化设计：大按钮、清晰布局
 * ⭐ 新增：发送目的地、查看详情、确认到达功能
 */
@Composable
private fun FavoriteItemCard(
    favorite: FavoriteLocation,
    onClick: () -> Unit,
    onSendToGuardian: () -> Unit,
    onViewDetails: () -> Unit,
    onConfirmArrival: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行：图标 + 信息
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧图标
                Icon(
                    imageVector = when (favorite.type) {
                        "HOME" -> Icons.Default.Home
                        "COMPANY" -> Icons.Default.Business
                        "HOSPITAL" -> Icons.Default.LocalHospital
                        else -> Icons.Default.Place
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 中间信息
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = favorite.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = favorite.address,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            
            // ⭐ 第二行：主要操作按钮（大按钮，适合长辈）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 发送目的地给亲友
                Button(
                    onClick = onSendToGuardian,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("发送目的地", fontSize = 15.sp)
                }
                
                // 查看详情
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("查看详情", fontSize = 15.sp)
                }
            }
            
            // ⭐ 第三行：确认到达 + 编辑/删除
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 确认到达
                Button(
                    onClick = onConfirmArrival,
                    modifier = Modifier.weight(2f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)  // 绿色
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("✅ 已到达", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                // 编辑
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
                }
                
                // 删除
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
                }
            }
        }
    }
}

/**
 * 添加/编辑收藏对话框
 * ⭐ 说明：推荐通过首页搜索地点后点击"收藏"按钮来添加真实地址
 */
@Composable
private fun AddEditFavoriteDialog(
    favorite: FavoriteLocation?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double, String) -> Unit
) {
    var name by remember { mutableStateOf(favorite?.name ?: "") }
    var address by remember { mutableStateOf(favorite?.address ?: "") }
    var latitude by remember { mutableDoubleStateOf(favorite?.latitude ?: 0.0) }
    var longitude by remember { mutableDoubleStateOf(favorite?.longitude ?: 0.0) }
    var selectedType by remember { mutableStateOf(favorite?.type ?: "CUSTOM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (favorite == null) "添加收藏" else "编辑收藏") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 名称输入
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("地点名称") },
                    placeholder = { Text("如：家、公司、市人民医院") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 地址输入
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("详细地址") },
                    placeholder = { Text("请输入详细地址") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 类型选择
                TypeSelector(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )

                // 地图选点提示
                Text(
                    text = "💡 提示：可以在地图上长按选点后自动填充坐标",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        return@Button
                    }
                    onSave(name, address, latitude, longitude, selectedType)
                },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 类型选择器
 */
@Composable
private fun TypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    val types = listOf(
        "HOME" to "家",
        "COMPANY" to "公司",
        "HOSPITAL" to "医院",
        "CUSTOM" to "其他"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("地点类型", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            types.forEach { (type, label) ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ⭐ 新增：搜索 POI 对话框
@Composable
private fun AddFavoriteSearchDialog(
    viewModel: FavoritesViewModel,
    searchKeyword: String,
    onSearchKeywordChange: (String) -> Unit,
    searchResults: List<com.example.myapplication.data.model.PoiResponse>,
    isSearching: Boolean,
    currentLocation: LatLng?,  // ⭐ 新增：传入当前位置
    onDismiss: () -> Unit,
    onSelectPoi: (com.example.myapplication.data.model.PoiResponse) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索地点") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 搜索输入框
                OutlinedTextField(
                    value = searchKeyword,
                    onValueChange = { 
                        onSearchKeywordChange(it)
                        // ⭐ 修复：输入时清空错误提示，避免误导用户
                        viewModel.clearError()
                    },
                    label = { Text("输入地点名称") },
                    placeholder = { Text("如：医院、超市、公园") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { 
                            // ⭐ 修复：优先使用全局位置，确保搜索时使用正确坐标
                            val searchLocation = currentLocation ?: com.example.myapplication.core.utils.LocationManager.getLocation()
                            android.util.Log.d("FavoritesSearch", "🔍 搜索: $searchKeyword, lat=${searchLocation?.latitude}, lng=${searchLocation?.longitude}")
                            viewModel.searchPoi(
                                searchKeyword,
                                lat = searchLocation?.latitude,
                                lng = searchLocation?.longitude
                            )
                        }) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                        }
                    }
                )
                
                // 搜索结果列表
                if (searchResults.isEmpty() && searchKeyword.isNotEmpty() && !isSearching) {
                    Text(
                        text = "未找到相关地点，请换一个关键词试试",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (searchResults.isNotEmpty()) {
                    Text(
                        text = "搜索结果（点击选择）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { poi ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPoi(poi) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = poi.name ?: "未知位置",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = poi.address ?: "未知地址",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                    if (poi.distance != null) {
                                        Text(
                                            text = "距离：${String.format("%.1f", poi.distance / 1000.0)}公里",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ⭐ 新增：收藏取名对话框
@Composable
private fun AddFavoriteNameDialog(
    poi: com.example.myapplication.data.model.PoiResponse,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("给这个地点取个名字") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 显示选中的地点信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = poi.name ?: "未知位置",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = poi.address ?: "未知地址",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                
                // 自定义名称输入
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("自定义名称") },
                    placeholder = { Text("如：看病的地方、买菜的地方") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "💡 提示：取一个你容易记住的名字",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customName.isBlank()) {
                        return@Button
                    }
                    onSave(customName)
                },
                enabled = customName.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
