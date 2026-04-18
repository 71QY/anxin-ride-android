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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val navigateToChat = intent.getBooleanExtra("navigate_to_chat", false)
        _navigateToChat = navigateToChat

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

                    // ⭐ 关键修复：自动登录逻辑 - 在 composable("main") 内部创建 ViewModel
                    // 默认从登录页开始，登录后再创建 ViewModel
                    val startDestination = "login"
                    
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
                                    isElderMode = isElderMode
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
        
        Column(modifier = Modifier.padding(paddingValues)) {
            when (currentDestination) {
                "home" -> {
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
                    // ⭐ 收藏常用地点页面
                    FavoritesScreen(
                        viewModel = favoritesViewModel,  // ⭐ 修复：使用顶层创建的 ViewModel，防止 Tab 切换时销毁
                        homeViewModel = homeViewModel,  // ⭐ 关键修复：传入 homeViewModel，确保位置同步
                        onNavigateToHomeWithDestination = { name, lat, lng ->
                            Log.d("MainActivity", "📍 从收藏跳转到首页，目的地：$name, lat=$lat, lng=$lng")
                            currentDestination = "home"
                            // ⭐ 通过 HomeViewModel 设置目的地
                            homeViewModel.setDestinationFromFavorite(name, lat, lng)
                        }
                    )
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
                            Log.d("MainActivity", "👨‍👩‍👧 === 点击亲情守护，跳转至 GuardianManagementScreen ===")
                            currentDestination = "guardian_management"
                        },
                        onNavigateToAccount = {
                            // ⭐ 跳转到账号安全页面
                            Log.d("MainActivity", "🔒 === 点击账号安全，跳转至 AccountSecurityScreen ===")
                            currentDestination = "account_security"
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