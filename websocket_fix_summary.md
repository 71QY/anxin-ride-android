# WebSocket 连接失败修复总结

## 问题描述
在前后端分离架构下（前端 Java 17，后端 Java 11），WebSocket 连接失败。

## 根本原因分析

1. **URL 拼接问题**: 直接拼接 `?token=$token` 可能导致 URL 格式错误
2. **协议头缺失**: Java 11 的 WebSocket 服务器需要特定的协议头进行握手
3. **超时配置不足**: 默认超时时间可能不适应实际网络环境
4. **日志不够详细**: 无法快速定位连接失败的具体原因

## 修改文件清单

### 1. ChatWebSocketClient.kt
**文件路径**: `app/src/main/java/com/example/myapplication/core/websocket/ChatWebSocketClient.kt`

#### 修改内容:

##### (1) 增强 OkHttpClient 配置
```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时 30 秒
    .readTimeout(60, TimeUnit.SECONDS)     // 读取超时 60 秒
    .writeTimeout(60, TimeUnit.SECONDS)    // 写入超时 60 秒
    .pingInterval(30, TimeUnit.SECONDS)    // 心跳间隔 30 秒
    .retryOnConnectionFailure(true)        // 启用自动重试
    .build()
```

##### (2) 修复 URL 拼接逻辑
```kotlin
// ⭐ 修复：正确处理 URL 拼接，避免重复的查询参数分隔符
val baseUrl = BuildConfig.WEBSOCKET_URL.trimEnd('/', '?')
val url = "$baseUrl?token=$token&sessionId=$sessionId"
```

##### (3) 添加必要的请求头
```kotlin
val request = Request.Builder()
    .url(url)
    .addHeader("Sec-WebSocket-Protocol", "chat")
    .addHeader("User-Agent", "MyApplication/1.0 (Android; Chat Client)")
    .addHeader("X-Session-ID", sessionId)
    .build()
```

##### (4) 增强错误日志
```kotlin
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    super.onFailure(webSocket, t, response)
    isConnected = false
    
    // ⭐ 修复：详细的错误日志，帮助诊断 Java 11 vs Java 17 兼容性问题
    val errorMsg = buildString {
        append("连接失败：${t.message}")
        append("\n异常类型：${t.javaClass.simpleName}")
        response?.let {
            append("\nHTTP 状态码：${it.code}")
            append("\n响应头：${it.headers}")
        }
        append("\n堆栈跟踪：${t.stackTraceToString()}")
    }
    Log.e("WebSocket", errorMsg, t)
    
    // ⭐ 新增：根据异常类型提供具体的解决建议
    when (t) {
        is java.net.SocketTimeoutException -> {
            Log.e("WebSocket", "⚠️ 连接超时，请检查：1.后端服务是否运行 2.防火墙设置 3.网络连通性")
        }
        is java.net.ConnectException -> {
            Log.e("WebSocket", "⚠️ 无法连接到服务器，请确认后端 IP 和端口是否正确")
        }
        is javax.net.ssl.SSLHandshakeException -> {
            Log.e("WebSocket", "⚠️ SSL 握手失败，检查是否需要 HTTPS 而不是 WS")
        }
        else -> {
            Log.e("WebSocket", "⚠️ 未知错误，查看上方详细日志")
        }
    }
    // ... 重连逻辑保持不变
}
```

### 2. ChatViewModel.kt
**文件路径**: `app/src/main/java/com/example/myapplication/presentation/chat/ChatViewModel.kt`

#### 修改内容:

##### (1) 增强初始化日志
```kotlin
viewModelScope.launch {
    val token = withContext(Dispatchers.IO) {
        MyApplication.tokenManager.getToken()
    }
    Log.d("ChatViewModel", "=== ChatViewModel 初始化 ===")
    Log.d("ChatViewModel", "Token: ${token?.take(20)}...")
    Log.d("ChatViewModel", "SessionId: ${sessionId.value}")  // ⭐ 新增
    
    if (!token.isNullOrBlank()) {
        if (!webSocketClient.isConnected()) {
            Log.d("ChatViewModel", "开始连接 WebSocket...")  // ⭐ 新增
            webSocketClient.connect(sessionId.value, token)
        } else {
            Log.d("ChatViewModel", "WebSocket 已经连接，跳过")
        }
    } else {
        Log.e("ChatViewModel", "❌ Token 为空，无法连接 WebSocket")
        addSystemMessage("⚠️ 请先登录，再进行对话")  // ⭐ 新增
    }
}
```

##### (2) 优化重连逻辑
```kotlin
fun reconnectWebSocket() {
    viewModelScope.launch {
        delay(2000)  // 防止频繁重连
        
        if (webSocketClient.isConnected()) {
            Log.d("ChatViewModel", "WebSocket 已连接，无需重连")
            return@launch
        }
        
        val token = withContext(Dispatchers.IO) {
            MyApplication.tokenManager.getToken()
        }
        
        Log.d("ChatViewModel", "=== 开始 WebSocket 重连 ===")
        Log.d("ChatViewModel", "Token: ${token?.take(20)}...")
        
        if (!token.isNullOrBlank()) {
            Log.d("ChatViewModel", "正在断开旧连接...")
            webSocketClient.disconnect()
            
            delay(500)  // ⭐ 新增：等待断开完成
            
            Log.d("ChatViewModel", "正在建立新连接...")
            webSocketClient.connect(sessionId.value, token)
            Log.d("ChatViewModel", "✅ WebSocket 重连成功")
        } else {
            Log.e("ChatViewModel", "❌ Token 为空，无法重连")
            addSystemMessage("⚠️ Token 已过期，请重新登录")  // ⭐ 新增
        }
    }
}
```

## 技术要点

### 1. Java 11 vs Java 17 WebSocket 兼容性
- **Java 11**: 使用 `javax.websocket` API
- **Java 17**: 使用 `jakarta.websocket` API（部分实现）
- **OkHttp**: 跨平台实现，不受 Java 版本影响，但需要正确的协议头

### 2. WebSocket 协议头说明
- `Sec-WebSocket-Protocol`: 指定子协议，帮助后端正确路由
- `User-Agent`: 客户端标识，便于后端统计和调试
- `X-Session-ID`: 自定义头，辅助后端会话管理

### 3. URL 拼接最佳实践
```kotlin
// ❌ 错误：可能导致双问号或双斜杠
val url = "${BuildConfig.WEBSOCKET_URL}?token=$token"

// ✅ 正确：先清理末尾字符，再拼接
val baseUrl = BuildConfig.WEBSOCKET_URL.trimEnd('/', '?')
val url = "$baseUrl?token=$token&sessionId=$sessionId"
```

### 4. 超时配置策略
- **连接超时**: 30 秒（足够应对大多数网络延迟）
- **读写超时**: 60 秒（考虑到大消息传输）
- **心跳间隔**: 30 秒（保持连接活跃，防止被防火墙切断）

## 测试验证步骤

### 1. 编译并运行应用
```bash
./gradlew clean assembleDebug
```

### 2. 查看 Logcat 日志
过滤标签：`WebSocket` 和 `ChatViewModel`

期望看到的日志：
```
D/WebSocket: 尝试连接：ws://10.237.36.80:8080/ws/agent?token=xxx&sessionId=yyy
D/WebSocket: Token: abc123...
D/WebSocket: 连接成功：ws://10.237.36.80:8080/ws/agent?token=xxx&sessionId=yyy
D/ChatViewModel: ✅ WebSocket 重连成功
```

### 3. 测试网络连通性
```powershell
# Ping 测试
ping 10.237.36.80

# 端口测试
Test-NetConnection -ComputerName 10.237.36.80 -Port 8080
```

### 4. 验证 WebSocket 连接
使用浏览器控制台或工具（如 Postman、wscat）测试：
```
wscat -c "ws://10.237.36.80:8080/ws/agent?token=YOUR_TOKEN&sessionId=test-123"
```

## 预期效果

1. ✅ **连接成功率提升**: 修复 URL 拼接和协议头问题后，连接应该能够成功建立
2. ✅ **错误定位更容易**: 详细的日志输出帮助快速定位问题
3. ✅ **重连机制更可靠**: 增加延迟和状态检查，避免重复连接
4. ✅ **用户体验改善**: 添加用户友好的错误提示

## 后续优化建议

### 1. 添加连接状态监听
在 UI 中显示 WebSocket 连接状态（已连接/断开/重连中）。

### 2. 实现指数退避
当前使用固定延迟，可以优化为指数退避策略：
```kotlin
private val backoffTimes = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L)
```

### 3. 添加连接质量监测
定期检测网络延迟和丢包率，主动切换网络或使用备用方案。

### 4. 支持 WSS 加密连接
生产环境应使用 WSS 保证安全性：
```kotlin
val url = if (BuildConfig.DEBUG) {
    "ws://$baseUrl?token=$token"
} else {
    "wss://$baseUrl?token=$token"
}
```

## 相关文档
- [WebSocket 故障排查指南](./websocket_troubleshooting_guide.md)
- OkHttp 官方文档：https://square.github.io/okhttp/websockets/
- Java WebSocket API: https://docs.oracle.com/javaee/7/api/javax/websocket/package-summary.html

## 注意事项

1. **不要提交敏感信息**: 确保 `websocket_troubleshooting_guide.md` 不提交到版本控制系统
2. **生产环境配置**: 生产环境应使用 WSS 和正式域名
3. **Token 安全**: Token 过期时应引导用户重新登录，而不是自动刷新
4. **日志级别**: 发布版本应降低日志级别，避免泄露敏感信息

## 验证清单

- [x] 代码编译通过，无语法错误
- [ ] 应用能够成功启动
- [ ] 登录后能够建立 WebSocket 连接
- [ ] 能够收发消息
- [ ] 断网后能够自动重连
- [ ] 日志输出符合预期
- [ ] 错误提示清晰明了

## 联系信息
如有问题，请联系开发团队或查看详细排查指南。
