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
import androidx.lifecycle.compose.collectAsStateWithLifecycle  // ⭐ 新增：用于collect StateFlow
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.amap.api.maps.MapsInitializer
import com.example.myapplication.core.datastore.TokenManager  // ⭐ 新增：用于检查登录状态
import com.example.myapplication.presentation.chat.ChatScreen
import com.example.myapplication.presentation.chat.ChatViewModel
import com.example.myapplication.presentation.chat.ChatMode
import com.example.myapplication.presentation.chat.ChatListScreen
import com.example.myapplication.presentation.chat.PrivateChatScreen  // ⭐ 新增：亲友私聊界面
import com.example.myapplication.presentation.home.HomeScreen
import com.example.myapplication.presentation.home.HomeViewModel
import com.example.myapplication.presentation.login.LoginScreen
import com.example.myapplication.presentation.order.OrderDetailScreen
import com.example.myapplication.presentation.order.OrderListScreen
import com.example.myapplication.presentation.profile.ProfileScreen
import com.example.myapplication.presentation.account.AccountNotCompleteScreen  // ⭐ 新增：账号未完善提示页面
import com.example.myapplication.service.AgentFloatService
import com.example.myapplication.ui.theme.AnxinChuxingTheme
import com.example.myapplication.debug.WebSocketDebugMonitor
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.myapplication.R
import com.example.myapplication.BuildConfig  // ⭐ 显式导入 BuildConfig

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    private var hasRequestedPermission = false
    
    private val homeViewModel: HomeViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private val tokenManager: TokenManager by lazy { TokenManager(applicationContext) }  // ⭐ 新增：Token管理器
    
    private var _navigateToChat by mutableStateOf(false)
    // ⭐ 新增：全局长辈模式状态（用于传递给 ChatScreen）
    private var _isElderMode by mutableStateOf(false)

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

                    // ⭐ 新增：根据登录状态决定起始页面
                    val startDestination = rememberSaveable { 
                        val hasToken = tokenManager.getToken() != null
                        val guardMode = tokenManager.getGuardMode()
                        Log.d("MainActivity", "检查登录状态：hasToken=$hasToken, guardMode=$guardMode")
                        if (hasToken) "main" else "login"
                    }
                    
                    // ⭐ 修复：移除重复的自动登录验证，由NormalHomeScreen/ElderSimplifiedScreen自行处理
                    // HomeViewModel.checkElderMode() 会在进入 main 路由时自动调用
                    Log.d("MainActivity", "✅ 启动目标页面: $startDestination")
                    
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") {
                            val loginViewModel: com.example.myapplication.presentation.login.LoginViewModel = 
                                androidx.hilt.navigation.compose.hiltViewModel()
                            
                            // ⭐ 修复：使用 rememberSaveable 保存状态，避免 recomposition 时重复调用
                            var hasReset by rememberSaveable { mutableStateOf(false) }
                            
                            LaunchedEffect(Unit) {
                                if (!hasReset) {
                                    Log.d("MainActivity", "✅ 进入登录页面，重置所有状态")
                                    loginViewModel.resetAllState()
                                    hasReset = true  // ⭐ 标记为已重置，防止重复调用
                                }
                            }
                            
                            LoginScreen(
                                viewModel = loginViewModel,
                                onLoginSuccess = {
                                    try {
                                        Log.d("MainActivity", "✅ 收到登录成功回调，准备跳转到主页")
                                        navController.navigate("main") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                        Log.d("MainActivity", "✅ 导航到 main 路由成功")
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "❌ 导航失败", e)
                                        e.printStackTrace()
                                    }
                                },
                                onRequestFloatPermission = {
                                    Log.d("MainActivity", "📱 请求悬浮窗权限（暂不实现）")
                                    // TODO: 后续实现悬浮窗权限请求
                                }
                            )
                        }
                        composable("main") {
                            val context = LocalContext.current
                            
                            // ⭐ 修复：移除重复的 checkElderMode 调用，由 HomeScreen 统一处理
                            // HomeViewModel.checkElderMode() 会在 HomeScreen 初始化时自动调用
                            
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
                            
                            // ⭐ 新增：监听长辈模式状态变化
                            LaunchedEffect(Unit) {
                                homeViewModel.isElderMode.collect { isElder ->
                                    _isElderMode = isElder
                                    Log.d("MainActivity", "长辈模式状态更新：$isElder")
                                }
                            }
                            
                            MyApplicationApp(
                                homeViewModel = homeViewModel,
                                chatViewModel = chatViewModel,
                                isElderMode = _isElderMode,  // ⭐ 传递长辈模式状态
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
                                onNavigateToChat = {
                                    navController.navigate("chat") {
                                        popUpTo("main") {
                                            inclusive = false
                                        }
                                    }
                                },
                                onNavigateToOrderTracking = { orderId ->
                                    navController.navigate("order_tracking/$orderId") {
                                        popUpTo("main") {
                                            inclusive = false
                                        }
                                    }
                                }
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
                        composable("orderList") {
                            OrderListScreen(
                                onOrderClick = { orderId ->
                                    navController.navigate("order_detail/$orderId")
                                }
                            )
                        }
                        // ⭐ 新增：行程实时追踪页面
                        composable("order_tracking/{orderId}") { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull()
                            if (orderId != null) {
                                com.example.myapplication.presentation.orderTracking.OrderTrackingScreen(
                                    orderId = orderId,
                                    onBackClick = { navController.popBackStack() }
                                )
                            } else {
                                Text("Invalid order ID")
                            }
                        }
                        composable("chat") {
                            // ⭐ 修复：设置 ChatViewModel 的长辈模式状态
                            chatViewModel.isElderMode = _isElderMode
                            Log.d("MainActivity", "🔧 设置 chatViewModel.isElderMode = $_isElderMode")
                            
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateToOrder = { orderId: Long ->
                                    if (orderId == -1L) {
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate("order_detail/$orderId")
                                    }
                                },
                                chatMode = ChatMode.AGENT,
                                isElderMode = _isElderMode  // ⭐ 传递长辈模式状态
                            )
                        }
                        composable("agent_chat") {
                            ChatListScreen(
                                onBackClick = { navController.popBackStack() },
                                onSessionSelected = { sessionId: String ->
                                    Log.d(TAG, "选择会话: $sessionId")
                                },
                                onNavigateToAgent = {
                                    navController.navigate("chat")
                                },
                                showBackButton = true  // ⭐ 长辈端显示返回键
                            )
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
        
        // ⭐ 新增：停止后台定位追踪服务
        try {
            val trackingIntent = Intent(this, com.example.myapplication.service.LocationTrackingService::class.java).apply {
                action = com.example.myapplication.service.LocationTrackingService.ACTION_STOP_TRACKING
            }
            startService(trackingIntent)
            Log.d(TAG, "Location tracking service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location tracking service", e)
        }
        
        Log.d(TAG, "Keep WebSocket connection, do not disconnect")
    }
}

@Composable
fun MyApplicationApp(
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    isElderMode: Boolean = false,  // ⭐ 新增：长辈模式状态参数
    onNavigateToOrderDetail: (Long) -> Unit = {},
    onNavigateToOrderList: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToOrderTracking: (Long) -> Unit = {}  // ⭐ 新增：跳转到行程追踪页面
) {
    var currentDestination by rememberSaveable { mutableStateOf<String?>("home") }
    // ⭐ 新增：保存选中的亲友信息（用于私聊）
    var selectedGuardianId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedGuardianName by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    
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
            // ⭐ 登录页面不显示底部导航栏
            if (currentDestination != "login") {
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
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            when (currentDestination) {
                "login" -> {
                    val loginViewModel: com.example.myapplication.presentation.login.LoginViewModel = 
                        androidx.hilt.navigation.compose.hiltViewModel()
                    
                    // ⭐ 修复：移除重复的 resetAllState() 调用，避免登录后状态被重置
                    // resetAllState() 只在 NavHost 的 "login" 路由中调用一次
                    
                    LoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = {
                            // ⭐ 登录成功后跳转到首页
                            currentDestination = "home"
                        }
                    )
                }
                "home" -> {
                    // ⭐ 新增：检查账号是否完善
                    val loginViewModel: com.example.myapplication.presentation.login.LoginViewModel = hiltViewModel()
                    val needCompleteProfile by loginViewModel.needCompleteProfile.collectAsStateWithLifecycle()
                    
                    if (needCompleteProfile) {
                        // ⚠️ 账号未完善，显示提示页面
                        AccountNotCompleteScreen(
                            onCompleteClick = {
                                Log.d("MainActivity", "⚠️ 用户点击去完善账号")
                                currentDestination = "login"
                            }
                        )
                    } else {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToProfile = { currentDestination = "profile" },
                            onRequestLocationPermission = { },
                            onNavigateToOrder = { orderId -> onNavigateToOrderDetail(orderId.toLongOrNull() ?: 0L) },
                            onNavigateToChat = onNavigateToChat,
                            onLogout = { 
                                // ⭐ TODO: 实现退出登录逻辑
                                currentDestination = "login"
                            },
                            onNavigateToOrderTracking = { orderId ->
                                currentDestination = "order_tracking"
                            }
                        )
                    }
                }
                "favorites" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无收藏",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击地图标记常用地点",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                "chat" -> {
                    // ⭐ 获取亲友列表（长辈端）
                    val guardianList by homeViewModel.guardianInfoList.collectAsStateWithLifecycle()
                    
                    // ⭐ 获取长辈列表（普通端）
                    val profileViewModel: com.example.myapplication.presentation.profile.ProfileViewModel = hiltViewModel()
                    val elderList by profileViewModel.elderInfoList.collectAsStateWithLifecycle()
                    
                    // ⭐ 加载长辈列表（如果是普通账户）
                    LaunchedEffect(Unit) {
                        if (elderList.isEmpty()) {
                            Log.d("MainActivity", "🔄 加载长辈列表...")
                            profileViewModel.loadElderInfoList()
                        }
                    }
                    
                    ChatListScreen(
                        onBackClick = { currentDestination = "home" },
                        onSessionSelected = { sessionId: String ->
                            Log.d("MainActivity", "选择会话: $sessionId")
                            // ⭐ 如果是亲友聊天，跳转到对应的聊天界面
                            if (sessionId.startsWith("guardian_")) {
                                val guardianId = sessionId.removePrefix("guardian_").toLongOrNull()
                                if (guardianId != null) {
                                    // ⭐ 找到对应的亲友信息并保存
                                    val guardian = guardianList.find { it.userId == guardianId }
                                    if (guardian != null) {
                                        selectedGuardianId = guardian.userId
                                        selectedGuardianName = guardian.name
                                        Log.d("MainActivity", "选中亲友: id=${guardian.userId}, name=${guardian.name}")
                                        currentDestination = "friend_chat"
                                    }
                                }
                            }
                            // ⭐ 新增：如果是长辈聊天，跳转到对应的聊天界面
                            else if (sessionId.startsWith("elder_")) {
                                val elderId = sessionId.removePrefix("elder_").toLongOrNull()
                                if (elderId != null) {
                                    // ⭐ 找到对应的长辈信息并保存
                                    val elder = elderList.find { it.userId == elderId }
                                    if (elder != null) {
                                        selectedGuardianId = elder.userId  // ⭐ 复用 selectedGuardianId
                                        selectedGuardianName = elder.name ?: "长辈"
                                        Log.d("MainActivity", "选中长辈: id=${elder.userId}, name=${elder.name}")
                                        currentDestination = "friend_chat"
                                    }
                                }
                            }
                        },
                        onNavigateToAgent = {
                            currentDestination = "agent_chat"
                        },
                        guardianList = guardianList,  // ⭐ 传递亲友列表（长辈端）
                        elderList = elderList,  // ⭐ 传递长辈列表（普通端）
                        showBackButton = false  // ⭐ 普通用户不显示返回键
                    )
                }
                "agent_chat" -> {
                    // ⭐ 新增：检查账号是否完善
                    val loginViewModel: com.example.myapplication.presentation.login.LoginViewModel = hiltViewModel()
                    val needCompleteProfile by loginViewModel.needCompleteProfile.collectAsStateWithLifecycle()
                    
                    if (needCompleteProfile) {
                        AccountNotCompleteScreen(
                            onCompleteClick = {
                                currentDestination = "login"
                            }
                        )
                    } else {
                        // ⭐ 设置 ChatViewModel 的长辈模式状态
                        chatViewModel.isElderMode = isElderMode
                        
                        ChatScreen(
                            viewModel = chatViewModel,
                            onNavigateToOrder = onNavigateToOrderDetail,
                            chatMode = ChatMode.AGENT,
                            isElderMode = isElderMode,  // ⭐ 使用参数传递的状态
                            onSwitchChatMode = { mode ->
                                when (mode) {
                                    ChatMode.FRIEND -> currentDestination = "chat"  // 跳转到聊天列表选择亲友
                                    else -> {}
                                }
                            },
                            onBackClick = {
                                // ⭐ 智能体聊天返回到首页
                                currentDestination = "home"
                            }
                        )
                    }
                }
                "friend_chat" -> {
                    // ⭐ 新增：亲友聊天界面（真正的P2P私聊）
                    PrivateChatScreen(
                        viewModel = hiltViewModel(),
                        guardianId = selectedGuardianId ?: 0L,
                        guardianName = selectedGuardianName ?: "亲友",
                        isElderMode = isElderMode,  // ⭐ 传递长辈模式状态
                        onBackClick = {
                            // ⭐ 亲友聊天返回到聊天列表（选择亲友的界面）
                            currentDestination = "chat"
                        }
                    )
                }
                "profile" -> {
                    // ⭐ 在 Composable 上下文中获取 ViewModel
                    val loginViewModel: com.example.myapplication.presentation.login.LoginViewModel = 
                        androidx.hilt.navigation.compose.hiltViewModel()
                    
                    ProfileScreen(
                        onNavigateToOrderList = onNavigateToOrderList,
                        onNavigateToGuardian = { currentDestination = "guardian" },  // ⭐ 跳转到亲情守护页面
                        onNavigateToAccount = { currentDestination = "account" },   // ⭐ 跳转到账号安全页面
                        onLogout = { 
                            // ⭐ 退出登录后：1. 断开 WebSocket  2. 重置 LoginViewModel 状态  3. 跳转到登录页
                            Log.d("MainActivity", "🔌 退出登录，断开 WebSocket...")
                            chatViewModel.disconnectWebSocket()
                            loginViewModel.resetAllState()
                            Log.d("MainActivity", "✅ 退出登录完成")
                            
                            currentDestination = "login"
                        }
                    )
                }
                "guardian" -> {
                    // ⭐ 亲情守护管理页面
                    com.example.myapplication.presentation.guardian.GuardianManagementScreen(
                        onNavigateBack = { currentDestination = "profile" },
                        onBindElder = { 
                            // TODO: 打开绑定长辈对话框或跳转到绑定页面
                            Log.d("MainActivity", "TODO: 打开绑定长辈对话框")
                        }
                    )
                }
                "account" -> {
                    // ⭐ 账号安全管理页面
                    com.example.myapplication.presentation.account.AccountSecurityScreen(
                        onNavigateBack = { currentDestination = "profile" }
                    )
                }
                else -> {
                    // ⭐ 在 Composable 上下文中获取 ViewModel
                    val loginViewModel: com.example.myapplication.presentation.login.LoginViewModel = 
                        androidx.hilt.navigation.compose.hiltViewModel()
                    
                    HomeScreen(
                        viewModel = homeViewModel,
                        chatViewModel = chatViewModel,  // ⭐ 关键修复：传入 ChatViewModel，确保使用同一个实例
                        onNavigateToProfile = { currentDestination = "profile" },
                        onRequestLocationPermission = { },
                        onNavigateToOrder = { orderId -> onNavigateToOrderDetail(orderId.toLongOrNull() ?: 0L) },
                        onNavigateToChat = onNavigateToChat,
                        onLogout = { 
                            // ⭐ 退出登录逻辑：断开 WebSocket + 重置状态
                            Log.d("MainActivity", "🔌 退出登录，断开 WebSocket...")
                            chatViewModel.disconnectWebSocket()
                            loginViewModel.resetAllState()
                            Log.d("MainActivity", "✅ 退出登录完成")
                            currentDestination = "login"
                        },
                        onNavigateToOrderTracking = { orderId ->
                            currentDestination = "order_tracking"
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