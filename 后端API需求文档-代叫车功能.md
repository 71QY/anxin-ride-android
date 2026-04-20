# 代叫车功能 - 后端API需求文档

## 📋 概述

本文档说明代叫车功能的前端实现逻辑和后端需要配合的API接口调整。

---

## 🔄 完整业务流程

### 1. 长辈分享收藏地点给亲友
```
长辈端操作：
  1. 长辈在收藏地点页面点击"分享给亲友"
  2. 选择要分享的亲友（userId）
  
后端处理：
  1. 保存分享记录
  2. 通过 WebSocket 向亲友推送 FAVORITE_SHARED 消息
  
WebSocket 消息格式：
{
  "type": "FAVORITE_SHARED",
  "elderUserId": 28,           // 长辈ID
  "proxyUserName": "长辈",      // 长辈姓名
  "favoriteId": 18,             // 收藏地点ID
  "favoriteName": "木棉酒店",   // 地点名称
  "favoriteAddress": "广东省潮州市湘桥区桥东街道木棉酒店(韩师店)",
  "favoriteLat": 23.655876083140846,
  "favoriteLng": 116.6705916234186,
  "elderCurrentLat": 23.65550773048479,  // ⭐ 长辈实时位置（起点）
  "elderCurrentLng": 116.67295605706695,
  "elderLocationTimestamp": 1776696789646,
  "timestamp": 1776696789646
}
```

---

### 2. 亲友收到分享并点击"立即叫车"

#### 前端行为：
```kotlin
// 1. 亲友在私聊界面看到卡片
// 2. 点击"一键填充到打车界面"或外层弹窗的"立即叫车"
// 3. 跳转到首页
// 4. 延迟500ms后调用创建订单接口

val elderId = chatViewModel.sharedLocation.value?.elderId  // 从分享数据中获取长辈ID
homeViewModel.createOrder(destinationName, elderId)
```

#### 请求参数（CreateOrderRequest）：
```json
{
  "poiName": "木棉酒店",        // 目的地名称（必填）
  "destLat": 23.655876083140846,  // 目的地纬度
  "destLng": 116.6705916234186,   // 目的地经度
  "passengerCount": 1,            // 乘客数量
  "remark": null,                 // 备注
  "startLat": 23.65550773048479,  // ⭐ 起点纬度（长辈当前位置）
  "startLng": 116.67295605706695, // ⭐ 起点经度（长辈当前位置）
  "elderId": 28                   // ⭐ 新增：长辈ID（为谁代叫车）
}
```

#### 后端处理逻辑：
```java
/**
 * 创建代叫车订单
 * 
 * 关键逻辑：
 * 1. 如果 elderId != null，说明是代叫车订单
 * 2. 订单状态初始化为：PENDING_ELDER_CONFIRM（待长辈确认）
 * 3. 记录 proxyUserId（代叫人，当前登录用户）
 * 4. 记录 elderUserId（乘车人，elderId）
 * 5. 使用 startLat/startLng 作为起点（长辈位置）
 * 6. 使用 destLat/destLng 作为终点（目的地）
 * 7. ⭐ 通过 WebSocket 向长辈推送 NEW_ORDER 消息
 */
@PostMapping("/api/order/create")
public Result<Order> createOrder(@RequestBody CreateOrderRequest request) {
    Long currentUserId = getCurrentUserId();  // 代叫人ID
    
    Order order = new Order();
    order.setProxyUserId(currentUserId);  // 代叫人
    
    if (request.getElderId() != null) {
        // 代叫车订单
        order.setElderUserId(request.getElderId());  // 乘车人（长辈）
        order.setStatus(OrderStatus.PENDING_ELDER_CONFIRM);  // 待长辈确认
        order.setStartLat(request.getStartLat());  // 起点：长辈位置
        order.setStartLng(request.getStartLng());
        
        // ⭐ 推送 NEW_ORDER 消息给长辈
        webSocketService.sendToUser(request.getElderId(), new GuardPushMessage(
            "NEW_ORDER",
            currentUserId,      // 代叫人ID
            request.getElderId(), // 长辈ID
            order.getId(),        // 订单ID
            getUserName(currentUserId),  // 代叫人姓名
            request.getPoiName(),        // 目的地
            ...
        ));
    } else {
        // 普通订单（自己叫车）
        order.setElderUserId(currentUserId);
        order.setStatus(OrderStatus.WAITING_DRIVER);
        order.setStartLat(request.getStartLat() != null ? request.getStartLat() : currentLocation.getLat());
        order.setStartLng(request.getStartLng() != null ? request.getStartLng() : currentLocation.getLng());
    }
    
    orderRepository.save(order);
    return Result.success(order);
}
```

---

### 3. 长辈收到代叫车请求并确认

#### WebSocket 消息（NEW_ORDER）：
```json
{
  "type": "NEW_ORDER",
  "orderId": 123,                    // 订单ID
  "proxyUserId": 27,                 // 代叫人ID
  "proxyUserName": "张三",           // 代叫人姓名
  "destAddress": "木棉酒店",         // 目的地
  "destLat": 23.655876083140846,
  "destLng": 116.6705916234186,
  "startLat": 23.65550773048479,     // 起点（长辈当前位置）
  "startLng": 116.67295605706695,
  "timestamp": 1776696790000
}
```

#### 前端行为：
```kotlin
// 长辈端收到 NEW_ORDER 消息后：
// 1. MainActivity 监听 MyApplication.proxyOrderRequestEvent
// 2. 弹出全局确认对话框："张三 请求帮您叫车到 木棉酒店，是否接受？"
// 3. 长辈点击"接受"或"拒绝"

if (accepted) {
    homeViewModel.confirmProxyOrder(orderId, confirmed = true)
} else {
    homeViewModel.confirmProxyOrder(orderId, confirmed = false, rejectReason = "暂时不需要")
}
```

---

### 4. 长辈确认后端的处理

#### 请求接口：
```
POST /api/guard/confirmProxyOrder/{orderId}
Body: {
  "confirmed": true,              // true-接受，false-拒绝
  "rejectReason": null            // 拒绝原因（可选）
}
```

#### 后端处理逻辑：
```java
/**
 * 长辈确认代叫车请求
 * 
 * 关键逻辑：
 * 1. 更新订单状态
 * 2. 如果 accepted，状态改为 WAITING_DRIVER（等待司机接单）
 * 3. 如果 rejected，状态改为 CANCELLED（已取消）
 * 4. ⭐ 通过 WebSocket 向亲友推送 PROXY_ORDER_CONFIRMED 消息
 */
@PostMapping("/api/guard/confirmProxyOrder/{orderId}")
public Result<Void> confirmProxyOrder(
    @PathVariable Long orderId,
    @RequestBody ConfirmProxyOrderRequest request
) {
    Long elderUserId = getCurrentUserId();  // 当前登录用户（长辈）
    Order order = orderRepository.findById(orderId);
    
    if (order == null || !order.getElderUserId().equals(elderUserId)) {
        return Result.error("订单不存在或无权操作");
    }
    
    if (request.getConfirmed()) {
        // 长辈接受
        order.setStatus(OrderStatus.WAITING_DRIVER);
        order.setElderConfirmTime(LocalDateTime.now());
        
        // ⭐ 推送 PROXY_ORDER_CONFIRMED 消息给亲友
        webSocketService.sendToUser(order.getProxyUserId(), new GuardPushMessage(
            "PROXY_ORDER_CONFIRMED",
            elderUserId,          // 长辈ID
            order.getProxyUserId(), // 亲友ID
            order.getId(),        // 订单ID
            "长辈已同意代叫车请求",
            ...
        ));
        
        // ⭐ 触发司机分配逻辑（延迟10秒）
        scheduler.schedule(() -> {
            assignDriver(order);
        }, 10, TimeUnit.SECONDS);
        
    } else {
        // 长辈拒绝
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request.getRejectReason());
        order.setCancelTime(LocalDateTime.now());
        
        // ⭐ 推送 PROXY_ORDER_CONFIRMED 消息给亲友（告知被拒绝）
        webSocketService.sendToUser(order.getProxyUserId(), new GuardPushMessage(
            "PROXY_ORDER_CONFIRMED",
            elderUserId,
            order.getProxyUserId(),
            order.getId(),
            "长辈拒绝了代叫车请求：" + request.getRejectReason(),
            ...
        ));
    }
    
    orderRepository.save(order);
    return Result.success();
}
```

---

### 5. 亲友收到确认结果

#### WebSocket 消息（PROXY_ORDER_CONFIRMED）：
```json
{
  "type": "PROXY_ORDER_CONFIRMED",
  "orderId": 123,
  "elderUserId": 28,
  "proxyUserId": 27,
  "message": "长辈已同意代叫车请求",  // 或 "长辈拒绝了代叫车请求：暂时不需要"
  "confirmed": true,                  // true-接受，false-拒绝
  "timestamp": 1776696800000
}
```

#### 前端行为：
```kotlin
// 亲友端 ChatViewModel 收到 PROXY_ORDER_CONFIRMED 消息后：
// 1. 更新 sharedLocation.orderStatus = 1（已同意）
// 2. 私聊界面的卡片自动更新为："✅ 您已同意代叫车请求"
// 3. 按钮变为"查看订单详情"
```

---

### 6. 司机接单后推送

#### WebSocket 消息（DRIVER_REQUEST）：
```json
{
  "type": "DRIVER_REQUEST",
  "orderId": 123,
  "driverId": 456,
  "driverName": "李师傅",
  "driverPhone": "138****1234",
  "carNumber": "粤A·12345",
  "message": "有司机接单，是否允许该司机接单？",
  "timestamp": 1776696810000
}
```

#### 前端行为：
```kotlin
// 长辈端和亲友端都会收到此消息
// 弹出确认对话框："司机李师傅（粤A·12345）已接单，是否允许？"
// 长辈点击"允许"后，订单状态变为 IN_PROGRESS（行程中）
```

---

## 📊 订单状态流转图

```
创建订单（代叫车）
    ↓
PENDING_ELDER_CONFIRM（待长辈确认）
    ↓
长辈确认
    ├─ 接受 → WAITING_DRIVER（等待司机接单）
    │           ↓
    │       DRIVER_ASSIGNED（司机已分配）
    │           ↓
    │       长辈/亲友确认司机
    │           ↓
    │       IN_PROGRESS（行程中）
    │           ↓
    │       COMPLETED（已完成）
    │
    └─ 拒绝 → CANCELLED（已取消）
```

---

## 🔧 需要后端修改的内容

### 1. 数据库表结构调整

**orders 表需要添加字段：**
```sql
ALTER TABLE orders ADD COLUMN elder_user_id BIGINT COMMENT '乘车人ID（长辈）';
ALTER TABLE orders ADD COLUMN proxy_user_id BIGINT COMMENT '代叫人ID（亲友）';
ALTER TABLE orders ADD COLUMN start_lat DOUBLE COMMENT '起点纬度';
ALTER TABLE orders ADD COLUMN start_lng DOUBLE COMMENT '起点经度';
ALTER TABLE orders ADD COLUMN elder_confirm_time DATETIME COMMENT '长辈确认时间';
ALTER TABLE orders ADD COLUMN cancel_reason VARCHAR(255) COMMENT '取消原因';
```

### 2. 订单状态枚举

```java
public enum OrderStatus {
    PENDING_ELDER_CONFIRM,  // ⭐ 新增：待长辈确认
    WAITING_DRIVER,         // 等待司机接单
    DRIVER_ASSIGNED,        // 司机已分配
    IN_PROGRESS,            // 行程中
    COMPLETED,              // 已完成
    CANCELLED               // 已取消
}
```

### 3. WebSocket 消息类型

需要支持以下消息类型：
- `FAVORITE_SHARED` - 收藏地点分享
- `NEW_ORDER` - 新订单通知（代叫车）
- `PROXY_ORDER_CONFIRMED` - 长辈确认结果
- `DRIVER_REQUEST` - 司机接单请求
- `DRIVER_LOCATION` - 司机位置更新
- `CHAT_MESSAGE` - 订单内聊天消息

### 4. API 接口清单

#### ✅ 已有接口（无需修改）
- `POST /api/order/create` - 创建订单（需要支持 elderId 参数）
- `POST /api/guard/confirmProxyOrder/{orderId}` - 长辈确认代叫车

#### ⚠️ 需要调整的接口
- `POST /api/order/create` 需要接收 `elderId`、`startLat`、`startLng` 参数
- 创建订单时，如果 `elderId != null`，需要：
  - 设置订单状态为 `PENDING_ELDER_CONFIRM`
  - 记录 `proxyUserId`（当前登录用户）
  - 记录 `elderUserId`（elderId）
  - 推送 `NEW_ORDER` 消息给长辈

---

## 🎯 关键要点总结

1. **代叫车订单标识**：通过 `elderId` 参数判断是否为代叫车订单
2. **订单初始状态**：代叫车订单初始状态为 `PENDING_ELDER_CONFIRM`
3. **消息推送时机**：
   - 创建订单后 → 推送 `NEW_ORDER` 给长辈
   - 长辈确认后 → 推送 `PROXY_ORDER_CONFIRMED` 给亲友
   - 司机接单后 → 推送 `DRIVER_REQUEST` 给双方
4. **起点位置**：代叫车订单使用 `startLat/startLng`（长辈当前位置）作为起点
5. **终点位置**：使用 `destLat/destLng`（收藏地点）作为终点

---

## 📝 测试用例

### 测试场景1：正常代叫车流程
```
1. 长辈分享收藏地点给亲友
2. 亲友点击"立即叫车"
3. 后端创建订单，状态=PENDING_ELDER_CONFIRM
4. 后端推送 NEW_ORDER 给长辈
5. 长辈点击"接受"
6. 后端更新订单状态=WAITING_DRIVER
7. 后端推送 PROXY_ORDER_CONFIRMED 给亲友
8. 10秒后分配司机
9. 后端推送 DRIVER_REQUEST 给双方
10. 长辈点击"允许"
11. 订单状态=IN_PROGRESS
```

### 测试场景2：长辈拒绝代叫车
```
1-4. 同上
5. 长辈点击"拒绝"
6. 后端更新订单状态=CANCELLED
7. 后端推送 PROXY_ORDER_CONFIRMED 给亲友（包含拒绝原因）
```

### 测试场景3：亲友端卡片状态更新
```
1. 亲友在私聊界面看到卡片（orderStatus=0，待确认）
2. 长辈确认后
3. 亲友收到 PROXY_ORDER_CONFIRMED 消息
4. 卡片自动更新为 orderStatus=1（已同意）
5. 按钮文字变为"查看订单详情"
```

---

## ❓ 常见问题

**Q1: 为什么需要 elderId 参数？**
A: 后端需要知道这个订单是为谁代叫的，才能：
- 正确设置订单的 elderUserId
- 推送 NEW_ORDER 消息给正确的长辈
- 区分代叫车订单和普通订单

**Q2: startLat/startLng 的作用是什么？**
A: 代叫车订单的起点是长辈的当前位置，而不是亲友的位置。这两个字段用于记录长辈叫车时的位置。

**Q3: 订单状态为什么要增加 PENDING_ELDER_CONFIRM？**
A: 代叫车订单需要长辈二次确认，不能直接进入 WAITING_DRIVER 状态。这个状态表示"订单已创建，等待长辈确认"。

**Q4: 为什么长辈确认后要延迟10秒再分配司机？**
A: 这是业务逻辑需求，给长辈和亲友一个缓冲时间，可以在司机分配前取消订单。

---

## 📞 联系方式

如有任何问题，请联系前端开发团队。
