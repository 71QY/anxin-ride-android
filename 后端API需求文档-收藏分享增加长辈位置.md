# 收藏分享功能 - 后端API修复说明

## 📋 问题描述

当前长辈端分享收藏地点给普通用户时，普通用户点击"立即叫车"后，**代叫车的起点使用的是默认位置（或当前位置），而不是长辈的实时位置**。

这导致司机可能去错地方接人。

## 🔧 需要修复的内容

### 1. WebSocket 推送消息增强

当长辈分享收藏地点时，后端推送的 `FAVORITE_SHARED` 消息需要**额外包含长辈的实时位置**。

#### 当前消息结构
```json
{
  "type": "GUARD_PUSH",
  "data": {
    "type": "FAVORITE_SHARED",
    "senderId": 123,
    "proxyUserName": "张阿姨",
    "favoriteName": "人民公园",
    "favoriteAddress": "北京市朝阳区xxx",
    "favoriteLatitude": 39.9042,
    "favoriteLongitude": 116.4074
  }
}
```

#### 修复后的消息结构
```json
{
  "type": "GUARD_PUSH",
  "data": {
    "type": "FAVORITE_SHARED",
    "senderId": 123,
    "proxyUserName": "张阿姨",
    "favoriteName": "人民公园",
    "favoriteAddress": "北京市朝阳区xxx",
    "favoriteLatitude": 39.9042,      // 收藏地点纬度（目的地）
    "favoriteLongitude": 116.4074,     // 收藏地点经度（目的地）
    
    // ⭐ 新增：长辈实时位置（作为代叫车的起点）
    "elderCurrentLat": 39.9150,        // 长辈当前纬度
    "elderCurrentLng": 116.4040,       // 长辈当前经度
    "elderLocationTimestamp": 1713600000000  // 位置更新时间戳
  }
}
```

### 2. 数据模型修改

需要在 `GuardPushMessage` 数据类中添加以下字段：

```kotlin
data class GuardPushMessage(
    // ... 现有字段 ...
    
    // ⭐ 新增：长辈实时位置（用于代叫车起点）
    val elderCurrentLat: Double? = null,      // 长辈当前纬度
    val elderCurrentLng: Double? = null,      // 长辈当前经度
    val elderLocationTimestamp: Long? = null  // 位置更新时间戳
)
```

## 💡 实现建议

### 方案 1：从缓存中获取长辈位置（推荐）

如果后端有缓存长辈的实时位置（通过定期上报），直接从缓存中读取：

```java
// 伪代码
Double elderLat = locationCache.get(elderId).getLatitude();
Double elderLng = locationCache.get(elderId).getLongitude();
Long timestamp = locationCache.get(elderId).getTimestamp();

// 添加到推送消息中
pushMessage.setElderCurrentLat(elderLat);
pushMessage.setElderCurrentLng(elderLng);
pushMessage.setElderLocationTimestamp(timestamp);
```

### 方案 2：查询数据库中的最新位置

如果后端没有缓存，可以查询数据库中长辈的最新位置记录：

```java
// 伪代码
UserLocation latestLocation = userLocationRepository.findLatestByUserId(elderId);
if (latestLocation != null) {
    pushMessage.setElderCurrentLat(latestLocation.getLatitude());
    pushMessage.setElderCurrentLng(latestLocation.getLongitude());
    pushMessage.setElderLocationTimestamp(latestLocation.getUpdateTime());
}
```

## ✅ 验收标准

1. **前端收到 FAVORITE_SHARED 消息时**，能够获取到：
   - `favoriteLatitude/favoriteLongitude`：收藏地点坐标（目的地）
   - `elderCurrentLat/elderCurrentLng`：长辈实时位置（起点）

2. **普通用户点击"立即叫车"后**：
   - 起点：使用长辈的实时位置（`elderCurrentLat/elderCurrentLng`）
   - 终点：使用收藏地点（`favoriteLatitude/favoriteLongitude`）

3. **如果长辈位置为空或过期**（超过 5 分钟）：
   - 降级使用默认位置或提示用户手动选择起点

## 📝 前端配合说明

前端将在收到消息后：
1. 优先使用 `elderCurrentLat/elderCurrentLng` 作为代叫车起点
2. 如果该字段为空，则使用当前位置作为起点（降级方案）
3. 在日志中记录使用的起点坐标，方便调试

---

**优先级**: 🔴 高（影响代叫车功能的准确性）  
**预计工作量**: 0.5 天  
**涉及文件**: 
- `GuardPushMessage.java/kt`（数据模型）
- `WebSocketPushService.java`（推送逻辑）
- `LocationCacheService.java`（位置缓存，如果有）
