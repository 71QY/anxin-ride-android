# 后端 API 对接改造 - 编译检查清单

## ✅ 已完成的修改

### 1. 数据模型层
- [x] `Result.kt` - 移除 success 字段，对齐后端响应格式
- [x] `AgentModels.kt` (新增) - 智能体模块数据模型

### 2. 网络接口层  
- [x] `ApiService.kt` - 新增 5 个智能体 HTTP 接口
- [x] `ApiService.kt` - 修改地图模块返回类型
- [x] `RetrofitClient.kt` - 无需修改（已支持）

### 3. Repository 层
- [x] `AgentRepository.kt` (新增) - 智能体功能封装
- [x] `RepositoryModule.kt` - 添加 AgentRepository Provider

### 4. ViewModel 层
- [x] `ChatViewModel.kt` - 注入 AgentRepository
- [x] `ChatViewModel.kt` - 新增 HTTP 方式搜索方法
- [x] `ChatViewModel.kt` - 新增 HTTP 方式确认方法
- [x] `ChatViewModel.kt` - 新增 HTTP 方式图片识别

## 🔍 需要验证的功能点

### 功能验证清单

#### 1. 认证模块
- [ ] 发送验证码 `/api/auth/code`
- [ ] 验证码登录 `/api/auth/login`
- [ ] 密码登录 `/api/auth/login`
- [ ] 注册 `/api/auth/register`
- [ ] 忘记密码 `/api/auth/forgot-password`

#### 2. 智能体模块 (HTTP 方式)
- [ ] 智能搜索目的地 `/api/agent/search`
- [ ] 确认选择 `/api/agent/confirm`
- [ ] 图片识别 `/api/agent/image`
- [ ] 更新位置 `/api/agent/location`
- [ ] 清理会话 `/api/agent/cleanup`

#### 3. 地图模块
- [ ] 搜索目的地 `/api/map/search-destination`
- [ ] 逆地理编码 `/api/map/geocode/reverse`
- [ ] POI 周边搜索 `/api/map/poi/nearby`
- [ ] POI 详情与路线规划 `/api/map/poi/detail`
- [ ] 路线规划 `/api/map/route`
- [ ] 地图点击确认下单 `/api/map/order/confirm`

#### 4. 订单模块
- [ ] 创建订单 `/api/order/create`
- [ ] 查询订单详情 `/api/order/{id}`
- [ ] 取消订单 `/api/order/{id}/cancel`
- [ ] 确认订单 `/api/order/{id}/confirm`
- [ ] 订单列表 `/api/order/list`

#### 5. 用户模块
- [ ] 获取个人信息 `/api/user/profile`
- [ ] 更新个人信息 `/api/user/profile`
- [ ] 上传头像 `/api/user/avatar`
- [ ] 获取头像 `/api/user/avatar/{filename}`
- [ ] 添加紧急联系人 `/api/user/emergency`
- [ ] 获取紧急联系人列表 `/api/user/emergency`
- [ ] 删除紧急联系人 `/api/user/emergency/{id}`
- [ ] 实名认证 `/api/user/realname`
- [ ] 修改密码 `/api/user/change-password`

#### 6. WebSocket 实时通信
- [ ] 连接 WebSocket `ws://localhost:8080/ws/agent`
- [ ] 发送消息（带 type 字段）
- [ ] 接收消息（解析 type、data 等字段）
- [ ] 心跳检测（30 秒 ping）
- [ ] 断线重连机制

## 📝 关键改动说明

### 1. Result 响应格式
**旧格式:**
```kotlin
data class Result<T>(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?
) {
    fun isSuccess() = success == true || code == 200 || code == 0
}
```

**新格式:**
```kotlin
data class Result<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String = "success",
    @SerializedName("data") val data: T? = null
) {
    fun isSuccess() = code == 200
}
```

### 2. 智能体模块响应类型
```kotlin
data class AgentSearchResponse(
    val type: String,          // SEARCH / ORDER / CHAT
    val message: String,
    val places: List<PoiResponse>? = null,
    val candidates: List<PoiResponse>? = null,
    val needConfirm: Boolean = false,
    val poi: PoiResponse? = null,
    val route: RouteInfo? = null
)
```

### 3. sessionId 管理
- 前端生成 UUID 作为 sessionId
- 所有智能体请求必须携带 sessionId
- 退出页面时调用 `/agent/cleanup` 清理会话

### 4. 图片 Base64 格式
**必须包含前缀:**
```
data:image/jpeg;base64,/9j/4AAQSkZJRg...
```

## 🚀 使用示例

### 示例 1: HTTP 方式智能搜索
```kotlin
// ChatScreen.kt
viewModel.searchDestinationByHttp("医院")
```

### 示例 2: WebSocket 方式智能搜索（保持不变）
```kotlin
// ChatScreen.kt
viewModel.sendMessage("附近的医院")
```

### 示例 3: 地图点击选点
```kotlin
// HomeViewModel.kt
val detail = apiService.getPoiDetail(
    poiName = "潮州市人民医院",
    lat = currentLat,
    lng = currentLng,
    mode = "driving"
)

// 保存 sessionId 用于后续下单
val sessionId = detail.data?.sessionId
```

## ⚠️ 注意事项

1. **Token 认证**: 所有需要登录的接口都会自动添加 Authorization 头
2. **401 处理**: AuthInterceptor 会自动清除过期的 Token
3. **位置同步**: ChatViewModel 通过 `syncLocationFromHome(lat, lng)` 获取位置
4. **距离判定**: 小于 50 米的位置变化不会重复同步
5. **缓存策略**: 
   - POI 搜索结果缓存 5-10 分钟
   - 逆地理编码缓存 30 分钟

## 📦 依赖项检查

- [x] Retrofit2 (网络请求)
- [x] Gson (JSON 解析)
- [x] Hilt (依赖注入)
- [x] Kotlinx Serialization (WebSocket 消息)
- [x] OkHttp3 (拦截器、日志)

## 🎯 下一步工作

1. **编译项目**: 检查是否有编译错误
2. **单元测试**: 测试各个 Repository 方法
3. **集成测试**: 连接真实后端进行测试
4. **UI 调试**: 确保界面展示正常
5. **性能优化**: 根据实际使用情况优化

---

**文档更新时间**: 2026-04-03  
**版本**: v1.0  
**状态**: 代码修改完成，待编译验证
