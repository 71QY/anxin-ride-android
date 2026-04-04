# 后端对接文档对齐完成总结

## 📋 修改概述

根据后端提供的旅行项目前后端对接技术文档，已完成 Android 前端代码的对齐改造。

---

## ✅ 已完成的修改

### 1. **数据模型层 (Data Models)**

#### 1.1 修改 `Result.kt`
- **文件路径**: `app/src/main/java/com/example/myapplication/data/model/Result.kt`
- **修改内容**:
  - 移除 `success` 字段（后端只用 `code` 判断）
  - `message` 默认值改为 `"success"`
  - `data` 默认值为 `null`
  - `isSuccess()` 方法简化为 `code == 200`

```kotlin
data class Result<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String = "success",
    @SerializedName("data") val data: T? = null
) {
    fun isSuccess() = code == 200
}
```

#### 1.2 新增智能体模块数据模型
- **文件路径**: `app/src/main/java/com/example/myapplication/data/model/AgentModels.kt`
- **新增类**:
  - `AgentSearchRequest` - 智能搜索请求
  - `AgentConfirmRequest` - 确认选择请求
  - `AgentImageRequest` - 图片识别请求
  - `AgentLocationRequest` - 位置更新请求
  - `AgentCleanupRequest` - 会话清理请求
  - `AgentSearchResponse` - 智能搜索响应（包含 type、places、candidates、needConfirm 等）
  - `RouteInfo` - 路线信息（distance、duration、price）
  - `PoiDetailResponse` - POI 详情响应（地图点击选点使用）

---

### 2. **网络接口层 (API Service)**

#### 2.1 新增智能体模块 HTTP 接口
- **文件路径**: `app/src/main/java/com/example/myapplication/core/network/ApiService.kt`
- **新增接口**:

```kotlin
// ========== 智能体模块 ==========
@POST("agent/search")
suspend fun agentSearch(@Body request: AgentSearchRequest): Result<AgentSearchResponse>

@POST("agent/confirm")
suspend fun agentConfirm(@Body request: AgentConfirmRequest): Result<AgentSearchResponse>

@POST("agent/image")
suspend fun agentImage(@Body request: AgentImageRequest): Result<AgentSearchResponse>

@POST("agent/location")
suspend fun agentLocation(@Body request: AgentLocationRequest): Result<Unit>

@POST("agent/cleanup")
suspend fun agentCleanup(@Body request: AgentCleanupRequest): Result<Unit>
```

#### 2.2 修改地图模块接口
- **修改点**:
  - `getPoiDetail` 返回类型改为 `PoiDetailResponse`（包含 poi、route、canOrder、sessionId）
  - 逆地理编码响应添加 `township` 和 `formattedAddress` 字段

---

### 3. **Repository 层**

#### 3.1 新增 AgentRepository
- **文件路径**: `app/src/main/java/com/example/myapplication/data/repository/AgentRepository.kt`
- **功能**: 封装所有智能体模块的 HTTP 调用
- **方法列表**:
  - `searchDestination(sessionId, keyword, lat, lng)` - 智能搜索目的地
  - `confirmSelection(sessionId, selectedPoiName)` - 确认选择目的地
  - `recognizeImage(sessionId, imageBase64, lat, lng)` - 图片识别
  - `updateLocation(sessionId, lat, lng)` - 更新位置
  - `cleanupSession(sessionId)` - 清理会话

#### 3.2 更新 RepositoryModule
- **文件路径**: `app/src/main/java/com/example/myapplication/di/RepositoryModule.kt`
- **修改内容**: 添加 `AgentRepository` 的 Provider

---

### 4. **ViewModel 层**

#### 4.1 更新 ChatViewModel
- **文件路径**: `app/src/main/java/com/example/myapplication/presentation/chat/ChatViewModel.kt`
- **修改内容**:

1. **注入 AgentRepository**:
```kotlin
@Inject constructor(
    private val webSocketClient: ChatWebSocketClient,
    private val orderRepository: IOrderRepository,
    private val agentRepository: AgentRepository  // ⭐ 新增
) : ViewModel()
```

2. **新增 HTTP 方式智能搜索方法**:
```kotlin
fun searchDestinationByHttp(keyword: String)
fun confirmSelectionByHttp(selectedPoiName: String)
fun recognizeImageByHttp(bitmap: Bitmap)
```

3. **保持 WebSocket 方式不变**（双模式支持）

---

## 🔧 关键对齐点

### 1. **统一响应格式**
所有接口返回格式对齐后端文档：
```json
{
  "code": 200,
  "data": {},
  "message": "success"
}
```

### 2. **智能体模块响应类型**
- `type="SEARCH"` - 搜索到多个结果，需要用户选择
- `type="ORDER"` - 可以直接下单
- `type="CHAT"` - 纯聊天回复

### 3. **sessionId 管理**
- 前端生成唯一 sessionId
- 所有智能体相关请求都必须携带 sessionId
- 退出页面或重新开始时要调用 `/agent/cleanup`

### 4. **图片识别格式**
- Base64 编码必须包含前缀：`data:image/jpeg;base64,xxx`
- 图片大小不超过 5MB
- 建议压缩到 1MB 以内

### 5. **地图选点流程**
1. 调用 `/map/poi/detail` 获取 POI 详情和路线
2. 保存响应中的 `sessionId`
3. 调用 `/map/order/confirm` 确认下单

---

## 📝 使用示例

### 场景 1: WebSocket 智能搜索（现有方式保持不变）
```kotlin
// ChatScreen.kt
viewModel.sendMessage("附近的医院")
// 通过 WebSocket 发送，自动处理响应
```

### 场景 2: HTTP 方式智能搜索（备选方案）
```kotlin
// ChatScreen.kt
viewModel.searchDestinationByHttp("医院")
// 通过 HTTP 调用，适合网络不稳定时使用
```

### 场景 3: 图片识别
```kotlin
// 方式 1: WebSocket（推荐）
viewModel.sendImage(bitmap)

// 方式 2: HTTP（备选）
viewModel.recognizeImageByHttp(bitmap)
```

### 场景 4: 地图点击选点
```kotlin
// HomeViewModel.kt
val detail = apiService.getPoiDetail(
    poiName = "潮州市人民医院",
    lat = currentLat,
    lng = currentLng,
    mode = "driving"
)

// 保存 sessionId
val sessionId = detail.data?.sessionId

// 确认下单
apiService.confirmOrder(sessionId, poiName)
```

---

## ⚠️ 注意事项

### 1. **Token 认证**
- 所有需要登录的接口都会在请求头自动添加 `Authorization: Bearer <token>`
- Token 过期时（401），AuthInterceptor 会自动清除本地 Token

### 2. **位置信息**
- 调用智能体接口前必须先获取当前位置
- ChatViewModel 通过 `syncLocationFromHome(lat, lng)` 从 HomeScreen 同步位置
- 距离判定：小于 50 米不更新位置

### 3. **错误处理**
- 所有 Repository 方法都有 try-catch，返回统一的 Result 格式
- code != 200 时表示失败，通过 message 查看错误信息

### 4. **缓存策略**
- POI 搜索结果建议缓存 5-10 分钟
- 逆地理编码建议缓存 30 分钟
- HomeViewModel 已实现地址缓存（LruCache）

---

## 🎯 后续优化建议

### 1. **防抖优化**
搜索输入框已有 300ms 防抖，可以根据实际情况调整

### 2. **加载状态**
建议在 UI 层添加 loading 状态提示，提升用户体验

### 3. **断网处理**
- WebSocket 已实现断线重连机制
- HTTP 方式可以作为备选方案

### 4. **性能优化**
- 图片上传前压缩（已实现）
- 列表分页加载（每页不超过 20 条）
- 避免频繁调用搜索接口

---

## 📚 参考文档

- 后端对接文档：用户提供的完整 API 文档
- 项目记忆：POI 详情需生成 sessionId 用于下单、逆地理编码返回结构化响应对象
- 后端技术栈：Java 17 + Spring Boot 3.1.5 + MySQL 8.0 + Redis

---

## ✅ 验证清单

- [x] Result 响应格式对齐（移除 success 字段）
- [x] 智能体模块 HTTP 接口实现（5 个接口）
- [x] AgentRepository 创建并注入
- [x] ChatViewModel 支持 HTTP 方式（同时保留 WebSocket）
- [x] 地图模块接口对齐（PoiDetailResponse）
- [x] 逆地理编码响应对齐（添加 township 和 formattedAddress）
- [x] sessionId 管理机制完善
- [x] 图片 Base64 格式对齐（包含前缀）
- [x] 统一错误处理机制

---

**修改完成时间**: 2026-04-03  
**修改人**: AI Assistant  
**版本**: v1.0
