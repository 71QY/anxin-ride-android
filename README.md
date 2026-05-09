安心出行 (Anxin Ride)

基于 Jetpack Compose 开发的智能打车应用 | 支持代叫车、长辈模式、实时后台位置追踪

---
 项目简介
安心出行是一款主打家庭出行场景的智能打车 Android 应用，核心聚焦代叫车与亲情守护能力。支持子女远程为父母发起叫车服务，实时查看行程状态，专为老年群体优化适老操作体验。项目采用现代化 Android 技术栈开发，兼顾业务实用性、系统稳定性与用户体验。
 核心亮点
- 代叫车功能：子女远程为长辈叫车，长辈端一键确认行程，适配家庭出行场景
- 长辈专属模式：超大字体、极简界面、语音播报，全方位适老化设计
- 后台持续定位：前台服务保活 + 高德地图SDK，解决后台锁屏定位中断问题
- 实时消息推送：基于 WebSocket 实现订单状态、司机位置实时同步
- 亲情守护体系：支持家庭成员绑定、位置共享、一键紧急联系
- 便捷出行工具：常用地址收藏、家人共享，一键发起打车请求

---
功能特性
1. 双端适配模式
- 普通用户端：完整打车功能，支持自主叫车、为家人代叫车、行程管理
- 长辈端：极简UI、大字体显示，仅保留确认行程、查看行程等核心操作
- 支持应用图标、主题动态切换，适配不同使用人群
2. 代叫车核心流程
   子女选择目的地 → 发起代叫请求 → WebSocket推送至长辈端 → 长辈确认/拒绝 → 司机接单 → 全程实时行程追踪
3. 后台定位服务
- 前台服务保活机制，抵御系统后台查杀
- 5秒高精度定位间隔，平衡实时性与电量消耗
- GPS+网络混合定位，适配室内外多场景
- 定位数据自动上报服务端，维持WebSocket长连接
4. 实时通信能力
- 订单状态变更实时推送通知
- 司机实时位置动态更新
- 代叫车请求即时弹窗提醒
- 收藏地址支持家人共享推送
5. 拓展实用功能
- POI搜索、地址逆地理编码、路线规划与轨迹展示
- DataStore轻量化本地数据存储
- 图片自动压缩、Base64格式上传适配后端
- TTS语音播报，适配长辈使用场景
- 一键拨打紧急联系人电话

---
 技术架构
技术栈
分类
技术
开发语言
Kotlin
UI框架
Jetpack Compose
架构模式
MVVM + Clean Architecture
依赖注入
Hilt
异步处理
Coroutines + Flow
网络请求
Retrofit + OkHttp
实时通信
WebSocket (Spring STOMP)
地图服务
高德地图 SDK
语音合成
科大讯飞 TTS
本地存储
DataStore
图片加载
Coil
项目结构
app/src/main/java/com/example/myapplication/
├── core/                  # 核心工具与基础模块
│   ├── datastore/         # 本地数据存储
│   ├── network/           # 网络层封装
│   ├── utils/             # 通用工具类
│   └── websocket/         # WebSocket长连接客户端
├── data/                  # 数据层
│   ├── model/             # 数据实体模型
│   └── repository/        # 数据仓库实现
├── di/                    # Hilt依赖注入配置
├── domain/                # 业务领域层
│   └── repository/        # 仓库接口定义
├── presentation/          # 视图表现层
│   ├── account/           # 账号管理模块
│   ├── chat/              # 消息推送模块
│   ├── favorites/         # 收藏地址模块
│   ├── home/              # 首页主界面
│   ├── login/             # 登录注册模块
│   ├── order/             # 订单管理模块
│   ├── orderTracking/     # 行程追踪模块
│   └── profile/           # 个人中心模块
├── service/               # 后台常驻服务
│   ├── LocationTrackingService.kt  # 后台定位追踪服务
│   └── AgentFloatService.kt        # 悬浮窗辅助服务
└── ui/theme/              # 全局UI主题配置

---
 快速开始
环境要求
- Android Studio Hedgehog 2023.1.1+
- JDK 17+
- minSdk 24（兼容 Android 7.0 及以上系统）
- Gradle 8.0+
  部署配置步骤
1. 克隆项目
   git clone https://github.com/your-username/anxin-ride-android.git
   cd anxin-ride-android
2. 配置高德地图 Key
   在 app/build.gradle.kts 中配置开发者密钥：
   android {
   defaultConfig {
   manifestPlaceholders["AMAP_KEY"] = "你的高德地图Key"
   }
   }
3. 配置后端接口地址
   修改 core/network/NetworkModule.kt 中的服务端域名：
   .baseUrl("http://你的服务器地址:端口/api/")
4. 编译运行项目
   ./gradlew clean
   ./gradlew installDebug
   也可直接在 Android Studio 中点击 Run 按钮运行项目。

---
 核心技术详解
1. 后台定位保活服务
   采用 Android 官方前台服务方案，完美适配 Android 8.0 - 14+ 系统后台限制，解决应用退后台、锁屏后定位中断问题，实现全程持续定位与数据上报。
   // 启动前台保活服务
   startForeground(NOTIFICATION_ID, notification)

// 高精度定位参数配置
val option = AMapLocationClientOption().apply {
locationMode = Hight_Accuracy
interval = 5000
isGpsFirst = true
}
2. WebSocket 实时通信
   基于 STOMP 协议搭建长连接，实现订单通知、司机位置、代叫请求的实时双向通信。
   webSocketClient.connect(userId) { message ->
   when (message.type) {
   "ORDER_CREATED" -> handleOrder()
   "DRIVER_LOCATION" -> updateLocation()
   "PROXY_ORDER_REQUEST" -> showDialog()
   }
   }
3. 代叫车核心业务
   封装代叫车业务逻辑，支持指定长辈用户、自动填充起止位置、服务端创建订单、WebSocket主动推送通知。
   fun createProxyOrderForElder(elderUserId: Long, destLat: Double, destLng: Double) {
   // 构建代叫车请求参数
   // 调用后端接口创建订单
   // 通过WebSocket推送通知至长辈端
   }
4. 图片压缩上传
   自定义图片压缩逻辑，自动压缩尺寸至800*800、画质70%，转换为Base64格式，适配后端上传限制。
   fun compressImage(bitmap: Bitmap): String {
   // 尺寸压缩 + 画质压缩
   // 返回带MIME前缀的标准Base64字符串
   }

 常见问题
Q1：应用切换后台后定位停止？
A：需授予应用后台定位权限，确保 LocationTrackingService 正常启动，通知栏出现服务常驻通知即为正常运行。
Q2：WebSocket 连接频繁断开？
A：检查设备网络状态与后端服务运行情况，通过Logcat日志排查连接异常原因。
Q3：长辈端无法接收代叫车通知？
A：确认应用通知权限已开启，且WebSocket长连接处于正常连接状态。
Q4：图片上传失败？
A：项目内置图片压缩逻辑，自动控制文件大小在500KB以内，检查Base64编码格式是否规范即可。

📧 联系方式
- 邮箱：1396587508@qq.com
- CSDN：https://blog.csdn.net/name_1111

如果本项目对你有帮助，欢迎点亮 Star 支持！