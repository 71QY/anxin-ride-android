package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                            val isElderMode by homeViewModel.isElderMode.collectAsStateWithLifecycle()  // ⭐ 修复：使用 collectAsStateWithLifecycle
                            
                            if (contactId != null) {
                                PrivateChatScreen(
                                    guardianId = contactId,
                                    guardianName = contactName,
                                    onBackClick = { navController.popBackStack() },
                                    isElderMode = isElderMode,
                                    onNavigateToHomeWithDestination = { name, lat, lng ->
                                        // ⭐ 一键填充到打车界面
                                        Log.d("MainActivity", "📍 [私聊] 从聊天跳转到首页，目的地：$name")
                                        
                                        // 保存目的地信息到 SharedPreferences
                                        val prefs = context.getSharedPreferences("taxi_destination", android.content.Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putString("destination_name", name)
                                            .putFloat("destination_lat", lat.toFloat())
                                            .putFloat("destination_lng", lng.toFloat())
                                            .putBoolean("should_apply", true)
                                            .apply()
                                        
                                        // ⭐ 修复：通过 NavHostController 返回到 main 路由
                                        navController.navigate("main") {
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
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
                
                // ⭐ 修复：将地点信息保存到 SharedPreferences，供 PrivateChatScreen 读取
                val chatPrefs = context.getSharedPreferences("chat_location_$elderId", android.content.Context.MODE_PRIVATE)
                chatPrefs.edit()
                    .putString("location_name", favoriteName)
                    .putString("location_address", favoriteAddress)
                    .putFloat("location_lat", favoriteLat.toFloat())
                    .putFloat("location_lng", favoriteLng.toFloat())
                    .putLong("timestamp", System.currentTimeMillis())
                    .apply()
                
                Log.d("MyApplicationApp", "💾 已保存地点信息：$favoriteName, lat=$favoriteLat, lng=$favoriteLng")
                
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
        // ⭐ 修复：将 isElderMode 定义移到 when 之前，确保所有分支都能访问
        val isElderMode by homeViewModel.isElderMode.collectAsStateWithLifecycle()
        
        // ⭐ 新增：全局代叫车确认弹窗（长辈端在任何界面都能收到）
        val proxyOrderRequest by homeViewModel.proxyOrderRequest.collectAsStateWithLifecycle()
        var showGlobalProxyOrderDialog by remember { mutableStateOf(false) }
        var pendingOrderId by remember { mutableStateOf<Long?>(null) }
        var requesterName by remember { mutableStateOf("") }
        var destination by remember { mutableStateOf("") }
        
        LaunchedEffect(proxyOrderRequest) {
            if (isElderMode && proxyOrderRequest != null) {
                Log.d("MainActivity", "🔔 [全局] 收到代叫车请求：${proxyOrderRequest!!.orderId}")
                pendingOrderId = proxyOrderRequest!!.orderId
                requesterName = proxyOrderRequest!!.requesterName
                destination = proxyOrderRequest!!.destination
                showGlobalProxyOrderDialog = true
                
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
                                homeViewModel.confirmProxyOrder(orderId, confirmed = true)
                                android.widget.Toast.makeText(context, "✅ 已接受叫车请求", android.widget.Toast.LENGTH_LONG).show()
                            }
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
                                homeViewModel.confirmProxyOrder(orderId, confirmed = false, rejectReason = "暂时不需要")
                                android.widget.Toast.makeText(context, "⚠️ 已拒绝叫车请求", android.widget.Toast.LENGTH_LONG).show()
                            }
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
                            
                            if (!destName.isNullOrBlank() && destLat != 0.0 && destLng != 0.0) {
                                Log.d("MyApplicationApp", "📍 应用来自聊天的目的地：$destName")
                                homeViewModel.setDestinationFromFavorite(destName, destLat, destLng)
                                
                                // 清除标记
                                prefs.edit().putBoolean("should_apply", false).apply()
                            }
                        }
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
                        onNavigateToTravelRecords = {
                            // ⭐ 跳转到出行记录页面
                            Log.d("MainActivity", "🚗 === 点击出行记录，跳转至 TravelRecordsScreen ===")
                            currentDestination = "travel_records"
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