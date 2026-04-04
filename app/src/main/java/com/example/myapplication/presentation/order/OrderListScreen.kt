package com.example.myapplication.presentation.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.Order

@Composable
fun OrderListScreen(
    viewModel: OrderListViewModel = hiltViewModel(),
    onOrderClick: (Long) -> Unit
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),  // ✅ 使用内边距而不是外部 padding
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orders) { order ->
                OrderCard(
                    order = order,
                    onClick = { onOrderClick(order.id) }
                )
            }
            // 加载更多指示器（可选，如果需要上拉加载）
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        // 错误提示 Snackbar
        errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("重试")
                    }
                }
            ) {
                Text(message)
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "订单号：${order.orderNo}",
                style = MaterialTheme.typography.titleMedium
            )
            // ⭐ 修复：使用 poiName 或 destAddress，而不是不存在的 poiAddress
            Text("目的地：${order.poiName ?: order.destAddress ?: "未知"}")
            Text("状态：${getStatusText(order.status)}")
            Text("预估价格：${order.estimatePrice ?: 0.0}元")
        }
    }
}
