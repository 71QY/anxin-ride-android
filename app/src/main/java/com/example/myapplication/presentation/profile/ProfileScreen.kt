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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.asImageBitmap
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
    
    // ⭐ 新增：控制各个折叠区域的状态
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                            val avatarUrl = if (!avatarValue.isNullOrBlank()) {
                                if (avatarValue.startsWith("http")) avatarValue else "http://10.224.165.80:8080$avatarValue"
                            } else null

                            if (avatarUrl != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(avatarUrl)
                                            .crossfade(true)
                                            .build()
                                    ),
                                    contentDescription = "头像",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
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

            item {
                Button(
                    onClick = onNavigateToOrderList,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isOperationLoading
                ) {
                    Text("查看我的订单")
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ⭐ 标题栏（可点击）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNicknameSection = !showNicknameSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "修改昵称",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = if (showNicknameSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showNicknameSection) "收起" else "展开"
                            )
                        }
                        
                        // ⭐ 折叠内容
                        androidx.compose.animation.AnimatedVisibility(
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
                                    singleLine = true
                                )
                                Button(
                                    onClick = { viewModel.changeNickname() },
                                    enabled = !isOperationLoading,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    if (isOperationLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text("修改")
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ⭐ 标题栏（可点击）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPasswordSection = !showPasswordSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "修改密码",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = if (showPasswordSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showPasswordSection) "收起" else "展开"
                            )
                        }
                        
                        // ⭐ 折叠内容
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showPasswordSection,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = codeInput,
                                        onValueChange = viewModel::updateCodeInput,
                                        label = { Text("验证码") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = { viewModel.sendCodeForPassword() },
                                        enabled = !(isOperationLoading || isSendingCode),
                                        modifier = Modifier.size(width = 120.dp, height = 56.dp)
                                    ) {
                                        if (isSendingCode) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text("获取验证码")
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = newPasswordInput,
                                    onValueChange = viewModel::updateNewPasswordInput,
                                    label = { Text("新密码") },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true
                                )
                                Text(
                                    text = "密码要求：10 位数，必须包含至少一个英文字符",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.changePassword() },
                                    enabled = !isOperationLoading &&
                                            newPasswordInput.isNotBlank() &&
                                            newPasswordInput.length == 10 &&
                                            newPasswordInput.any { it.isLetter() } &&
                                            codeInput.isNotBlank(),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    if (isOperationLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text("修改密码")
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ⭐ 标题栏（可点击）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRealNameSection = !showRealNameSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "实名认证",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = if (showRealNameSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showRealNameSection) "收起" else "展开"
                            )
                        }
                        
                        // ⭐ 折叠内容
                        androidx.compose.animation.AnimatedVisibility(
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
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = idCardInput,
                                    onValueChange = viewModel::updateIdCardInput,
                                    label = { Text("身份证号") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Text(
                                    text = "测试账号：张三 110101199001011234",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ⭐ 标题栏（可点击）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showContactsSection = !showContactsSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "紧急联系人",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Icon(
                                imageVector = if (showContactsSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showContactsSection) "收起" else "展开"
                            )
                        }
                        
                        // ⭐ 折叠内容
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showContactsSection,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("紧急联系人", style = MaterialTheme.typography.titleMedium)
                                    Button(
                                        onClick = { showAddContactDialog = true },
                                        enabled = !isOperationLoading
                                    ) {
                                        Text("添加")
                                    }
                                }

                                if (isContactsLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else if (contacts.isNotEmpty()) {
                                    contacts.forEach { contact ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${contact.name} - ${contact.phone}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            IconButton(onClick = { viewModel.deleteEmergencyContact(contact.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
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
                errorMessage?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                successMessage?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
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
                            showAddContactDialog = false
                        },
                        name = contactNameInput,
                        phone = contactPhoneInput,
                        onNameChange = viewModel::updateContactNameInput,
                        onPhoneChange = viewModel::updateContactPhoneInput,
                        isLoading = isOperationLoading
                    )
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
    isLoading: Boolean
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
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("电话") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
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
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                    Image(
                        bitmap = scaledBitmap.asImageBitmap(),
                        contentDescription = "头像预览",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                    )
                } ?: CircularProgressIndicator()

                Text(
                    text = "系统将自动为您调整头像大小至 200x200 像素",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { loadedBitmap?.let { onConfirmCrop(Bitmap.createScaledBitmap(it, 200, 200, true)) } },
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