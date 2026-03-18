package com.example.myapplication.presentation.chat

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.ChatMessage
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToOrder: (Long) -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val orderState by viewModel.orderState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }

    // 图片选择对话框显示状态
    var showImagePickerDialog by remember { mutableStateOf(false) }

    // 权限状态
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val mediaPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // 相机启动器（返回 Bitmap）
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            viewModel.sendImage(it) // 调用 ViewModel 发送图片
        }
    }

    // 相册启动器（返回 Uri）
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.sendImageFromUri(context, it) // 需要 ViewModel 实现此方法
        }
    }

    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) {
            audioPermissionState.launchPermissionRequest()
        }
    }

    // 监听订单创建状态，成功时跳转
    LaunchedEffect(orderState) {
        if (orderState is ChatViewModel.OrderState.Success) {
            val order = (orderState as ChatViewModel.OrderState.Success).order
            onNavigateToOrder(order.id)
            viewModel.resetOrderState()
        }
    }

    // 图片选择对话框
    if (showImagePickerDialog) {
        AlertDialog(
            onDismissRequest = { showImagePickerDialog = false },
            title = { Text("选择图片来源") },
            text = { Text("请选择拍照或从相册选取") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImagePickerDialog = false
                        if (cameraPermissionState.status.isGranted) {
                            takePictureLauncher.launch(null)
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                ) { Text("拍照") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImagePickerDialog = false
                        if (mediaPermissionState.status.isGranted) {
                            pickImageLauncher.launch("image/*")
                        } else {
                            mediaPermissionState.launchPermissionRequest()
                        }
                    }
                ) { Text("相册") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true,
            contentPadding = PaddingValues(8.dp)
        ) {
            items(messages.reversed()) { msg ->
                ChatBubble(
                    message = msg,
                    onCreateOrder = { destName -> viewModel.createOrder(destName) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") }
            )

            // 发送按钮
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
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
                        viewModel.startVoiceInput(context)
                    } else {
                        Toast.makeText(context, "需要录音权限", Toast.LENGTH_SHORT).show()
                        audioPermissionState.launchPermissionRequest()
                    }
                }
            ) {
                Icon(Icons.Default.Mic, contentDescription = "语音")
            }

            // 图片按钮（新增）
            IconButton(
                onClick = { showImagePickerDialog = true }
            ) {
                Icon(Icons.Default.Image, contentDescription = "图片")
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onCreateOrder: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Card {
            Text(
                text = message.content,
                modifier = Modifier.padding(8.dp)
            )
        }

        if (!message.isUser && !message.suggestions.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
            ) {
                message.suggestions.forEach { suggestion ->
                    Button(
                        onClick = { onCreateOrder(suggestion) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(suggestion)
                    }
                }
            }
        }
    }
}