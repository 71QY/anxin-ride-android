# 安心出行 Android 前端 - 设计与开发文档

## 📋 项目概述

**项目名称**: 安心出行 (AnXin Travel)  
**项目类型**: Android 移动端应用  
**架构模式**: MVVM + Clean Architecture  
**UI 框架**: Jetpack Compose  
**开发语言**: Kotlin  
**JDK 版本**: JDK 17  
**最低 SDK**: API 26 (Android 8.0)  
**目标 SDK**: API 34 (Android 14)  

---

## 🏗️ 技术栈

### 核心框架
- **Kotlin**: 1.9.22
- **Android Gradle Plugin**: 8.1.0
- **Jetpack Compose BOM**: 2024.02.00
- **Compose Compiler**: 1.5.8 (兼容 Kotlin 1.9.22)

### 依赖注入
- **Hilt**: 2.49
- **Hilt Navigation Compose**: 1.1.0

### 网络通信
- **Retrofit**: 2.9.0
- **OkHttp**: 4.12.0 (含 Logging Interceptor)
- **Gson**: 2.10.1
- **Kotlinx Serialization JSON**: 1.6.3

### 异步编程
- **Kotlin Coroutines**: 1.7.3 (core + android)

### 数据存储
- **DataStore Preferences**: 1.0.0

### UI 组件
- **Material Design 3**: Compose Material3
- **Coil**: 2.5.0 (图片加载)
- **Accompanist Permissions**: 0.32.0 (权限管理)

### 地图与语音
- **高德地图 SDK**: 11.1.0 (本地 AAR)
- **讯飞语音 SDK**: Msc.jar (本地 JAR)

### 构建优化
- **kapt**: Kotlin 注解处理
- **Parallel Build**: 启用
- **Build Cache**: 启用
- **JVM Args**: `-Xmx8192m -XX:MaxMetaspaceSize=2048m`

---

## 📁 项目结构

```
MyApplication/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/myapplication/
│   │   │   ├── core/                    # 核心层
│   │   │   │   ├── datastore/           # 数据持久化
│   │   │   │   │   └── TokenManager.kt  # Token 管理
│   │   │   │   ├── network/             # 网络层
│   │   │   │   │   ├── ApiService.kt    # API 接口定义
│   │   │   │   │   ├── NetworkModule.kt # Hilt 网络模块
│   │   │   │   │   └── Result.kt        # 统一响应封装
│   │   │   │   ├── utils/               # 工具类
│   │   │   │   └── websocket/           # WebSocket 客户端
│   │   │   │       └── WebSocketClient.kt
│   │   │   ├── data/                    # 数据层
│   │   │   │   ├── model/               # 数据模型
│   │   │   │   │   ├── UserProfile.kt
│   │   │   │   │   ├── Order.kt
│   │   │   │   │   ├── PoiData.kt
│   │   │   │   │   ├── ChatMessage.kt
│   │   │   │   │   └── ...
│   │   │   │   └── repository/          # 数据仓库
│   │   │   │       ├── AgentRepository.kt
│   │   │   │       └── OrderRepository.kt
│   │   │   ├── di/                      # 依赖注入模块
│   │   │   │   ├── NetworkModule.kt
│   │   │   │   └── RepositoryModule.kt
│   │   │   ├── presentation/            # 表现层 (UI + ViewModel)
│   │   │   │   ├── chat/                # 智能体聊天模块
│   │   │   │   │   ├── ChatScreen.kt
│   │   │   │   │   └── ChatViewModel.kt
│   │   │   │   ├── home/                # 主页模块
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   └── HomeViewModel.kt
│   │   │   │   ├── login/               # 登录模块
│   │   │   │   │   ├── LoginScreen.kt
│   │   │   │   │   └── LoginViewModel.kt
│   │   │   │   ├── order/               # 订单模块
│   │   │   │   │   ├── OrderDetailScreen.kt
│   │   │   │   │   ├── OrderListScreen.kt
│   │   │   │   │   └── OrderViewModel.kt
│   │   │   │   └── profile/             # 个人中心模块
│   │   │   │       ├── ProfileScreen.kt
│   │   │   │       └── ProfileViewModel.kt
│   │   │   ├── service/                 # 后台服务
│   │   │   │   └── AgentFloatService.kt # 智能体悬浮窗服务
│   │   │   ├── map/                     # 地图组件
│   │   │   │   └── MapViewComposable.kt
│   │   │   ├── ui/theme/                # 主题配置
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Type.kt
│   │   │   ├── MainActivity.kt          # 主 Activity
│   │   │   └── MyApplication.kt         # Application 类
│   │   ├── jniLibs/                     # 原生库
│   │   │   ├── arm64-v8a/
│   │   │   └── armeabi-v7a/
│   │   ├── res/                         # 资源文件
│   │   └── AndroidManifest.xml
│   ├── libs/                            # 第三方库
│   │   ├── amap-full-11.1.0.aar
│   │   └── Msc.jar
│   └── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 🔧 环境配置

### JDK 配置
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=2048m -XX:+UseParallelGC -Dfile.encoding=UTF-8
```

### 编译选项
```kotlin
// app/build.gradle.kts
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
}
```

### 后端地址配置
```kotlin
// app/build.gradle.kts - defaultConfig
val apiBaseUrl = project.findProperty("api.baseUrl")?.toString() ?: "http://10.237.36.80:8080/api/"
val websocketUrl = project.findProperty("websocket.url")?.toString() ?: "ws://10.237.36.80:8080/ws/agent"
val amapKey = project.findProperty("amap.key")?.toString() ?: ""
val iflytekAppid = project.findProperty("iflytek.appid")?.toString() ?: ""

buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
buildConfigField("String", "WEBSOCKET_URL", "\"$websocketUrl\"")
buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
buildConfigField("String", "IFLYTEK_APPID", "\"$iflytekAppid\"")
```

---

## 🎨 架构设计

### MVVM 架构图
```
┌─────────────────────────────────────┐
│         Presentation Layer          │
│  ┌──────────┐    ┌──────────────┐  │
│  │  Screen   │◄──►│  ViewModel  │  │
│  │ (Compose)│    │ (StateFlow)  │  │
│  └──────────┘    └──────┬───────┘  │
└─────────────────────────┼──────────┘
                          │
┌─────────────────────────┼──────────┐
│         Data Layer      │          │
│  ┌──────────┐    ┌──────▼───────┐  │
│  │Repository│◄──►│  ApiService  │  │
│  │          │    │  (Retrofit)  │  │
│  └──────────┘    └──────────────┘  │
└─────────────────────────────────────┘
```

### 核心设计原则

1. **单向数据流 (Unidirectional Data Flow)**
   - UI 状态由 ViewModel 通过 StateFlow 管理
   - UI 事件通过回调函数传递给 ViewModel
   - 使用 `collectAsStateWithLifecycle()` 确保生命周期安全

2. **依赖注入 (Hilt)**
   - `@HiltViewModel`: ViewModel 注入
   - `@Inject`: 构造函数注入
   - `@Module` + `@Provides`: 提供依赖实例

3. **协程作用域管理**
   - ViewModel 中使用 `viewModelScope`
   - IO 操作切换到 `Dispatchers.IO`
   - 自动取消,避免内存泄漏

---

## 📡 网络层设计

### ApiService 接口定义
[ApiService.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/core/network/ApiService.kt)

```kotlin
interface ApiService {
    // 认证相关
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Result<LoginResponse>
    
    @POST("auth/send-code")
    suspend fun sendCode(@Query("phone") phone: String): Result<Unit>
    
    // 用户相关
    @GET("user/profile")
    suspend fun getUserProfile(): Result<UserProfile>
    
    @Multipart
    @POST("user/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Result<AvatarResponse>
    
    // 智能体相关
    @POST("agent/search")
    suspend fun searchDestination(@Body request: AgentSearchRequest): Result<AgentResponse>
    
    @POST("agent/image")
    suspend fun agentImage(@Body request: AgentImageRequest): Result<AgentResponse>
    
    @POST("agent/confirm")
    suspend fun confirmSelection(@Body request: AgentConfirmRequest): Result<AgentResponse>
    
    // 订单相关
    @POST("order/create")
    suspend fun createOrder(@Body request: CreateOrderRequest): Result<OrderResponse>
    
    @GET("order/list")
    suspend fun getOrderList(): Result<List<Order>>
}
```

### 统一响应封装
```kotlin
data class Result<T>(
    val code: Int,
    val message: String,
    val data: T?,
    val success: Boolean = code == 200
) {
    fun isSuccess(): Boolean = success
}
```

### Retrofit 配置 (NetworkModule)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = tokenManager.getToken()
                val request = chain.request().newBuilder()
                    .apply {
                        if (!token.isNullOrEmpty()) {
                            addHeader("Authorization", "Bearer $token")
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
```

---

## 💬 WebSocket 通信

### WebSocketClient 实现
[WebSocketClient.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/core/websocket/WebSocketClient.kt)

**功能特性**:
- ✅ 自动重连机制 (指数退避)
- ✅ 心跳保活 (每 30 秒)
- ✅ 线程安全 (synchronized)
- ✅ 连接状态监听
- ✅ 消息回调

**使用示例**:
```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val webSocketClient: WebSocketClient
) : ViewModel() {
    
    init {
        webSocketClient.connect(
            url = BuildConfig.WEBSOCKET_URL,
            onMessage = { message ->
                parseServerMessage(message)
            },
            onError = { error ->
                Log.e("ChatViewModel", "WebSocket 错误", error)
            }
        )
    }
    
    fun sendMessage(text: String) {
        val json = JSONObject().apply {
            put("type", "TEXT")
            put("content", text)
            put("sessionId", sessionId.value)
        }
        webSocketClient.sendRaw(json.toString())
    }
}
```

---

## 🗂️ 数据模型

### 核心数据类

#### 1. 用户资料 (UserProfile)
```kotlin
data class UserProfile(
    val id: Long,
    val phone: String,
    val nickname: String?,
    val avatar: String?,
    val realName: String?,
    val idCard: String?,
    val verified: Boolean
)
```

#### 2. 订单信息 (Order)
```kotlin
data class Order(
    val orderId: String,
    val passengerName: String,
    val passengerPhone: String,
    val poiName: String,
    val poiLat: Double,
    val poiLng: Double,
    val passengerCount: Int,
    val remark: String?,
    val status: String,
    val createTime: String
)
```

#### 3. POI 地点数据 (PoiData)
```kotlin
data class PoiData(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distance: Double?,
    val type: String?,
    val duration: Int?,
    val price: Double?,
    val score: Double?  // 评分字段
)
```

#### 4. 聊天消息 (ChatMessage)
```kotlin
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val suggestions: List<String> = emptyList(),
    val imageBase64: String? = null
)
```

---

## 🎭 表现层设计

### ViewModel 最佳实践

#### StateFlow 状态管理
```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {
    
    // 私有 MutableStateFlow
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()
    
    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 加载数据
    fun loadProfile() {
        viewModelScope.launch {
            _isProfileLoading.value = true
            try {
                val result = api.getUserProfile()
                if (result.isSuccess()) {
                    _profile.value = result.data
                } else {
                    _errorMessage.value = result.message
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isProfileLoading.value = false
            }
        }
    }
}
```

#### Compose UI 收集状态
```kotlin
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isProfileLoading.collectAsStateWithLifecycle()
    
    if (isLoading) {
        CircularProgressIndicator()
    } else {
        profile?.let { userProfile ->
            Text("昵称: ${userProfile.nickname}")
        }
    }
}
```

---

## 📸 头像上传流程

### 完整流程图
```
用户点击头像
    ↓
选择图片 (相册/拍照)
    ↓
弹出裁剪对话框 (AvatarCropDialog)
    ↓
拖动滑块调整大小 (0.5x - 3.0x)
    ↓
点击确认 → 居中裁剪为 200x200
    ↓
保存为临时文件 (JPEG 90% 质量)
    ↓
压缩到 200x200 (85% 质量)
    ↓
Multipart 上传到服务器
    ↓
刷新个人资料
```

### 关键代码

#### 1. 图片选择与权限
[ProfileScreen.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/profile/ProfileScreen.kt#L103-L145)

```kotlin
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let {
        selectedImageUri = it
        showAvatarCropDialog = true
    }
}

fun pickImage() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
```

#### 2. 手动裁剪对话框
[ProfileScreen.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/profile/ProfileScreen.kt#L784-L893)

```kotlin
@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismissRequest: () -> Unit,
    onConfirmCrop: (Bitmap) -> Unit
) {
    var cropScale by remember { mutableStateOf(1f) }
    
    AlertDialog(
        title = { Text("调整头像") },
        text = {
            Column {
                // 实时预览
                Box(modifier = Modifier.size(200.dp).clip(CircleShape)) {
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * cropScale).toInt(),
                        (bitmap.height * cropScale).toInt(),
                        true
                    )
                    Image(bitmap = scaledBitmap.asImageBitmap(), ...)
                }
                
                // 缩放滑块
                Slider(
                    value = cropScale,
                    onValueChange = { cropScale = it },
                    valueRange = 0.5f..3.0f,
                    steps = 25
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                // 居中裁剪 200x200
                val centerX = (scaledBitmap.width - 200) / 2
                val centerY = (scaledBitmap.height - 200) / 2
                val croppedBitmap = Bitmap.createBitmap(
                    scaledBitmap, maxOf(0, centerX), maxOf(0, centerY), 200, 200
                )
                onConfirmCrop(croppedBitmap)
            }) { Text("确认") }
        }
    )
}
```

#### 3. 压缩与上传
[ProfileViewModel.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/profile/ProfileViewModel.kt#L294-L387)

```kotlin
fun uploadAvatar(uri: Uri, file: File) {
    viewModelScope.launch {
        val compressedFile = compressImage(file)
        
        val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData(
            "avatarFile", compressedFile.name, requestBody
        )
        
        val response = api.uploadAvatar(filePart)
        if (response.isSuccess()) {
            _successMessage.value = "头像上传成功"
            loadProfile()
        }
    }
}

private suspend fun compressImage(sourceFile: File): File = withContext(Dispatchers.IO) {
    val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
    
    // B 站头像规则: 最大 200x200, 质量 85%
    val maxSize = 200
    val quality = 85
    
    var width = bitmap.width
    var height = bitmap.height
    
    if (width > maxSize || height > maxSize) {
        val scale = maxSize.toFloat() / maxOf(width, height)
        width = (width * scale).toInt()
        height = (height * scale).toInt()
    }
    
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
    
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    
    val compressedFile = File(sourceFile.parent, "compressed_${System.currentTimeMillis()}.jpg")
    FileOutputStream(compressedFile).use { fos ->
        fos.write(outputStream.toByteArray())
    }
    
    compressedFile
}
```

---

## 🤖 智能体功能

### 图片识别流程

#### 1. 发送图片
[ChatViewModel.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/chat/ChatViewModel.kt#L702-L848)

```kotlin
fun recognizeImageByHttp(bitmap: Bitmap) {
    viewModelScope.launch {
        // 位置检查
        if (currentLat == null || currentLng == null) {
            addSystemMessage("🛰️ 正在获取您的位置...")
            delay(3000)
            if (currentLat == null || currentLng == null) {
                addSystemMessage("⚠️ 位置获取失败")
                return@launch
            }
        }
        
        // 图片压缩 (500KB 以内)
        val base64 = withContext(Dispatchers.IO) {
            val compressedBitmap = compressImage(bitmap)
            val stream = ByteArrayOutputStream()
            
            // 第一次压缩: 60% 质量
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            var byteArray = stream.toByteArray()
            
            // 如果超过 500KB, 继续压缩到 40%
            if (byteArray.size > 500 * 1024) {
                stream.reset()
                compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
                byteArray = stream.toByteArray()
            }
            
            // 添加前缀
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64String"
        }
        
        // 调用 API
        val result = agentRepository.recognizeImage(
            sessionId = sessionId.value,
            imageBase64 = base64,
            lat = currentLat!!,
            lng = currentLng!!
        )
        
        // 处理响应
        when (response.type.uppercase()) {
            "SEARCH" -> { /* 显示候选地点 */ }
            "ORDER" -> { /* 创建订单 */ }
            "CHAT" -> { /* AI 回复 */ }
            "ERROR" -> { /* 错误提示 */ }
        }
    }
}
```

#### 2. 响应类型处理

| 类型 | 说明 | 处理方式 |
|------|------|---------|
| SEARCH | 搜索到多个地点 | 显示候选列表对话框 |
| ORDER | 精确匹配,直接下单 | 校验 POI 名称后创建订单 |
| CHAT | AI 聊天回复 | 显示文本消息 |
| ERROR | 识别失败 | 显示错误提示 |

---

## 🛒 订单创建防护机制

### 四层 POI 名称校验

#### 1. createOrder 入口验证
[ChatViewModel.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/chat/ChatViewModel.kt#L400-L410)

```kotlin
fun createOrder(poiName: String, poiLat: Double, poiLng: Double, ...) {
    viewModelScope.launch {
        if (poiName.isBlank()) {
            Log.e("ChatViewModel", "❌ POI 名称为空,拒绝创建订单")
            _orderState.value = OrderState.Error("目的地名称不能为空")
            addSystemMessage("⚠️ 目的地名称缺失,请重新选择")
            return@launch
        }
        // ... 继续创建订单
    }
}
```

#### 2. confirmSelectionByHttp 验证
[ChatViewModel.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/chat/ChatViewModel.kt#L679-L693)

```kotlin
if (response?.type == "ORDER") {
    response.poi?.let { poi ->
        val poiName = poi.name
        if (poiName.isNullOrBlank()) {
            Log.e("ChatViewModel", "❌ POI 名称为空,无法创建订单")
            addSystemMessage("⚠️ 目的地名称缺失,请重新选择")
            return@let
        }
        createOrder(poiName, poi.lat, poi.lng, 1, null)
    }
}
```

#### 3. searchAndAutoSelect 验证
[ChatViewModel.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/chat/ChatViewModel.kt#L628-L636)

```kotlin
"ORDER" -> {
    response.poi?.let { poi ->
        val poiName = poi.name
        if (poiName.isNullOrBlank()) {
            Log.e("ChatViewModel", "❌ POI 名称为空,无法创建订单")
            addSystemMessage("⚠️ 目的地名称缺失,请重新选择")
            return@let
        }
        createOrder(poiName, poi.lat, poi.lng, 1, null)
    }
}
```

#### 4. 图片识别 ORDER 响应验证
[ChatViewModel.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/presentation/chat/ChatViewModel.kt#L810-L820)

```kotlin
"ORDER" -> {
    response.poi?.let { poi ->
        val poiName = poi.name
        if (poiName.isNullOrBlank()) {
            Log.e("ChatViewModel", "❌ POI 名称为空,无法创建订单")
            addSystemMessage("⚠️ 目的地名称缺失")
            return@let
        }
        createOrder(poiName, poi.lat, poi.lng, 1, null)
    }
}
```

---

## 🔐 安全规范

### Token 管理
[TokenManager.kt](file:///D:/Android_items/MyApplication/app/src/main/java/com/example/myapplication/core/datastore/TokenManager.kt)

```kotlin
@Singleton
class TokenManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    
    suspend fun saveToken(token: String) {
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }
    
    suspend fun getToken(): String? {
        return dataStore.data.map { preferences ->
            preferences[TOKEN_KEY]
        }.first()
    }
    
    suspend fun clearToken() {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
        }
    }
}
```

### JWT 认证拦截器
```kotlin
.addInterceptor { chain ->
    val token = tokenManager.getToken()
    val request = chain.request().newBuilder()
        .apply {
            if (!token.isNullOrEmpty()) {
                addHeader("Authorization", "Bearer $token")
            }
        }
        .build()
    chain.proceed(request)
}
```

---

## 🚀 性能优化

### 1. 构建优化
```properties
# gradle.properties
org.gradle.parallel=true
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=2048m
```

### 2. kapt 优化
```kotlin
// app/build.gradle.kts
kapt {
    correctErrorTypes = true
    useBuildCache = true
    
    javacOptions {
        option("-Adagger.fastInit=enabled")
        option("-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true")
        option("-Xmaxerrs", "500")
    }
}
```

### 3. Compose 优化
```kotlin
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
}

android {
    buildFeatures {
        compose = true
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }
}
```

### 4. 图片压缩策略
- **头像上传**: 200x200, JPEG 85% 质量
- **智能体图片**: 最大 1920x1080, JPEG 60%/40% 分级压缩,限制 500KB

---

## 📱 权限配置

### AndroidManifest.xml
```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- 定位权限 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- 存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- 相机权限 -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 录音权限 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

---

## 🧪 调试技巧

### 1. 网络日志
```kotlin
HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

### 2. Log 标签规范
```kotlin
Log.d("ChatViewModel", "=== 开始图片识别 ===")
Log.d("ChatViewModel", "sessionId=${sessionId.value}")
Log.e("ChatViewModel", "❌ POI 名称为空,无法创建订单")
```

### 3. WebSocket 调试
```kotlin
webSocketClient.onStateChanged = { state ->
    Log.d("WebSocket", "状态变化: $state")
}
```

---

## 📦 打包发布

### Release 配置
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### ProGuard 规则
```proguard
# 保留数据模型
-keep class com.example.myapplication.data.model.** { *; }

# 保留 Gson 序列化
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
```

---

## 🔄 版本历史

### v1.0 (2026-04-04)
- ✅ 完成登录/注册/忘记密码功能
- ✅ 实现智能体聊天 (WebSocket + HTTP)
- ✅ 支持图片识别与订单创建
- ✅ 个人中心头像上传 (手动裁剪)
- ✅ 订单管理与详情查看
- ✅ 修复订单地点名为空问题 (四层校验)

---

## 📞 联系方式

**前端开发团队**: 安心出行业务部  
**后端对接**: Spring Boot 2.7.14 服务  
**文档维护**: 持续更新中

---

**最后更新**: 2026-04-04  
**文档版本**: v1.0
