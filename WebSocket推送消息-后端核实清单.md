# WebSocket 推送消息 - 后端字段核实清单

**前端版本**: v1.0 (2026-04-21)  
**优先级**: 🔴 高（影响代叫车功能正常运行）

---

## 📋 需要后端确认的3个关键问题

### 1️⃣ PROXY_ORDER_CONFIRMED 消息缺少 userId 字段

**当前返回**（从日志验证）:
```json
{
  "orderId": 141,
  "confirmTime": "2026-04-21T02:56:32.009187",
  "type": "PROXY_ORDER_CONFIRMED",
  "confirmed": true,
  "elderUserId": 28
}
```

**期望返回**:
```json
{
  "userId": 28,                      // ⭐ 请新增此字段
  "elderUserId": 28,                 // 保留兼容
  "orderId": 141,
  "confirmed": true,
  "confirmTime": "2026-04-21T02:56:32.009187"
}
```

**原因**: 前端统一使用 `pushMessage.userId` 获取长辈ID，如果该字段为空会导致 `elderId=0`，进而引发一系列问题（SharedPreferences key 错误、订单ID传递失败等）。

---

### 2️⃣ FAVORITE_SHARED 消息是否包含长辈实时位置？

**期望返回**:
```json
{
  "type": "FAVORITE_SHARED",
  "userId": 28,                      // 请确认是否有此字段
  "elderUserId": 28,
  "proxyUserName": "张阿姨",
  "favoriteName": "人民公园",
  "favoriteAddress": "北京市朝阳区xxx",
  "favoriteLatitude": 39.9042,
  "favoriteLongitude": 116.4074,
  "elderCurrentLat": 39.9150,        // ⭐ 请确认是否有此字段
  "elderCurrentLng": 116.4040,       // ⭐ 请确认是否有此字段
  "elderLocationTimestamp": 1713600000000  // ⭐ 请确认是否有此字段
}
```

**待确认问题**:
1. ✅ 是否返回顶层 `userId` 字段？
2. ❓ 是否返回 `elderCurrentLat/elderCurrentLng`（长辈实时位置）？
3. ❓ 如果长辈位置为空，是返回 `null` 还是不返回该字段？

**原因**: 前端需要使用长辈实时位置作为代叫车的起点，如果缺失会导致司机去错地方接人。

---

### 3️⃣ 数字类型建议改为整数

**当前返回**（从日志第12行验证）:
```json
{
  "data": {
    "id": 137.0,                     // ⚠️ 浮点数
    "userId": 28.0,                  // ⚠️ 浮点数
    "status": 0.0                    // ⚠️ 浮点数
  }
}
```

**期望返回**:
```json
{
  "data": {
    "id": 137,                       // ✅ 整数
    "userId": 28,                    // ✅ 整数
    "status": 0                      // ✅ 整数
  }
}
```

**原因**: 虽然前端可以自动转换，但使用整数更符合语义，避免潜在的精度问题。

---

## ✅ 已验证正确的部分

### ORDER_CREATED 消息
```json
{
  "type": "ORDER_CREATED",
  "message": "您的亲友亲友为您叫了一辆车",
  "success": true,
  "data": {
    "userId": 28,                    // ✅ 有
    "guardianUserId": 27,            // ✅ 有
    "orderId": 141,                  // ✅ 有
    "requesterName": "亲友",         // ✅ 有（前端映射到 proxyUserName）
    "destAddress": "...",            // ✅ 有
    "poiName": "...",                // ✅ 有
    "destLat": 23.655876083140846,   // ✅ 有
    "destLng": 116.6705916234186,    // ✅ 有
    "startLat": 23.6557241112633,    // ✅ 有
    "startLng": 116.67212612973297   // ✅ 有
  }
}
```
**状态**: ✅ 完全正确，无需修改

---

## 🔧 建议的统一规范

### 所有 WebSocket 推送消息都包含 userId 字段

为了简化前端处理逻辑，建议所有推送给用户的消息都包含顶层 `userId` 字段：

```json
{
  "type": "XXX_MESSAGE",
  "userId": 28,              // ⭐ 统一字段：接收者ID
  "data": { ... }
}
```

**好处**:
1. 前端可以直接从 `pushMessage.userId` 获取接收者ID
2. 不需要针对不同消息类型使用不同的字段名
3. 减少前端的降级逻辑复杂度

---

## 📊 影响评估

| 问题 | 影响范围 | 严重程度 | 修复工作量 |
|-----|---------|---------|----------|
| PROXY_ORDER_CONFIRMED 缺少 userId | 长辈确认后亲友端无法正确更新状态 | 🔴 高 | 0.1天 |
| FAVORITE_SHARED 缺少长辈位置 | 代叫车起点不准确 | 🟡 中 | 0.2天 |
| 数字类型为浮点数 | 轻微性能影响 | 🟢 低 | 0.1天 |

---

## 📞 联系方式

如有疑问，请联系前端开发团队。

**完整技术文档**: `代叫车与收藏功能-完整技术架构.md`

---

**生成时间**: 2026-04-21  
**文档版本**: v1.0
