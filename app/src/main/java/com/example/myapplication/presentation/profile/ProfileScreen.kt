package com.example.myapplication.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    onNavigateToOrderList: () -> Unit = {}   // 新增参数，用于跳转到订单列表
) {
    val profile by viewModel.profile.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val isProfileLoading by viewModel.isProfileLoading.collectAsState()
    val isContactsLoading by viewModel.isContactsLoading.collectAsState()
    val isOperationLoading by viewModel.isOperationLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // 修改昵称对话框状态
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var tempNickname by remember { mutableStateOf("") }

    // 添加联系人对话框状态
    var showAddContactDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 个人资料卡片
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (isProfileLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            profile?.let {
                                Text("手机号：${it.phone ?: "未知"}")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("昵称：${it.nickname ?: "未设置"}")
                                    Button(
                                        onClick = {
                                            tempNickname = it.nickname ?: ""
                                            showEditNicknameDialog = true
                                        },
                                        enabled = !isOperationLoading
                                    ) {
                                        Text("修改")
                                    }
                                }
                            } ?: run {
                                Text("暂无用户信息")
                                Button(
                                    onClick = { viewModel.loadProfile() },
                                    enabled = !isOperationLoading
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 新增：查看订单按钮
                Button(
                    onClick = onNavigateToOrderList,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isOperationLoading
                ) {
                    Text("查看我的订单")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 紧急联系人标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "紧急联系人",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Button(
                        onClick = { showAddContactDialog = true },
                        enabled = !isOperationLoading
                    ) {
                        Text("添加")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 联系人列表
                if (isContactsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(contacts) { contact ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(contact.name ?: "")
                                    Text(contact.phone ?: "")
                                }
                            }
                        }
                    }
                }

                // 操作加载指示
                if (isOperationLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }

            // 错误提示
            errorMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(
                            onClick = {
                                viewModel.loadProfile()
                                viewModel.loadEmergencyContacts()
                            }
                        ) {
                            Text("重试")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }
    }

    // 修改昵称对话框
    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = tempNickname,
                    onValueChange = { tempNickname = it },
                    label = { Text("新昵称") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempNickname.isNotBlank()) {
                            viewModel.updateNickname(tempNickname)
                            showEditNicknameDialog = false
                        }
                    },
                    enabled = !isOperationLoading
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 添加联系人对话框
    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("添加紧急联系人") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text("姓名") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("电话") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newContactName.isNotBlank() && newContactPhone.isNotBlank()) {
                            viewModel.addEmergencyContact(newContactName, newContactPhone)
                            showAddContactDialog = false
                            newContactName = ""
                            newContactPhone = ""
                        }
                    },
                    enabled = !isOperationLoading
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}