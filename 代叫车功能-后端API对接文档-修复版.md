# 代叫车功能-后端API对接文档（修复版）

## 📋 文档说明

本文档基于前端代叫车业务流程修复，明确了**正确的业务逻辑**和**接口字段规范**。

---

## 🎯 核心业务逻辑（重要）

### ✅ 正确的代叫车流程

```
【第一步】长辈端发送收藏地址给亲友
    ↓
【第二步】亲友端创建订单（起点=长辈位置，终点=收藏地址）
    ↓
【第三步】长辈端收到NEW_ORDER推送，显示确认卡片
    ↓
【第四步】长辈点击"接受" → 调用 confirmProxyOrder API
    ↓
【第五步】亲友端收到PROXY_ORDER_CONFIRMED推送
    ↓
【第六步】系统派单 → 司机接单 → 推送DRIVER_REQUEST
    ↓
【第七步】亲友端显示确认弹窗（同意/拒绝司机）⭐ 仅亲友端有权限
    ↓
【第八步】亲友端同意 → 推送ORDER_ACCEPTED给双方
    ↓
【第九步】长辈端可以看到司机信息、联系司机（但不能取消/拒绝）
```

### ❌ 错误的逻辑（已修复）

- ❌ 长辈端自己创建订单
- ❌ 长辈端可以直接选择/拒绝司机
- ❌ 长辈端可以取消订单
- ❌ 长辈端和亲友端都进入订单追踪页面并混用UI逻辑

---

## 📌 关键修改点

### 1. 代叫车订单判断逻辑

**修复前**：使用 `guardianUserId != userId` 判断代叫车订单
```kotlin
// ❌ 错误：后端返回的userId和guardianUserId可能相同
val isProxyOrder = order.guardianUserId != null && order.guardianUserId != order.userId
```

**修复后**：使用**订单状态**判断代叫车订单
```kotlin
// ✅ 正确：代叫车订单状态为 0/1/2，普通订单直接跳到 3+
val isProxyOrder = order.status == 0 || order.status == 1 || order.status == 2
```

### 2. DRIVER_REQUEST 消息处理

**修复前**：长辈端和亲友端都直接更新司机信息
**修复后**：
- **亲友端**：显示确认弹窗（同意/拒绝）
- **长辈端**：只刷新订单，查看司机信息

---

## 🔧 接口对接规范

### 1. 创建订单接口

**接口**：`POST /api/guard/proxyOrder`

**请求体**（亲友端调用）：
```json
{
  "elderId": 28,          // 长辈用户ID（必填）
  "startLat": 23.655348,  // 起点纬度（长辈当前位置）
  "startLng": 116.674149, // 起点经度
  "destLat": 23.662117,   // 终点纬度（收藏地址）
  "destLng": 116.649874,  // 终点经度
  "destAddress": "广东省潮州市湘桥区太平街道太平路牌坊街",
  "needConfirm": true     // 需要长辈确认
}
```

**响应体**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 153,
    "orderNo": "AX202604211152251059",
    "userId": 28,              // 长辈ID（乘客）
    "guardianUserId": 27,      // 亲友ID（代叫人）⭐ 关键字段
    "startLat": 23.655348,
    "startLng": 116.674149,
    "destLat": 23.662117,
    "destLng": 116.649874,
    "destAddress": "广东省潮州市湘桥区太平街道太平路牌坊街",
    "status": 0,               // 0-待长辈确认
    "estimatePrice": 10.04,
    "createTime": "2026-04-21T11:52:20.985350400"
  }
}
```

**⚠️ 重要字段说明**：
- `userId`：必须是**长辈ID**（乘车人）
- `guardianUserId`：必须是**亲友ID**（代叫人）
- **两个ID必须不同**，否则前端无法正确判断代叫车订单！

---

### 2. 长辈确认订单接口

**接口**：`POST /api/guard/confirmProxyOrder/{orderId}`

**请求头**：
```
X-User-Id: 28  // 长辈ID
```

**请求体**：
```json
{
  "confirmed": true,        // true=同意，false=拒绝
  "rejectReason": null      // 拒绝原因（confirmed=false时必填）
}
```

**响应体**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": 153,
    "confirmed": true,
    "confirmTime": "2026-04-21T11:52:26.614070200"
  }
}
```

**业务逻辑**：
- 确认后订单状态从 `0` 变为 `1`（已确认）
- 后端需要推送 `PROXY_ORDER_CONFIRMED` 消息给亲友端

---

### 3. 确认/拒绝司机接单接口

**接口**：`POST /api/order/confirmDriver`

**请求头**：
```
X-User-Id: 27  // 亲友ID（代叫人）
```

**请求体**：
```json
{
  "orderId": 153,
  "accepted": true  // true=同意，false=拒绝
}
```

**响应体**：
```json
{
  "code": 200,
  "message": "success"
}
```

**业务逻辑**：
- **仅亲友端可以调用此接口**（长辈端无权限）
- 同意后订单状态从 `2` 变为 `3`（司机已接单）
- 后端需要推送 `ORDER_ACCEPTED` 消息给双方

---

## 📡 WebSocket 推送消息规范

### 1. NEW_ORDER（新订单推送）

**推送给**：长辈端

**消息格式**：
```json
{
  "type": "NEW_ORDER",
  "orderId": 153,
  "orderNo": "AX202604211152251059",
  "userId": 28,              // 长辈ID
  "elderUserId": 28,         // 长辈ID
  "proxyUserId": 27,         // 亲友ID
  "proxyUserName": "亲友",   // 亲友姓名
  "startLat": 23.655348,
  "startLng": 116.674149,
  "destLat": 23.662117,
  "destLng": 116.649874,
  "destAddress": "广东省潮州市湘桥区太平街道太平路牌坊街",
  "poiName": "广东省潮州市湘桥区太平街道太平路牌坊街",
  "estimatePrice": 10.04,
  "timestamp": 1776743545619
}
```

---

### 2. PROXY_ORDER_CONFIRMED（长辈确认推送）

**推送给**：亲友端

**消息格式**：
```json
{
  "type": "PROXY_ORDER_CONFIRMED",
  "orderId": 153,
  "userId": 28,              // 长辈ID
  "elderUserId": 28,         // 长辈ID
  "confirmed": true,         // 是否同意
  "rejectReason": null,      // 拒绝原因（confirmed=false时返回）
  "confirmTime": "2026-04-21T11:52:26.614070200"
}
```

---

### 3. DRIVER_REQUEST（司机接单请求）

**推送给**：亲友端（代叫人）

**消息格式**：
```json
{
  "type": "DRIVER_REQUEST",
  "orderId": 153,
  "userId": 27,              // 亲友ID（代叫人）
  "driverId": 100,
  "driverName": "赵师傅",
  "driverPhone": "13880053734",
  "driverAvatar": null,
  "carNo": "苏A Z8A7L",
  "carType": "特斯拉Model 3",
  "carColor": "黑色",
  "rating": 5.0,
  "driverLat": 23.653199,
  "driverLng": 116.670957,
  "etaMinutes": 5,
  "message": "是否允许该司机接单？"
}
```

**⚠️ 重要说明**：
- 此消息**只推送给亲友端**（代叫人）
- 长辈端**不接收**此消息（或接收但不处理）
- 亲友端可以选择"同意"或"拒绝"

---

### 4. ORDER_ACCEPTED（司机接单确认）

**推送给**：长辈端 + 亲友端

**消息格式**：
```json
{
  "type": "ORDER_ACCEPTED",
  "orderId": 153,
  "userId": 28,              // 长辈ID
  "driverId": 100,
  "driverName": "赵师傅",
  "driverPhone": "13880053734",
  "driverAvatar": null,
  "carNo": "苏A Z8A7L",
  "carType": "特斯拉Model 3",
  "carColor": "黑色",
  "rating": 5.0,
  "driverLat": 23.653199,
  "driverLng": 116.670957,
  "etaMinutes": 5
}
```

---

## 🗺️ 订单状态流转

### 代叫车订单状态

| 状态 | 说明 | 触发条件 |
|------|------|---------|
| 0 | 待长辈确认 | 亲友创建订单后 |
| 1 | 已确认 | 长辈点击"接受"后 |
| 2 | 正在寻找司机 | 系统开始派单 |
| 3 | 司机已接单 | 亲友端确认司机后 |
| 4 | 司机已到达 | 司机到达上车点 |
| 5 | 行程中 | 乘客上车后 |
| 6 | 已完成 | 到达目的地后 |
| 7 | 已取消 | 亲友端取消订单 |
| 8 | 已拒绝 | 长辈端拒绝订单 |

### 普通订单状态

| 状态 | 说明 | 触发条件 |
|------|------|---------|
| 2 | 等待司机接单 | 用户创建订单后 |
| 3 | 司机已接单 | 司机接单后 |
| 4 | 司机已到达 | 司机到达上车点 |
| 5 | 行程中 | 乘客上车后 |
| 6 | 已完成 | 到达目的地后 |

---

## 🔐 权限控制规范

### 长辈端权限

| 操作 | 是否允许 | 说明 |
|------|---------|------|
| 查看订单详情 | ✅ | 可以查看 |
| 联系司机 | ✅ | 可以拨打电话 |
| 确认/拒绝订单 | ✅ | 可以接受/拒绝代叫车 |
| 取消订单 | ❌ | 不能取消（只能亲友端取消） |
| 选择/拒绝司机 | ❌ | 不能选择司机（只能亲友端操作） |

### 亲友端权限

| 操作 | 是否允许 | 说明 |
|------|---------|------|
| 查看订单详情 | ✅ | 可以查看 |
| 联系司机 | ✅ | 可以拨打电话 |
| 取消订单 | ✅ | 可以取消订单 |
| 选择/拒绝司机 | ✅ | 可以同意/拒绝司机 |
| 确认/拒绝订单 | ❌ | 不需要确认（长辈端确认） |

---

## 🐛 已知问题与修复

### 问题1：userId和guardianUserId字段混淆

**问题描述**：
- 后端返回的 `userId` 和 `guardianUserId` 值相同
- 导致前端无法判断代叫车订单

**修复方案**：
- 前端改用**订单状态**判断（`status <= 2` 为代叫车）
- 后端需确保 `userId` 和 `guardianUserId` 字段语义清晰：
  - `userId` = 长辈ID（乘客）
  - `guardianUserId` = 亲友ID（代叫人）

### 问题2：DRIVER_REQUEST消息推送对象错误

**问题描述**：
- DRIVER_REQUEST消息推送给长辈端
- 导致长辈端提前看到司机信息

**修复方案**：
- DRIVER_REQUEST只推送给**亲友端**（代叫人）
- 亲友端确认后，推送ORDER_ACCEPTED给双方

### 问题3：长辈端和亲友端UI逻辑混用

**问题描述**：
- 两端都进入OrderTrackingScreen
- 长辈端可以看到取消订单、选择司机按钮

**修复方案**：
- 通过 `isElderMode` 区分用户角色
- 长辈端隐藏取消订单、选择司机功能
- 长辈端只显示司机信息和联系按钮

---

## 📝 后端修改建议

### 1. 确保订单字段语义清晰

```java
// 订单实体
public class Order {
    private Long id;
    private Long userId;              // 长辈ID（乘客）
    private Long guardianUserId;      // 亲友ID（代叫人）⭐ 必须与userId不同
    private Integer status;           // 订单状态
    // ... 其他字段
}
```

### 2. 创建订单时正确赋值

```java
// 代叫车订单创建
Order order = new Order();
order.setUserId(elderId);              // 长辈ID（乘客）
order.setGuardianUserId(proxyUserId);  // 亲友ID（代叫人）
order.setStatus(0);                    // 待长辈确认
```

### 3. DRIVER_REQUEST消息推送逻辑

```java
// 司机接单时
if (order.getGuardianUserId() != null) {
    // 代叫车订单：只推送给亲友端
    webSocketService.sendToUser(
        order.getGuardianUserId(),  // 亲友ID
        buildDriverRequestMessage(order, driver)
    );
} else {
    // 普通订单：推送给用户
    webSocketService.sendToUser(
        order.getUserId(),
        buildOrderAcceptedMessage(order, driver)
    );
}
```

### 4. ORDER_ACCEPTED消息推送逻辑

```java
// 亲友端确认司机后
// 推送给长辈端
webSocketService.sendToUser(
    order.getUserId(),  // 长辈ID
    buildOrderAcceptedMessage(order, driver)
);

// 推送给亲友端（确认成功提示）
webSocketService.sendToUser(
    order.getGuardianUserId(),  // 亲友ID
    buildOrderAcceptedMessage(order, driver)
);
```

---

## ✅ 验收标准

### 测试场景1：代叫车完整流程

1. ✅ 亲友端创建订单，订单状态=0
2. ✅ 长辈端收到NEW_ORDER推送，显示确认卡片
3. ✅ 长辈端点击"接受"，调用confirmProxyOrder接口
4. ✅ 亲友端收到PROXY_ORDER_CONFIRMED推送
5. ✅ 司机接单，亲友端收到DRIVER_REQUEST推送
6. ✅ 亲友端显示确认弹窗（同意/拒绝）
7. ✅ 亲友端点击"同意"，订单状态变为3
8. ✅ 长辈端收到ORDER_ACCEPTED推送，看到司机信息
9. ✅ 长辈端可以联系司机，但不能取消订单

### 测试场景2：长辈端权限控制

1. ✅ 长辈端不显示"取消订单"按钮
2. ✅ 长辈端不显示"选择司机"弹窗
3. ✅ 长辈端可以看到司机信息和联系按钮
4. ✅ 长辈端收到DRIVER_REQUEST时不显示弹窗

### 测试场景3：亲友端权限控制

1. ✅ 亲友端可以取消订单（status < 6）
2. ✅ 亲友端收到DRIVER_REQUEST时显示确认弹窗
3. ✅ 亲友端可以选择同意/拒绝司机
4. ✅ 亲友端可以联系司机

---

## 📞 联系方式

如有问题，请及时沟通。

---

**文档版本**：v2.0（修复版）  
**更新时间**：2026-04-21  
**前端版本**：基于最新修复代码生成
