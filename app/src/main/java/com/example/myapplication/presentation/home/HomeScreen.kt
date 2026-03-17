package com.example.myapplication.presentation.home

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.core.utils.SpeechRecognizerHelper
import com.example.myapplication.map.MapViewComposable
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToOrder: (Long) -> Unit
) {
    val context = LocalContext.current
    val destination by viewModel.destination.collectAsState()
    val orderState by viewModel.orderState.collectAsState()

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) {
            audioPermissionState.launchPermissionRequest()
        }
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            MapViewComposable { aMap ->
                // 可配置定位
            }
        }

        OutlinedTextField(
            value = destination,
            onValueChange = { viewModel.updateDestination(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            label = { Text("点击麦克风说出目的地") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (audioPermissionState.status.isGranted) {
                            val helper = SpeechRecognizerHelper(context) { result ->
                                viewModel.updateDestination(result)
                            }
                            helper.startListening()
                        } else {
                            Toast.makeText(context, "需要录音权限", Toast.LENGTH_SHORT).show()
                            audioPermissionState.launchPermissionRequest()
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "语音输入"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.createOrder(destination, 39.9087, 116.3975)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            enabled = destination.isNotBlank()
        ) {
            Text("一键叫车", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = orderState) {
            is HomeViewModel.OrderState.Idle -> {}
            is HomeViewModel.OrderState.Loading -> {
                CircularProgressIndicator()
                Text("正在创建订单...")
            }
            is HomeViewModel.OrderState.Success -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("订单创建成功", fontSize = 20.sp)
                        Text("订单号：${state.order.orderNo}")
                        Text("预估价格：${state.order.estimatePrice}元")
                        Button(
                            onClick = { onNavigateToOrder(state.order.id) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("查看详情")
                        }
                    }
                }
            }
            is HomeViewModel.OrderState.Error -> {
                Text("错误：${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}