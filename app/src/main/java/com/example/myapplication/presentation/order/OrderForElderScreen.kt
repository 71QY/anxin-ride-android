package com.example.myapplication.presentation.order

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.ElderInfo

/**
 * 代叫车界面（亲友操作）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderForElderScreen(
    viewModel: OrderForElderViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onOrderSuccess: (Long) -> Unit
) {
    val context = LocalContext.current
    
    val elders by viewModel.elders.collectAsStateWithLifecycle()
    val selectedElder by viewModel.selectedElder.collectAsStateWithLifecycle()
    val poiName by viewModel.poiName.collectAsStateWithLifecycle()
    val destLat by viewModel.destLat.collectAsStateWithLifecycle()
    val destLng by viewModel.destLng.collectAsStateWithLifecycle()
    val passengerCount by viewModel.passengerCount.collectAsStateWithLifecycle()
    val remark by viewModel.remark.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val orderResult by viewModel.orderResult.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 监听订单创建结果
    LaunchedEffect(orderResult) {
        if (orderResult != null) {
            Toast.makeText(context, "代叫车成功！", Toast.LENGTH_SHORT).show()
            onOrderSuccess(orderResult!!.id)
            viewModel.clearOrderResult()
        }
    }

    // 监听错误信息
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("为长辈叫车") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
            // ⭐ 选择长辈
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "选择长辈",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (elders.isEmpty()) {
                            Text(
                                text = "暂无已添加的长辈，请先在亲情守护中添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            elders.forEach { elder ->
                                ElderItem(
                                    elder = elder,
                                    isSelected = selectedElder == elder,
                                    onClick = { viewModel.selectElder(elder) }
                                )
                            }
                        }
                    }
                }
            }

            // ⭐ 目的地输入
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "目的地",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = poiName,
                            onValueChange = { /* TODO: 需要地图选点功能 */ },
                            label = { Text("目的地名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = false,  // ⭐ 暂时禁用，需要集成地图选点
                            placeholder = { Text("请在地图上选择目的地") }
                        )

                        Text(
                            text = "提示：此功能需要集成地图选点，暂未实现",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ⭐ 乘客数量
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "乘客数量",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..4).forEach { count ->
                                FilterChip(
                                    selected = passengerCount == count,
                                    onClick = { viewModel.updatePassengerCount(count) },
                                    label = { Text("$count 人") }
                                )
                            }
                        }
                    }
                }
            }

            // ⭐ 备注
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "备注（可选）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = remark,
                            onValueChange = viewModel::updateRemark,
                            label = { Text("给司机的备注") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            placeholder = { Text("例如：老人行动不便，请耐心等候") },
                            enabled = true,  // ⭐ 明确启用输入框
                            readOnly = false  // ⭐ 明确设置为可编辑
                        )
                    }
                }
            }

            // ⭐ 提交按钮
            item {
                Button(
                    onClick = { viewModel.createOrderForElder() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && selectedElder != null && poiName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoading) "下单中..." else "确认代叫车")
                }

                if (selectedElder == null) {
                    Text(
                        text = "请先选择长辈",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 长辈列表项
 */
@Composable
fun ElderItem(
    elder: ElderInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = elder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = elder.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = if (elder.status == 0) "待激活" else "已绑定",
                style = MaterialTheme.typography.labelSmall,
                color = if (elder.status == 0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}
