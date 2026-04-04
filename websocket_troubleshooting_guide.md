# WebSocket 连接问题排查指南

## 问题背景
前端使用 Java 17，后端使用 Java 11，WebSocket 连接失败。

## 已修复的问题

### 1. URL 拼接错误
**问题**: 如果 `BuildConfig.WEBSOCKET_URL` 已经包含 `/` 或 `?`，直接拼接 `?token=$token` 会导致 URL 格式错误。

**修复**:
```kotlin
val baseUrl = BuildConfig.WEBSOCKET_URL.trimEnd('/', '?')
val url = "$baseUrl?token=$token&sessionId=$sessionId"
```

### 2. 缺少必要的请求头
**问题**: Java 11 的 WebSocket 服务器可能需要特定的协议头才能正确握手。

**修复**: 添加了以下请求头：
- `Sec-WebSocket-Protocol: chat` - 指定 WebSocket 子协议
- `User-Agent: MyApplication/1.0 (Android; Chat Client)` - 客户端标识
- `X-Session-ID: {sessionId}` - 会话 ID（辅助后端调试）

### 3. 超时时间过短
**问题**: 默认的超时时间可能不适应网络环境，导致连接失败。

**修复**: 
- 连接超时：30 秒
- 读取超时：60 秒
- 写入超时：60 秒
- 心跳间隔：30 秒

### 4. 错误日志不够详细
**问题**: 无法快速定位连接失败的原因。

**修复**: 增强了错误日志输出，包括：
- 详细的异常信息和堆栈跟踪
- HTTP 状态码和响应头
- 根据异常类型提供解决建议

## 排查步骤

### 步骤 1: 检查后端服务
确保后端服务正在运行，并且监听正确的 IP 和端口：
```bash
# 在后端服务器上执行
netstat -ano | findstr :8080
```

应该看到类似：
```
TCP    0.0.0.0:8080           0.0.0.0:0              LISTENING       12345
TCP    [::]:8080              [::]:0                 LISTENING       12345
```

### 步骤 2: 测试网络连通性
在前端开发机上执行：
```powershell
# Ping 后端服务器
ping 10.237.36.80

# 测试 8080 端口
Test-NetConnection -ComputerName 10.237.36.80 -Port 8080
```

### 步骤 3: 测试 WebSocket 连接
使用浏览器或工具测试 WebSocket：
```
ws://10.237.36.80:8080/ws/agent?token=YOUR_TEST_TOKEN&sessionId=test-123
```

### 步骤 4: 查看 Android 日志
在 Android Studio 的 Logcat 中过滤 "WebSocket" 标签，查看详细日志。

关键日志示例：
```
D/WebSocket: 尝试连接：ws://10.237.36.80:8080/ws/agent?token=xxx&sessionId=yyy
D/WebSocket: Token: abc123...
D/WebSocket: 连接成功：ws://10.237.36.80:8080/ws/agent?token=xxx&sessionId=yyy
```

或者错误日志：
```
E/WebSocket: 连接失败：Connection refused
异常类型：ConnectException
⚠️ 无法连接到服务器，请确认后端 IP 和端口是否正确
```

### 步骤 5: 检查防火墙
确保后端服务器的防火墙允许 8080 端口：

**Windows**:
```powershell
# 查看防火墙规则
Get-NetFirewallRule | Where-Object {$_.DisplayName -like "*8080*"}

# 添加入站规则（如果需要）
New-NetFirewallRule -DisplayName "Allow WebSocket 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

### 步骤 6: 验证 Token 有效性
确保 Token 是有效的 JWT，可以通过以下方式验证：

1. 从 SharedPreferences 中提取 Token：
```kotlin
val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
val token = prefs.getString("token", null)
Log.d("Token", "Current token: $token")
```

2. 使用在线工具解析 JWT，检查是否过期。

## 常见问题及解决方案

### 问题 1: SocketTimeoutException
**症状**: 连接超时
**原因**: 
1. 后端服务未启动
2. 防火墙阻止连接
3. 网络不通

**解决**:
- 检查后端服务是否运行
- 关闭防火墙或添加例外规则
- 确保设备在同一局域网

### 问题 2: ConnectException
**症状**: 无法连接到服务器
**原因**:
1. IP 地址或端口错误
2. 后端服务未监听 0.0.0.0

**解决**:
- 确认 `BuildConfig.WEBSOCKET_URL` 配置正确
- 检查后端配置的监听地址

### 问题 3: SSLHandshakeException
**症状**: SSL 握手失败
**原因**: 使用了 WSS 但证书不受信任

**解决**:
- 开发环境使用 WS 而不是 WSS
- 或将证书添加到信任列表

### 问题 4: 401 Unauthorized
**症状**: WebSocket 连接被拒绝，HTTP 401
**原因**: Token 无效或过期

**解决**:
- 重新登录获取新 Token
- 检查 Token 格式是否正确

## 配置验证清单

- [ ] 后端服务正在运行（访问 http://10.237.36.80:8080/api/ 确认）
- [ ] 8080 端口开放（使用 Test-NetConnection 测试）
- [ ] WebSocket 地址正确（ws://10.237.36.80:8080/ws/agent）
- [ ] Token 有效且未过期
- [ ] SessionId 已正确生成
- [ ] 防火墙允许 8080 端口
- [ ] 设备在同一局域网或通过 ADB reverse 转发

## 调试技巧

### 1. 启用 OkHttp 日志拦截器
在 `NetworkModule.kt` 中添加：
```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

### 2. 使用 Chrome DevTools 调试 WebSocket
在 Chrome 中打开：
```
chrome://inspect/#devices
```

### 3. 后端日志
检查后端 Java 11 的 WebSocket 日志，查看是否收到连接请求。

## 联系支持
如果以上方法都无法解决问题，请提供：
1. Android Logcat 完整日志
2. 后端控制台日志
3. 网络测试结果（Ping、端口测试）
4. 当前配置信息（IP、端口、Token 等）
