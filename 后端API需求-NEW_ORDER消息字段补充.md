# 后端 API 需求：NEW_ORDER WebSocket 推送消息字段补充

## 📋 需求背景

**问题**：长辈端私聊界面无法显示代叫车卡片，原因是 `NEW_ORDER` WebSocket 推送消息缺少必要的坐标字段。

**影响**：
- ❌ 长辈端进入私聊界面时，`sharedLocation` 为 null
- ❌ 无法显示"立即叫车"卡片
- ❌ 用户体验受损

---

## 🔧 需要修改的接口

### WebSocket 推送消息类型：`NEW_ORDER`

**当前问题**：后端发送的 `NEW_ORDER` 消息缺少以下关键字段：
- `destLat` / `destLng`（目的地坐标）
- `startLat` / `startLng`（起点坐标/长辈当前位置）
- `poiName`（目的地名称）
- `proxyUserId`（代叫人ID）

---

## ✅ 修复方案

### 1. 在 `NEW_ORDER` 消息中添加缺失字段

**消息格式示例**：

```json
{
  "type": "NEW_ORDER",
  "orderId": 123,
  "orderNo": "AX177670600684870661000",
  
  // ⭐ 新增：目的地信息
  "poiName": "木棉酒店(韩师店)",
  "destAddress": "广东省潮州市湘桥区桥东街道木棉酒店(韩师店)",
  "destLat": 23.65587615966797,
  "destLng": 116.67059326171875,
  
  // ⭐ 新增：起点信息（长辈当前位置）
  "startLat": 23.655607223510742,
  "startLng": 116.67277526855469,
  
  // 代叫人信息
  "proxyUserName": "亲友",
  "proxyUserId": 27,
  
  // 长辈信息
  "elderUserId": 28,
  
  // 时间戳
  "timestamp": 1776706006848
}
```

---

## 📊 字段说明

| 字段名 | 类型 | 必填 | 说明 | 用途 |
|--------|------|------|------|------|
| `type` | String | ✅ | 固定值：`"NEW_ORDER"` | 消息类型标识 |
| `orderId` | Long | ✅ | 订单ID | 关联订单 |
| `orderNo` | String | ✅ | 订单号 | 订单编号 |
| **`poiName`** | String | ✅ | **目的地名称** | **卡片显示目的地** |
| `destAddress` | String | ✅ | 目的地详细地址 | 地址详情 |
| **`destLat`** | Double | ✅ | **目的地纬度** | **地图标记、路线规划** |
| **`destLng`** | Double | ✅ | **目的地经度** | **地图标记、路线规划** |
| **`startLat`** | Double | ✅ | **起点纬度（长辈位置）** | **代叫车起点坐标** |
| **`startLng`** | Double | ✅ | **起点经度（长辈位置）** | **代叫车起点坐标** |
| `proxyUserName` | String | ✅ | 代叫人姓名 | 显示"XX为您叫车" |
| **`proxyUserId`** | Long | ✅ | **代叫人ID** | **关联代叫人** |
| `elderUserId` | Long | ✅ | 长辈用户ID | 关联长辈 |
| `timestamp` | Long | ✅ | 时间戳（毫秒） | 消息时间 |

---

## 🎯 前端使用场景

### 场景 1：长辈端收到 NEW_ORDER 消息

**流程**：
1. 长辈端 WebSocket 收到 `NEW_ORDER` 消息
2. 提取字段并创建 `SharedLocationInfo` 对象
3. 更新 StateFlow：`_sharedLocation.value = sharedInfo`
4. 持久化保存到 SharedPreferences
5. 私聊界面从 StateFlow 读取数据，显示卡片

**代码逻辑**（ChatViewModel.kt）：
```kotlin
if (_isElderMode.value) {
    val sharedInfo = SharedLocationInfo(
        elderId = pushMessage.elderUserId ?: pushMessage.proxyUserId ?: 0L,
        elderName = pushMessage.proxyUserName ?: "亲友",
        favoriteName = pushMessage.poiName ?: pushMessage.destAddress ?: "未知目的地",
        favoriteAddress = pushMessage.destAddress ?: "",
        latitude = pushMessage.destLat ?: 0.0,
        longitude = pushMessage.destLng ?: 0.0,
        elderCurrentLat = pushMessage.startLat,  // 长辈当前位置作为起点
        elderCurrentLng = pushMessage.startLng,
        elderLocationTimestamp = System.currentTimeMillis(),
        orderId = pushMessage.orderId,
        orderStatus = 0  // 0-待确认
    )
    _sharedLocation.value = sharedInfo
    
    // 持久化保存
    viewModelScope.launch {
        val prefs = MyApplication.instance.getSharedPreferences("shared_location_cache", MODE_PRIVATE)
        prefs.edit()
            .putLong("elderId_${sharedInfo.elderId}", sharedInfo.elderId)
            .putString("favoriteName_${sharedInfo.elderId}", sharedInfo.favoriteName)
            .putFloat("latitude_${sharedInfo.elderId}", sharedInfo.latitude.toFloat())
            .putFloat("longitude_${sharedInfo.elderId}", sharedInfo.longitude.toFloat())
            // ... 其他字段
            .apply()
    }
}
```

### 场景 2：私聊界面显示卡片

**条件检查**（PrivateChatScreen.kt）：
```kotlin
val currentCardLocation by chatViewModel.sharedLocation.collectAsStateWithLifecycle()

// 显示卡片的条件
if (currentCardLocation != null && guardianId != null && isElderMode) {
    // 渲染代叫车卡片
    SharedLocationCard(
        location = currentCardLocation!!,
        onCallCar = { /* 跳转到行程追踪 */ }
    )
}
```

---

## 📝 后端实现建议

### 1. 数据来源

- `orderId`, `orderNo`：从订单表查询
- `poiName`, `destAddress`, `destLat`, `destLng`：从订单表的目的地字段获取
- `startLat`, `startLng`：从长辈的实时位置缓存获取（或订单表的起点字段）
- `proxyUserName`, `proxyUserId`：从代叫人（亲友）的用户表查询
- `elderUserId`：从订单表的长辈用户ID字段获取

### 2. 推送时机

当亲友端发起代叫车订单后（订单状态=0），立即向长辈端推送 `NEW_ORDER` 消息。

### 3. 兼容性

- 如果某些字段暂时无法获取，可以设置为 `null`，但**强烈建议提供完整数据**
- 前端已做容错处理，但缺少坐标会导致卡片无法显示

---

## ✅ 验收标准

1. **编译通过**：前端代码无编译错误
2. **日志验证**：长辈端收到 `NEW_ORDER` 后，日志显示：
   ```
   ChatViewModel D  👴 长辈端收到 NEW_ORDER，更新 sharedLocation
   ChatViewModel D  ✅ [长辈端] sharedLocation 已更新：orderId=XXX
   ChatViewModel D  ✅ [长辈端] 目的地：木棉酒店(韩师店)
   ChatViewModel D  ✅ [长辈端] 起点：lat=xxx, lng=xxx
   ChatViewModel D  ✅ [长辈端] 已持久化保存 sharedLocation
   ```
3. **UI 验证**：长辈端进入私聊界面后，**立即显示代叫车卡片**（无需退出重进）
4. **功能验证**：点击卡片上的"立即叫车"按钮，能正常跳转到行程追踪页面

---

## 📞 联系方式

如有问题，请联系前端开发团队。

**优先级**：🔴 高（影响核心功能）

**预计工作量**：30分钟（添加字段 + 测试）
