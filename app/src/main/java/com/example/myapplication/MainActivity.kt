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
import com.example.myapplication.presentation.chat.ChatMode  // ⭐ 新增：导入聊天模式枚举
import com.example.myapplication.presentation.chat.ChatListScreen  // ⭐ 新增：导入聊天列表界面
import com.example.myapplication.presentation.home.HomeScreen
import com.example.myapplication.presentation.home.HomeViewModel
import com.example.myapplication.presentation.login.LoginScreen
import com.example.myapplication.presentation.order.OrderDetailScreen
import com.example.myapplication.presentation.order.OrderListScreen
import com.example.myapplication.presentation.profile.ProfileScreen
import com.example.myapplication.service.AgentFloatService
import com.example.myapplication.ui.theme.MyApplicationTheme
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
    
    // ⭐ 修改：使用 Activity 的 viewModels() 委托，Hilt 会自动提供 ViewModelFactory
    private val homeViewModel: HomeViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    
    // ⭐ 新增：用于监听导航到聊天界面的请求
    private var _navigateToChat by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ⭐ 修改：同时检查 onCreate 和 intent
        val navigateToChat = intent.getBooleanExtra("navigate_to_chat", false)
        _navigateToChat = navigateToChat

        try {
            // ⭐ 优化：简化初始化流程
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)

            // ⭐ 修改：添加异常处理，避免讯飞 SDK 初始化失败导致崩溃
            try {
                val iflytekAppid = BuildConfig.IFLYTEK_APPID
                Log.d(TAG, "🎤 讯飞 AppID: $iflytekAppid")
                if (iflytekAppid.isNotBlank()) {
                    SpeechUtility.createUtility(this, SpeechConstant.APPID + "=" + iflytekAppid)
                    Log.d(TAG, "✅ 讯飞 SDK 初始化完成（请查看后续日志确认是否成功）")
                } else {
                    Log.e(TAG, "❌ 讯飞 AppID 为空，请检查 build.gradle.kts 配置")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 讯飞 SDK 初始化异常", e)
            }

            enableEdgeToEdge()

            setContent {
                MyApplicationTheme {
                    val navController = rememberNavController()
                    val context = LocalContext.current

                    // ⭐ 新增：应用启动时立即触发定位（在登录成功后）
                    LaunchedEffect(Unit) {
                        Log.d("MainActivity", "🚀 应用启动，准备触发定位")
                    }

                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    Log.d("MainActivity", "✅ 登录成功")
                                    // ⭐ 修改：使用高德地图自带定位，不需要手动启动
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onRequestFloatPermission = {
                                    // ⭐ 修改：暂时禁用悬浮窗权限请求
                                }
                            )
                        }
                        composable("main") {
                            val context = LocalContext.current
                            
                            // ⭐ 新增：监听位置变化并同步到 ChatViewModel
                            LaunchedEffect(Unit) {
                                Log.d("MainActivity", "📍 开始监听位置变化")
                                homeViewModel.currentLocation.collect { location ->
                                    if (location != null) {
                                        Log.d("MainActivity", "📍 收到位置更新：lat=${location.latitude}, lng=${location.longitude}")
                                        chatViewModel.syncLocationFromHome(location.latitude, location.longitude)
                                    } else {
                                        Log.w("MainActivity", "⚠️ 位置为 null")
                                    }
                                }
                            }
                            
                            MyApplicationApp(
                                homeViewModel = homeViewModel,
                                chatViewModel = chatViewModel,
                                onNavigateToOrderDetail = { orderId ->
                                    navController.navigate("order_detail/$orderId")
                                },
                                onNavigateToOrderList = {
                                    // ⭐ 修改：使用 popUpTo 避免重复添加订单列表界面
                                    navController.navigate("orderList") {
                                        popUpTo("main") {
                                            inclusive = false
                                        }
                                    }
                                },
                                onNavigateToChat = {
                                    // ⭐ 修改：使用 popUpTo 避免重复添加聊天界面
                                    navController.navigate("chat") {
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
                                    onBackClick = { navController.popBackStack() }  // ⭐ 新增：返回键回调
                                )
                            } else {
                                Text("无效的订单 ID")
                            }
                        }
                        composable("orderList") {
                            OrderListScreen(
                                onOrderClick = { orderId ->
                                    navController.navigate("order_detail/$orderId")
                                }
                            )
                        }
                        composable("chat") {
                            ChatListScreen(
                                onBackClick = { navController.popBackStack() },
                                onSessionSelected = { sessionId: String ->
                                    Log.d(TAG, "选择会话: $sessionId")
                                },
                                onNavigateToAgent = {
                                    navController.navigate("agent_chat")
                                }
                            )
                        }
                        // ⭐ 新增：智能体聊天页面
                        composable("agent_chat") {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateToOrder = { orderId: Long ->
                                    if (orderId == -1L) {
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate("order_detail/$orderId")
                                    }
                                },
                                chatMode = ChatMode.AGENT
                            )
                        }
                    }
                }
            }

            Log.d(TAG, "=== MainActivity onCreate 完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate 过程中发生异常", e)
            throw e
        }
    }

    // ⭐ 新增：处理 Activity 重启时的 Intent
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navigateToChat = intent.getBooleanExtra("navigate_to_chat", false)
        if (navigateToChat) {
            Log.d(TAG, "onNewIntent: 从悬浮窗进入聊天界面")
            _navigateToChat = true
            // ⭐ 重要：不设置任何 flags，让用户自然留在当前导航栈中
            // Compose 会通过 StateFlow 自动检测到变化并导航到聊天界面
        }
    }

    // ⭐ 新增：在登录成功后请求悬浮窗权限
    fun requestFloatPermissionAfterLogin() {
        lifecycleScope.launch {
            delay(500) // 稍微延迟，等待登录动画完成
            // ⭐ 修改：暂时禁用悬浮窗权限请求
            // requestFloatPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== MainActivity onDestroy ===")
        
        // ⭐ 修改：确保停止悬浮窗服务
        try {
            val intent = Intent(this, AgentFloatService::class.java)
            stopService(intent)
            Log.d(TAG, "悬浮窗服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止悬浮窗服务失败", e)
        }
        
        // ⭐ 重要：不要在 onDestroy 中断开 WebSocket!
        // WebSocket 应该保持连接，除非用户主动退出登录
        Log.d(TAG, "保持 WebSocket 连接，不断开")
        Log.d(TAG, "注意：切换页面不会断开 WebSocket")
    }
}

// ⭐ 修改：接收外部传入的 ViewModel
@Composable
fun MyApplicationApp(
    homeViewModel: HomeViewModel,  // ⭐ 新增参数
    chatViewModel: ChatViewModel,  // ⭐ 新增参数
    onNavigateToOrderDetail: (Long) -> Unit = {},
    onNavigateToOrderList: () -> Unit = {},
    onNavigateToChat: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf<String?>("home") }
    val context = LocalContext.current
    
    // ⭐ 新增：每次切换到主页时刷新定位
    LaunchedEffect(currentDestination) {
        if (currentDestination == "home") {
            Log.d("MyApplicationApp", "🔄 返回主页")
            // ⭐ 修改：使用高德地图自带定位，不需要手动启动
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                // ⭐ 修改：按指定顺序排列导航项，统一图标大小
                NavigationBarItem(
                    selected = currentDestination == "home",
                    onClick = {
                        currentDestination = "home"
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_home),
                            contentDescription = "首页",
                            modifier = Modifier.size(24.dp)  // ⭐ 统一图标大小
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
                            modifier = Modifier.size(24.dp)  // ⭐ 统一图标大小
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
                            contentDescription = "聊天",
                            modifier = Modifier.size(24.dp)  // ⭐ 统一图标大小
                        )
                    },
                    label = { Text("聊天") }
                )
                NavigationBarItem(
                    selected = currentDestination == "profile",
                    onClick = {
                        currentDestination = "profile"
                    },
                    icon = {
                        Icon(
                            painterResource(id = R.drawable.ic_account_box),
                            contentDescription = "个人",
                            modifier = Modifier.size(24.dp)  // ⭐ 统一图标大小
                        )
                    },
                    label = { Text("个人") }
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            when (currentDestination) {
                "home" -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToProfile = { currentDestination = "profile" },
                        onRequestLocationPermission = { },
                        onNavigateToOrder = { orderId -> onNavigateToOrderDetail(orderId.toLongOrNull() ?: 0L) },
                        onNavigateToChat = onNavigateToChat
                    )
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
                            text = "点击地图标记喜欢的地点",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                "chat" -> {
                    ChatListScreen(
                        onBackClick = { currentDestination = "home" },
                        onSessionSelected = { sessionId: String ->
                            // 会话选择逻辑
                        },
                        onNavigateToAgent = {
                            currentDestination = "agent_chat"
                        }
                    )
                }
                "agent_chat" -> {
                    ChatScreen(
                        viewModel = chatViewModel,
                        onNavigateToOrder = onNavigateToOrderDetail,
                        chatMode = ChatMode.AGENT
                    )
                }
                "profile" -> {
                    ProfileScreen(
                        onNavigateToOrderList = onNavigateToOrderList
                    )
                }
                else -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToProfile = { currentDestination = "profile" },
                        onRequestLocationPermission = { },
                        onNavigateToOrder = { orderId -> onNavigateToOrderDetail(orderId.toLongOrNull() ?: 0L) },
                        onNavigateToChat = onNavigateToChat
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
    MyApplicationTheme {
        Greeting("Android")
    }
}