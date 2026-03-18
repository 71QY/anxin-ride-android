package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat  // 使用 AutoMirrored 版本
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.presentation.chat.ChatScreen
import com.example.myapplication.presentation.home.HomeScreen
import com.example.myapplication.presentation.login.LoginScreen
import com.example.myapplication.presentation.order.OrderDetailScreen
import com.example.myapplication.presentation.order.OrderListScreen
import com.example.myapplication.presentation.profile.ProfileScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import com.amap.api.maps.MapsInitializer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=af1a4954")

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("main") {
                        MyApplicationApp(
                            onNavigateToOrderDetail = { orderId ->
                                navController.navigate("order_detail/$orderId")
                            },
                            onNavigateToOrderList = {
                                navController.navigate("orderList")
                            },
                            onNavigateToChat = {
                                navController.navigate("chat")
                            }
                        )
                    }
                    composable("order_detail/{orderId}") { backStackEntry ->
                        val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull()
                        if (orderId != null) {
                            OrderDetailScreen(orderId = orderId)
                        } else {
                            Text("无效的订单ID")
                        }
                    }
                    composable("orderList") {
                        OrderListScreen(
                            onOrderClick = { orderId ->
                                navController.navigate("order_detail/$orderId")
                            }
                        )
                    }
                    // ✅ 修正后的聊天目的地
                    composable("chat") {
                        ChatScreen(
                            onNavigateToOrder = { orderId: Long ->  // 显式指定类型
                                navController.navigate("order_detail/$orderId")
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@PreviewScreenSizes
@Composable
fun MyApplicationApp(
    onNavigateToOrderDetail: (Long) -> Unit = {},
    onNavigateToOrderList: () -> Unit = {},
    onNavigateToChat: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            painterResource(destination.icon),
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
            // 使用自动镜像图标
            item(
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "智能体") },
                label = { Text("智能体") },
                selected = false,
                onClick = onNavigateToChat
            )
        }
    ) {
        when (currentDestination) {
            AppDestinations.HOME -> HomeScreen(
                onNavigateToOrder = onNavigateToOrderDetail
            )
            AppDestinations.FAVORITES -> Greeting("Favorites")
            AppDestinations.PROFILE -> ProfileScreen(
                onNavigateToOrderList = onNavigateToOrderList
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("首页", R.drawable.ic_home),
    FAVORITES("收藏", R.drawable.ic_favorite),
    PROFILE("个人", R.drawable.ic_account_box),
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