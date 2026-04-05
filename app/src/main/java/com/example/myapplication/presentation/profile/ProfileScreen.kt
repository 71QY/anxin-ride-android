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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToOrderList: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val isProfileLoading by viewModel.isProfileLoading.collectAsStateWithLifecycle()
    val isContactsLoading by viewModel.isContactsLoading.collectAsStateWithLifecycle()
    val isOperationLoading by viewModel.isOperationLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()

    val nicknameInput by viewModel.nicknameInput.collectAsStateWithLifecycle()
    val codeInput by viewModel.codeInput.collectAsStateWithLifecycle()
    val newPasswordInput by viewModel.newPasswordInput.collectAsStateWithLifecycle()
    val isSendingCode by viewModel.isSendingCode.collectAsStateWithLifecycle()
    val realNameInput by viewModel.realNameInput.collectAsStateWithLifecycle()
    val idCardInput by viewModel.idCardInput.collectAsStateWithLifecycle()
    val contactNameInput by viewModel.contactNameInput.collectAsStateWithLifecycle()
    val contactPhoneInput by viewModel.contactPhoneInput.collectAsStateWithLifecycle()

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAvatarCropDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // 控制各个折叠区域的状态
    var showNicknameSection by remember { mutableStateOf(false) }
    var showPasswordSection by remember { mutableStateOf(false) }
    var showRealNameSection by remember { mutableStateOf(false) }
    var showContactsSection by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun confirmAvatarCrop(croppedBitmap: Bitmap) {
        selectedImageUri?.let { uri ->
            try {
                val file = File(context.cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.close()
                viewModel.uploadAvatar(uri, file)
            } catch (e: Exception) {
                Log.e("ProfileScreen", "❌ 保存裁剪图片失败", e)
            }
        }
        showAvatarCropDialog = false
        selectedImageUri = null
    }

    LaunchedEffect(Unit) {
        Log.d("ProfileScreen", "=== ProfileScreen 首次加载 ===")
        viewModel.loadProfile()
        viewModel.loadEmergencyContacts()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("ProfileScreen", "图片选择成功：$it")
            selectedImageUri = it
            showAvatarCropDialog = true
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                imagePickerLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                imagePickerLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要存储权限") },
            text = { Text("需要访问相册才能选择头像，请在设置中允许访问相册") },
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
                title = { Text("个人中心") },
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
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (profile == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠️ 未加载到用户信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "可能原因:\n1. 尚未登录\n2. Token 未准备好\n3. 网络请求失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = {
                                    viewModel.loadProfile()
                                    viewModel.loadEmergencyContacts()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("重新加载")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .clickable { pickImage() },
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarValue = profile?.avatar
                                Log.d("ProfileScreen", "头像值: $avatarValue")
                                val avatarUrl = if (!avatarValue.isNullOrBlank()) {
                                    val cleanUrl = avatarValue.trim()
                                    when {
                                        cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> cleanUrl
                                        cleanUrl.startsWith("/") -> "http://10.224.165.80:8080$cleanUrl"
                                        else -> "http://10.224.165.80:8080/$cleanUrl"
                                    }
                                } else null
                                Log.d("ProfileScreen", "头像URL: $avatarUrl")

                                if (avatarUrl != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context)
                                                .data(avatarUrl)
                                                .crossfade(true)
                                                .size(200, 200)
                                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                .build()
                                        ),
                                        contentDescription = "头像",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "默认头像",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "点击更换头像",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (errorMessage?.contains("头像") == true || errorMessage?.contains("上传") == true) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = "❌ $errorMessage",
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            if (successMessage?.contains("头像") == true) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Text(
                                        text = "✅ $successMessage",
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            profile?.nickname?.let { nickname ->
                                Text(
                                    text = "昵称：$nickname",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Text(
                                text = profile?.phone ?: "未登录",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val verified = profile?.verified ?: 0
                                Icon(
                                    imageVector = if (verified == 1) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (verified == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (verified == 1) "已实名认证" else "未实名认证",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (verified == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNicknameSection = !showNicknameSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "修改昵称",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                imageVector = if (showNicknameSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showNicknameSection) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showNicknameSection,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = nicknameInput,
                                    onValueChange = viewModel::updateNicknameInput,
                                    label = { Text("昵称") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("请输入新昵称") },
                                    trailingIcon = {
                                        if (nicknameInput.isNotBlank()) {
                                            IconButton(onClick = { viewModel.updateNicknameInput("") }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "清空",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    isError = errorMessage?.contains("昵称") == true
                                )

                                if (errorMessage?.contains("昵称") == true) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Text(
                                            text = "❌ $errorMessage",
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                if (successMessage?.contains("昵称") == true) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = "✅ $successMessage",
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { viewModel.changeNickname() },
                                        enabled = !isOperationLoading && nicknameInput.isNotBlank()
                                    ) {
                                        if (isOperationLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text("修改")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPasswordSection = !showPasswordSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "修改密码",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                imageVector = if (showPasswordSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showPasswordSection) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showPasswordSection,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = codeInput,
                                    onValueChange = viewModel::updateCodeInput,
                                    label = { Text("验证码") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    trailingIcon = {
                                        Button(
                                            onClick = { viewModel.sendCodeForPassword() },
                                            enabled = !(isOperationLoading || isSendingCode),
                                            modifier = Modifier.size(width = 100.dp, height = 36.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            if (isSendingCode) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text(
                                                    "获取",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    },
                                    isError = errorMessage?.contains("验证码") == true
                                )

                                if (errorMessage?.contains("验证码") == true) {
                                    Text(
                                        text = "❌ $errorMessage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }

                                if (successMessage?.contains("验证码") == true) {
                                    Text(
                                        text = "✅ $successMessage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = newPasswordInput,
                                    onValueChange = viewModel::updateNewPasswordInput,
                                    label = { Text("新密码") },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    isError = errorMessage?.contains("密码") == true
                                )

                                Text(
                                    text = "💡 密码要求：至少 10 位，必须包含字母和特殊符号",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (errorMessage?.contains("密码") == true)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp)
                                )

                                if (errorMessage?.contains("密码") == true && !errorMessage!!.contains("验证码")) {
                                    Text(
                                        text = "❌ $errorMessage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }

                                if (successMessage?.contains("密码") == true) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = "✅ $successMessage",
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { viewModel.changePassword() },
                                        enabled = !isOperationLoading && codeInput.isNotBlank() && newPasswordInput.isNotBlank()
                                    ) {
                                        if (isOperationLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text("修改密码")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRealNameSection = !showRealNameSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "实名认证",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                imageVector = if (showRealNameSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showRealNameSection) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showRealNameSection,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = realNameInput,
                                    onValueChange = viewModel::updateRealNameInput,
                                    label = { Text("姓名") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    isError = errorMessage?.contains("姓名") == true || errorMessage?.contains("身份证") == true
                                )
                                OutlinedTextField(
                                    value = idCardInput,
                                    onValueChange = viewModel::updateIdCardInput,
                                    label = { Text("身份证号") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    isError = errorMessage?.contains("身份证") == true
                                )
                                Text(
                                    text = "测试账号：张三 110101199001011234",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (errorMessage?.contains("实名") == true || errorMessage?.contains("身份证") == true || errorMessage?.contains("姓名") == true) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Text(
                                            text = "❌ $errorMessage",
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                if (successMessage?.contains("实名") == true || successMessage?.contains("认证") == true) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = "✅ $successMessage",
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Button(
                                    onClick = viewModel::submitRealNameAuth,
                                    enabled = !isOperationLoading && realNameInput.isNotBlank() && idCardInput.isNotBlank(),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    if (isOperationLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text("提交认证")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showContactsSection = !showContactsSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContactPhone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "紧急联系人",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { showAddContactDialog = true },
                                    enabled = !isOperationLoading
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "添加",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("添加")
                                }
                                Icon(
                                    imageVector = if (showContactsSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showContactsSection) "收起" else "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showContactsSection,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isContactsLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else if (contacts.isNotEmpty()) {
                                    contacts.forEach { contact ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color.White
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = contact.name,
                                                            style = MaterialTheme.typography.bodyLarge,
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
                                                    enabled = !isOperationLoading
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "删除",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "暂无紧急联系人",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
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
                        errorMessage = if (errorMessage?.contains("联系人") == true || errorMessage?.contains("姓名") == true || errorMessage?.contains("电话") == true) errorMessage else null,
                        successMessage = if (successMessage?.contains("联系人") == true || successMessage?.contains("添加") == true) successMessage else null
                    )

                    LaunchedEffect(successMessage) {
                        if (successMessage?.contains("联系人") == true || successMessage?.contains("添加") == true) {
                            showAddContactDialog = false
                        }
                    }
                }
            }

            item {
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
            }
        }
    }
}

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
                    isError = errorMessage?.contains("联系人") == true || errorMessage?.contains("姓名") == true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("电话") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = errorMessage?.contains("电话") == true || errorMessage?.contains("手机") == true
                )

                if (errorMessage != null && (errorMessage.contains("联系人") || errorMessage.contains("姓名") || errorMessage.contains("电话") || errorMessage.contains("手机"))) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "❌ $errorMessage",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (successMessage != null && (successMessage.contains("联系人") || successMessage.contains("添加"))) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
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

@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismissRequest: () -> Unit,
    onConfirmCrop: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropScale by remember { mutableStateOf(1f) }
    var cropOffsetX by remember { mutableStateOf(0f) }
    var cropOffsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(imageUri) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            loadedBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        } catch (e: Exception) {
            Log.e("AvatarCropDialog", "❌ 加载图片失败", e)
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
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape),
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
                            contentDescription = "头像预览",
                            modifier = Modifier
                                .size(200.dp)
                                .clip(CircleShape)
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
                    loadedBitmap?.let { bitmap ->
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * cropScale).toInt(),
                            (bitmap.height * cropScale).toInt(),
                            true
                        )
                        val centerX = (scaledBitmap.width - 200) / 2
                        val centerY = (scaledBitmap.height - 200) / 2
                        val croppedBitmap = Bitmap.createBitmap(
                            scaledBitmap,
                            maxOf(0, centerX),
                            maxOf(0, centerY),
                            200,
                            200
                        )
                        onConfirmCrop(croppedBitmap)
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