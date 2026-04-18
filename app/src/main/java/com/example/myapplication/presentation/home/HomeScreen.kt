package com.example.myapplication.presentation.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState  // ⭐ 优化方案：添加浮点数动画
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale  // ⭐ 优化方案：添加缩放修饰符
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ListItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch  // ⭐ 新增：协程启动
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.example.myapplication.data.model.PoiResponse
import com.example.myapplication.data.model.ElderInfo  // ⭐ 新增：长辈信息
import com.example.myapplication.map.MapViewComposable
import com.example.myapplication.presentation.chat.ChatViewModel  // ⭐ 新增：导入 ChatViewModel
import com.google.accompanist.permissions.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,  // ⭐ 修复：移除默认参数，必须由调用方传入
    chatViewModel: ChatViewModel,  // ⭐ 修复：改为非空，必须由调用方传入
    onNavigateToProfile: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onNavigateToOrder: (String) -> Unit,
    onNavigateToChat: () -> Unit = {},
    onLogout: () -> Unit = {},  // ⭐ 新增：退出登录回调
    onNavigateToOrderTracking: (Long) -> Unit = {}  // ⭐ 新增：跳转到行程追踪页面
) {
    // ⭐ 新增：检查长辈模式
    val isElderMode by viewModel.isElderMode.collectAsStateWithLifecycle()
    
    // ⭐ 新增：监听用户信息加载状态
    val isProfileLoaded by viewModel.isProfileLoaded.collectAsStateWithLifecycle()
    
    // ⭐ 修复：移除 checkElderMode 调用，由 HomeViewModel init 块自动执行
    // 原因：避免重复调用导致协程冲突和 ViewModel 销毁
    
    // ⭐ 关键调试：每次重组都打印模式状态
    Log.d("HomeScreen", "🔍 === HomeScreen 重组 ===")
    Log.d("HomeScreen", "🔍 isElderMode=$isElderMode")
    Log.d("HomeScreen", "🔍 isProfileLoaded=$isProfileLoaded")
    Log.d("HomeScreen", "🔍 userId=${viewModel.userId.value}")
    
    // ⭐ 修复：监听 isProfileLoaded 状态，添加延迟避免误判
    var hasCheckedProfile by remember { mutableStateOf(false) }
    LaunchedEffect(isProfileLoaded) {
        // ⭐ 关键修复：给 loadProfile 留出时间，不立即判断
        if (!hasCheckedProfile) {
            delay(1000)  // 等待 1 秒让 loadProfile 完成
            hasCheckedProfile = true
        }
        
        val userId = viewModel.userId.value
        if (userId != null && !isProfileLoaded && hasCheckedProfile) {
            Log.w("HomeScreen", "⚠️ 用户信息加载失败，Token可能已过期，触发退出登录")
            onLogout()
        }
    }
    
    // ⭐ 根据模式显示不同界面
    Log.d("HomeScreen", "🔍 当前模式判断: isElderMode=$isElderMode")
    
    if (isElderMode) {
        Log.d("HomeScreen", "✅ 显示长辈模式界面")
        ElderSimplifiedScreen(
            viewModel = viewModel,
            chatViewModel = chatViewModel,  // ⭐ 直接使用传入的 chatViewModel
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToChat = onNavigateToChat,
            onLogout = onLogout,  // ⭐ 传递退出登录回调
            onNavigateToOrderTracking = onNavigateToOrderTracking  // ⭐ 新增：传递行程追踪导航回调
        )
    } else {
        Log.d("HomeScreen", "ℹ️ 显示普通模式界面")
        NormalHomeScreen(
            viewModel = viewModel,
            onNavigateToProfile = onNavigateToProfile,
            onRequestLocationPermission = onRequestLocationPermission,
            onNavigateToOrder = onNavigateToOrder,
            onNavigateToChat = onNavigateToChat,
            onLogout = onLogout,  // ⭐ 新增：传递退出登录回调
            onNavigateToOrderTracking = onNavigateToOrderTracking  // ⭐ 新增：传递行程追踪导航回调
        )
    }
}
