# 智能体模块 API 接口注意事项（完整版）

## 📋 接口列表

1. **目的地搜索** - `POST /api/agent/search` ⭐⭐⭐⭐
2. **图片识别** - `POST /api/agent/image` ⭐⭐⭐
3. **确认选择** - `POST /api/agent/confirm` ⭐⭐⭐⭐⭐ (核心)
4. **位置更新** - `POST /api/agent/location` ⭐⭐
5. **会话清理** - `POST /api/agent/cleanup` ⭐

---

# 1️⃣ /api/agent/search - 目的地搜索接口

## 📋 接口信息
- **路径**: `POST /api/agent/search`
- **用途**: 根据关键词搜索目的地，返回 POI 列表
- **重要性**: ⭐⭐⭐⭐ (核心搜索功能)

## 🔧 请求参数

### ✅ 正确的请求示例
```json
{
  "sessionId": "ea8377ae-e992-4972-803d-1960bbfe6f0a",
  "keyword": "医院",
  "lat": 23.65322884514967,
  "lng": 116.67698337185544,
  "page": 1,
  "pageSize": 20,
  "sortByDistance": true
}
```

### 📊 参数说明

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| sessionId | String | ✅ | 会话 ID（每次对话唯一标识） | - |
| keyword | String | ✅ | 搜索关键词（如"医院"、"酒店"） | - |
| lat | Double | ✅ | **用户当前纬度**（用于计算距离和排序） | - |
| lng | Double | ✅ | **用户当前经度**（用于计算距离和排序） | - |
| page | Integer | ❌ | 页码（从 1 开始） | 1 |
| pageSize | Integer | ❌ | 每页数量 | 20 |
| sortByDistance | Boolean | ❌ | 是否按距离排序 | true |

## 📝 响应格式

### ✅ 成功响应
```json
{
  "code": 200,
  "message": "为你找到以下地点",
  "success": true,
  "data": {
    "type": "SEARCH",
    "needConfirm": true,
    "places": [
      {
        "id": "poi_001",
        "name": "潮州市中心医院",
        "address": "广东省潮州市湘桥区环城西路",
        "lat": 23.658,
        "lng": 116.625,
        "distance": 5200,
        "duration": 720,
        "price": 25.5,
        "score": 4.8
      }
    ]
  },
  "timestamp": 1717516800000
}
```

### ⚠️ 注意事项
1. **places 数组不能为空**：即使只找到一个地点，也要返回数组
2. **needConfirm 字段**：
   - `true`：需要用户确认选择（弹出候选列表）
   - `false`：可以直接下单（只有一个结果）
3. **distance 单位**：米（m）
4. **duration 单位**：秒（s）
5. **price 单位**：元（¥）

### ❌ 错误响应示例

#### 位置信息缺失
```json
{
  "code": 400,
  "message": "位置信息缺失，无法计算距离",
  "success": false
}
```

#### 未找到相关地点
```json
{
  "code": 404,
  "message": "未找到相关地点，请尝试其他关键词",
  "success": false,
  "data": {
    "type": "CHAT",
    "message": "😕 附近没有找到相关地点，建议您换一种说法或提供更详细的信息"
  }
}
```

---

# 2️⃣ /api/agent/image - 图片识别接口

## 📋 接口信息
- **路径**: `POST /api/agent/image`
- **用途**: 识别用户上传的图片，提取文字信息并推荐地点
- **重要性**: ⭐⭐⭐ (辅助功能)

## 🔧 请求参数

### ✅ 正确的请求示例
```json
{
  "sessionId": "ea8377ae-e992-4972-803d-1960bbfe6f0a",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQE...",
  "lat": 23.65322884514967,
  "lng": 116.67698337185544
}
```

### 📊 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | String | ✅ | 会话 ID |
| imageBase64 | String | ✅ | Base64 编码的图片（包含前缀：`data:image/jpeg;base64,`） |
| lat | Double | ✅ | 用户当前纬度 |
| lng | Double | ✅ | 用户当前经度 |

## 📝 响应格式

### ✅ 成功响应
```json
{
  "code": 200,
  "message": "根据图片内容，为您找到 3 个地点",
  "success": true,
  "data": {
    "type": "SEARCH",
    "needConfirm": true,
    "places": [
      {
        "id": "poi_001",
        "name": "某某餐厅",
        "address": "广东省潮州市...",
        "lat": 23.658,
        "lng": 116.625,
        "distance": 5200,
        "duration": 720,
        "price": 25.5
      }
    ]
  }
}
```

---

# 3️⃣ /api/agent/confirm - 确认选择接口（最重要）⭐⭐⭐⭐⭐

## 📋 接口信息
- **路径**: `POST /api/agent/confirm`
- **用途**: 用户确认选择目的地后，创建订单
- **重要性**: ⭐⭐⭐⭐⭐ (核心下单接口)

## ⚠️ 当前问题（2026-04-04 最新日志）

```
2026-04-04 13:07:00.520  ChatViewModel  D  === 用户选择候选地点 ===
2026-04-04 13:07:00.520  ChatViewModel  D  选择：潮州市中心医院
2026-04-04 13:07:00.579  ChatViewModel  E  ❌ 确认失败：系统繁忙，请稍后再试
```

**前端已发送的完整参数**：
```json
{
  "sessionId": "ea8377ae-e992-4972-803d-1960bbfe6f0a",
  "selectedPoiName": "潮州市中心医院",
  "lat": 23.65322884514967,
  "lng": 116.67698337185544
}
```

## 🔧 请求参数

### ✅ 正确的请求示例
```json
{
  "sessionId": "e33f98ad-69c5-43a0-86b9-8c25816b4a18",
  "selectedPoiName": "潮州市中心医院",
  "lat": 23.65392678909576,
  "lng": 116.67702550971906
}
```

### 📊 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | String | ✅ | 会话 ID（必须与之前搜索时保持一致） |
| selectedPoiName | String | ✅ | 选择的 POI 名称（必须与搜索结果中的 name 完全匹配） |
| lat | Double | ✅ | **用户当前纬度**（用于计算起点到终点的路线） |
| lng | Double | ✅ | **用户当前经度**（用于计算起点到终点的路线） |

## 💡 后端处理建议

### 1. 参数校验顺序
```java
if (sessionId == null || sessionId.isBlank()) {
    return error("会话 ID 不能为空");
}

if (selectedPoiName == null || selectedPoiName.isBlank()) {
    return error("POI 名称不能为空");
}

if (lat == null || lng == null) {
    return error("位置信息缺失，无法计算路线");  // ⭐ 明确的错误提示
}

if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return error("经纬度坐标超出有效范围");
}
```

### 2. 路线计算异常处理
```java
try {
    RouteInfo route = mapService.calculateRoute(userLat, userLng, poiLat, poiLng);
    
    if (route == null) {
        return error("无法规划路线，请检查起点和终点是否有效");
    }
    
    // 创建订单逻辑...
} catch (Exception e) {
    log.error("路线计算失败", e);
    return error("路线计算失败：" + e.getMessage());  // ⭐ 避免返回"系统繁忙"
}
```

## 📝 响应格式

### ✅ 成功响应（必须包含完整的 poi 和 route）
```json
{
  "code": 200,
  "message": "确认成功",
  "success": true,
  "data": {
    "type": "ORDER",
    "message": "已为您创建订单",
    "poi": {
      "id": "poi_123",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区某某路",
      "lat": 23.658,
      "lng": 116.625
    },
    "route": {
      "distance": 5200,
      "duration": 720,
      "price": 25.5
    }
  },
  "timestamp": 1717516800000
}
```

### ❌ 不推荐的错误响应（太模糊）
```json
{
  "code": 500,
  "message": "系统繁忙",
  "success": false
}
```

### ✅ 推荐的错误响应（具体原因）
```json
{
  "code": 400,
  "message": "位置信息缺失，无法计算路线",
  "success": false
}
```

或者：
```json
{
  "code": 400,
  "message": "会话已过期，请重新搜索",
  "success": false
}
```

## 🐛 可能的问题原因

1. **空指针异常（NPE）**：使用了为 null 的 lat、lng、poi 或 route
2. **会话校验失败**：sessionId 不存在或已过期
3. **POI 匹配失败**：数据库中找不到"潮州市中心医院"
4. **路线计算失败**：地图 API 调用失败或返回错误
5. **数据库查询超时**：订单创建过程超时
6. **异常处理不当**：捕获了异常但返回了模糊的错误信息

## 📞 调试建议

### 后端必须添加的详细日志
```java
log.info("=== 开始处理确认请求 ===");
log.info("sessionId={}", sessionId);
log.info("selectedPoiName={}", selectedPoiName);
log.info("lat={}, lng={}", lat, lng);  // ⭐ 关键：记录位置参数

try {
    // 业务逻辑...
} catch (Exception e) {
    log.error("处理确认请求失败", e);  // ⭐ 记录完整堆栈
    throw e;
}
```

### 请后端提供以下信息
- ✅ **完整的异常堆栈**（不是只有 "系统繁忙"）
- ✅ **接收到的请求参数**（确认是否有 lat/lng）
- ✅ **sessionId 校验结果**（是否过期或无效）
- ✅ **地图 API 调用日志**（是否调用成功）
- ✅ **数据库查询日志**（是否查询超时）

---

# 4️⃣ /api/agent/location - 位置更新接口

## 📋 接口信息
- **路径**: `POST /api/agent/location`
- **用途**: 实时更新用户位置（用于行程中跟踪）
- **重要性**: ⭐⭐ (辅助功能)

## 🔧 请求参数

```json
{
  "sessionId": "ea8377ae-e992-4972-803d-1960bbfe6f0a",
  "lat": 23.65322884514967,
  "lng": 116.67698337185544
}
```

## 📝 响应格式

```json
{
  "code": 200,
  "message": "位置更新成功",
  "success": true
}
```

---

# 5️⃣ /api/agent/cleanup - 会话清理接口

## 📋 接口信息
- **路径**: `POST /api/agent/cleanup`
- **用途**: 清理会话数据（退出或结束时调用）
- **重要性**: ⭐ (维护功能)

## 🔧 请求参数

```json
{
  "sessionId": "ea8377ae-e992-4972-803d-1960bbfe6f0a"
}
```

## 📝 响应格式

```json
{
  "code": 200,
  "message": "清理成功",
  "success": true
}
```

---

# 🎯 错误码规范（所有接口统一）

| HTTP 状态码 | code | message 示例 | 前端处理方式 |
|------------|------|-------------|-------------|
| 200 | 200 | 操作成功 | 显示成功提示，继续流程 |
| 400 | 400 | 参数错误/位置信息缺失 | 提示用户检查输入 |
| 400 | 400 | 会话已过期 | 引导用户重新搜索 |
| 400 | 400 | 无法计算路线 | 提示用户更换目的地 |
| 404 | 404 | 未找到相关地点 | 建议换关键词 |
| 401 | 401 | Token 无效 | 跳转登录页 |
| 500 | 500 | **具体的错误原因** | 根据具体错误提示用户 |
| 500 | 500 | ~~系统繁忙~~ | ❌ 避免这种模糊提示 |

---

# 📌 总结

## 核心诉求

1. ✅ **所有接口都需要位置参数**（lat、lng），用于计算距离和路线
2. ✅ **提供明确的错误提示**，避免"系统繁忙"等模糊描述
3. ✅ **完善异常处理和日志记录**，便于问题排查
4. ✅ **保证地图 API 服务的稳定性**和可用性
5. ✅ **响应数据必须完整**（特别是 poi 和 route 字段）

## 预期效果

- 前端能够根据明确的错误提示快速定位问题
- 减少前后端沟通成本
- 提升用户体验和下单成功率

---

**文档版本**: v1.0  
**更新时间**: 2026-04-04  
**联系人**: 前端开发团队  
**紧急程度**: 🔴 高（/api/agent/confirm 接口急需修复）
