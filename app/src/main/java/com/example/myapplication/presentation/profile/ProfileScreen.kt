package com.example.myapplication.presentation.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.myapplication.data.model.ElderInfo  // ⭐ 新增：长辈信息模型
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToOrderList: () -> Unit = {},
    onNavigateToGuardian: () -> Unit = {},  // ⭐ 新增：跳转到亲情守护页面
    onNavigateToAccount: () -> Unit = {},   // ⭐ 新增：跳转到账号安全页面
    onLogout: () -> Unit = {}  // ⭐ 新增：退出登录回调
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val isProfileLoading by viewModel.isProfileLoading.collectAsStateWithLifecycle()
    val isContactsLoading by viewModel.isContactsLoading.collectAsStateWithLifecycle()
    val isOperationLoading by viewModel.isOperationLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    
    // ⭐ 新增：监听认证失败，自动退出登录
    val authFailure by viewModel.authFailure.collectAsStateWithLifecycle()
    
    // ⭐ 修复：使用本地变量启用智能转换
    val currentProfile = profile
    val currentErrorMessage = errorMessage
    val currentSuccessMessage = successMessage

    val nicknameInput by viewModel.nicknameInput.collectAsStateWithLifecycle()
    val codeInput by viewModel.codeInput.collectAsStateWithLifecycle()
    val newPasswordInput by viewModel.newPasswordInput.collectAsStateWithLifecycle()
    val isSendingCode by viewModel.isSendingCode.collectAsStateWithLifecycle()
    val realNameInput by viewModel.realNameInput.collectAsStateWithLifecycle()
    val idCardInput by viewModel.idCardInput.collectAsStateWithLifecycle()
    val contactNameInput by viewModel.contactNameInput.collectAsStateWithLifecycle()
    val contactPhoneInput by viewModel.contactPhoneInput.collectAsStateWithLifecycle()
    
    // ⭐ 新增：亲情守护相关状态
    val elderPhoneInput by viewModel.elderPhoneInput.collectAsStateWithLifecycle()
    val elderNameInput by viewModel.elderNameInput.collectAsStateWithLifecycle()
    val elderIdCardInput by viewModel.elderIdCardInput.collectAsStateWithLifecycle()  // ⭐ 新增：长辈身份证号
    val relationshipInput by viewModel.relationshipInput.collectAsStateWithLifecycle()  // ⭐ 新增：与长辈关系
    val isAddingElder by viewModel.isAddingElder.collectAsStateWithLifecycle()
    val guardianInfo by viewModel.guardianInfo.collectAsStateWithLifecycle()  // ⭐ 新增：守护者信息
    val guardianInfoList by viewModel.guardianInfoList.collectAsStateWithLifecycle()  // ⭐ 新增：守护者列表
    
    // ⭐ 新增：亲友信息输入（后端要求必填）
    val guardianNameInput by viewModel.guardianNameInput.collectAsStateWithLifecycle()
    val guardianIdCardInput by viewModel.guardianIdCardInput.collectAsStateWithLifecycle()

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAvatarCropDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    var showContactsSection by remember { mutableStateOf(false) }
    
    var showFamilyGuardianSection by remember { mutableStateOf(true) }  // ⭐ 长辈模式默认展开
    var showAddElderDialog by remember { mutableStateOf(false) }
    var showBindElderDialog by remember { mutableStateOf(false) }  // ⭐ 新增：绑定已有长辈对话框
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }  // ⭐ 新增：退出确认对话框
    
    // ⭐ 新增：解绑确认对话框状态
    var showUnbindConfirmDialog by remember { mutableStateOf(false) }
    var elderToUnbind by remember { mutableStateOf<ElderInfo?>(null) }
    
    // ⭐ 新增：监听认证失败，自动退出登录
    LaunchedEffect(authFailure) {
        if (authFailure) {
            Log.w("ProfileScreen", "⚠️ 检测到认证失败，清除 Token 并跳转到登录页")
            viewModel.resetAuthFailure()  // ⭐ 重置标志
            onLogout()  // ⭐ 调用退出登录回调
        }
    }
    
    // ⭐ 长辈模式下加载亲友列表
    LaunchedEffect(profile) {
        if (profile?.guardMode == 1) {
            Log.d("ProfileScreen", "✅ 检测到长辈模式，开始加载亲友列表")
            viewModel.loadGuardianInfo()
        } else {
            Log.d("ProfileScreen", "⚠️ 非长辈模式 (guardMode=${profile?.guardMode})，不加载亲友列表")
        }
    }

    val context = LocalContext.current

    fun confirmAvatarCrop(croppedBitmap: Bitmap) {
        Log.d("ProfileScreen", "✂️ 确认头像裁剪")
        Log.d("ProfileScreen", "  croppedBitmap 尺寸: ${croppedBitmap.width}x${croppedBitmap.height}")
        
        // ⭐ 关键修复：先保存 selectedImageUri 的引用，避免后面被清空
        val uriToUpload = selectedImageUri
        Log.d("ProfileScreen", "selectedImageUri: $uriToUpload")
        
        if (uriToUpload == null) {
            Log.e("ProfileScreen", "❌ selectedImageUri 为 null，无法上传")
            showAvatarCropDialog = false
            selectedImageUri = null
            return
        }
        
        try {
            val file = File(context.cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
            Log.d("ProfileScreen", "保存裁剪后的图片到: ${file.absolutePath}")
            
            val outputStream = FileOutputStream(file)
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            
            Log.d("ProfileScreen", "✅ 裁剪图片保存成功，文件大小: ${file.length() / 1024}KB")
            Log.d("ProfileScreen", "🚀 开始调用 viewModel.uploadAvatar...")
            
            // ⭐ 修复：先关闭对话框，再调用上传（避免 Compose 重组取消协程）
            showAvatarCropDialog = false
            selectedImageUri = null
            
            // ⭐ 直接调用，ViewModel 内部会启动独立的协程
            viewModel.uploadAvatar(uriToUpload, file)
            Log.d("ProfileScreen", "✅ viewModel.uploadAvatar 已调用")
        } catch (e: Exception) {
            Log.e("ProfileScreen", "❌ 保存裁剪图片失败", e)
            showAvatarCropDialog = false
            selectedImageUri = null
        }
    }

    LaunchedEffect(Unit) {
        Log.d("ProfileScreen", "=== ProfileScreen first load ===")
        Log.d("ProfileScreen", "currentProfile before load: $currentProfile")
        viewModel.loadProfile()
        viewModel.loadEmergencyContacts()
    }
    
    // ⭐ 新增：监听 profile 变化
    LaunchedEffect(profile) {
        Log.d("ProfileScreen", "=== Profile updated ===")
        Log.d("ProfileScreen", "profile: $profile")
        Log.d("ProfileScreen", "profile?.phone: ${profile?.phone}")
        Log.d("ProfileScreen", "profile?.nickname: ${profile?.nickname}")
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        Log.d("ProfileScreen", "🖼️ 图片选择器回调: uri=$uri")
        uri?.let {
            Log.d("ProfileScreen", "✅ 图片选择成功: $it")
            Log.d("ProfileScreen", "URI scheme: ${it.scheme}")
            Log.d("ProfileScreen", "URI path: ${it.path}")
            selectedImageUri = it
            showAvatarCropDialog = true
            Log.d("ProfileScreen", "✅ 显示裁剪对话框")
        } ?: run {
            Log.w("ProfileScreen", "⚠️ 用户取消了图片选择")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            showPermissionDialog = true
        }
    }

    fun pickImage() {
        Log.d("ProfileScreen", "📸 开始选择头像图片")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.READ_MEDIA_IMAGES
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d("ProfileScreen", "Android 版本: ${Build.VERSION.SDK_INT}, 权限状态: $hasPermission")
            
            if (hasPermission) {
                Log.d("ProfileScreen", "✅ 权限已授予，打开图片选择器")
                imagePickerLauncher.launch("image/*")
            } else {
                Log.w("ProfileScreen", "❌ 权限未授予，请求权限: $permission")
                permissionLauncher.launch(permission)
            }
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d("ProfileScreen", "Android 版本: ${Build.VERSION.SDK_INT}, 权限状态: $hasPermission")
            
            if (hasPermission) {
                Log.d("ProfileScreen", "✅ 权限已授予，打开图片选择器")
                imagePickerLauncher.launch("image/*")
            } else {
                Log.w("ProfileScreen", "❌ 权限未授予，请求权限: $permission")
                permissionLauncher.launch(permission)
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要存储权限") },
            text = { Text("选择头像需要存储权限，请在设置中允许访问。") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = {
                        viewModel.loadProfile()
                        viewModel.loadEmergencyContacts()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ⭐ 根据模式显示不同内容
            if (currentProfile?.guardMode == 1) {
                // ===== 长辈模式：只显示亲情守护 + 账号管理 =====
                
                // 1. 简化信息卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "个人中心（长辈模式）",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "手机号：${currentProfile.phone ?: "未知"}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // 2. 快捷操作 - ⭐ 新增：我的订单
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onNavigateToOrderList() },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ListAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(8.dp).size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "我的订单",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "查看历史订单记录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "进入",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 3. 亲情守护模块
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "亲情守护",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "您已由亲友守护",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    
                                    // ⭐ 显示守护者详细信息（从 API 加载）
                                    if (guardianInfoList.isNotEmpty()) {
                                        // 有真实数据，显示亲友列表
                                        guardianInfoList.forEach { guardian ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "姓名：${guardian.realName}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "手机号：${guardian.phone}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else if (currentProfile?.guardianName != null) {
                                        // API 未返回，使用 fallback 数据
                                        Text(
                                            text = "守护者：${currentProfile.guardianName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        // 加载中或无数据
                                        Text(
                                            text = "加载中...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }
                                    
                                    Text(
                                        text = "亲友可为您代叫车辆，您可随时解绑",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 4. 账号管理模块
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "账号管理",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = when {
                                    isProfileLoading -> "当前账号：加载中..."
                                    currentProfile != null -> "当前账号：${currentProfile.phone ?: "未知"}"
                                    else -> "当前账号：未登录"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // ⭐ 退出登录按钮（长辈模式 - 小按钮防误触）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                OutlinedButton(
                                    onClick = { showLogoutConfirmDialog = true },
                                    modifier = Modifier.width(160.dp),  // ⭐ 限制宽度，避免太大
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.ExitToApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)  // ⭐ 小图标
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "退出登录",
                                        fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)  // ⭐ 小字体
                                    )
                                }
                            }
                            
                            Text(
                                text = "提示：退出后将返回登录页面",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,  // ⭐ 居中显示
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // ⭐ 新增：底部留白，让退出登录按钮距离底部导航栏约1cm（40dp）
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            } else {
                // ===== 普通模式：显示完整功能 =====
                
                // 顶部用户信息卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 头像区域
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable { pickImage() },
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarValue = profile?.avatar
                                val avatarUrl = if (!avatarValue.isNullOrBlank()) {
                                    val cleanUrl = avatarValue.trim()
                                    when {
                                        // 已经是完整 URL
                                        cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> cleanUrl
                                        // 后端返回 /api/xxx，直接使用（BASE_URL已包含/api/）
                                        // 旧后端地址 (A): cleanUrl.startsWith("/api/") -> "http://10.241.75.80:8080$cleanUrl"
                                        // 中间后端地址 (B): cleanUrl.startsWith("/api/") -> "http://192.168.189.57:8080$cleanUrl"
                                        // 新后端地址 (C):
                                        cleanUrl.startsWith("/api/") -> "http://192.168.189.80:8080$cleanUrl"
                                        // 以 / 开头的其他路径
                                        // 旧后端地址 (A): cleanUrl.startsWith("/") -> "http://10.241.75.80:8080/api$cleanUrl"
                                        // 中间后端地址 (B): cleanUrl.startsWith("/") -> "http://192.168.189.57:8080/api$cleanUrl"
                                        // 新后端地址 (C):
                                        cleanUrl.startsWith("/") -> "http://192.168.189.80:8080/api$cleanUrl"
                                        // 不带 / 的相对路径
                                        // 旧后端地址 (A): else -> "http://10.241.75.80:8080/api/$cleanUrl"
                                        // 中间后端地址 (B): else -> "http://192.168.189.57:8080/api/$cleanUrl"
                                        // 新后端地址 (C):
                                        else -> "http://192.168.189.80:8080/api/$cleanUrl"
                                    }
                                } else null
                                
                                Log.d("ProfileScreen", "🖼️ 头像 URL 处理:")
                                Log.d("ProfileScreen", "  原始值: $avatarValue")
                                Log.d("ProfileScreen", "  处理后: $avatarUrl")

                                if (avatarUrl != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context)
                                                .data(avatarUrl)
                                                .crossfade(true)
                                                .size(160, 160)
                                                .build()
                                        ),
                                        contentDescription = "头像",
                                        modifier = Modifier.size(80.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(80.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "默认头像",
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                // 编辑图标
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "更换头像",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 昵称
                            profile?.nickname?.let { nickname ->
                                Text(
                                    text = nickname,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            // 手机号
                            Text(
                                text = profile?.phone ?: "未登录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 认证状态徽章
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (profile?.verified == 1) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (profile?.verified == 1) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (profile?.verified == 1) Color.White else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (profile?.verified == 1) "已实名认证" else "未实名认证",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (profile?.verified == 1) Color.White else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "点击头像可更换",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // 快捷操作卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onNavigateToOrderList() },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ListAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(8.dp).size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "我的订单",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "查看历史订单记录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "进入",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 账户设置分组标题
                item {
                    Text(
                        text = "账户设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                // 紧急联系人卡片
                item {
                    Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ExpandableSection(
                        title = "紧急联系人",
                        icon = Icons.Default.ContactPhone,
                        expanded = showContactsSection,
                        onExpandToggle = { showContactsSection = !showContactsSection },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { showAddContactDialog = true },
                                enabled = !isOperationLoading,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("添加", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isContactsLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(strokeWidth = 2.dp)
                                        Text(
                                            text = "加载中...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else if (contacts.isNotEmpty()) {
                                contacts.forEach { contact ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = contact.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = contact.phone,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteEmergencyContact(contact.id) },
                                                enabled = !isOperationLoading,
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContactPhone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = "暂无紧急联系人",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "点击右上角\"添加\"按钮添加联系人",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

                // ⭐ 亲情守护入口卡片（跳转到独立页面）
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onNavigateToGuardian() },  // ⭐ 点击跳转到亲情守护页面
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.FamilyRestroom,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = "亲情守护",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "管理绑定的长辈账号",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ⭐ 账号安全入口卡片（跳转到独立页面）
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onNavigateToAccount() },  // ⭐ 点击跳转到账号安全页面
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = "账号安全",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "管理密码、实名认证等",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // ⭐ 退出登录按钮（放在最底部）
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { showLogoutConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("退出登录")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }  // 结束 LazyColumn items
        }  // 结束 LazyColumn
    }  // 结束 Scaffold

    // 对话框（放在 Scaffold 外部但仍在同一个 Composable 作用域内）
    if (showAddContactDialog) {
        AddEmergencyContactDialog(
            onDismissRequest = { showAddContactDialog = false },
            onConfirm = { name, phone ->
                viewModel.updateContactNameInput(name)
                viewModel.updateContactPhoneInput(phone)
                viewModel.addEmergencyContact()
            },
            name = contactNameInput,
            phone = contactPhoneInput,
            onNameChange = viewModel::updateContactNameInput,
            onPhoneChange = viewModel::updateContactPhoneInput,
            isLoading = isOperationLoading,
            errorMessage = errorMessage,
            successMessage = successMessage
        )
        LaunchedEffect(successMessage) {
            if (successMessage?.contains("联系人") == true || successMessage?.contains("添加") == true) {
                showAddContactDialog = false
            }
        }
    }
    
    // ⭐ 新增：帮长辈注册账号对话框（v2.0）⭐
    if (showAddElderDialog) {
        AlertDialog(
            onDismissRequest = { showAddElderDialog = false },
            title = { Text("帮长辈注册账号") },
            text = {
                // ⭐ 修复：添加垂直滚动，确保错误/成功提示可见
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)  // ⭐ 设置最大高度，触发滚动
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "填写长辈信息，系统将自动创建账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // ⭐ 重要提示卡片
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "重要说明",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "• 该手机号将作为长辈的登录账号\n• 一个身份证号只能绑定一个账号\n• 注册成功后长辈可直接用手机号+验证码登录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    // ⭐ 长辈姓名（必填）
                    OutlinedTextField(
                        value = elderNameInput,
                        onValueChange = viewModel::updateElderNameInput,
                        label = { Text("长辈姓名 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入长辈真实姓名") },
                        isError = currentErrorMessage?.contains("长辈姓名") == true,
                        supportingText = {
                            if (currentErrorMessage?.contains("长辈姓名") == true) {
                                Text(currentErrorMessage!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    
                    // ⭐ 长辈身份证号（必填）
                    OutlinedTextField(
                        value = elderIdCardInput,
                        onValueChange = viewModel::updateElderIdCardInput,
                        label = { Text("长辈身份证号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入18位身份证号") },
                        isError = currentErrorMessage?.contains("身份证号") == true,
                        supportingText = {
                            if (currentErrorMessage?.contains("身份证号") == true) {
                                Text(currentErrorMessage!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("18位，支持最后一位为X", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    
                    // ⭐ 长辈手机号（必填）
                    OutlinedTextField(
                        value = elderPhoneInput,
                        onValueChange = viewModel::updateElderPhoneInput,
                        label = { Text("长辈手机号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        placeholder = { Text("请输入长辈手机号，将作为登录账号") },
                        isError = currentErrorMessage?.contains("长辈手机号") == true,
                        supportingText = {
                            if (currentErrorMessage?.contains("长辈手机号") == true) {
                                Text(currentErrorMessage!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("该手机号将作为长辈的登录账号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    
                    // ⭐ 与长辈关系（选填）
                    OutlinedTextField(
                        value = relationshipInput,
                        onValueChange = viewModel::updateRelationshipInput,
                        label = { Text("与长辈关系") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如：子女、亲属、护工（选填）") }
                    )

                    Text(
                        text = "提示：注册成功后，让长辈用该手机号接收验证码登录即可直接使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // ⭐ 新增：通用错误提示卡片（显示后端返回的所有错误）
                    if (currentErrorMessage != null && currentErrorMessage.isNotBlank()) {
                        // ⭐ 调试日志
                        Log.d("ProfileScreen", "✅ 显示错误提示卡片: $currentErrorMessage")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "注册失败",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentErrorMessage,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                
                                // ⭐ 根据错误类型提供解决方案
                                when {
                                    currentErrorMessage.contains("身份证已绑定") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 解决方案：\n该身份证号已被其他账号使用。请确认：\n1. 身份证号是否填写正确\n2. 该长辈是否已被其他亲友注册\n3. 如需帮助，请联系已绑定的亲友",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    currentErrorMessage.contains("手机号已存在") || currentErrorMessage.contains("已注册") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 解决方案：\n该手机号已有账号。您可以：\n1. 改用「绑定长辈账号」功能\n2. 让长辈直接用该手机号登录",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    currentErrorMessage.contains("长辈模式") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 解决方案：\n您当前为长辈模式，无法帮他人注册。请切换为普通账号后再操作。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    currentErrorMessage.contains("格式") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 格式要求：\n• 手机号：11位数字，以1开头\n• 身份证号：18位，支持最后一位为X",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // ⭐ 调试日志
                        Log.d("ProfileScreen", "⚠️ 不显示错误提示卡片: currentErrorMessage=$currentErrorMessage")
                    }
                    
                    // ⭐ 新增：显示成功提示卡片（在对话框内）
                    if (currentSuccessMessage != null && currentSuccessMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "✅ 注册成功",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                Text(
                                    text = currentSuccessMessage,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Divider()
                                
                                Text(
                                    text = "📱 下一步操作：\n1. 告知长辈使用手机号 ${elderPhoneInput} 登录\n2. 长辈点击「获取验证码」接收短信\n3. 输入验证码后即可直接使用\n4. 长辈登录后自动进入长辈模式",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.registerElder()  // ⭐ 调用新接口
                    },
                    enabled = !isAddingElder
                ) {
                    if (isAddingElder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("确认注册")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddElderDialog = false }) {
                    Text("取消")
                }
            }
        )
        
        // ⭐ 修复：移除自动关闭对话框逻辑，让用户手动关闭
        // 不再监听 successMessage 自动关闭，用户可以查看成功提示后手动关闭
    }
    
    // ⭐ 新增：绑定已有长辈账号对话框（v2.0）⭐
    if (showBindElderDialog) {
        AlertDialog(
            onDismissRequest = { 
                showBindElderDialog = false
                viewModel.clearSuccess()  // ⭐ 清除成功消息
                viewModel.clearError()    // ⭐ 清除错误消息
            },
            title = { Text("绑定已有长辈账号") },
            text = {
                // ⭐ 修复：添加垂直滚动，确保错误/成功提示可见
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)  // ⭐ 设置最大高度，触发滚动
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "输入长辈信息，绑定已存在的账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // ⭐ 重要提示卡片
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "适用场景",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "• 长辈已被其他亲友注册过\n• 您想成为该长辈的守护者之一\n• 需要验证长辈身份（姓名+身份证）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    // ⭐ 长辈手机号（必填）
                    OutlinedTextField(
                        value = elderPhoneInput,
                        onValueChange = viewModel::updateElderPhoneInput,
                        label = { Text("长辈手机号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        placeholder = { Text("请输入长辈手机号") },
                        isError = currentErrorMessage?.contains("长辈手机号") == true,
                        supportingText = {
                            if (currentErrorMessage?.contains("长辈手机号") == true) {
                                Text(currentErrorMessage!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("该手机号必须是已注册的长辈账号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    
                    // ⭐ 长辈姓名（必填）
                    OutlinedTextField(
                        value = elderNameInput,
                        onValueChange = viewModel::updateElderNameInput,
                        label = { Text("长辈姓名 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入长辈真实姓名") },
                        isError = currentErrorMessage?.contains("长辈姓名") == true,
                        supportingText = {
                            if (currentErrorMessage?.contains("长辈姓名") == true) {
                                Text(currentErrorMessage!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    
                    // ⭐ 长辈身份证号（必填）
                    OutlinedTextField(
                        value = elderIdCardInput,
                        onValueChange = viewModel::updateElderIdCardInput,
                        label = { Text("长辈身份证号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入18位身份证号") },
                        isError = currentErrorMessage?.contains("身份证号") == true,
                        supportingText = {
                            if (currentErrorMessage?.contains("身份证号") == true) {
                                Text(currentErrorMessage!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("18位，支持最后一位为X", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    
                    Text(
                        text = "💡 一个长辈可以被多位亲友绑定守护",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // ⭐ 新增：通用错误提示卡片
                    if (currentErrorMessage != null && currentErrorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "绑定失败",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentErrorMessage,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                
                                // ⭐ 根据错误类型提供解决方案
                                when {
                                    currentErrorMessage.contains("未注册") || currentErrorMessage.contains("不存在") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 解决方案：\n该手机号尚未注册长辈账号。您可以：\n1. 改用「帮长辈注册账号」功能\n2. 让长辈先完成注册",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    currentErrorMessage.contains("不匹配") || currentErrorMessage.contains("不一致") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 解决方案：\n身份证号与账号信息不符。请确认：\n1. 姓名是否与注册时一致\n2. 身份证号是否正确\n3. 联系已绑定的亲友核实信息",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    currentErrorMessage.contains("已达上限") || currentErrorMessage.contains("4位") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 说明：\n该长辈已有4位亲友守护，达到上限。无法继续添加新的守护者。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    currentErrorMessage.contains("已绑定") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 说明：\n您已经绑定过该长辈账号，无需重复绑定。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    currentErrorMessage.contains("格式") -> {
                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = "💡 格式要求：\n• 手机号：11位数字，以1开头\n• 身份证号：18位，支持最后一位为X",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // ⭐ 新增：显示成功提示卡片
                    if (currentSuccessMessage != null && currentSuccessMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "✅ 绑定成功",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                Text(
                                    text = currentSuccessMessage,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Divider()
                                
                                Text(
                                    text = "✅ 您已成为该长辈的守护者\n📱 长辈端会自动看到您的守护\n🚗 您可以为该长辈代叫车辆",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // ⭐ 新增：手动刷新按钮
                                Button(
                                    onClick = {
                                        viewModel.loadElderInfoList()  // 刷新长辈列表
                                        Log.d("ProfileScreen", "✅ 用户点击刷新长辈列表")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "刷新长辈列表",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bindExistingElder()  // ⭐ 调用新接口
                    },
                    enabled = !isAddingElder
                ) {
                    if (isAddingElder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("确认绑定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBindElderDialog = false }) {
                    Text("取消")
                }
            }
        )
        
        // ⭐ 修复：移除自动关闭对话框逻辑，让用户手动关闭
        // 不再监听 successMessage 自动关闭，用户可以查看成功提示后手动关闭
    }

    if (showAvatarCropDialog && selectedImageUri != null) {
        AvatarCropDialog(
            imageUri = selectedImageUri!!,
            onDismissRequest = {
                showAvatarCropDialog = false
                selectedImageUri = null
            },
            onConfirmCrop = { croppedBitmap -> confirmAvatarCrop(croppedBitmap) }
        )
    }
    
    // ⭐ 新增：切换账号确认对话框
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { 
                Text(
                    if (currentProfile?.guardMode == 1) "退出登录" else "切换账号",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    if (currentProfile?.guardMode == 1) {
                        "确定要退出登录吗？\n退出后需要重新输入手机号和验证码登录。"
                    } else {
                        "确定要切换账号吗？\n当前账号将退出登录。"
                    }
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        // 调用 ViewModel 的 logout 方法
                        viewModel.logout {
                            // 成功后跳转到登录页
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确定退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // ⭐ 新增：解绑确认对话框
    if (showUnbindConfirmDialog && elderToUnbind != null) {
        UnbindConfirmDialog(
            elder = elderToUnbind!!,
            onDismissRequest = {
                showUnbindConfirmDialog = false
                elderToUnbind = null
            },
            onConfirm = {
                elderToUnbind?.let { elder ->
                    viewModel.unbindElder(
                        guardId = elder.guardId,
                        onSuccess = {
                            // ⭐ 解绑成功后不自动关闭对话框，显示成功提示让用户手动关闭
                            Log.d("ProfileScreen", "✅ 解绑成功")
                        },
                        onError = { error ->
                            Log.e("ProfileScreen", "❌ 解绑失败: $error")
                        }
                    )
                }
            },
            isLoading = isAddingElder
        )
    }
}

// 可折叠卡片组件
@Composable
fun ExpandableSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 标题栏
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandToggle() },
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (trailingContent != null) {
                        trailingContent()
                    }
                    Surface(
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // 内容区域
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            ) {
                content()
            }
        }
    }
}

// 添加紧急联系人对话框
@Composable
fun AddEmergencyContactDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String, String) -> Unit,
    name: String,
    phone: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String? = null,
    successMessage: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("添加紧急联系人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("姓名") },
                    singleLine = true,
                    isError = errorMessage?.contains("姓名") == true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("电话") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = errorMessage?.contains("电话") == true
                )
                if (errorMessage != null && (errorMessage.contains("姓名") || errorMessage.contains("电话"))) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = "❌ $errorMessage",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (successMessage != null && successMessage.contains("添加")) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = "✅ $successMessage",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, phone) },
                enabled = !isLoading && name.isNotBlank() && phone.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("添加")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        }
    )
}

// ⭐ 新增：解绑确认对话框
@Composable
fun UnbindConfirmDialog(
    elder: ElderInfo,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    isLoading: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("确认解绑") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ⭐ 隐私保护：隐藏手机号中间4位
                val maskedPhone = if (elder.phone.length >= 7) {
                    "${elder.phone.substring(0, 3)}****${elder.phone.substring(elder.phone.length - 4)}"
                } else {
                    elder.phone
                }
                
                Text(
                    text = "您确定要解绑长辈吗？",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "长辈姓名",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = elder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Divider()
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "手机号",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = maskedPhone,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                // ⭐ 警告提示
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "解绑后将无法为该长辈代叫车辆，且长辈端将不再显示您的守护信息。此操作可撤销，您可以重新绑定。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("解绑中...")
                } else {
                    Text("确认解绑")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isLoading
            ) {
                Text("取消")
            }
        }
    )
}

// 头像裁剪对话框
@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismissRequest: () -> Unit,
    onConfirmCrop: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropScale by remember { mutableStateOf(1f) }

    LaunchedEffect(imageUri) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            loadedBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        } catch (e: Exception) {
            Log.e("AvatarCropDialog", "Failed to load image", e)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("调整头像") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                loadedBitmap?.let { bitmap ->
                    Box(
                        modifier = Modifier.size(200.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * cropScale).toInt(),
                            (bitmap.height * cropScale).toInt(),
                            true
                        )
                        Image(
                            bitmap = scaledBitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.size(200.dp).clip(CircleShape)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("调整大小", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = cropScale,
                            onValueChange = { cropScale = it },
                            valueRange = 0.5f..3.0f,
                            steps = 25
                        )
                        Text(
                            text = "当前缩放: ${(cropScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } ?: CircularProgressIndicator()
                Text(
                    text = "拖动滑块调整头像大小，确认后系统将自动裁剪为圆形",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Log.d("AvatarCropDialog", "👆 用户点击了确认按钮")
                    loadedBitmap?.let { bitmap ->
                        Log.d("AvatarCropDialog", "开始裁剪，原始尺寸: ${bitmap.width}x${bitmap.height}, 缩放比例: $cropScale")
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * cropScale).toInt(),
                            (bitmap.height * cropScale).toInt(),
                            true
                        )
                        val centerX = (scaledBitmap.width - 200) / 2
                        val centerY = (scaledBitmap.height - 200) / 2
                        Log.d("AvatarCropDialog", "裁剪区域: (${maxOf(0, centerX)}, ${maxOf(0, centerY)}) 200x200")
                        val croppedBitmap = Bitmap.createBitmap(
                            scaledBitmap,
                            maxOf(0, centerX),
                            maxOf(0, centerY),
                            200,
                            200
                        )
                        Log.d("AvatarCropDialog", "✅ 裁剪完成，调用 onConfirmCrop")
                        onConfirmCrop(croppedBitmap)
                    } ?: run {
                        Log.e("AvatarCropDialog", "❌ loadedBitmap 为 null")
                    }
                },
                enabled = loadedBitmap != null
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        }
    )
}