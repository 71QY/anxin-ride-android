package com.example.myapplication.presentation.order

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// ⭐ 状态码转文字函数
fun getStatusText(status: Int): String {
    return when (status) {
        0 -> "待接单"
        1 -> "已接单"
        2 -> "进行中"
        3 -> "已完成"
        4 -> "已取消"
        else -> "未知状态 ($status)"
    }
}

@Composable
fun OrderDetailScreen(
    orderId: Long,
    viewModel: OrderDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}  // ⭐ 新增：返回键回调
) {
    val order by viewModel.order.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadOrder(orderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订单详情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("出错了：$errorMessage")
                    Button(onClick = { viewModel.loadOrder(orderId) }) {
                        Text("重试")
                    }
                }
            } else if (order != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("订单号：${order!!.orderNo}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    // ⭐ 修复：使用正确的字段名
                    Text("目的地：${order!!.getAddress() ?: "未知"}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("预估价格：${order!!.estimatePrice ?: 0.0}元", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("状态：${getStatusText(order!!.status)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    if (order!!.status == 0) {
                        Button(
                            onClick = { viewModel.cancelOrder(orderId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("取消订单")
                        }
                    }
                }
            }
        }
    }
}
