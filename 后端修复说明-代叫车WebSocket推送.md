# 代叫车功能后端修复说明

## 问题描述

当前代叫车功能已成功创建订单,但长辈端(userId=19)未收到`ORDER_CREATED` WebSocket推送消息,导致无法弹出确认对话框。

## 前端现状

### 1. 订单创建流程(已完成✅)
```kotlin
// HomeViewModel.kt - createProxyOrderForElder()
POST /api/guard/proxyOrder
{
  "elderId": 19,
  "startLat": 23.654201279716982,
  "startLng": 116.67573649918842,
  "destLat": 23.655919077622933,
  "destLng": 116.67473295406674,
  "destAddress": "金山中学",
  "needConfirm": true
}

// 响应成功
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 11,
    "orderNo": "AX177618627818612481000",
    "userId": 19,        // 长辈ID
    "proxyUserId": 1,    // 代叫人ID
    "elderUserId": 19,   // 长辈ID
    "status": 0          // 待确认
  }
}
```

### 2. 长辈端WebSocket监听(已实现✅)
```kotlin
// HomeViewModel.kt - connectWebSocketForElderMode()
// sessionId格式: user_{userId}
val wsSessionId = "user_19"  // 长辈端的sessionId
webSocketClient.connect(wsSessionId, token)

// 监听 ORDER_CREATED 消息
when (type) {
    "ORDER_CREATED" -> {
        val orderJson = JSONObject(dataObj)
        val orderId = orderJson.optLong("orderId", -1L)
        val requesterName = orderJson.optString("requesterName", "亲友")
        val destination = orderJson.optString("destination", "未知目的地")
        
        // 通过全局事件总线触发确认对话框
        MyApplication.sendProxyOrderRequest(orderId, requesterName, destination)
    }
}
```

## 后端需要修复的问题

### 问题1: 缺少WebSocket推送逻辑 ❌

**当前行为**: 创建代叫车订单后,仅返回HTTP响应,未向长辈端推送WebSocket消息

**期望行为**: 创建订单成功后,向`elderUserId`对应的WebSocket连接发送`ORDER_CREATED`消息

### 修复方案

#### 步骤1: 在代叫车接口中添加WebSocket推送

**文件位置**: `GuardController.java` (或对应的代叫车接口Controller)

**修改方法**: `proxyOrder()` 或 `createOrderForElder()`

```java
@PostMapping("/proxyOrder")
public Result<?> proxyOrder(@RequestBody CreateOrderForElderRequest request) {
    try {
        // 1. 验证权限(检查代叫人是否绑定了该长辈)
        Long currentUserId = getCurrentUserId();
        if (!isBound(currentUserId, request.getElderId())) {
            return Result.error(403, "您未绑定该长辈,无法代叫车");
        }
        
        // 2. 创建订单
        Order order = orderService.createProxyOrder(request);
        
        // 3. ⭐ 关键修复:向长辈端推送ORDER_CREATED消息
        if (request.getNeedConfirm() != null && request.getNeedConfirm()) {
            // 获取长辈的WebSocket会话
            String elderSessionId = "user_" + request.getElderId();
            WebSocketSession elderSession = webSocketSessionManager.getSession(elderSessionId);
            
            if (elderSession != null && elderSession.isOpen()) {
                // 构造推送消息
                Map<String, Object> message = new HashMap<>();
                message.put("type", "ORDER_CREATED");
                message.put("message", "您的亲友已为您叫车,请确认");
                
                // data字段包含订单详情
                Map<String, Object> data = new HashMap<>();
                data.put("orderId", order.getId());
                data.put("orderNo", order.getOrderNo());
                data.put("requesterName", getUserName(currentUserId));  // 代叫人姓名
                data.put("destination", request.getDestAddress());
                data.put("startLat", order.getStartLat());
                data.put("startLng", order.getStartLng());
                data.put("destLat", order.getDestLat());
                data.put("destLng", order.getDestLng());
                data.put("createTime", order.getCreateTime());
                
                message.put("data", data);
                
                // 发送消息
                String jsonMessage = objectMapper.writeValueAsString(message);
                elderSession.sendMessage(new TextMessage(jsonMessage));
                
                log.info("✅ 已向长辈 userId={} 推送代叫车通知, orderId={}", 
                    request.getElderId(), order.getId());
            } else {
                log.warn("⚠️ 长辈 userId={} 未连接WebSocket,无法推送通知", 
                    request.getElderId());
            }
        }
        
        // 4. 返回订单信息
        return Result.success(order);
        
    } catch (Exception e) {
        log.error("❌ 代叫车失败", e);
        return Result.error("代叫车失败: " + e.getMessage());
    }
}
```

#### 步骤2: 确保WebSocket会话管理器正确存储会话

**文件位置**: `WebSocketSessionManager.java` (或类似的会话管理类)

```java
@Component
public class WebSocketSessionManager {
    
    // ⭐ 关键:使用sessionId作为key存储会话
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    /**
     * 注册WebSocket会话
     * @param sessionId 格式: user_{userId}
     */
    public void registerSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        log.info("✅ WebSocket会话已注册: sessionId={}, 当前活跃会话数={}", 
            sessionId, sessions.size());
    }
    
    /**
     * 移除WebSocket会话
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("🔌 WebSocket会话已移除: sessionId={}", sessionId);
    }
    
    /**
     * 获取WebSocket会话
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * 检查会话是否存在且活跃
     */
    public boolean isSessionActive(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }
}
```

#### 步骤3: 确保WebSocket握手时正确提取sessionId

**文件位置**: `WebSocketConfig.java` 或 `WebSocketHandler.java`

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ChatWebSocketHandler(sessionManager), "/ws/chat")
                .setAllowedOrigins("*");
    }
}

public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    private final WebSocketSessionManager sessionManager;
    
    public ChatWebSocketHandler(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // ⭐ 关键:从URL参数中提取sessionId
        String query = session.getUri().getQuery();
        String sessionId = extractSessionId(query);
        
        if (sessionId != null) {
            sessionManager.registerSession(sessionId, session);
            log.info("✅ WebSocket连接建立: sessionId={}, remoteAddress={}", 
                sessionId, session.getRemoteAddress());
            
            // 发送欢迎消息
            Map<String, Object> welcome = new HashMap<>();
            welcome.put("type", "WELCOME");
            welcome.put("message", "连接成功");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
        } else {
            log.warn("⚠️ WebSocket连接缺少sessionId,关闭连接");
            session.close(CloseStatus.BAD_DATA);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String query = session.getUri().getQuery();
        String sessionId = extractSessionId(query);
        
        if (sessionId != null) {
            sessionManager.removeSession(sessionId);
            log.info("🔌 WebSocket连接关闭: sessionId={}, status={}", sessionId, status);
        }
    }
    
    /**
     * 从查询字符串中提取sessionId
     * URL格式: ws://host/ws/chat?token=xxx&sessionId=user_19
     */
    private String extractSessionId(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        
        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("sessionId=")) {
                return param.substring("sessionId=".length());
            }
        }
        return null;
    }
}
```

## 测试验证

### 测试步骤

1. **长辈端登录并切换到长辈模式**
   - userId=19登录
   - 设置guardMode=1
   - 确认WebSocket连接成功(查看日志:`✅ WebSocket连接建立: sessionId=user_19`)

2. **普通用户端发起代叫车**
   - userId=1登录
   - 选择目的地"金山中学"
   - 点击"帮长辈叫车"
   - 选择长辈(userId=19)

3. **验证长辈端收到推送**
   - 查看长辈端日志应出现:
     ```
     📥 HomeViewModel 收到消息: {"type":"ORDER_CREATED","message":"您的亲友已为您叫车,请确认","data":{...}}
     🔔 收到代叫车通知：您的亲友已为您叫车,请确认
     ✅ 代叫车请求详情：orderId=11, from=周, to=金山中学
     📤 发送代叫车请求事件到全局总线
     📩 收到全局代叫车请求事件：orderId=11, from=周, to=金山中学
     ✅ 已调用 onProxyOrderRequestReceived
     ```
   - 长辈端应弹出确认对话框

### 预期日志输出

**普通用户端(代叫人)**:
```
D HomeViewModel: 🚗 开始帮长辈叫车：elderUserId=19, poiName=金山中学
D HomeViewModel: 📤 发送代叫车请求：CreateOrderForElderRequest(elderId=19, ...)
D HomeViewModel: 📥 收到响应：isSuccess=true, code=200
D HomeViewModel: ✅ 代叫车请求已发送
D HomeViewModel:   - 订单ID: 11
D HomeViewModel:   - 订单号: AX177618627818612481000
D HomeViewModel:   - 长辈ID: 19
D HomeViewModel:   - 目的地: 金山中学
```

**后端日志**:
```
INFO  GuardController: ✅ 已向长辈 userId=19 推送代叫车通知, orderId=11
```

**长辈端**:
```
D HomeViewModel: 📥 HomeViewModel 收到消息: {"type":"ORDER_CREATED",...}
D HomeViewModel: 🔔 收到代叫车通知：您的亲友已为您叫车,请确认
D HomeViewModel: ✅ 代叫车请求详情：orderId=11, from=周, to=金山中学
D HomeViewModel: 📤 发送代叫车请求事件到全局总线
D ElderSimplifiedScreen: 📩 proxyOrderRequest 变化: ProxyOrderRequest(orderId=11, ...)
D ElderSimplifiedScreen: ✅ 收到代叫车请求：orderId=11, from=周, to=金山中学
D ElderSimplifiedScreen: ✅ 已设置 showProxyOrderConfirmDialog = true
```

## 注意事项

### 1. WebSocket连接时机
- 长辈端必须在**长辈模式下**才会连接WebSocket
- 连接时机:`checkElderMode()`检测到`guardMode==1`时调用`connectWebSocketForElderMode()`
- sessionId格式必须为:`user_{userId}` (例如:`user_19`)

### 2. 消息格式要求
```json
{
  "type": "ORDER_CREATED",
  "message": "您的亲友已为您叫车,请确认",
  "data": {
    "orderId": 11,
    "orderNo": "AX177618627818612481000",
    "requesterName": "周",
    "destination": "金山中学",
    "startLat": 23.654201279716982,
    "startLng": 116.67573649918842,
    "destLat": 23.655919077622933,
    "destLng": 116.67473295406674,
    "createTime": "2026-04-15T01:04:38.1902945"
  }
}
```

**关键字段**:
- `type`: 必须为`"ORDER_CREATED"`(大写)
- `data.orderId`: 订单ID(Long类型)
- `data.requesterName`: 代叫人姓名(用于显示)
- `data.destination`: 目的地名称(用于显示)

### 3. 边界情况处理

#### 情况1: 长辈端未连接WebSocket
```java
if (elderSession == null || !elderSession.isOpen()) {
    log.warn("⚠️ 长辈 userId={} 未连接WebSocket,订单已创建但无法推送通知", 
        request.getElderId());
    // 注意:仍然返回成功,订单已创建,长辈端可通过刷新页面看到待确认订单
}
```

#### 情况2: needConfirm=false
```java
// 如果不需要确认,则不推送消息,直接派单
if (request.getNeedConfirm() != null && !request.getNeedConfirm()) {
    log.info("ℹ️ 代叫车无需确认,直接派单, orderId={}", order.getId());
    // 不调用推送逻辑
}
```

#### 情况3: 长辈端离线后上线
- 建议后端提供`GET /api/order/pending`接口,查询待确认订单
- 长辈端上线后主动拉取待确认订单列表

### 4. 性能优化建议

#### 建议1: 异步推送
```java
// 使用线程池异步推送,避免阻塞主流程
@Async("websocketExecutor")
public void sendProxyOrderNotification(Long elderUserId, Order order, String requesterName) {
    // 推送逻辑
}
```

#### 建议2: 推送失败重试
```java
// 如果推送失败,可以记录到数据库或消息队列,稍后重试
if (!elderSession.isOpen()) {
    // 记录到pending_notifications表
    pendingNotificationRepository.save(new PendingNotification(
        elderUserId, "ORDER_CREATED", order.getId(), System.currentTimeMillis()
    ));
}
```

## 总结

**核心问题**: 后端创建代叫车订单后,未向长辈端推送WebSocket消息

**修复要点**:
1. ✅ 前端已实现WebSocket监听和事件处理
2. ❌ 后端需要在`proxyOrder`接口中添加推送逻辑
3. ❌ 后端需要确保WebSocket会话管理器正确存储和检索会话
4. ❌ 后端需要确保WebSocket握手时正确提取sessionId

**修复后效果**:
- 长辈端实时收到代叫车通知
- 自动弹出确认对话框
- 用户体验流畅,无需手动刷新
