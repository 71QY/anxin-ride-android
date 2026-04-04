# 后端 API 对接 - 快速使用指南

## 📖 概述

本文档提供后端 API 对接的快速上手指南，包含常用接口的调用示例。

---

## 🔑 1. 认证模块

### 1.1 发送验证码
```kotlin
val response = apiService.sendCode("13800138000")
if (response.isSuccess()) {
    println("验证码发送成功")
} else {
    println("发送失败：${response.message}")
}
```

### 1.2 验证码登录
```kotlin
val request = LoginRequest(
    phone = "13800138000",
    code = "123456",
    loginType = LoginRequest.TYPE_CODE
)

val response = apiService.login(request)
if (response.isSuccess()) {
    val token = response.data?.token
    val userId = response.data?.userId
    
    // 保存 Token
    MyApplication.tokenManager.saveToken(token!!, userId!!)
    
    println("登录成功")
} else {
    println("登录失败：${response.message}")
}
```

### 1.3 密码登录
```kotlin
val request = LoginRequest(
    phone = "13800138000",
    password = "Aa123456!",
    loginType = LoginRequest.TYPE_PASSWORD
)

val response = apiService.login(request)
// 处理逻辑同上
```

---

## 🤖 2. 智能体模块（推荐使用 WebSocket）

### 2.1 WebSocket 方式（主流程）

#### 连接 WebSocket
```kotlin
// ChatViewModel 会自动连接
val token = MyApplication.tokenManager.getToken()
webSocketClient.connect(sessionId, token)
```

#### 发送消息
```kotlin
// 文本消息
viewModel.sendMessage("附近的医院")

// 图片消息
viewModel.sendImage(bitmap)

// 确认选择
viewModel.selectCandidate(poiData)
```

#### 接收消息
```kotlin
// ChatViewModel 会自动解析并更新 UI
// messages StateFlow 会收到新的聊天消息
// poiList StateFlow 会收到 POI 列表
// candidates StateFlow 会收到候选列表
```

### 2.2 HTTP 方式（备选方案）

#### 智能搜索目的地
```kotlin
val result = agentRepository.searchDestination(
    sessionId = sessionId,
    keyword = "医院",
    lat = 23.65,
    lng = 116.67
)

if (result.isSuccess()) {
    val response = result.data
    when (response?.type) {
        "SEARCH" -> {
            // 搜索到多个结果，需要用户选择
            val places = response.places
            val needConfirm = response.needConfirm
        }
        "ORDER" -> {
            // 可以直接下单
            val poi = response.poi
            val route = response.route
        }
        "CHAT" -> {
            // 纯聊天回复
            val message = response.message
        }
    }
}
```

#### 确认选择
```kotlin
val result = agentRepository.confirmSelection(
    sessionId = sessionId,
    selectedPoiName = "潮州市人民医院"
)

if (result.isSuccess()) {
    val response = result.data
    if (response?.type == "ORDER") {
        // 可以创建订单
        val poi = response.poi
        val route = response.route
    }
}
```

#### 图片识别
```kotlin
val result = agentRepository.recognizeImage(
    sessionId = sessionId,
    imageBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
    lat = 23.65,
    lng = 116.67
)

if (result.isSuccess()) {
    val response = result.data
    val message = response?.message  // OCR 识别结果
    val places = response?.places     // 相关地点列表
}
```

#### 清理会话
```kotlin
// 退出页面或重新开始搜索时调用
val result = agentRepository.cleanupSession(sessionId)
```

---

## 🗺️ 3. 地图模块

### 3.1 搜索目的地（简化版）
```kotlin
val response = apiService.searchDestination(
    keyword = "医院",
    lat = 23.65,
    lng = 116.67
)

if (response.isSuccess()) {
    val poiList = response.data  // List<PoiResponse>
}
```

### 3.2 逆地理编码
```kotlin
val response = apiService.reverseGeocode(
    lat = 23.65,
    lng = 116.67
)

if (response.isSuccess()) {
    val address = response.data
    println("${address?.province}${address?.city}${address?.district}${address?.township}")
    println(address?.formattedAddress)
}
```

### 3.3 POI 周边搜索
```kotlin
val response = apiService.searchNearby(
    keyword = "餐厅",
    lat = 23.65,
    lng = 116.67,
    page = 1,
    pageSize = 20,
    radius = 5000,
    nationwide = false
)

if (response.isSuccess()) {
    val poiList = response.data
}
```

### 3.4 POI 详情与路线规划（地图点击选点）
```kotlin
val response = apiService.getPoiDetail(
    poiName = "潮州市人民医院",
    lat = 23.65,
    lng = 116.67,
    mode = "driving"
)

if (response.isSuccess()) {
    val detail = response.data
    
    // POI 信息
    val poi = detail?.poi
    println("名称：${poi?.name}")
    println("地址：${poi?.address}")
    
    // 路线信息
    val route = detail?.route
    println("距离：${route?.distance}米")
    println("时间：${route?.duration}秒")
    println("价格：¥${route?.price}")
    
    // 重要：保存 sessionId 用于后续下单
    val sessionId = detail?.sessionId
}
```

### 3.5 路线规划
```kotlin
val response = apiService.getRoute(
    origin = "116.67,23.65",      // 格式：经度，纬度
    destination = "116.6423,23.6612",
    mode = "driving"
)

if (response.isSuccess()) {
    val route = response.data
    println("距离：${route?.distance}米")
    println("时间：${route?.duration}秒")
    println("价格：¥${route?.price}")
}
```

### 3.6 地图点击确认下单
```kotlin
// 必须先调用 getPoiDetail 获取 sessionId
val response = apiService.confirmOrder(
    sessionId = sessionId,
    poiName = "潮州市人民医院"
)

if (response.isSuccess()) {
    val orderInfo = response.data
    println("订单 ID: ${orderInfo?.orderId}")
    println("状态：${orderInfo?.status}")
}
```

---

## 🚗 4. 订单模块

### 4.1 创建订单
```kotlin
val request = CreateOrderRequest(
    destName = "潮州市人民医院",
    destLat = 23.6612,
    destLng = 116.6423,
    passengerCount = 1,
    remark = null
)

val response = apiService.createOrder(request)

if (response.isSuccess()) {
    val order = response.data
    println("订单号：${order?.orderNo}")
    println("状态：${order?.status}")  // PENDING / ACCEPTED / IN_PROGRESS / COMPLETED / CANCELLED
    println("预估价格：¥${order?.estimatedPrice}")
} else {
    println("创建失败：${response.message}")
}
```

### 4.2 查询订单详情
```kotlin
val response = apiService.getOrder(orderId = 1)

if (response.isSuccess()) {
    val order = response.data
    // 显示订单详情
}
```

### 4.3 取消订单
```kotlin
val response = apiService.cancelOrder(orderId = 1)

if (response.isSuccess()) {
    println("订单已取消")
} else {
    println("取消失败：${response.message}")
}
```

### 4.4 确认订单（到达目的地）
```kotlin
val response = apiService.confirmOrder(orderId = 1)

if (response.isSuccess()) {
    println("行程已完成")
}
```

### 4.5 订单列表
```kotlin
val response = apiService.getOrderList(
    status = null,  // null=全部，或指定状态码
    page = 1,
    size = 10
)

if (response.isSuccess()) {
    val pageData = response.data
    val orders = pageData?.list
    val total = pageData?.total
}
```

---

## 👤 5. 用户模块

### 5.1 获取个人信息
```kotlin
val response = apiService.getUserProfile()

if (response.isSuccess()) {
    val profile = response.data
    println("昵称：${profile?.nickname}")
    println("头像：${profile?.avatar}")
    println("实名认证：${if (profile?.verified == 1) "已认证" else "未认证"}")
}
```

### 5.2 更新个人信息
```kotlin
val profile = UserProfile(
    id = userId,
    nickname = "张三",
    avatar = "http://xxx.com/avatar.jpg"
)

val response = apiService.updateUserProfile(profile)
```

### 5.3 上传头像
```kotlin
// 准备图片文件
val file = File(context.cacheDir, "avatar.jpg")
// ... 写入图片数据 ...

val requestFile = file.asRequestBody("image/jpeg".toMediaType())
val body = MultipartBody.Part.createFormData(
    "avatarFile",
    "avatar.jpg",
    requestFile
)

val response = apiService.uploadAvatar(body)

if (response.isSuccess()) {
    val avatarUrl = response.data?.filename
    println("头像上传成功：$avatarUrl")
}
```

### 5.4 添加紧急联系人
```kotlin
val contact = EmergencyContact(
    name = "李四",
    phone = "13900139000",
    relationship = "朋友"
)

val response = apiService.addEmergencyContact(contact)
```

### 5.5 获取紧急联系人列表
```kotlin
val response = apiService.getEmergencyContacts()

if (response.isSuccess()) {
    val contacts = response.data
}
```

### 5.6 删除紧急联系人
```kotlin
val response = apiService.deleteEmergencyContact(id = 1)
```

### 5.7 实名认证
```kotlin
val request = RealNameRequest(
    realName = "张三",
    idCard = "445100199001011234"
)

val response = apiService.realNameAuth(request)

if (response.isSuccess()) {
    println("实名认证成功")
}
```

### 5.8 修改密码
```kotlin
val request = ChangePasswordRequest(
    phone = "13800138000",
    code = "123456",
    newPassword = "Aa654321!"
)

val response = apiService.changePassword(request)
```

---

## 🔌 6. WebSocket 实时通信

### 6.1 连接
```kotlin
val token = MyApplication.tokenManager.getToken()
webSocketClient.connect(sessionId, token)
```

### 6.2 发送消息
```kotlin
// 文本消息
val request = WebSocketRequest(
    sessionId = sessionId,
    type = "user_message",
    content = "附近的医院",
    lat = 23.65,
    lng = 116.67
)
webSocketClient.sendRaw(Json.encodeToString(request))

// 图片消息
val request = WebSocketRequest(
    sessionId = sessionId,
    type = "image",
    content = "",
    imageBase64 = "data:image/jpeg;base64,...",
    lat = 23.65,
    lng = 116.67
)
webSocketClient.sendRaw(Json.encodeToString(request))

// 确认选择
val request = WebSocketRequest(
    sessionId = sessionId,
    type = "confirm",
    content = "潮州市人民医院",
    destName = "潮州市人民医院",
    destLat = 23.6612,
    destLng = 116.6423
)
webSocketClient.sendRaw(Json.encodeToString(request))
```

### 6.3 接收消息
```kotlin
webSocketClient.messages.collect { message ->
    val response = Json.decodeFromString<WebSocketResponse>(message)
    
    when (response.type) {
        "chat_reply" -> {
            // 聊天回复
            println(response.message)
        }
        "search_result", "poi_list" -> {
            // 搜索结果
            val poiList = extractPoiList(response.data)
        }
        "order_created" -> {
            // 订单创建成功
            println(response.message)
        }
        "image_recognition" -> {
            // 图片识别结果
            println(response.ocrText)
        }
        "error" -> {
            // 错误信息
            println(response.message)
        }
    }
}
```

### 6.4 断线重连
```kotlin
// ChatViewModel 已实现自动重连机制
// 每 30 秒检查一次连接状态
// 断开后自动尝试重连
```

---

## ⚠️ 7. 常见错误处理

### 7.1 统一错误处理
```kotlin
fun handleApiError(error: Exception) {
    Log.e("API Error", error.message, error)
    // 显示错误提示
    Toast.makeText(context, "网络错误：${error.message}", Toast.LENGTH_SHORT).show()
}
```

### 7.2 401 未授权
```kotlin
// AuthInterceptor 会自动处理 401 响应
// 清除本地 Token 并提示重新登录
if (response.code == 401) {
    MyApplication.tokenManager.clearToken()
    // 跳转到登录页
}
```

### 7.3 网络错误
```kotlin
try {
    val response = apiService.xxx()
    if (!response.isSuccess()) {
        println("业务错误：${response.message}")
    }
} catch (e: Exception) {
    println("网络异常：${e.message}")
}
```

---

## 📊 8. 响应数据格式

### 成功响应
```json
{
  "code": 200,
  "data": {...},
  "message": "success"
}
```

### 失败响应
```json
{
  "code": 400,
  "data": null,
  "message": "手机号格式不正确"
}
```

---

## 🎯 9. 最佳实践

### 9.1 防抖优化
```kotlin
// 搜索输入框防抖 500ms
searchJob?.cancel()
searchJob = viewModelScope.launch {
    delay(500)
    searchPoiFromBackend(text)
}
```

### 9.2 缓存策略
```kotlin
// LruCache 缓存地址
private val addressCache = LruCache<Long, String>(20)

// 使用前先查缓存
val cachedAddress = addressCache.get(cacheKey)
if (cachedAddress != null) {
    // 使用缓存
} else {
    // 请求接口
}
```

### 9.3 位置同步
```kotlin
// ChatViewModel 从 HomeScreen 同步位置
fun syncLocationFromHome(lat: Double, lng: Double) {
    // 距离判定：小于 50 米不更新
    val shouldUpdate = lastSyncedLat?.let { prevLat ->
        lastSyncedLng?.let { prevLng ->
            val distance = calculateDistance(prevLat, prevLng, lat, lng)
            distance >= 50.0
        } ?: true
    } ?: true
    
    if (shouldUpdate) {
        currentLat = lat
        currentLng = lng
    }
}
```

---

**文档更新时间**: 2026-04-03  
**版本**: v1.0
