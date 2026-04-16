package com.example.myapplication.presentation.guardian

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.model.ElderInfo
import com.example.myapplication.presentation.profile.ProfileViewModel

/**
 * 亲情守护管理页面
 * 独立页面,展示和管理所有绑定的长辈
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianManagementScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onBindElder: () -> Unit = {}  // 跳转到绑定长辈对话框或页面
) {
    val elderList by viewModel.elderInfoList.collectAsState()
    val isLoading by viewModel.isOperationLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    
    // ⭐ 新增：输入框状态
    val elderPhoneInput by viewModel.elderPhoneInput.collectAsState()
    val elderNameInput by viewModel.elderNameInput.collectAsState()
    val elderIdCardInput by viewModel.elderIdCardInput.collectAsState()
    val relationshipInput by viewModel.relationshipInput.collectAsState()
    val elderNicknameInput by viewModel.elderNicknameInput.collectAsState()
    val elderPasswordInput by viewModel.elderPasswordInput.collectAsState()
    val elderConfirmPasswordInput by viewModel.elderConfirmPasswordInput.collectAsState()
    val isAddingElder by viewModel.isAddingElder.collectAsState()
    
    var showUnbindDialog by remember { mutableStateOf(false) }
    var elderToUnbind by remember { mutableStateOf<ElderInfo?>(null) }
    
    // ⭐ 新增：绑定对话框状态
    var showBindDialog by remember { mutableStateOf(false) }
    var bindMode by remember { mutableStateOf("register") }  // "register" 或 "bind"
    
    // ⭐ 修复Bug 1&2：添加缺失的对话框状态变量
    var showRegisterDialog by remember { mutableStateOf(false) }
    var showBindExistingDialog by remember { mutableStateOf(false) }
    
    // 加载长辈列表
    LaunchedEffect(Unit) {
        Log.d("GuardianManagement", "=== 进入亲情守护管理页面 ===")
        viewModel.loadElderInfoList()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("亲情守护") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 刷新按钮
                    IconButton(onClick = { 
                        viewModel.loadElderInfoList()
                        Log.d("GuardianManagement", "🔄 手动刷新长辈列表")
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 统计卡片
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
                            Icons.Default.FamilyRestroom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "我的长辈",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Text(
                        text = "已绑定 ${elderList.size} 位长辈",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    

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
                
                // 3秒后自动清除成功消息
                LaunchedEffect(successMessage) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.clearSuccess()
                }
            }
            
            // 长辈列表
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (elderList.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.FamilyRestroom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "暂无绑定的长辈",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击下方按钮绑定长辈账号",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(elderList) { elder ->
                        ElderCard(
                            elder = elder,
                            onUnbind = {
                                elderToUnbind = elder
                                showUnbindDialog = true
                            }
                        )
                    }
                    
                    // 底部添加按钮
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { showBindDialog = true },  // ⭐ 显示绑定对话框
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("绑定新长辈")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    
    // 解绑确认对话框
    if (showUnbindDialog && elderToUnbind != null) {
        AlertDialog(
            onDismissRequest = {
                showUnbindDialog = false
                elderToUnbind = null
            },
            title = { Text("确认解绑") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("确定要解绑以下长辈吗？")
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("姓名：${elderToUnbind?.name ?: "未知"}")
                            Text("手机号：${elderToUnbind?.phone ?: "未知"}")
                        }
                    }
                    Text(
                        text = "解绑后，您将无法再为该长辈代叫车辆。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        elderToUnbind?.let { elder ->
                            viewModel.unbindElder(
                                guardId = elder.guardId,
                                onSuccess = {
                                    showUnbindDialog = false
                                    elderToUnbind = null
                                    viewModel.loadElderInfoList()  // 刷新列表
                                },
                                onError = { error ->
                                    Log.e("GuardianManagement", "❌ 解绑失败: $error")
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认解绑")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnbindDialog = false
                    elderToUnbind = null
                }) {
                    Text("取消")
                }
            }
        )
    }
    
    // ⭐ 新增：绑定长辈选择对话框
    if (showBindDialog) {
        AlertDialog(
            onDismissRequest = { showBindDialog = false },
            title = { Text("选择绑定方式") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "请选择一种绑定方式：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    // 选项1：帮长辈注册账号
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                bindMode = "register"
                                showBindDialog = false
                                showRegisterDialog = true  // ⭐ 修复Bug 3：显示注册对话框
                            },
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
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "帮长辈注册账号",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "适用于长辈还没有账号的情况，填写信息后自动创建新账号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 选项2：绑定已有账号
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                bindMode = "bind"
                                showBindDialog = false
                                showBindExistingDialog = true  // ⭐ 修复Bug 4：显示绑定对话框
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
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
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "绑定已有账号",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "适用于长辈已有账号的情况，输入手机号、姓名、身份证号进行绑定",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBindDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // ⭐ 新增：帮长辈注册账号对话框
    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRegisterDialog = false
                viewModel.clearSuccess()
                viewModel.clearError()
            },
            title = { Text("帮长辈注册账号") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "填写长辈信息，系统将自动创建账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 长辈姓名
                    OutlinedTextField(
                        value = elderNameInput,
                        onValueChange = viewModel::updateElderNameInput,
                        label = { Text("长辈姓名 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入长辈真实姓名") }
                    )
                    
                    // 长辈身份证号
                    OutlinedTextField(
                        value = elderIdCardInput,
                        onValueChange = viewModel::updateElderIdCardInput,
                        label = { Text("长辈身份证号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入18位身份证号") },
                        supportingText = {
                            Text("18位，支持最后一位为X", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                    
                    // 长辈手机号
                    OutlinedTextField(
                        value = elderPhoneInput,
                        onValueChange = viewModel::updateElderPhoneInput,
                        label = { Text("长辈手机号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        placeholder = { Text("请输入长辈手机号") },
                        supportingText = {
                            Text("该手机号将作为长辈的登录账号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                    
                    // ⭐ 新增：设置登录密码
                    OutlinedTextField(
                        value = elderPasswordInput,
                        onValueChange = viewModel::updateElderPasswordInput,
                        label = { Text("设置登录密码 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        placeholder = { Text("请设置10位密码，包含字母和特殊符号") },
                        supportingText = {
                            Text("密码要求：10位，必须包含字母和特殊符号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                    
                    // ⭐ 新增：确认密码
                    OutlinedTextField(
                        value = elderConfirmPasswordInput,
                        onValueChange = viewModel::updateElderConfirmPasswordInput,
                        label = { Text("确认密码 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        placeholder = { Text("请再次输入密码") }
                    )
                    
                    // 与长辈关系
                    OutlinedTextField(
                        value = relationshipInput,
                        onValueChange = viewModel::updateRelationshipInput,
                        label = { Text("与长辈关系") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如：子女、亲属（选填）") }
                    )
                    
                    // 错误提示
                    errorMessage?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    
                    // 成功提示
                    successMessage?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.registerElder()
                    },
                    enabled = !isAddingElder
                ) {
                    if (isAddingElder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isAddingElder) "注册中..." else "确认注册")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) {
                    Text("取消")
                }
            }
        )
        
        // 监听成功消息，自动关闭对话框
        LaunchedEffect(successMessage) {
            // ⭐ 修复Bug 7：更精确的成功判断逻辑
            if (successMessage != null && (successMessage!!.contains("成功") || successMessage!!.contains("注册"))) {
                kotlinx.coroutines.delay(1500)  // 延迟1.5秒让用户看到成功提示
                showRegisterDialog = false
                viewModel.loadElderInfoList()  // 刷新列表
            }
        }
    }
    
    // ⭐ 新增：绑定已有长辈账号对话框
    if (showBindExistingDialog) {
        AlertDialog(
            onDismissRequest = { 
                showBindExistingDialog = false
                viewModel.clearSuccess()
                viewModel.clearError()
            },
            title = { Text("绑定已有长辈账号") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "输入长辈信息，绑定已存在的账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 长辈手机号
                    OutlinedTextField(
                        value = elderPhoneInput,
                        onValueChange = viewModel::updateElderPhoneInput,
                        label = { Text("长辈手机号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        placeholder = { Text("请输入长辈手机号") },
                        supportingText = {
                            Text("该手机号必须是已注册的长辈账号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                    
                    // 长辈姓名
                    OutlinedTextField(
                        value = elderNameInput,
                        onValueChange = viewModel::updateElderNameInput,
                        label = { Text("长辈姓名 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入长辈真实姓名") }
                    )
                    
                    // 长辈身份证号
                    OutlinedTextField(
                        value = elderIdCardInput,
                        onValueChange = viewModel::updateElderIdCardInput,
                        label = { Text("长辈身份证号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入18位身份证号") },
                        supportingText = {
                            Text("18位，支持最后一位为X", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                    
                    // 错误提示
                    errorMessage?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    
                    // 成功提示
                    successMessage?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bindExistingElder()
                    },
                    enabled = !isAddingElder
                ) {
                    if (isAddingElder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isAddingElder) "绑定中..." else "确认绑定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBindExistingDialog = false }) {
                    Text("取消")
                }
            }
        )
        
        // 监听成功消息，自动关闭对话框
        LaunchedEffect(successMessage) {
            // ⭐ 修复Bug 8：更精确的成功判断逻辑
            if (successMessage != null && (successMessage!!.contains("成功") || successMessage!!.contains("绑定"))) {
                kotlinx.coroutines.delay(1500)  // 延迟1.5秒让用户看到成功提示
                showBindExistingDialog = false
                viewModel.loadElderInfoList()  // 刷新列表
            }
        }
    }
}

/**
 * 长辈信息卡片
 */
@Composable
private fun ElderCard(
    elder: ElderInfo,
    onUnbind: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 头部：姓名和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    Column {
                        Text(
                            text = elder.name ?: "未知",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = elder.phone ?: "未知手机号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 状态标签
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (elder.status == 1) "✅ 已激活" else "⏳ 待激活",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (elder.status == 1) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        }
                    )
                )
            }
            
            Divider()
            
            // 详细信息
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow(label = "用户ID", value = elder.userId?.toString() ?: "未知")
                InfoRow(label = "绑定关系ID", value = elder.guardId.toString())
                if (!elder.relationship.isNullOrBlank()) {
                    InfoRow(label = "与长辈关系", value = elder.relationship)
                }
                InfoRow(label = "绑定时间", value = formatBindTime(elder.bindTime))
                InfoRow(label = "账号状态", value = if (elder.status == 1) "✅ 已激活" else "⏳ 待激活")
            }
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onUnbind,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("解绑")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatBindTime(bindTime: String?): String {
    return if (bindTime.isNullOrBlank()) {
        "未知"
    } else {
        try {
            // 假设后端返回 ISO 8601 格式：2024-01-15T10:30:00
            val parts = bindTime.split("T")
            if (parts.size >= 2) {
                val date = parts[0]  // 2024-01-15
                val time = parts[1].substring(0, 5)  // 10:30
                "$date $time"
            } else {
                bindTime
            }
        } catch (e: Exception) {
            bindTime
        }
    }
}
