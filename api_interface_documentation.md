# 🚗 智能出行助手 - API 接口对接说明

## 📋 问题反馈

**当前问题：** `/agent/confirm` 接口返回 "系统繁忙，请稍后重试"

**错误日志：**
```
type=ERROR, message=系统繁忙，请稍后重试
```

**前端调用代码：**
```kotlin
val result = agentRepository.confirmSelection(
    sessionId = "e7acc074-3b69-423b-8917-de0c85ed5f21",
    selectedPoiName = "韩山师范学院",
    lat = 23.653332218330387,
    lng = 116.67718986247037
)
```

---

## 🔧 智能体模块 API（核心功能）

### 1. 智能搜索目的地

**接口地址：** `POST /agent/search`

**请求参数：**
```json
{
  "sessionId": "e7acc074-3b69-423b-8917-de0c85ed5f21",
  "keyword": "韩山师范学院",
  "lat": 23.653332218330387,
  "lng": 116.67718986247037
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | ✅ | 会话 ID（UUID 格式，客户端生成并保持一致） |
| keyword | String | ✅ | 搜索关键词（地点名称） |
| lat | Double | ✅ | 用户当前位置纬度 |
| lng | Double | ✅ | 用户当前位置经度 |

**响应示例（需要确认）：**
```json
{
  "type": "SEARCH",
  "message": "为你找到以下地点",
  "places": [
    {
      "id": "poi_001",
      "name": "韩山师范学院",
      "address": "广东省潮州市湘桥区东山路",
      "lat": 23.6533,
      "lng": 116.6772,
      "distance": 500.5,
      "duration": 180,
      "price": 15.0,
      "score": 4.8,
      "type": "学校"
    },
    {
      "id": "poi_002",
      "name": "韩山师范学院东区",
      "address": "广东省潮州市湘桥区",
      "lat": 23.6545,
      "lng": 116.6785,
      "distance": 800.2,
      "duration": 240,
      "price": 18.0,
      "score": 4.6,
      "type": "学校"
    }
  ],
  "needConfirm": true
}
```

**响应示例（直接下单）：**
```json
{
  "type": "ORDER",
  "message": "已确认目的地，正在创建订单",
  "poi": {
    "id": "poi_001",
    "name": "韩山师范学院",
    "address": "广东省潮州市湘桥区东山路",
    "lat": 23.6533,
    "lng": 116.6772
  },
  "route": {
    "distance": 5000,
    "duration": 900,
    "price": 25.0
  }
}
```

**⚠️ 注意事项：**
1. **places 数组必须包含 score 字段**（用于自动选择评分最高的）
2. **needConfirm = true** 时，前端会弹出候选列表供用户选择
3. **needConfirm = false** 或返回 **type=ORDER** 时，前端直接创建订单
4. POI 对象所有字段都可能为 null，前端需做空值处理

---

### 2. 确认选择目的地 ⭐ 当前报错接口

**接口地址：** `POST /agent/confirm`

**请求参数：**
```json
{
  "sessionId": "e7acc074-3b69-423b-8917-de0c85ed5f21",
  "selectedPoiName": "韩山师范学院",
  "lat": 23.653332218330387,
  "lng": 116.67718986247037
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | ✅ | 会话 ID（与搜索时保持一致） |
| selectedPoiName | String | ✅ | 用户选择的 POI 名称（精确匹配） |
| lat | Double | ✅ | 用户当前位置纬度 |
| lng | Double | ✅ | 用户当前位置经度 |

**✅ 正确响应示例：**
```json
{
  "type": "ORDER",
  "message": "已确认目的地，正在创建订单",
  "poi": {
    "id": "poi_001",
    "name": "韩山师范学院",
    "address": "广东省潮州市湘桥区东山路",
    "lat": 23.6533,
    "lng": 116.6772
  },
  "route": {
    "distance": 5000,
    "duration": 900,
    "price": 25.0
  }
}
```

**❌ 错误响应示例（当前问题）：**
```json
{
  "type": "ERROR",
  "message": "系统繁忙，请稍后重试"
}
```

**🔍 排查建议：**
1. 检查后端是否正确接收并解析了请求参数
2. 检查 sessionId 是否有效（是否与之前的搜索请求匹配）
3. 检查 POI 名称是否能精确匹配到数据库中的记录
4. 查看后端日志，确认具体的错误堆栈信息
5. 检查数据库连接和查询是否正常

---

### 3. 图片识别

**接口地址：** `POST /agent/image`

**请求参数：**
```json
{
  "sessionId": "e7acc074-3b69-423b-8917-de0c85ed5f21",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "lat": 23.653332218330387,
  "lng": 116.67718986247037
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | ✅ | 会话 ID |
| imageBase64 | String | ✅ | Base64 编码的图片（包含前缀：`data:image/jpeg;base64,`） |
| lat | Double | ✅ | 用户当前位置纬度 |
| lng | Double | ✅ | 用户当前位置经度 |

**响应示例：**
```json
{
  "type": "image_recognition",
  "message": "识别到地点：潮州市中心医院",
  "ocrText": "潮州市中心医院 急诊部",
  "places": [
    {
      "id": "poi_003",
      "name": "潮州市中心医院",
      "address": "广东省潮州市...",
      "lat": 23.6521,
      "lng": 116.6745,
      "distance": 1200.0,
      "score": 4.7
    }
  ]
}
```

---

### 4. 更新位置

**接口地址：** `POST /agent/location`

**请求参数：**
```json
{
  "sessionId": "e7acc074-3b69-423b-8917-de0c85ed5f21",
  "lat": 23.653332218330387,
  "lng": 116.67718986247037
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success"
}
```

---

### 5. 清理会话

**接口地址：** `POST /agent/cleanup`

**请求参数：**
```json
{
  "sessionId": "e7acc074-3b69-423b-8917-de0c85ed5f21"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success"
}
```

---

## 🗡️ WebSocket 接口（实时通信）

### WebSocket 连接地址

**开发环境：** `ws://192.168.1.106:8080/ws/agent`

**鉴权方式：** 在 Token 管理中获取 Token，通过 URL 参数传递

**连接示例：**
```kotlin
val url = "ws://192.168.1.106:8080/ws/agent?token=$token"
```

### 消息格式

**客户端发送：**
```json
{
  "sessionId": "e7acc074-3b69-423b-8917-de0c85ed5f21",
  "type": "user_message",
  "content": "我要去附近的医院",
  "lat": 23.653332218330387,
  "lng": 116.67718986247037,
  "page": 1,
  "pageSize": 20,
  "sortByDistance": true
}
```

**服务端响应：**
```json
{
  "type": "search",
  "message": "为你找到以下地点",
  "data": {
    "$ref": "$.places"
  }
}
```

**⚠️ JSON 引用格式处理：**
- 当后端使用 FastJSON 时，可能会返回 `$ref` 引用格式
- 前端已做兼容处理，会从原始 JSON 中提取 `places` 数组
- 建议后端尽量直接返回完整数据，避免使用引用

**心跳机制：**
- 客户端每 30 秒发送一次 ping
- 服务端应回复 pong

```json
// 客户端发送
{
  "type": "ping",
  "sessionId": "e7acc074-3b69-423b-8917-de0c85ed5f21",
  "timestamp": 1775280220155
}

// 服务端响应
{
  "type": "pong"
}
```

---

## 📊 数据结构定义

### PoiResponse（POI 信息）
```kotlin
@Serializable
data class PoiResponse(
    val id: String?,           // POI ID
    val name: String?,         // 名称
    val address: String?,      // 地址
    val lat: Double,           // 纬度
    val lng: Double,           // 经度
    val distance: Double?,     // 距离（米）
    val duration: Int?,        // 预计耗时（秒）
    val price: Double?,        // 预估价格（元）
    val score: Double?,        // ⭐ 评分（0-5 分）
    val type: String?          // 类型
)
```

### AgentSearchResponse（智能体搜索响应）
```kotlin
@Serializable
data class AgentSearchResponse(
    val type: String,              // SEARCH / ORDER / CHAT / ERROR
    val message: String,           // 提示消息
    val places: List<PoiResponse>?, // POI 列表
    val candidates: List<PoiResponse>?, // 候选列表（冗余字段）
    val needConfirm: Boolean,      // 是否需要用户确认
    val poi: PoiResponse?,         // 直接下单时的 POI
    val route: RouteInfo?          // 路线信息
)
```

### RouteInfo（路线信息）
```kotlin
@Serializable
data class RouteInfo(
    val distance: Int,     // 距离（米）
    val duration: Int,     // 耗时（秒）
    val price: Double      // 价格（元）
)
```

---

## 🎯 前端业务逻辑

### 场景 1：用户说"我要去 XXX"
```
1. 前端发送 WebSocket 消息
2. 后端返回 SEARCH 类型 + places 数组
3. 如果 needConfirm=true，前端弹出候选列表
4. 用户选择后，前端调用 HTTP POST /agent/confirm
5. 后端返回 ORDER 类型，前端创建订单
```

### 场景 2：用户发送具体地点名称（如"韩山师范学院东区"）
```
1. 前端检测到不包含"我要去"等前缀
2. 前端直接调用 HTTP POST /agent/search
3. 后端返回 SEARCH 类型 + places 数组
4. 前端按 score 降序排序，自动选择评分最高的
5. 前端调用 HTTP POST /agent/confirm
6. 后端返回 ORDER 类型，前端创建订单
```

### 场景 3：用户发送图片
```
1. 前端压缩图片并转为 Base64
2. 前端调用 HTTP POST /agent/image
3. 后端返回 OCR 识别结果和 places 数组
4. 前端弹出候选列表供用户选择
5. 后续流程同场景 1
```

---

## ⚠️ 重要注意事项

### 1. SessionId 管理
- SessionId 由前端生成（UUID 格式）
- 同一会话过程中必须保持一致
- 会话结束后可调用 `/agent/cleanup` 清理

### 2. 位置信息
- 所有接口都需要传递 lat 和 lng
- 位置信息由高德地图 SDK 提供
- 如果位置获取失败，前端会显示友好提示

### 3. 错误处理
- 网络错误：前端显示"网络连接失败，请检查网络"
- 接口限流：前端显示"系统繁忙，请稍后再试"
- 其他错误：前端显示后端返回的 message

### 4. 数据兼容性
- 所有字段都可能为 null，前端已做空值处理
- 后端返回的数值类型建议使用默认值（如 distance=0）
- 字符串字段为空时建议返回空字符串而非 null

### 5. 性能优化
- 前端已实现防抖（debounce）机制
- 图片上传前会压缩到 500KB 以内
- 位置信息变化小于 50 米时不重复请求

---

## 🐛 当前待解决问题

### ❌ POST /agent/confirm 接口报错

**现象：**
- 请求参数正确
- sessionId 有效
- POI 名称可以精确匹配
- 但返回 "系统繁忙，请稍后重试"

**需要的支持：**
1. 请后端开发同学检查该接口的日志
2. 确认是否存在数据库查询失败
3. 确认 sessionId 验证逻辑是否正确
4. 如果是并发问题，请增加重试机制

**临时方案：**
- 如果 HTTP 接口持续失败，可以考虑改回 WebSocket 方式
- 或者前端增加降级逻辑（HTTP 失败后尝试 WebSocket）

---

## 📞 联系方式

如有疑问，请联系前端开发团队。

**更新日期：** 2026-04-04
**版本：** v1.0
