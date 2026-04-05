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
import com.example.myapplication.presentation.chat.ChatMode
import com.example.myapplication.presentation.chat.ChatListScreen
import com.example.myapplication.presentation.home.HomeScreen
import com.example.myapplication.presentation.home.HomeViewModel
import com.example.myapplication.presentation.login.LoginScreen
import com.example.myapplication.presentation.order.OrderDetailScreen
import com.example.myapplication.presentation.order.OrderListScreen
import com.example.myapplication.presentation.profile.ProfileScreen
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
    
    private val homeViewModel: HomeViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    
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

                    NavHost(navController = navController, startDestination = "login") {
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
                        composable("chat") {
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
    onNavigateToOrderDetail: (Long) -> Unit = {},
    onNavigateToOrderList: () -> Unit = {},
    onNavigateToChat: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf<String?>("home") }
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
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination == "home",
                    onClick = {
                        currentDestination = "home"
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_home),
                            contentDescription = "Home",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentDestination == "favorites",
                    onClick = {
                        currentDestination = "favorites"
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Favorites",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Favorites") }
                )
                NavigationBarItem(
                    selected = currentDestination == "chat",
                    onClick = {
                        currentDestination = "chat"
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = currentDestination == "profile",
                    onClick = {
                        currentDestination = "profile"
                    },
                    icon = {
                        Icon(
                            painterResource(id = R.drawable.ic_account_box),
                            contentDescription = "Profile",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Profile") }
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
                            text = "No favorites yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap map to mark favorite locations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                "chat" -> {
                    ChatListScreen(
                        onBackClick = { currentDestination = "home" },
                        onSessionSelected = { sessionId: String ->
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
    AnxinChuxingTheme {
        Greeting("Android")
    }
}