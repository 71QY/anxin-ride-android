package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place  // ⭐ 新增：地点图标
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card  // ⭐ 新增
import androidx.compose.material3.CardDefaults  // ⭐ 新增
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape  // ⭐ 新增
import androidx.compose.foundation.layout.Row  // ⭐ 新增
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner  // ⭐ 新增：用于获取 LifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle  // ⭐ 用于 StateFlow 收集
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.amap.api.maps.MapsInitializer
import com.example.myapplication.presentation.chat.ChatScreen
import com.example.myapplication.presentation.chat.ChatViewModel
import com.example.myapplication.presentation.chat.ChatMode
import com.example.myapplication.presentation.chat.ChatListScreen
import com.example.myapplication.presentation.chat.PrivateChatScreen  // ⭐ 新增：私聊界面
import com.example.myapplication.presentation.home.HomeScreen
import com.example.myapplication.presentation.home.HomeViewModel
import com.example.myapplication.presentation.login.LoginScreen
import com.example.myapplication.presentation.order.OrderDetailScreen
import com.example.myapplication.presentation.order.OrderListScreen
import com.example.myapplication.presentation.orderTracking.OrderTrackingScreen  // ⭐ 新增：订单追踪页面
import com.example.myapplication.presentation.profile.ProfileScreen
import com.example.myapplication.presentation.favorites.FavoritesScreen  // ⭐ 新增：收藏页面
import com.example.myapplication.service.AgentFloatService
import com.example.myapplication.ui.theme.AnxinChuxingTheme
import com.example.myapplication.debug.WebSocketDebugMonitor
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext  // ⭐ 新增：用于切换协程上下文
import com.example.myapplication.R

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    private var hasRequestedPermission = false
    
    // ⭐ 修复：移除 by viewModels()，改为在 MyApplicationApp 中使用 hiltViewModel()
    // 原因：by viewModels() 在 Activity 级别创建，导航时可能被销毁
    // private val homeViewModel: HomeViewModel by viewModels()
    // private val chatViewModel: ChatViewModel by viewModels()
    
    private var _navigateToChat by mutableStateOf(false)
    
    /**
     * ⭐ 新增：处理收藏分享通知点击
     */
    private fun handleFavoriteSharedIntent(intent: Intent) {
        val elderId = intent.getLongExtra("FAVORITE_SHARED_ELDER_ID", -1L)
        val elderName = intent.getStringExtra("FAVORITE_SHARED_ELDER_NAME")
        val favoriteName = intent.getStringExtra("FAVORITE_NAME")
        val favoriteAddress = intent.getStringExtra("FAVORITE_ADDRESS")
        val favoriteLat = intent.getDoubleExtra("FAVORITE_LAT", 0.0)
        val favoriteLng = intent.getDoubleExtra("FAVORITE_LNG", 0.0)
        
        if (elderId != -1L && elderName != null && favoriteName != null) {
            Log.d(TAG, "📍 收到收藏分享通知点击：elderId=$elderId, elderName=$elderName, favorite=$favoriteName")
            
            // ⭐ 保存信息到 SharedPreferences，等待 NavHost 创建后跳转
            val prefs = getSharedPreferences("favorite_shared", MODE_PRIVATE)
            prefs.edit()
                .putLong("elder_id", elderId)
                .putString("elder_name", elderName)
                .putString("favorite_name", favoriteName)
                .putString("favorite_address", favoriteAddress ?: "")
                .putFloat("favorite_lat", favoriteLat.toFloat())
                .putFloat("favorite_lng", favoriteLng.toFloat())
                .putBoolean("should_navigate", true)
                .apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val navigateToChat = intent.getBooleanExtra("navigate_to_chat", false)
        _navigateToChat = navigateToChat
        
        // ⭐ 新增：处理收藏分享通知点击
        handleFavoriteSharedIntent(intent)

        try {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)

            try {
                val iflytekAppid = BuildConfig.IFLYTEK_APPID
                Log.d(TAG, "iFlytek AppID: $iflytekAppid")
                if (iflytekAppid.isNotBlank()) {
                    SpeechUtility.createUtility(this, SpeechConstant.APPID + "=" + iflytekAppid)
                    Log.d(TAG, "iFlytek SDK initialized")
                } else {
                    Log.e(TAG, "iFlytek AppID is empty, check build.gradle.kts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "iFlytek SDK initialization failed", e)
            }

            enableEdgeToEdge()

            setContent {
                AnxinChuxingTheme {
                    val navController = rememberNavController()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        Log.d("MainActivity", "App started, preparing location")
                    }

                    // ⭐ 修复：使用 rememberSaveable 保存 startDestination，避免重组时变化
                    val tokenManager = com.example.myapplication.MyApplication.tokenManager
                    val startDestination = rememberSaveable {
                        val token = tokenManager.getToken()
                        Log.d("MainActivity", "🔍 === 自动登录检查 ===")
                        Log.d("MainActivity", "   Token: ${if (token != null) "存在" else "null"}")
                        
                        if (token != null) {
                            Log.d("MainActivity", "✅ 检测到本地 Token，自动登录进入主页")
                            "main"
                        } else {
                            Log.d("MainActivity", "❌ 未检测到 Token，显示登录页")
                            "login"
                        }
                    }
                    
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    Log.d("MainActivity", "Login successful")
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }


                                },
                                onRequestFloatPermission = {
                                }
                            )
                        }
                        composable("main") {
                            val context = LocalContext.current
                            
                            // ⭐ 关键修复：在 NavGraph 作用域内创建 ViewModel，确保生命周期与路由绑定
                            val homeViewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                            val chatViewModel: ChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                            
                            LaunchedEffect(Unit) {
                                Log.d("MainActivity", "Starting to monitor location changes")
                                homeViewModel.currentLocation.collect { location ->
                                    if (location != null) {
                                        Log.d("MainActivity", "Location update received: lat=${location.latitude}, lng=${location.longitude}")
                                        chatViewModel.syncLocationFromHome(location.latitude, location.longitude)
                                    } else {
                                        Log.w("MainActivity", "Location is null")
                                    }
                                }
                            }
                            
                            MyApplicationApp(
                                homeViewModel = homeViewModel,
                                chatViewModel = chatViewModel,
                                navController = navController,  // ⭐ 新增：传入 navController
                                onNavigateToOrderDetail = { orderId ->
                                    navController.navigate("order_detail/$orderId")
                                },
                                onNavigateToOrderList = {
                                    navController.navigate("orderList") {
                                        popUpTo("main") {
                                            inclusive = false
                                        }
                                    }
                                },
                                onNavigateToChat = {}  // ⭐ 修复：空实现，实际导航由 MyApplicationApp 内部管理
                            )
                        }
                        composable("order_detail/{orderId}") { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull()
                            if (orderId != null) {
                                OrderDetailScreen(
                                    orderId = orderId,
                                    onBackClick = { navController.popBackStack() }
                                )
                            } else {
                                Text("Invalid order ID")
                            }
                        }
                        // ⭐ 新增：订单追踪页面路由（实时显示司机位置、订单状态、路线）
                        composable("order_tracking/{orderId}") { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull()
                            if (orderId != null) {
                                OrderTrackingScreen(
                                    orderId = orderId,
                                    onBackClick = { navController.popBackStack() }
                                )
                            } else {
                                Text("Invalid order ID")
                            }
                        }
                        composable("orderList") {
                            OrderListScreen(
                                onOrderClick = { orderId ->
                                    // ⭐ 修改：跳转到订单追踪页面（实时显示司机位置、路线）
                                    navController.navigate("order_tracking/$orderId")
                                }
                            )
                        }
                        // ⭐ 注意：chat 路由已移到 MyApplicationApp 内部管理，此处不再重复定义
                        composable("agent_chat") {
                            ChatListScreen(
                                onBackClick = { navController.popBackStack() },
                                onSessionSelected = { sessionId: String ->
                                    Log.d(TAG, "选择会话: $sessionId")
                                },
                                onNavigateToAgent = {
                                    navController.navigate("chat")
                                }
                            )
                        }
                        // ⭐ 新增：私聊界面路由
                        composable("private_chat/{contactId}/{contactName}") { backStackEntry ->
                            val contactId = backStackEntry.arguments?.getString("contactId")?.toLongOrNull()
                            val contactName = backStackEntry.arguments?.getString("contactName") ?: "未知"
                            
                            // ⭐ 修复：在 NavGraph 作用域内创建 ViewModel
                            val homeViewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                            val chatViewModel: ChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()  // ⭐ 修复：在此作用域内创建 chatViewModel
                            val isElderMode by homeViewModel.isElderMode.collectAsStateWithLifecycle()  // ⭐ 修复：使用 collectAsStateWithLifecycle
                            
                            if (contactId != null) {
                                PrivateChatScreen(
                                    viewModel = androidx.hilt.navigation.compose.hiltViewModel(),
                                    chatViewModel = chatViewModel,  // ⭐ 新增：传入 ChatViewModel，实时接收 WebSocket 消息
                                    guardianId = contactId,
                                    guardianName = contactName,
                                    onBackClick = { navController.popBackStack() },
                                    isElderMode = isElderMode,
                                    onNavigateToHomeWithDestination = fun(name: String, lat: Double, lng: Double) {
                                        // ⭐ 一键填充到打车界面
                                        Log.d("MainActivity", "📍 [私聊] 从聊天跳转到首页，目的地：$name")
                                        
                                        // ⭐ 修复：同时保存目的地和长辈实时位置（作为代叫车起点）
                                        val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putString("destination_name", name)
                                            .putFloat("destination_lat", lat.toFloat())
                                            .putFloat("destination_lng", lng.toFloat())
                                            .putBoolean("should_apply", true)
                                            // ⭐ 新增：保存长辈实时位置（从 ChatViewModel 获取）
                                            .apply()
                                        
                                        // ⭐ 从 ChatViewModel 获取长辈实时位置并保存
                                        val sharedLocation = chatViewModel.sharedLocation.value
                                        Log.d("MainActivity", "🔍 [私聊] sharedLocation=$sharedLocation")
                                        if (sharedLocation?.elderCurrentLat != null && sharedLocation.elderCurrentLng != null) {
                                            prefs.edit()
                                                .putFloat("elder_start_lat", sharedLocation.elderCurrentLat.toFloat())
                                                .putFloat("elder_start_lng", sharedLocation.elderCurrentLng.toFloat())
                                                .putLong("elder_location_timestamp", sharedLocation.elderLocationTimestamp ?: System.currentTimeMillis())
                                                .apply()
                                            Log.d("MainActivity", "✅ [私聊] 已保存长辈实时位置：lat=${sharedLocation.elderCurrentLat}, lng=${sharedLocation.elderCurrentLng}")
                                        } else {
                                            Log.w("MainActivity", "⚠️ [私聊] 未收到长辈实时位置，将使用当前位置")
                                            Log.w("MainActivity", "⚠️ [私聊] sharedLocation.elderCurrentLat=${sharedLocation?.elderCurrentLat}")
                                            Log.w("MainActivity", "⚠️ [私聊] sharedLocation.elderCurrentLng=${sharedLocation?.elderCurrentLng}")
                                        }
                                        
                                        // ⭐ 关键修复：只有亲友端才能创建订单，长辈端不能创建
                                        val isElderMode = homeViewModel.isElderMode.value
                                        if (isElderMode) {
                                            Log.w("MainActivity", "⛔ [私聊] 长辈端禁止创建订单，直接拦截")
                                            Toast.makeText(
                                                context,
                                                "😊 温馨提示：\n长辈端不支持直接下单叫车哦~\n请联系您的亲友帮忙代叫 🙏",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return  // ⭐ 匿名函数中直接使用 return 即可
                                        }
                                        
                                        // ⭐ 关键修复：先跳转到首页，然后延迟创建订单
                                        navController.navigate("main") {
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                        
                                        // ⭐ 延迟创建订单，确保首页已经渲染
                                        val currentActivity = this@MainActivity
                                        currentActivity.lifecycleScope.launch {
                                            Log.d("MainActivity", "⏰ [延迟任务] 开始等待 500ms...")
                                            kotlinx.coroutines.delay(500)
                                            Log.d("MainActivity", "⏰ [延迟任务] 等待完成，准备创建订单")
                                            // 在 UI 线程中调用 createOrder
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Log.d("MainActivity", "🚕 [私聊] 开始创建代叫车订单：$name")
                                                
                                                // ⭐ 关键修复：传递长辈ID（从 ChatViewModel 获取）
                                                val elderId = chatViewModel.sharedLocation.value?.elderId
                                                Log.d("MainActivity", "🚕 [私聊] 长辈ID：$elderId")
                                                Log.d("MainActivity", "🚕 [私聊] 目的地：$name, lat=$lat, lng=$lng")
                                                homeViewModel.createOrder(name, elderId)
                                                Log.d("MainActivity", "✅ [私聊] createOrder 已调用")
                                                
                                                // ⭐ 修复：不清除 sharedLocation，让长辈端可以持续显示卡片
                                                // chatViewModel.clearSharedLocation()  // ❌ 已移除
                                            }
                                        }
                                    },
                                    onNavigateToOrderTracking = { orderId ->
                                        // ⭐ 关键修复：已确认订单，直接跳转到行程追踪界面
                                        Log.d("MainActivity", "📋 [私聊] 查看订单详情，orderId=$orderId")
                                        navController.navigate("order_tracking/$orderId")
                                    },
                                    onNavigateToElderLocation = { elderName, lat, lng ->
                                        // ⭐ 新增：跳转到长辈位置地图界面
                                        Log.d("MainActivity", "📍 [私聊] 查看长辈位置 - $elderName: lat=$lat, lng=$lng")
                                        // TODO: 创建专门的地图界面显示长辈位置
                                        // 目前先跳转到首页，并设置地图中心点为长辈位置
                                        val prefs = context.getSharedPreferences("elder_location_view", android.content.Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putString("elder_name", elderName)
                                            .putFloat("elder_lat", lat.toFloat())
                                            .putFloat("elder_lng", lng.toFloat())
                                            .putBoolean("should_focus", true)
                                            .apply()
                                        
                                        // 跳转到首页
                                        navController.navigate("main") {
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    },
                                    onConfirmProxyOrder = { orderId, confirmed ->
                                        // ⭐ 新增：长辈确认/拒绝代叫车
                                        Log.d("MainActivity", "📱 [私聊] 长辈${if (confirmed) "同意" else "拒绝"}代叫车，orderId=$orderId")
                                        homeViewModel.confirmProxyOrder(orderId, confirmed, if (!confirmed) "暂时不需要" else null)
                                    }
                                )
                            } else {
                                Text("无效的联系人ID")
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "=== MainActivity onCreate completed ===")
        } catch (e: Exception) {
            Log.e(TAG, "Exception occurred during onCreate", e)
            throw e
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navigateToChat = intent.getBooleanExtra("navigate_to_chat", false)
        if (navigateToChat) {
            Log.d(TAG, "onNewIntent: Navigate to chat from float window")
            _navigateToChat = true
        }
    }

    fun requestFloatPermissionAfterLogin() {
        lifecycleScope.launch {
            delay(500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== MainActivity onDestroy ===")
        
        try {
            val intent = Intent(this, AgentFloatService::class.java)
            stopService(intent)
            Log.d(TAG, "Float service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop float service", e)
        }
        
        Log.d(TAG, "Keep WebSocket connection, do not disconnect")
    }
}

@Composable
fun MyApplicationApp(
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    navController: NavHostController,  // ⭐ 新增：传入 navController
    onNavigateToOrderDetail: (Long) -> Unit = {},
    onNavigateToOrderList: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onBackToHome: (() -> Unit)? = null  // ⭐ 新增：返回主页回调
) {
    var currentDestination by rememberSaveable { mutableStateOf<String?>("home") }
    val context = LocalContext.current
    
    // ⭐ 修复：在顶层创建 ViewModel，确保 Tab 切换时 ViewModel 不销毁
    val favoritesViewModel: com.example.myapplication.presentation.favorites.FavoritesViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    
    // ⭐ 新增：返回主页的回调函数（本地定义）
    val localOnBackToHome = {
        Log.d("MyApplicationApp", "⬅️ 返回主页")
        currentDestination = "home"
    }
    
    LaunchedEffect(currentDestination) {
        if (currentDestination == "home") {
            Log.d("MyApplicationApp", "Return to home, check location status")
            
            var currentLoc = homeViewModel.currentLocation.value
            
            if (currentLoc == null || currentLoc.latitude == 0.0 || currentLoc.longitude == 0.0) {
                Log.w("MyApplicationApp", "Current location invalid, waiting for AMap positioning...")
                
                var waitCount = 0
                while (waitCount < 50) {
                    delay(100)
                    currentLoc = homeViewModel.currentLocation.value
                    if (currentLoc != null && currentLoc.latitude != 0.0 && currentLoc.longitude != 0.0) {
                        Log.d("MyApplicationApp", "Positioning successful: lat=${currentLoc.latitude}, lng=${currentLoc.longitude}")
                        break
                    }
                    waitCount++
                }
                
                if (currentLoc == null || currentLoc.latitude == 0.0 || currentLoc.longitude == 0.0) {
                    Log.e("MyApplicationApp", "Positioning timeout, please check location permission")
                    return@LaunchedEffect
                }
            } else {
                Log.d("MyApplicationApp", "Current location valid: lat=${currentLoc.latitude}, lng=${currentLoc.longitude}")
            }
            
            chatViewModel.syncLocationFromHome(currentLoc.latitude, currentLoc.longitude)
            Log.d("MyApplicationApp", "Location synced to ChatViewModel")
        }
    }
    
    // ⭐ 新增：处理收藏分享通知点击
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("favorite_shared", android.content.Context.MODE_PRIVATE)
        val shouldNavigate = prefs.getBoolean("should_navigate", false)
        
        if (shouldNavigate) {
            val elderId = prefs.getLong("elder_id", -1L)
            val elderName = prefs.getString("elder_name", "")
            val favoriteName = prefs.getString("favorite_name", "")
            val favoriteAddress = prefs.getString("favorite_address", "")
            val favoriteLat = prefs.getFloat("favorite_lat", 0f).toDouble()
            val favoriteLng = prefs.getFloat("favorite_lng", 0f).toDouble()
            
            if (elderId != -1L && !elderName.isNullOrBlank() && !favoriteName.isNullOrBlank()) {
                Log.d("MyApplicationApp", "📍 处理收藏分享通知，跳转到与 $elderName 的聊天")
                
                // 清除标记
                prefs.edit().putBoolean("should_navigate", false).apply()
                
                // ⭐ 修复：不再保存 SharedPreferences，ChatViewModel 已经通过 StateFlow 实时更新
                Log.d("MyApplicationApp", "✅ 将使用 WebSocket StateFlow 实时接收地点信息")
                
                // 跳转到私聊界面
                navController.navigate("private_chat/$elderId/$elderName")
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination == "home",
                    onClick = {
                        currentDestination = "home"
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_home),
                            contentDescription = "首页",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = currentDestination == "favorites",
                    onClick = {
                        currentDestination = "favorites"
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("收藏") }
                )
                NavigationBarItem(
                    selected = currentDestination == "chat",
                    onClick = {
                        currentDestination = "chat"
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "对话",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("对话") }
                )
                NavigationBarItem(
                    selected = currentDestination == "profile",
                    onClick = {
                        currentDestination = "profile"
                    },
                    icon = {
                        Icon(
                            painterResource(id = R.drawable.ic_account_box),
                            contentDescription = "我的",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("我的") }
                )
            }
        }
    ) { paddingValues ->
        // ⭐ 新增：获取 LifecycleOwner 用于启动协程
        val lifecycleOwner = LocalLifecycleOwner.current
        
        // ⭐ 修复：将 isElderMode 定义移到 when 之前，确保所有分支都能访问
        val isElderMode by homeViewModel.isElderMode.collectAsStateWithLifecycle()
        
        // ⭐ 新增：全局代叫车确认弹窗（长辈端在任何界面都能收到）
        val proxyOrderRequest by homeViewModel.proxyOrderRequest.collectAsStateWithLifecycle()
        var showGlobalProxyOrderDialog by remember { mutableStateOf(false) }
        var pendingOrderId by remember { mutableStateOf<Long?>(null) }
        var requesterName by remember { mutableStateOf("") }
        var destination by remember { mutableStateOf("") }
        
        // ⭐ 新增：全局收藏分享弹窗（普通用户在任何界面都能收到）
        val sharedLocation by chatViewModel.sharedLocation.collectAsStateWithLifecycle()
        var showGlobalShareDialog by remember { mutableStateOf(false) }
        var pendingSharedLocation by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }
        var pendingSharedElderName by remember { mutableStateOf("") }
        // ⭐ 关键修复：使用 rememberSaveable 避免重组时重置，防止无限弹窗
        var hasShownShareDialog by rememberSaveable { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
        
        // ⭐ 修复：使用 snapshotFlow 监听 StateFlow 的变化，确保每次更新都触发
        LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { sharedLocation }.collect { location ->
                if (location != null && !isElderMode) {
                    val elderId = location.elderId
                    val alreadyShown = hasShownShareDialog[elderId] == true
                    
                    // ⭐ 关键修复：检查分享是否过期（超过30秒不弹窗）
                    val isExpired = location.elderLocationTimestamp != null && 
                        (System.currentTimeMillis() - location.elderLocationTimestamp) > 30 * 1000
                    
                    if (!alreadyShown && !isExpired) {
                        Log.d("MainActivity", "🔔 [全局] 收到收藏分享：${location.favoriteName}")
                        
                        // ⭐ 修复：检查当前是否在私聊界面
                        val isInPrivateChat = currentDestination?.startsWith("private_chat") == true
                        
                        if (!isInPrivateChat) {
                            // 不在私聊界面，显示全局弹窗
                            pendingSharedLocation = Triple(
                                location.favoriteName,
                                location.latitude,
                                location.longitude
                            )
                            pendingSharedElderName = location.elderName
                            showGlobalShareDialog = true
                            hasShownShareDialog = hasShownShareDialog + (elderId to true)  // ⭐ 标记该长辈已弹窗
                            Log.d("MainActivity", "✅ [全局] 显示全局弹窗")
                        } else {
                            // 在私聊界面，不弹窗，让 PrivateChatScreen 显示卡片
                            Log.d("MainActivity", "⏭️ [全局] 当前在私聊界面，跳过全局弹窗，由 PrivateChatScreen 显示卡片")
                        }
                    } else {
                        if (alreadyShown) {
                            Log.d("MainActivity", "⏭️ [全局] 该长辈的分享已弹过窗，跳过")
                        }
                        if (isExpired) {
                            Log.d("MainActivity", "⏭️ [全局] 分享已过期（>${30}秒），跳过")
                        }
                    }
                }
            }
        }
        
        // ⭐ 新增：全局监听代叫车请求事件（无论当前在哪个页面都能响应）
        LaunchedEffect(Unit) {
            MyApplication.proxyOrderRequestEvent.collect { event ->
                Log.d("MainActivity", "🚗 [全局事件] 收到代叫车请求：orderId=${event.orderId}, from=${event.requesterName}")
                
                // ⭐ 只有长辈端才显示确认弹窗
                if (isElderMode) {
                    pendingOrderId = event.orderId
                    requesterName = event.requesterName
                    destination = event.destination
                    showGlobalProxyOrderDialog = true
                    
                    Log.d("MainActivity", "✅ [全局事件] 已触发代叫车弹窗")
                } else {
                    Log.d("MainActivity", "⏭️ [全局事件] 当前是普通用户，忽略代叫车请求")
                }
            }
        }
        
        // ⭐ 修复：监听 HomeViewModel 的 _proxyOrderRequest StateFlow（来自 WebSocket 推送）
        LaunchedEffect(proxyOrderRequest) {
            if (isElderMode && proxyOrderRequest != null) {
                Log.d("MainActivity", "🔔 [全局] 收到代叫车请求：${proxyOrderRequest!!.orderId}")
                
                // ⭐ 关键修复：检查是否已经显示过该订单的弹窗
                if (pendingOrderId == proxyOrderRequest!!.orderId) {
                    Log.d("MainActivity", "⏭️ [全局] 该订单已显示过弹窗，跳过")
                    return@LaunchedEffect
                }
                
                // ⭐ 新增：再次检查是否为长辈端，防止竞态条件
                if (!isElderMode) {
                    Log.w("MainActivity", "⚠️ [全局] 当前不是长辈端，忽略代叫车请求")
                    return@LaunchedEffect
                }
                
                pendingOrderId = proxyOrderRequest!!.orderId
                requesterName = proxyOrderRequest!!.requesterName
                destination = proxyOrderRequest!!.destination
                showGlobalProxyOrderDialog = true
                
                Log.d("MainActivity", "✅ [全局] 已触发代叫车弹窗")
                
                // 清除请求，避免重复弹出
                homeViewModel.clearProxyOrderRequest()
            }
        }
        
        // ⭐ 修复：在 MainActivity 层级监听 HomeViewModel 的导航事件（无论当前在哪个页面都能响应）
        LaunchedEffect(Unit) {
            homeViewModel.events.collect { event ->
                when (event) {
                    is com.example.myapplication.presentation.home.HomeViewModel.HomeEvent.NavigateToOrderTracking -> {
                        Log.d("MainActivity", "🚀 [全局监听] 收到导航事件，前往行程追踪: orderId=${event.orderId}")
                        // ⭐ 先切换到首页，再跳转到订单追踪页面
                        currentDestination = "home"
                        // 延迟一下，确保首页已经渲染
                        kotlinx.coroutines.delay(300)
                        navController.navigate("order_tracking/${event.orderId}")
                    }
                    else -> {
                        // 其他事件忽略
                    }
                }
            }
        }
        
        // ⭐ 新增：监听全局导航事件（来自 ChatViewModel）
        LaunchedEffect(Unit) {
            MyApplication.navigateToOrderTrackingEvent.collect { orderId ->
                Log.d("MainActivity", "🚀 [全局事件] 收到导航到行程追踪事件: orderId=$orderId")
                // ⭐ 先切换到首页，再跳转到订单追踪页面
                currentDestination = "home"
                // 延迟一下，确保首页已经渲染
                kotlinx.coroutines.delay(300)
                navController.navigate("order_tracking/$orderId")
                Log.d("MainActivity", "✅ [全局事件] 已执行导航: order_tracking/$orderId")
            }
        }
        
        // 全局代叫车确认对话框
        if (showGlobalProxyOrderDialog && pendingOrderId != null) {
            AlertDialog(
                onDismissRequest = {
                    // 不允许点击外部关闭，必须选择接受或拒绝
                },
                title = {
                    Text(
                        "🚗 代叫车请求",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "代叫人：$requesterName",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "目的地：$destination",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Divider()
                        Text(
                            "是否接受此叫车请求？",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingOrderId?.let { orderId ->
                                Log.d("MainActivity", "✅ [全局] 长辈接受代叫车：orderId=$orderId")
                                Log.d("MainActivity", "🔍 [调试] pendingOrderId 的值: $pendingOrderId")
                                Log.d("MainActivity", "🔍 [调试] orderId 的值: $orderId")
                                if (orderId == 0L) {
                                    Log.e("MainActivity", "❌❌❌ 警告：orderId 为 0！这会导致后端无法正确处理")
                                }
                                homeViewModel.confirmProxyOrder(orderId, confirmed = true)
                                android.widget.Toast.makeText(context, "✅ 已接受叫车请求", android.widget.Toast.LENGTH_LONG).show()
                            } ?: run {
                                Log.e("MainActivity", "❌❌❌ 错误：pendingOrderId 为 null！")
                            }
                            // ⭐ 关键修复：清除请求状态，避免无限循环
                            homeViewModel.clearProxyOrderRequest()
                            showGlobalProxyOrderDialog = false
                            pendingOrderId = null
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("接受", fontSize = 16.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingOrderId?.let { orderId ->
                                Log.d("MainActivity", "❌ [全局] 长辈拒绝代叫车：orderId=$orderId")
                                Log.d("MainActivity", "🔍 [调试] pendingOrderId 的值: $pendingOrderId")
                                Log.d("MainActivity", "🔍 [调试] orderId 的值: $orderId")
                                if (orderId == 0L) {
                                    Log.e("MainActivity", "❌❌❌ 警告：orderId 为 0！这会导致后端无法正确处理")
                                }
                                homeViewModel.confirmProxyOrder(orderId, confirmed = false, rejectReason = "暂时不需要")
                                android.widget.Toast.makeText(context, "⚠️ 已拒绝叫车请求", android.widget.Toast.LENGTH_LONG).show()
                            } ?: run {
                                Log.e("MainActivity", "❌❌❌ 错误：pendingOrderId 为 null！")
                            }
                            // ⭐ 关键修复：清除请求状态，避免无限循环
                            homeViewModel.clearProxyOrderRequest()
                            showGlobalProxyOrderDialog = false
                            pendingOrderId = null
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("拒绝", fontSize = 16.sp)
                    }
                }
            )
        }
        
        // ⭐ 新增：全局收藏分享弹窗
        if (showGlobalShareDialog && pendingSharedLocation != null) {
            AlertDialog(
                onDismissRequest = {
                    // 不允许点击外部关闭
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "📍 收到分享的地点",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "$pendingSharedElderName 分享了以下地点给您：",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = pendingSharedLocation!!.first,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Divider()
                        
                        Text(
                            "是否立即为她代叫车？",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val (name, lat, lng) = pendingSharedLocation!!
                            Log.d("MainActivity", "🚕 [全局弹窗] 用户点击立即叫车：$name")
                                                    
                            // ⭐ 新增：获取长辈实时位置（作为代叫车起点）
                            val elderCurrentLat = sharedLocation?.elderCurrentLat
                            val elderCurrentLng = sharedLocation?.elderCurrentLng
                            val elderLocationTimestamp = sharedLocation?.elderLocationTimestamp
                                                    
                            // 检查长辈位置是否有效（不超过 5 分钟）
                            val isElderLocationValid = elderCurrentLat != null && 
                                elderCurrentLng != null && 
                                elderLocationTimestamp != null &&
                                (System.currentTimeMillis() - elderLocationTimestamp) < 5 * 60 * 1000
                                                    
                            if (isElderLocationValid) {
                                Log.d("MainActivity", "✅ 使用长辈实时位置作为起点：lat=$elderCurrentLat, lng=$elderCurrentLng")
                            } else {
                                Log.w("MainActivity", "⚠️ 长辈位置无效或过期，将使用当前位置作为起点")
                            }
                                                    
                            // ⭐ 修复：先保存 elderId 和 elderName，避免后面被清空
                            val elderId = sharedLocation?.elderId
                            val elderName = pendingSharedElderName
                                                    
                            // 清除全局弹窗状态
                            showGlobalShareDialog = false
                            pendingSharedLocation = null
                            pendingSharedElderName = ""
                            // ⭐ 关键修复：不要立即移除标志，等订单创建完成后再移除
                            // if (elderId != null) {
                            //     hasShownShareDialog = hasShownShareDialog - elderId
                            // }
                                                    
                            // ⭐ 修复：不清除 sharedLocation，以便私聊界面可以显示卡片
                            // chatViewModel.clearSharedLocation()  // ❌ 已移除
                                                    
                            // ⭐ 方案 A：直接跳转到首页并自动填充目的地（不经过私聊界面）
                            Log.d("MainActivity", "✅ [全局弹窗] 直接跳转到首页并填充目的地")
                            currentDestination = "home"
                                                    
                            // ⭐ 保存目的地到 SharedPreferences，供 HomeScreen 读取
                            val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("destination_name", name)
                                .putFloat("destination_lat", lat.toFloat())
                                .putFloat("destination_lng", lng.toFloat())
                                .putBoolean("should_apply", true)
                                .apply()
                                                    
                            // ⭐ 保存长辈实时位置（作为代叫车起点）
                            if (isElderLocationValid && elderCurrentLat != null && elderCurrentLng != null) {
                                prefs.edit()
                                    .putFloat("elder_start_lat", elderCurrentLat.toFloat())
                                    .putFloat("elder_start_lng", elderCurrentLng.toFloat())
                                    .putLong("elder_location_timestamp", elderLocationTimestamp ?: System.currentTimeMillis())
                                    .apply()
                                Log.d("MainActivity", "✅ [全局弹窗] 已保存长辈实时位置到 SharedPreferences")
                            }
                                                    
                            // ⭐ 标记该分享已被处理（用于私聊界面判断是否显示"一键填充"按钮）
                            if (elderId != null) {
                                prefs.edit()
                                    .putBoolean("share_processed_$elderId", true)
                                    .apply()
                                Log.d("MainActivity", "✅ [全局弹窗] 已标记分享为已处理：elderId=$elderId")
                            }
                                                    
                            // ⭐ 关键修复：延迟设置目的地，确保首页已经渲染
                            lifecycleOwner.lifecycleScope.launch {
                                Log.d("MainActivity", "⏰ [全局弹窗-延迟任务] 开始等待 500ms...")
                                kotlinx.coroutines.delay(500)
                                Log.d("MainActivity", "⏰ [全局弹窗-延迟任务] 等待完成，准备设置目的地")
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Log.d("MainActivity", "🚕 [全局弹窗] 开始设置目的地：$name")
                                    Log.d("MainActivity", "🚕 [全局弹窗] 长辈ID：$elderId")
                                    Log.d("MainActivity", "🚕 [全局弹窗] 目的地：$name, lat=$lat, lng=$lng")
                                    
                                    // ⭐ 关键修复：只设置目的地和起点，不自动创建订单
                                    if (isElderLocationValid && elderCurrentLat != null && elderCurrentLng != null) {
                                        Log.d("MainActivity", "✅ [全局弹窗] 使用长辈实时位置作为起点：lat=$elderCurrentLat, lng=$elderCurrentLng")
                                        homeViewModel.setDestinationFromFavorite(
                                            name, lat, lng,
                                            startLat = elderCurrentLat,
                                            startLng = elderCurrentLng
                                        )
                                    } else {
                                        Log.w("MainActivity", "⚠️ [全局弹窗] 长辈位置无效，将使用当前位置作为起点")
                                        homeViewModel.setDestinationFromFavorite(name, lat, lng)
                                    }
                                    
                                    Log.d("MainActivity", "✅ [全局弹窗] 已设置目的地，请用户在 POI 详情对话框中点击“确认叫车”")
                                    
                                    // ⭐ 关键修复：订单创建成功后，移除标志，允许下次分享时再次弹窗
                                    if (elderId != null) {
                                        hasShownShareDialog = hasShownShareDialog - elderId
                                        Log.d("MainActivity", "✅ [全局弹窗] 已移除 elderId=$elderId 的标志，允许下次分享")
                                    }
                                    
                                    // ⭐ 修复：不清除 sharedLocation，让长辈端可以持续显示卡片
                                    // chatViewModel.clearSharedLocation()  // ❌ 已移除
                                }
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("立即叫车", fontSize = 16.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            Log.d("MainActivity", "ℹ️ [全局弹窗] 用户稍后查看")
                            
                            val elderId = sharedLocation?.elderId ?: 0L
                            val elderName = pendingSharedElderName
                            
                            // 清除全局弹窗状态
                            showGlobalShareDialog = false
                            pendingSharedLocation = null
                            pendingSharedElderName = ""
                            // ⭐ 修复：从 Map 中移除该长辈的标志，允许下次分享时再次弹窗
                            if (elderId > 0) {
                                hasShownShareDialog = hasShownShareDialog - elderId
                            }
                            
                            // ⭐ 修复：不清除 sharedLocation，让 PrivateChatScreen 可以显示卡片
                            // chatViewModel.clearSharedLocation()  // ❌ 已移除
                            
                            Log.d("MainActivity", "✅ [全局弹窗] 保留 sharedLocation，跳转到私聊界面显示卡片")
                            
                            // 跳转到私聊界面
                            if (elderId > 0) {
                                navController.navigate("private_chat/$elderId/$elderName")
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("稍后查看", fontSize = 16.sp)
                    }
                }
            )
        }
        
        Column(modifier = Modifier.padding(paddingValues)) {
            when (currentDestination) {
                "home" -> {
                    // ⭐ 新增：检查是否有待应用的目的地（从聊天界面跳转过来）
                    LaunchedEffect(Unit) {
                        val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
                        val shouldApply = prefs.getBoolean("should_apply", false)
                        
                        if (shouldApply) {
                            val destName = prefs.getString("destination_name", "")
                            val destLat = prefs.getFloat("destination_lat", 0f).toDouble()
                            val destLng = prefs.getFloat("destination_lng", 0f).toDouble()
                            
                            // ⭐ 新增：读取长辈实时位置（作为代叫车起点）
                            val elderStartLat = prefs.getFloat("elder_start_lat", 0f).toDouble()
                            val elderStartLng = prefs.getFloat("elder_start_lng", 0f).toDouble()
                            val elderLocationTimestamp = prefs.getLong("elder_location_timestamp", 0L)
                            
                            // 检查长辈位置是否有效（不超过 5 分钟）
                            val isElderLocationValid = elderStartLat != 0.0 && 
                                elderStartLng != 0.0 && 
                                elderLocationTimestamp != 0L &&
                                (System.currentTimeMillis() - elderLocationTimestamp) < 5 * 60 * 1000
                            
                            if (!destName.isNullOrBlank() && destLat != 0.0 && destLng != 0.0) {
                                Log.d("MyApplicationApp", "📍 应用来自聊天的目的地：$destName")
                                
                                if (isElderLocationValid) {
                                    Log.d("MyApplicationApp", "✅ 使用长辈实时位置作为起点：lat=$elderStartLat, lng=$elderStartLng")
                                    homeViewModel.setDestinationFromFavorite(
                                        destName, destLat, destLng,
                                        startLat = elderStartLat,
                                        startLng = elderStartLng
                                    )
                                } else {
                                    Log.w("MyApplicationApp", "⚠️ 长辈位置无效或过期，将使用当前位置作为起点")
                                    homeViewModel.setDestinationFromFavorite(destName, destLat, destLng)
                                }
                                
                                // 清除标记
                                prefs.edit().putBoolean("should_apply", false).apply()
                            }
                        }
                    }
                    
                    // ⭐ 新增：检查是否需要聚焦到长辈位置
                    try {
                        val elderPrefs = context.getSharedPreferences("elder_location_view", android.content.Context.MODE_PRIVATE)
                        val shouldFocus = elderPrefs.getBoolean("should_focus", false)
                        
                        if (shouldFocus) {
                            val elderName = elderPrefs.getString("elder_name", "")
                            val elderLat = elderPrefs.getFloat("elder_lat", 0f).toDouble()
                            val elderLng = elderPrefs.getFloat("elder_lng", 0f).toDouble()
                            
                            if (!elderName.isNullOrBlank() && elderLat != 0.0 && elderLng != 0.0) {
                                Log.d("MyApplicationApp", "📍 聚焦到长辈位置：$elderName - lat=$elderLat, lng=$elderLng")
                                
                                // ⭐ 调用 HomeViewModel 的方法，设置地图中心点为长辈位置
                                homeViewModel.focusOnElderLocation(elderName, elderLat, elderLng)
                                
                                // 清除标记
                                elderPrefs.edit().putBoolean("should_focus", false).apply()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MyApplicationApp", "❌ 处理长辈位置聚焦失败", e)
                    }
                    
                    HomeScreen(
                        viewModel = homeViewModel,
                        chatViewModel = chatViewModel,  // ⭐ 关键修复：传入 chatViewModel，确保 WebSocket 连接
                        onNavigateToProfile = { currentDestination = "profile" },
                        onRequestLocationPermission = { },
                        onNavigateToOrder = { orderId -> 
                            // ⭐ 修改：跳转到订单追踪页面（实时显示司机位置、路线）
                            navController.navigate("order_tracking/$orderId")
                        },
                        onNavigateToChat = {
                            // ⭐ 修复：切换到 chat 标签页
                            Log.d("MyApplicationApp", "🤖 点击智能体按钮，切换到聊天标签页")
                            currentDestination = "chat"
                        },
                        onNavigateToOrderTracking = { orderId ->
                            // ⭐ 新增：跳转到行程追踪页面
                            Log.d("MainActivity", "🚀 收到跳转事件，前往行程追踪: orderId=$orderId")
                            navController.navigate("order_tracking/$orderId")
                        }
                    )
                }
                "favorites" -> {
                    // ⭐ 修复：根据用户角色显示不同的收藏页面
                    if (isElderMode) {
                        // 长辈端：适老化设计，语音播报、一键分享、确认到达
                        com.example.myapplication.presentation.favorites.ElderFavoritesScreen(
                            viewModel = favoritesViewModel,
                            homeViewModel = homeViewModel,
                            onNavigateToHomeWithDestination = { name, lat, lng ->
                                Log.d("MainActivity", "📍 [长辈端] 从收藏跳转到首页，目的地：$name")
                                currentDestination = "home"
                                homeViewModel.setDestinationFromFavorite(name, lat, lng)
                            },
                            onNavigateToChat = { guardianId, guardianName ->
                                // ⭐ 修复：分享成功后自动跳转到与该亲友的聊天界面
                                Log.d("MainActivity", "💬 [长辈端] 分享成功，跳转到与 $guardianName 的聊天")
                                navController.navigate("private_chat/$guardianId/$guardianName")
                            }
                        )
                    } else {
                        // 普通用户端：使用原 FavoritesScreen，支持添加/编辑/删除
                        FavoritesScreen(
                            viewModel = favoritesViewModel,
                            homeViewModel = homeViewModel,
                            onNavigateToHomeWithDestination = { name, lat, lng ->
                                Log.d("MainActivity", "📍 [普通用户] 从收藏跳转到首页，目的地：$name")
                                currentDestination = "home"
                                homeViewModel.setDestinationFromFavorite(name, lat, lng)
                            }
                        )
                    }
                }
                "chat" -> {
                    // ⭐ 新增：收集长辈/亲友列表
                    val guardianList by homeViewModel.guardianInfoList.collectAsStateWithLifecycle()
                    val elderList by homeViewModel.elderInfoList.collectAsStateWithLifecycle()
                    
                    ChatListScreen(
                        onBackClick = { currentDestination = "home" },
                        onSessionSelected = { sessionId: String ->
                            Log.d("MainActivity", "选择会话: $sessionId")
                            // ⭐ 解析 sessionId，跳转到私聊界面
                            if (sessionId.startsWith("elder_")) {
                                val elderId = sessionId.removePrefix("elder_").toLongOrNull()
                                val elder = elderList.find { it.userId.toString() == elderId.toString() }
                                if (elderId != null && elder != null) {
                                    navController.navigate("private_chat/$elderId/${elder.name}")
                                }
                            } else if (sessionId.startsWith("guardian_")) {
                                val guardianId = sessionId.removePrefix("guardian_").toLongOrNull()
                                val guardian = guardianList.find { it.userId.toString() == guardianId.toString() }
                                if (guardianId != null && guardian != null) {
                                    navController.navigate("private_chat/$guardianId/${guardian.name}")
                                }
                            }
                        },
                        onNavigateToAgent = {
                            currentDestination = "agent_chat"
                        },
                        guardianList = if (isElderMode) guardianList else emptyList(),  // ⭐ 长辈模式显示亲友
                        elderList = if (!isElderMode) elderList else emptyList(),  // ⭐ 普通模式显示长辈
                        showBackButton = false  // ⭐ 底部导航不显示返回键
                    )
                }
                "agent_chat" -> {
                    ChatScreen(
                        viewModel = chatViewModel,
                        onNavigateToOrder = { orderId: Long ->
                            // ⭐ 修改：跳转到订单追踪页面（实时显示司机位置、路线）
                            navController.navigate("order_tracking/$orderId")
                        },
                        chatMode = ChatMode.AGENT,
                        isElderMode = isElderMode,  // ⭐ 修复：传递长辈模式状态
                        showBackButton = true,  // ⭐ 新增：显示返回按钮
                        onBackClick = { 
                            // ⭐ 修复：从智能体助手返回到聊天列表页
                            currentDestination = "chat"
                        }
                    )
                }
                "profile" -> {
                    ProfileScreen(
                        onNavigateToOrderList = onNavigateToOrderList,
                        onNavigateToGuardian = {
                            // ⭐ 跳转到亲情守护管理页面
                            Log.d("MainActivity", "👨‍👩‍ === 点击亲情守护，跳转至 GuardianManagementScreen ===")
                            currentDestination = "guardian_management"
                        },
                        onNavigateToAccount = {
                            // ⭐ 跳转到账号安全页面
                            Log.d("MainActivity", "🔒 === 点击账号安全，跳转至 AccountSecurityScreen ===")
                            currentDestination = "account_security"
                        },
                        onLogout = {
                            // ⭐ 修复：退出登录后返回登录页面
                            Log.d("MainActivity", "🚪 === 退出登录，返回登录页面 ===")
                            navController.navigate("login") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    )
                }
                "guardian_management" -> {
                    // ⭐ 亲情守护管理页面
                    com.example.myapplication.presentation.guardian.GuardianManagementScreen(
                        onNavigateBack = { currentDestination = "profile" }
                    )
                }
                "account_security" -> {
                    // ⭐ 账号安全页面
                    com.example.myapplication.presentation.account.AccountSecurityScreen(
                        onNavigateBack = { currentDestination = "profile" }
                    )
                }
                "travel_records" -> {
                    // ⭐ 出行记录页面(行程凭证)
                    com.example.myapplication.presentation.favorites.TravelRecordsScreen(
                        viewModel = favoritesViewModel,
                        onNavigateBack = { currentDestination = "profile" }
                    )
                }
                else -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        chatViewModel = chatViewModel,  // ⭐ 关键修复：传入 chatViewModel，确保 WebSocket 连接
                        onNavigateToProfile = { currentDestination = "profile" },
                        onRequestLocationPermission = { },
                        onNavigateToOrder = { orderId -> 
                            // ⭐ 修改：跳转到订单追踪页面（实时显示司机位置、路线）
                            navController.navigate("order_tracking/$orderId")
                        },
                        onNavigateToChat = {
                            // ⭐ 修复：切换到 chat 标签页
                            Log.d("MyApplicationApp", "🤖 点击智能体按钮，切换到聊天标签页")
                            currentDestination = "chat"
                        },
                        onNavigateToOrderTracking = { orderId ->
                            // ⭐ 新增：跳转到行程追踪页面
                            Log.d("MainActivity", "🚀 收到跳转事件，前往行程追踪: orderId=$orderId")
                            navController.navigate("order_tracking/$orderId")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AnxinChuxingTheme {
        Greeting("Android")
    }
}