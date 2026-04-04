package com.example.myapplication.presentation.order

import androidx.compose.foundation.layout.*
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
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    val order by viewModel.order.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadOrder(orderId)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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
                Text("订单详情", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("订单号：${order!!.orderNo}")
                Text("目的地：${order!!.getAddress() ?: order!!.poiAddress}")  // ⭐ 使用兼容方法
                Text("预估价格：${order!!.estimatedPrice}元")
                Text("状态：${getStatusText(order!!.status)}") // ⭐ 根据状态码显示文字
                Spacer(modifier = Modifier.height(16.dp))
                if (order!!.status == 0) { // ⭐ 修改：使用 Int 类型，0=pending
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
