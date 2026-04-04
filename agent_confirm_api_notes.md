# 智能体模块 API 接口注意事项（完整版）

## 📋 接口列表

1. **目的地搜索** - `POST /api/agent/search`
2. **图片识别** - `POST /api/agent/image`
3. **确认选择** - `POST /api/agent/confirm` ⭐⭐⭐⭐⭐
4. **位置更新** - `POST /api/agent/location`
5. **会话清理** - `POST /api/agent/cleanup`
6. **WebSocket 消息** - `ws://{host}/api/agent/ws`

---

# 1️⃣ /api/agent/search - 目的地搜索接口

# 1️⃣ /api/agent/search - 目的地搜索接口

## 📋 接口信息
- **路径**: `POST /api/agent/search`
- **用途**: 根据关键词搜索目的地，返回 POI 列表
- **重要性**: ⭐⭐⭐⭐ (核心搜索功能)

---

## 🔧 **请求参数**

### ✅ **正确的请求示例**
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

### 📊 **参数说明**

| 参数名 | 类型 | 必填 | 说明 | 默认值 |
|--------|------|------|------|--------|
| sessionId | String | ✅ | 会话 ID（每次对话唯一标识） | - |
| keyword | String | ✅ | 搜索关键词（如“医院”、“酒店”） | - |
| lat | Double | ✅ | **用户当前纬度**（用于计算距离和排序） | - |
| lng | Double | ✅ | **用户当前经度**（用于计算距离和排序） | - |
| page | Integer | ❌ | 页码（从 1 开始） | 1 |
| pageSize | Integer | ❌ | 每页数量 | 20 |
| sortByDistance | Boolean | ❌ | 是否按距离排序 | true |

---

## 📝 **响应格式**

### ✅ **成功响应**
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

### ⚠️ **注意事项**
1. **places 数组不能为空**：即使只找到一个地点，也要返回数组
2. **needConfirm 字段**：
   - `true`：需要用户确认选择（弹出候选列表）
   - `false`：可以直接下单（只有一个结果）
3. **distance 单位**：米（m）
4. **duration 单位**：秒（s）
5. **price 单位**：元（¥）

---

## ❌ **错误响应示例**

### 位置信息缺失
```json
{
  "code": 400,
  "message": "位置信息缺失，无法计算距离",
  "success": false
}
```

### 未找到相关地点
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

---

## ⚠️ **关键问题说明**

### **当前问题现象（2026-04-04 最新日志）**
```
2026-04-04 12:54:05.301  ChatViewModel  D  data={"$ref":"$.places"}
2026-04-04 12:54:05.301  ChatViewModel  D  🔍 检测到 JSON 引用格式，尝试从原始 JSON 提取 places
2026-04-04 12:54:05.308  ChatViewModel  D  ✅ 从原始 JSON 的 places 字段提取到数据
2026-04-04 12:54:05.315  ChatViewModel  D  🔔 parseServerMessage: 需要确认，弹出候选列表
2026-04-04 12:54:09.343  ChatViewModel  D  === 用户选择候选地点 ===
2026-04-04 12:54:09.343  ChatViewModel  D  选择：煌仔一品鸡煲店
2026-04-04 12:54:09.443  ChatViewModel  E  ❌ 确认失败：系统繁忙，请稍后再试
```

**问题分析**：
1. ✅ 前端搜索功能正常，能正确解析 FastJSON 引用格式
2. ✅ 候选列表显示正常
3. ✅ 用户选择功能正常
4. ❌ **确认接口调用失败** - 后端返回 "系统繁忙"

### **根本原因分析**
前端请求参数中**已包含完整的位置信息**（lat 和 lng），但后端可能：
1. ❌ 没有正确接收或解析位置参数
2. ❌ 使用位置参数计算路线时发生异常
3. ❌ 异常处理不当，返回了模糊的"系统繁忙"错误
4. ❌ sessionId 校验失败或会话过期
5. ❌ POI 名称匹配失败

---

## 🔧 **请求参数规范**

### ✅ **正确的请求示例**
```json
{
  "sessionId": "e33f98ad-69c5-43a0-86b9-8c25816b4a18",
  "selectedPoiName": "潮州市中心医院",
  "lat": 23.65392678909576,
  "lng": 116.67702550971906
}
```

### ❌ **错误的请求示例（缺少位置）**
```json
{
  "sessionId": "e33f98ad-69c5-43a0-86b9-8c25816b4a18",
  "selectedPoiName": "潮州市中心医院"
  // ❌ 缺少 lat 和 lng，导致无法计算路线
}
```

---

## 📊 **必需参数说明**

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| sessionId | String | ✅ | 会话 ID（必须与之前搜索时保持一致） | "e33f98ad-69c5-43a0-86b9-8c25816b4a18" |
| selectedPoiName | String | ✅ | 选择的 POI 名称（必须与搜索结果中的 name 完全匹配） | "潮州市中心医院" |
| lat | Double | ✅ | **用户当前纬度**（用于计算起点到终点的路线） | 23.65392678909576 |
| lng | Double | ✅ | **用户当前经度**（用于计算起点到终点的路线） | 116.67702550971906 |

---

## 💡 **后端处理建议**

### **1. 参数校验顺序**
```java
// 推荐校验顺序
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

### **2. 路线计算异常处理**
```java
try {
    // 调用地图 API 计算路线
    RouteInfo route = mapService.calculateRoute(userLat, userLng, poiLat, poiLng);
    
    if (route == null) {
        return error("无法规划路线，请检查起点和终点是否有效");  // ⭐ 具体错误原因
    }
    
    // 创建订单逻辑...
} catch (Exception e) {
    log.error("路线计算失败", e);
    return error("路线计算失败：" + e.getMessage());  // ⭐ 避免返回"系统繁忙"
}
```

### **3. 会话校验**
```java
// 验证 sessionId 是否有效
Session session = sessionRepository.findById(sessionId);
if (session == null || session.isExpired()) {
    return error("会话已过期，请重新搜索");  // ⭐ 明确提示
}
```

---

## 📝 **响应格式规范**

### ✅ **成功响应（期望返回）**
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
      "distance": 5200,        // 单位：米
      "duration": 720,         // 单位：秒
      "price": 25.5            // 单位：元
    }
  },
  "timestamp": 1717516800000
}
```

### ❌ **不推荐的错误响应（太模糊）**
```json
{
  "code": 500,
  "message": "系统繁忙",
  "success": false
}
```

### ✅ **推荐的错误响应（具体原因）**
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

---

## 🐛 **常见问题排查清单**

### **Q1: 前端一直显示"系统繁忙"**
**排查步骤**：
1. ✅ 检查后端日志，查看具体异常堆栈
2. ✅ 确认接收到了完整的 4 个参数（特别是 lat 和 lng）
3. ✅ 验证 sessionId 是否在数据库中存在且未过期
4. ✅ 检查地图 API 密钥是否正确配置
5. ✅ 验证地图 API 服务是否正常

**可能原因**：
- 空指针异常（NPE）：使用了为 null 的 lat 或 lng
- 路线计算 API 调用失败
- 数据库查询超时

---

### **Q2: 响应中 route 字段为空**
**可能原因**：
- 起点（用户位置）或终点（POI 位置）坐标无效
- 地图 API 返回错误（如配额超限、密钥错误）
- 两点之间无法规划路线（如跨海、超出服务范围）

**解决方案**：
- 检查 lat/lng 是否在合理范围内
- 检查地图 API 账户余额和配额
- 添加降级策略（如返回预估距离）

---

### **Q3: POI 名称匹配失败**
**可能原因**：
- selectedPoiName 与数据库中存储的名称不完全一致
- 存在特殊字符、空格、繁简体差异

**解决方案**：
- 使用 POI ID 而非名称进行匹配（更可靠）
- 或者进行模糊匹配（忽略空格、大小写）

---

## 🎯 **前端期望的错误码映射**

| HTTP 状态码 | code | message 示例 | 前端处理方式 |
|------------|------|-------------|-------------|
| 200 | 200 | 确认成功 | 显示成功提示，跳转订单详情 |
| 400 | 400 | 会话已过期 | 引导用户重新搜索 |
| 400 | 400 | 位置信息缺失 | 检查前端是否传递了 lat/lng |
| 400 | 400 | 无法计算路线 | 提示用户更换目的地 |
| 400 | 400 | 未找到匹配的地点 | 重新搜索 |
| 401 | 401 | Token 无效 | 跳转登录页 |
| 500 | 500 | **具体的错误原因** | 根据具体错误提示用户 |
| 500 | 500 | ~~系统繁忙~~ | ❌ 避免这种模糊提示 |

---

## 📞 **调试建议**

### **1. 添加详细日志（后端）**
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

### **2. 使用 Postman 测试**
```bash
# 测试用例 1：正常请求（模拟前端）
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "e33f98ad-69c5-43a0-86b9-8c25816b4a18",
    "selectedPoiName": "煌仔一品鸡煲店",
    "lat": 23.65392678909576,
    "lng": 116.67702550971906
  }'

# 预期响应：
{
  "code": 200,
  "message": "确认成功",
  "success": true,
  "data": {
    "type": "ORDER",
    "message": "已为您创建订单",
    "poi": {
      "name": "煌仔一品鸡煲店",
      "lat": 23.xxx,
      "lng": 116.xxx
    },
    "route": {
      "distance": 5200,
      "duration": 720,
      "price": 25.5
    }
  }
}

# 测试用例 2：缺少位置参数（应该返回明确错误）
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session-123",
    "selectedPoiName": "测试地点"
  }'

# 预期响应（明确的错误提示）：
{
  "code": 400,
  "message": "位置信息缺失，无法计算路线",
  "success": false
}
```

### **3. 数据库检查**
```sql
-- 检查会话是否存在
SELECT * FROM sessions WHERE session_id = 'e33f98ad-69c5-43a0-86b9-8c25816b4a18';

-- 检查 POI 是否存在
SELECT * FROM pois WHERE name = '煌仔一品鸡煲店';

-- 检查地图 API 配置
SELECT * FROM map_api_config WHERE status = 'active';
```

### **4. 查看后端错误日志（关键）**
请后端开发人员提供以下信息：
- ✅ **完整的异常堆栈**（不是只有 "系统繁忙"）
- ✅ **接收到的请求参数**（确认是否有 lat/lng）
- ✅ **sessionId 校验结果**（是否过期或无效）
- ✅ **地图 API 调用日志**（是否调用成功）
- ✅ **数据库查询日志**（是否查询超时）

### **5. 前端调试代码（临时添加）**
在 `ChatViewModel.kt` 的 `selectCandidate()` 方法中添加更详细的日志：

```kotlin
fun selectCandidate(poi: PoiData) {
    viewModelScope.launch {
        Log.d("ChatViewModel", "=== 用户选择候选地点 ===")
        Log.d("ChatViewModel", "选择：${poi.name}")
        Log.d("ChatViewModel", "POI 详情：lat=${poi.lat}, lng=${poi.lng}, address=${poi.address}")
        Log.d("ChatViewModel", "当前用户位置：currentLat=$currentLat, currentLng=$currentLng")
        Log.d("ChatViewModel", "sessionId=${sessionId.value}")
        
        // ... 其他代码
        
        val result = agentRepository.confirmSelection(
            sessionId = sessionId.value,
            selectedPoiName = poi.name,
            lat = lat,
            lng = lng
        )
        
        Log.d("ChatViewModel", "确认请求结果：isSuccess=${result.isSuccess()}")
        Log.d("ChatViewModel", "错误信息：${result.message}")
        if (result.data != null) {
            Log.d("ChatViewModel", "返回数据：type=${result.data?.type}, message=${result.data?.message}")
        }
        
        // ... 其他代码
    }
}
```

---

## 💬 **与前端协作建议**

1. **建立快速沟通渠道**
   - 拉前后端开发人员和产品经理到同一群组
   - 实时同步问题和进展

2. **共享调试工具**
   - 使用相同的 Postman 测试集合
   - 共享测试账号和 sessionId

3. **统一错误码文档**
   - 维护在线的错误码对照表
   - 及时更新新增的错误场景

4. **灰度发布**
   - 先在测试环境充分测试
   - 小流量验证后再全量发布

---

## 📌 **总结**

**核心诉求**：
1. ✅ 确保接口能正确接收和处理位置参数（lat、lng）
2. ✅ 提供明确的错误提示，避免"系统繁忙"等模糊描述
3. ✅ 完善异常处理和日志记录，便于问题排查
4. ✅ 保证路线计算服务的稳定性和可用性

**预期效果**：
- 前端能够根据明确的错误提示快速定位问题
- 减少前后端沟通成本
- 提升用户体验和下单成功率

---

**文档版本**: v1.0  
**更新时间**: 2026-04-04  
**联系人**: 前端开发团队
