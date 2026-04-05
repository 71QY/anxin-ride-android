# 安心出行 Android 前端 - 源代码清单

**项目名称**: 安心出行 (AnXin Travel)  
**最后更新**: 2026-04-05  
**代码版本**: v2.0  
**总文件数**: 约60个Kotlin源文件

---

## 📂 核心层 (core/)

### 1. 数据持久化 (datastore/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| TokenManager.kt | `core/datastore/` | JWT Token管理,使用DataStore存储和读取认证令牌 |

### 2. 网络层 (network/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| ApiService.kt | `core/network/` | Retrofit API接口定义,包含所有后端接口(登录、用户、订单、智能体等) |
| AuthInterceptor.kt | `core/network/` | ⭐新增:统一认证拦截器,自动添加Bearer Token到请求头 |
| RetrofitClient.kt | `core/network/` | ⭐新增:Retrofit客户端封装,提供单例网络客户端实例 |
| Result.kt | `core/network/` | 统一响应封装类,包含code/message/data/success字段 |

### 3. 工具类 (utils/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| SensitiveWordFilter.kt | `core/utils/` | ⭐新增:敏感词过滤器,替换敏感内容为*** |
| SpeechRecognizerHelper.kt | `core/utils/` | ⭐新增:讯飞语音识别助手,封装语音转文字功能 |
| BaiduSpeechRecognizerHelper.kt | `core/utils/` | ⭐新增:百度语音识别助手,备用语音方案 |

### 4. WebSocket (websocket/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| ChatWebSocketClient.kt | `core/websocket/` | ⭐重构:专用聊天WebSocket客户端,支持自动重连、心跳保活、消息回调 |

---

## 📂 数据层 (data/)

### 1. 数据模型 (model/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| UserProfile.kt | `data/model/` | 用户资料数据类(id/phone/nickname/avatar/realName/idCard/verified) |
| Order.kt | `data/model/` | 订单数据类(orderId/passengerName/poiName/status等) |
| PoiData.kt | `data/model/` | POI地点数据类(id/name/address/lat/lng/distance/score) |
| ChatMessage.kt | `data/model/` | 聊天消息数据类(content/isUser/timestamp/suggestions/imageBase64) |
| LoginRequest.kt | `data/model/` | 登录请求数据类(phone/code) |
| LoginResponse.kt | `data/model/` | 登录响应数据类(token/userInfo) |
| AvatarResponse.kt | `data/model/` | ⭐新增:头像上传响应数据类(avatarUrl) |
| ChangeNicknameRequest.kt | `data/model/` | ⭐新增:修改昵称请求数据类(nickname) |
| ChangePasswordRequest.kt | `data/model/` | ⭐新增:修改密码请求数据类(code/newPassword) |
| EmergencyContact.kt | `data/model/` | ⭐新增:紧急联系人数据类(id/name/phone) |
| ChatSession.kt | `data/model/` | ⭐新增:聊天会话数据类(sessionId/title/lastMessage/createTime) |
| CreateOrderRequest.kt | `data/model/` | 创建订单请求数据类(poiName/poiLat/poiLng/passengerCount/remark) |
| AgentModels.kt | `data/model/` | 智能体相关数据类(AgentSearchRequest/AgentImageRequest/AgentConfirmRequest/AgentResponse) |
| PageResponse.kt | `data/model/` | ⭐新增:分页响应数据类(total/pageSize/current/records) |
| PoiDetail.kt | `data/model/` | POI详情数据类 |

### 2. 数据仓库 (repository/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| AgentRepository.kt | `data/repository/` | 智能体数据仓库,封装图片识别、地点搜索等API调用 |
| OrderRepository.kt | `data/repository/` | 订单数据仓库,封装订单列表、详情等API调用 |

---

## 📂 依赖注入 (di/)

| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| NetworkModule.kt | `di/` | Hilt网络模块,提供OkHttpClient/Retrofit/ApiService单例 |
| RepositoryModule.kt | `di/` | Hilt仓库模块,提供Repository实例 |

---

## 📂 领域层 (domain/) ⭐新增

### 仓储接口 (repository/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| IOrderRepository.kt | `domain/repository/` | 订单仓储接口,定义订单相关操作契约 |

---

## 📂 调试工具 (debug/) ⭐新增

| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| HiltDebugChecker.kt | `debug/` | Hilt依赖注入检查工具,验证DI配置正确性 |
| WebSocketDebugMonitor.kt | `debug/` | WebSocket连接监控工具,记录状态变化和消息日志 |

---

## 📂 表现层 (presentation/)

### 1. 登录模块 (login/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| LoginScreen.kt | `presentation/login/` | 登录界面Compose UI,支持手机号+验证码登录、忘记密码跳转 |
| LoginViewModel.kt | `presentation/login/` | 登录ViewModel,处理登录逻辑、验证码发送、Token保存 |

### 2. 主页模块 (home/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| HomeScreen.kt | `presentation/home/` | 主页界面,集成高德地图、实时定位、POI搜索、悬浮窗入口 |
| HomeViewModel.kt | `presentation/home/` | 主页ViewModel,管理位置信息、地图状态、POI数据 |

### 3. 智能体聊天模块 (chat/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| ChatScreen.kt | `presentation/chat/` | 聊天界面,支持文本/图片/语音输入,显示AI回复和候选地点 |
| ChatViewModel.kt | `presentation/chat/` | 聊天ViewModel,处理WebSocket通信、图片识别、订单创建、位置同步 |
| ChatListScreen.kt | `presentation/chat/` | ⭐新增:会话列表界面,展示历史聊天会话 |
| ChatListViewModel.kt | `presentation/chat/` | ⭐新增:会话列表ViewModel,管理会话数据 |

### 4. 订单模块 (order/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| OrderListScreen.kt | `presentation/order/` | 订单列表界面,分页展示用户订单,支持下拉刷新 |
| OrderViewModel.kt | `presentation/order/` | 订单列表ViewModel,加载和管理订单列表数据 |
| OrderDetailScreen.kt | `presentation/order/` | 订单详情界面,展示完整订单信息(乘客/目的地/状态/时间) |
| OrderDetailViewModel.kt | `presentation/order/` | ⭐新增:订单详情ViewModel,加载单个订单详情 |

### 5. 个人中心模块 (profile/)
| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| ProfileScreen.kt | `presentation/profile/` | 个人中心界面,包含头像上传、修改昵称、修改密码、实名认证、紧急联系人管理 |
| ProfileViewModel.kt | `presentation/profile/` | 个人中心ViewModel,处理用户资料CRUD、头像上传压缩、表单验证 |

---

## 📂 服务层 (service/)

| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| AgentFloatService.kt | `service/` | 智能体悬浮窗后台服务,允许在其他应用上层显示聊天窗口 |

---

## 📂 地图组件 (map/)

| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| MapViewComposable.kt | `map/` | 高德地图Compose封装组件,提供MapView的Compose适配 |

---

## 📂 主题配置 (ui/theme/)

| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| Color.kt | `ui/theme/` | 应用颜色定义(Primary/Secondary/Error等色值) |
| Theme.kt | `ui/theme/` | Material3主题配置,定义Light/Dark主题 |
| Type.kt | `ui/theme/` | 字体样式定义(Typography) |

---

## 📂 根目录文件

| 文件名 | 路径 | 功能说明 |
|--------|------|----------|
| MainActivity.kt | 根目录 | 主Activity,管理导航(NavHost)、权限请求、位置同步、SDK初始化 |
| MyApplication.kt | 根目录 | Application类,Hilt入口,全局初始化 |

---

## 📊 代码统计

### 按模块分类
| 模块 | 文件数 | 主要功能 |
|------|--------|----------|
| core/ | 8 | 网络、数据存储、工具类、WebSocket |
| data/ | 17 | 数据模型(15个) + 仓库(2个) |
| di/ | 2 | Hilt依赖注入模块 |
| domain/ | 1 | 领域层接口 |
| debug/ | 2 | 调试工具 |
| presentation/ | 14 | UI界面(9个) + ViewModel(9个,部分共用) |
| service/ | 1 | 后台服务 |
| map/ | 1 | 地图组件 |
| ui/theme/ | 3 | 主题配置 |
| 根目录 | 2 | Activity和Application |
| **总计** | **约50个** | - |

### 按功能分类
| 功能模块 | 核心文件 |
|----------|----------|
| 用户认证 | LoginScreen/LoginViewModel/TokenManager/AuthInterceptor |
| 个人中心 | ProfileScreen/ProfileViewModel + 5个Request/Response模型 |
| 智能体聊天 | ChatScreen/ChatViewModel/ChatWebSocketClient/AgentRepository |
| 订单管理 | OrderListScreen/OrderDetailScreen/OrderViewModel/OrderRepository |
| 地图定位 | HomeScreen/HomeViewModel/MapViewComposable |
| 语音识别 | SpeechRecognizerHelper/BaiduSpeechRecognizerHelper |
| 安全防护 | SensitiveWordFilter/TokenManager |

---

## 🔑 关键代码文件详解

### 1. MainActivity.kt (478行)
**职责**: 
- 应用入口和导航管理
- SDK初始化(高德地图、讯飞语音)
- 位置同步(HomeViewModel → ChatViewModel)
- 权限请求处理

**关键代码段**:
```kotlin
// 位置同步
homeViewModel.currentLocation.collect { location ->
    chatViewModel.syncLocationFromHome(location.latitude, location.longitude)
}
```

### 2. ProfileScreen.kt (1195行)
**职责**:
- 个人中心UI展示
- 头像裁剪对话框(AvatarCropDialog)
- 折叠式表单(昵称/密码/实名/联系人)
- 错误和成功提示

**核心功能**:
- 手动裁剪头像(拖动滑块0.5x-3.0x)
- 修改昵称(折叠卡片)
- 修改密码(验证码+格式校验)
- 实名认证(姓名+身份证)
- 紧急联系人管理(增删查)

### 3. ChatViewModel.kt (最大文件)
**职责**:
- WebSocket连接管理
- 图片识别(Base64压缩)
- 订单创建(四层POI校验)
- 位置同步
- 敏感词过滤

**关键方法**:
- `recognizeImageByHttp()`: 图片识别
- `createOrder()`: 创建订单(含POI名称校验)
- `syncLocationFromHome()`: 同步位置
- `sendMessage()`: 发送文本消息

### 4. ApiService.kt
**职责**: 定义所有后端API接口

**接口分类**:
- 认证: login/sendCode
- 用户: getUserProfile/uploadAvatar/changeNickname/changePassword/submitRealNameAuth
- 紧急联系人: getEmergencyContacts/addEmergencyContact/deleteEmergencyContact
- 智能体: searchDestination/agentImage/confirmSelection
- 订单: createOrder/getOrderList/getOrderDetail

### 5. ChatWebSocketClient.kt
**职责**: WebSocket通信客户端

**特性**:
- 自动重连(指数退避算法)
- 心跳保活(每30秒)
- 线程安全(synchronized)
- 连接状态监听
- 消息回调

---

## 🆕 v2.0 新增文件清单

### 完全新增的文件 (12个)
1. `core/network/AuthInterceptor.kt` - 认证拦截器
2. `core/network/RetrofitClient.kt` - Retrofit客户端封装
3. `core/utils/SensitiveWordFilter.kt` - 敏感词过滤
4. `core/utils/SpeechRecognizerHelper.kt` - 讯飞语音助手
5. `core/utils/BaiduSpeechRecognizerHelper.kt` - 百度语音助手
6. `core/websocket/ChatWebSocketClient.kt` - 聊天WebSocket(重构)
7. `domain/repository/IOrderRepository.kt` - 订单仓储接口
8. `debug/HiltDebugChecker.kt` - Hilt调试工具
9. `debug/WebSocketDebugMonitor.kt` - WebSocket监控
10. `data/model/AvatarResponse.kt` - 头像响应模型
11. `data/model/ChangeNicknameRequest.kt` - 修改昵称请求
12. `data/model/ChangePasswordRequest.kt` - 修改密码请求
13. `data/model/EmergencyContact.kt` - 紧急联系人模型
14. `data/model/ChatSession.kt` - 聊天会话模型
15. `data/model/PageResponse.kt` - 分页响应模型
16. `presentation/chat/ChatListScreen.kt` - 会话列表界面
17. `presentation/chat/ChatListViewModel.kt` - 会话列表VM
18. `presentation/order/OrderDetailViewModel.kt` - 订单详情VM

### 重大修改的文件 (5个)
1. `MainActivity.kt` - 添加位置同步、SDK初始化优化
2. `ProfileScreen.kt` - 新增昵称/密码/实名/联系人功能,修复括号错误
3. `ProfileViewModel.kt` - 新增对应业务逻辑
4. `ChatViewModel.kt` - 新增图片识别、语音输入、敏感词过滤
5. `ApiService.kt` - 新增用户管理和紧急联系人接口

---

## 📝 代码规范

### 命名约定
- **ViewModel**: `{Feature}ViewModel.kt`
- **Screen**: `{Feature}Screen.kt`
- **Model**: `{Entity}.kt`
- **Repository**: `{Entity}Repository.kt`
- **工具类**: `{Function}Helper.kt` / `{Function}Filter.kt`

### 注释规范
- 文件头部: 无(通过包名和类名自解释)
- 关键方法: KDoc注释(参数/返回值/异常)
- 复杂逻辑: 行内注释(⭐标记重要修改)
- Log标签: 统一使用类名简写(如"ChatViewModel")

### 架构分层
```
Presentation (UI + ViewModel)
    ↓
Domain (Interface) ⭐新增
    ↓
Data (Repository + Model)
    ↓
Core (Network + DataStore + Utils)
```

---

## 🔗 文件依赖关系

### 核心依赖链
```
MainActivity
  ├─→ HomeViewModel → HomeScreen
  ├─→ ChatViewModel → ChatScreen
  ├─→ LoginViewModel → LoginScreen
  └─→ NavHost管理路由

ChatViewModel
  ├─→ ChatWebSocketClient (WebSocket通信)
  ├─→ AgentRepository (图片识别API)
  ├─→ OrderRepository (订单API)
  ├─→ SensitiveWordFilter (敏感词过滤)
  └─→ SpeechRecognizerHelper (语音识别)

ProfileViewModel
  ├─→ ApiService (用户API)
  ├─→ TokenManager (Token管理)
  └─→ 头像压缩逻辑(Bitmap处理)

OrderViewModel
  └─→ OrderRepository → ApiService
```

---

## 📦 第三方库集成

### 本地库 (libs/)
| 文件名 | 用途 |
|--------|------|
| amap-full-11.1.0.aar | 高德地图SDK |
| Msc.jar | 讯飞语音SDK |
| bdasr_aipd_V3_20250717_1e379e2.aar | 百度语音SDK |

### 原生库 (jniLibs/)
- `libAMapSDK_MAP_v11_1_000.so` - 高德地图原生库
- `libmsc.so` - 讯飞语音原生库
- `libapssdk.so` - 百度语音原生库

---

## 🎯 代码亮点

### 1. 四层POI名称校验
**位置**: `ChatViewModel.kt`  
**目的**: 防止空地点名导致订单创建失败  
**实现**: 在createOrder/confirmSelection/searchAndAutoSelect/图片识别ORDER响应四处校验

### 2. 头像手动裁剪
**位置**: `ProfileScreen.kt` - `AvatarCropDialog`  
**功能**: 拖动滑块调整大小(0.5x-3.0x),居中裁剪200x200  
**压缩**: JPEG 85%质量,符合B站头像规范

### 3. 图片分级压缩
**位置**: `ChatViewModel.kt` - `recognizeImageByHttp`  
**策略**: 
- 第一次: 60%质量
- 超过500KB: 降至40%质量
- 添加Base64前缀: `data:image/jpeg;base64,`

### 4. 位置自动同步
**位置**: `MainActivity.kt`  
**机制**: LaunchedEffect监听HomeViewModel.location → 调用ChatViewModel.syncLocationFromHome

### 5. WebSocket自动重连
**位置**: `ChatWebSocketClient.kt`  
**算法**: 指数退避(1s → 2s → 4s → 8s → 最大30s)

---

**文档生成时间**: 2026-04-05  
**维护者**: 安心出行业务部前端团队
