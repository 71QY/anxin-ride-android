package com.example.myapplication.presentation.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * 图片预览栏组件
 * 支持：
 * - 最多显示3张图片
 * - 点击删除单张图片
 * - 正方形小框显示
 * - 右上角删除按钮
 */
@Composable
fun ImagePreviewBar(
    pendingImages: List<String>,  // Base64 字符串列表
    onRemoveImage: (Int) -> Unit,  // 删除指定索引的图片
    modifier: Modifier = Modifier
) {
    if (pendingImages.isEmpty()) return
    
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pendingImages) { index, base64 ->
            ImagePreviewItem(
                base64 = base64,
                onRemove = { onRemoveImage(index) }
            )
        }
    }
}

/**
 * 单个图片预览项
 * - 正方形小框（60dp x 60dp）
 * - 右上角删除按钮
 * - 点击可查看大图（可选）
 */
@Composable
fun ImagePreviewItem(
    base64: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(60.dp)
    ) {
        // 解码并显示图片
        val bitmap = remember(base64) {
            try {
                val base64Data = if (base64.contains(",")) {
                    base64.substringAfter(",")
                } else {
                    base64
                }
                
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                Log.e("ImagePreviewItem", "❌ 图片解码失败: ${e.message}")
                null
            }
        }
        
        if (bitmap != null) {
            // 图片主体
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "待发送图片",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            // 半透明遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            )
        } else {
            // 加载失败的占位符
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        // 右上角删除按钮
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .offset(x = 4.dp, y = (-4).dp)
                .background(
                    color = Color.Red.copy(alpha = 0.8f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除图片",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 图片数量限制提示对话框
 */
@Composable
fun ImageLimitDialog(
    maxCount: Int = 3,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ 图片数量超限") },
        text = { Text("一次最多只能上传 $maxCount 张图片，请删除部分图片后再试") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
