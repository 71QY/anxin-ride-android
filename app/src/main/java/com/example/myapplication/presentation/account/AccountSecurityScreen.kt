package com.example.myapplication.presentation.account

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.presentation.profile.ProfileViewModel

/**
 * 账号安全管理页面
 * 包含：修改昵称、修改密码、实名认证等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val profile by viewModel.profile.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val isLoading by viewModel.isOperationLoading.collectAsState()
    
    // 修改昵称相关状态
    var showChangeNicknameDialog by remember { mutableStateOf(false) }
    var newNickname by remember { mutableStateOf("") }
    
    // 修改密码相关状态
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var passwordStep by remember { mutableStateOf(1) }  // ⭐ 1-验证码步骤, 2-新密码步骤
    var verifyCode by remember { mutableStateOf("") }
    var isSendingCode by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    // 实名认证相关状态
    var showRealNameDialog by remember { mutableStateOf(false) }
    var realName by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号安全") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 用户信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "账号信息",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Divider()
                    
                    InfoItem(label = "手机号", value = profile?.phone ?: "未设置")
                    InfoItem(label = "昵称", value = profile?.nickname ?: "未设置")
                    InfoItem(label = "实名状态", value = if (profile?.verified == 1) "已认证" else "未认证")
                }
            }
            
            // 错误提示
            errorMessage?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                LaunchedEffect(errorMessage) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.clearError()
                }
            }
            
            // 成功提示
            successMessage?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
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
                            text = it,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                LaunchedEffect(successMessage) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.clearSuccess()
                }
            }
            
            // 功能列表
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 修改昵称
                SettingItem(
                    icon = Icons.Default.Person,
                    title = "修改昵称",
                    subtitle = "当前：${profile?.nickname ?: "未设置"}",
                    onClick = { showChangeNicknameDialog = true }
                )
                
                // 修改密码
                SettingItem(
                    icon = Icons.Default.Lock,
                    title = "修改密码",
                    subtitle = "定期修改密码可提高账号安全性",
                    onClick = { showChangePasswordDialog = true }
                )
                
                // 实名认证
                SettingItem(
                    icon = Icons.Default.VerifiedUser,
                    title = "实名认证",
                    subtitle = if (profile?.verified == 1) "已完成认证" else "完成实名认证以使用更多功能",
                    trailingContent = {
                        if (profile?.verified == 1) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    onClick = { 
                        if (profile?.verified != 1) {
                            showRealNameDialog = true
                        }
                    }
                )
            }
        }
    }
    
    // 修改昵称对话框
    if (showChangeNicknameDialog) {
        AlertDialog(
            onDismissRequest = { 
                showChangeNicknameDialog = false
                newNickname = ""
                viewModel.clearError()
            },
            title = { Text("修改昵称") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newNickname,
                        onValueChange = { newNickname = it },
                        label = { Text("新昵称") },
                        placeholder = { Text("请输入新昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "昵称长度：1-20个字符",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNickname.isBlank()) {
                            viewModel.setErrorMessage("昵称不能为空")
                            return@Button
                        }
                        if (newNickname.length > 20) {
                            viewModel.setErrorMessage("昵称不能超过20个字符")
                            return@Button
                        }
                        viewModel.changeNickname(newNickname)
                        showChangeNicknameDialog = false
                        newNickname = ""
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangeNicknameDialog = false
                    newNickname = ""
                    viewModel.clearError()
                }) {
                    Text("取消")
                }
            }
        )
    }
    
    // ⭐ 修改密码对话框（两步流程：1.验证码 2.新密码）
    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showChangePasswordDialog = false
                passwordStep = 1
                verifyCode = ""
                newPassword = ""
                confirmPassword = ""
                countdown = 0
                viewModel.clearError()
            },
            title = { 
                Text(if (passwordStep == 1) "步骤 1/2：验证身份" else "步骤 2/2：设置新密码") 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (passwordStep == 1) {
                        // ⭐ 第一步：发送验证码
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "验证说明",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "我们将向您的手机号 ${profile?.phone ?: "未知"} 发送验证码，请输入验证码以继续。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        OutlinedTextField(
                            value = verifyCode,
                            onValueChange = { verifyCode = it },
                            label = { Text("验证码") },
                            placeholder = { Text("请输入6位验证码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorMessage?.contains("验证码") == true,
                            supportingText = {
                                if (errorMessage?.contains("验证码") == true) {
                                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        
                        // 发送验证码按钮
                        Button(
                            onClick = {
                                viewModel.sendVerifyCodeForPassword()
                                countdown = 60
                            },
                            enabled = !isSendingCode && countdown == 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (countdown > 0) "${countdown}秒后重试" else "发送验证码")
                        }
                        
                        // 倒计时逻辑
                        if (countdown > 0) {
                            LaunchedEffect(countdown) {
                                kotlinx.coroutines.delay(1000)
                                countdown--
                            }
                        }
                    } else {
                        // ⭐ 第二步：输入新密码
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "验证成功",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "请设置新密码，密码必须满足以下要求：\n• 必须是 10 位\n• 包含字母\n• 包含特殊符号",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("新密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("请输入10位新密码") },
                            isError = errorMessage?.contains("密码") == true,
                            supportingText = {
                                if (errorMessage?.contains("密码") == true) {
                                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("必须是10位，且包含字母和特殊符号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        )
                        
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("确认新密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("请再次输入新密码") },
                            isError = errorMessage?.contains("不一致") == true,
                            supportingText = {
                                if (errorMessage?.contains("不一致") == true) {
                                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordStep == 1) {
                            // ⭐ 第一步：验证验证码
                            if (verifyCode.isBlank()) {
                                viewModel.setErrorMessage("请输入验证码")
                                return@Button
                            }
                            if (verifyCode.length != 6) {
                                viewModel.setErrorMessage("验证码必须是6位")
                                return@Button
                            }
                            // TODO: 调用后端验证验证码接口
                            // 暂时直接跳到下一步
                            passwordStep = 2
                            viewModel.clearError()
                        } else {
                            // ⭐ 第二步：修改密码
                            if (newPassword.isBlank() || confirmPassword.isBlank()) {
                                viewModel.setErrorMessage("所有字段都不能为空")
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                viewModel.setErrorMessage("两次输入的密码不一致")
                                return@Button
                            }
                            // TODO: 调用后端修改密码接口，传入验证码和新密码
                            viewModel.changePasswordWithCode(verifyCode, newPassword)
                            showChangePasswordDialog = false
                            passwordStep = 1
                            verifyCode = ""
                            newPassword = ""
                            confirmPassword = ""
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (passwordStep == 1) "下一步" else "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangePasswordDialog = false
                    passwordStep = 1
                    verifyCode = ""
                    newPassword = ""
                    confirmPassword = ""
                    countdown = 0
                    viewModel.clearError()
                }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 实名认证对话框
    if (showRealNameDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRealNameDialog = false
                realName = ""
                idCard = ""
                viewModel.clearError()
            },
            title = { Text("实名认证") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = realName,
                        onValueChange = { realName = it },
                        label = { Text("真实姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = idCard,
                        onValueChange = { idCard = it },
                        label = { Text("身份证号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("18位，支持最后一位为X")
                        }
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "💡 实名认证后，您可以使用更多功能，如亲情守护、代叫车辆等。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (realName.isBlank() || idCard.isBlank()) {
                            viewModel.setErrorMessage("所有字段都不能为空")
                            return@Button
                        }
                        viewModel.realNameAuth(realName, idCard)
                        showRealNameDialog = false
                        realName = ""
                        idCard = ""
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("提交认证")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRealNameDialog = false
                    realName = ""
                    idCard = ""
                    viewModel.clearError()
                }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick
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
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
