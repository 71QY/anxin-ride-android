package com.example.myapplication.presentation.favorites

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.TravelRecord
import java.util.Locale

/**
 * ⭐ 出行记录页面(行程凭证)
 * 功能:查看长辈的出行历史,方便子女了解老人去过哪里
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelRecordsScreen(
    viewModel: FavoritesViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val travelRecords by viewModel.travelRecords.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // ⭐ 新增：TTS语音播报
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsInitialized by remember { mutableStateOf(false) }
    
    // ⭐ 初始化TTS
    LaunchedEffect(Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(context, "⚠️ TTS不支持中文", Toast.LENGTH_SHORT).show()
                } else {
                    ttsInitialized = true
                }
            }
        }
    }
    
    // ⭐ 释放TTS资源
    DisposableEffect(Unit) {
        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
    }
    
    // ⭐ 语音播报函数
    fun speakRecordInfo(record: TravelRecord) {
        if (!ttsInitialized) return
        
        val message = buildString {
            append("${record.destinationName}")
            append("，状态：${when(record.status) { 1 -> "已完成"; 2 -> "已取消"; else -> "进行中" }}")
            append("，出发时间：${formatTime(record.startTime)}")
            record.arriveTime?.let {
                append("，到达时间：${formatTime(it)}")
            }
            record.durationMinutes?.let {
                append("，行程时长：${it}分钟")
            }
            record.distanceMeters?.let {
                append("，行程距离：${String.format("%.1f公里", it / 1000.0)}")
            }
        }
        
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    LaunchedEffect(Unit) {
        // 加载出行记录
        viewModel.getTravelRecords(
            page = 1,
            size = 50,
            onSuccess = {},
            onError = { error ->
                Toast.makeText(context, "❌ $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "出行记录",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading && travelRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (travelRecords.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Text(
                        text = "暂无出行记录",
                        fontSize = 18.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "完成订单后会自动记录",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(travelRecords) { record ->
                    TravelRecordCard(
                        record = record,
                        onClick = {
                            // ⭐ 点击卡片时语音播报关键信息
                            speakRecordInfo(record)
                        }
                    )
                }
            }
        }
    }
}

/**
 * ⭐ 出行记录卡片
 */
@Composable
private fun TravelRecordCard(
    record: TravelRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部:目的地 + 状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.destinationName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 状态标签
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (record.status) {
                        1 -> Color(0xFF52C41A)  // 已完成 - 绿色
                        2 -> Color(0xFFFF4D4F)  // 已取消 - 红色
                        else -> Color(0xFF1677FF)  // 进行中 - 蓝色
                    }
                ) {
                    Text(
                        text = when (record.status) {
                            1 -> "已完成"
                            2 -> "已取消"
                            else -> "进行中"
                        },
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            // 地址
            Text(
                text = record.destinationAddress,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            
            // 时间信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 出发时间
                Column {
                    Text(
                        text = "出发",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = formatTime(record.startTime),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 到达时间
                record.arriveTime?.let { arriveTime ->
                    Column {
                        Text(
                            text = "到达",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = formatTime(arriveTime),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 行程统计
            if (record.status == 1) {  // 只显示已完成的统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    record.durationMinutes?.let { duration ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${duration}分钟",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    
                    record.distanceMeters?.let { distance ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f公里", distance / 1000.0),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ⭐ 格式化时间字符串
 */
private fun formatTime(timeStr: String): String {
    return try {
        // 假设后端返回格式: "2026-04-18T10:30:00"
        val parts = timeStr.split("T")
        if (parts.size == 2) {
            val date = parts[0]  // 2026-04-18
            val time = parts[1].substring(0, 5)  // 10:30
            "$date $time"
        } else {
            timeStr
        }
    } catch (e: Exception) {
        timeStr
    }
}
