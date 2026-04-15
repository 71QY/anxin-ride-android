# Travel App 安装与配置指南

## 📋 系统要求

### 开发环境
- **操作系统**: Windows 10/11, macOS, 或 Linux
- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **JDK**: JDK 17（必须）
- **Android SDK**: API Level 24+ (Android 7.0+)
- **Gradle**: 8.2+

### 设备要求
- Android 手机或模拟器（API 24+）
- 至少 4GB RAM
- 网络连接（用于访问后端服务）

---

## 🚀 快速开始

### 步骤 1: 解压项目

将 `travel_last4.5.zip` 解压到你选择的目录，例如：
```
D:\Android_items\MyApplication
```

### 步骤 2: 配置 local.properties

在项目根目录创建 `local.properties` 文件，添加以下内容：

```properties
# ==================== Android SDK 路径 ====================
# Windows 示例:
sdk.dir=D\:\\Tools\\Sdk

# macOS 示例:
# sdk.dir=/Users/yourname/Library/Android/sdk

# Linux 示例:
# sdk.dir=/home/yourname/Android/Sdk


# ==================== 高德地图 Key ====================
amap.key=9e8fc479c3054b0388b78ea284b58f92
amap.web.key=4deaf4093cad0b049151ee780f5f85cd


# ==================== 讯飞语音 AppID ====================
iflytek.appid=af1a4954


# ==================== Java JDK 路径 ====================
# Windows 示例:
org.gradle.java.home=D\:\\Java\\jdk-17

# macOS 示例:
# org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

# Linux 示例:
# org.gradle.java.home=/usr/lib/jvm/java-17-openjdk


# ==================== 后端 API 地址 ====================
# 如果使用本地后端（模拟器访问宿主机）:
api.baseUrl=http://10.0.2.2:8080/api/
websocket.url=ws://10.0.2.2:8080/ws/agent

# 如果使用远程服务器（替换为你的服务器 IP）:
# api.baseUrl=http://YOUR_SERVER_IP:8080/api/
# websocket.url=ws://YOUR_SERVER_IP:8080/ws/agent
```

**重要提示：**
- `sdk.dir`: 修改为你的 Android SDK 实际路径
- `org.gradle.java.home`: 修改为你的 JDK 17 安装路径
- `api.baseUrl` 和 `websocket.url`: 根据后端部署位置修改

---

## 🔧 后端服务配置

### 选项 A: 使用本地后端（开发测试）

1. 确保后端服务在你的电脑上运行在 `8080` 端口
2. 模拟器会自动通过 `10.0.2.2` 访问宿主机的 localhost
3. 真机调试需要配置网络，使手机能访问电脑 IP

### 选项 B: 使用远程服务器（推荐比赛演示）

1. 将后端部署到云服务器
2. 修改 `local.properties` 中的地址：
   ```properties
   api.baseUrl=http://YOUR_SERVER_IP:8080/api/
   websocket.url=ws://YOUR_SERVER_IP:8080/ws/agent
   ```
3. 确保服务器防火墙开放 8080 端口

---

## 📱 编译与运行

### 方法 1: 使用 Android Studio（推荐）

1. **打开项目**
   - 启动 Android Studio
   - 选择 "Open" → 选择项目根目录
   - 等待 Gradle 同步完成（首次可能需要 5-10 分钟）

2. **配置模拟器或连接真机**
   
   **使用模拟器：**
   - Tools → Device Manager → Create Device
   - 选择设备型号（推荐 Pixel 5 或更高）
   - 下载并选择系统镜像（API 30+ 推荐）
   - 点击 Finish 创建
   
   **使用真机：**
   - 开启手机的"开发者选项"和"USB 调试"
   - 用 USB 线连接电脑
   - 手机上授权 USB 调试

3. **运行应用**
   - 点击工具栏的绿色 Run 按钮（▶️）
   - 或按 `Shift + F10`
   - 选择目标设备
   - 等待编译和安装完成

### 方法 2: 使用命令行

```bash
# 进入项目目录
cd D:\Android_items\MyApplication

# 编译 Debug 版本
gradlew assembleDebug

# 安装到连接的设备
gradlew installDebug

# 或者直接运行
gradlew bootRun
```

---

## ⚙️ 常见问题排查

### 问题 1: Gradle 同步失败

**症状**: 显示 "Gradle sync failed" 或 "Could not resolve dependencies"

**解决方案**:
```bash
# 清理 Gradle 缓存
gradlew clean

# 删除 .gradle 文件夹后重新同步
# 重启 Android Studio
```

### 问题 2: JDK 版本错误

**症状**: "Unsupported class file major version" 或 "JDK 17 required"

**解决方案**:
1. 确认已安装 JDK 17
2. 检查 `local.properties` 中的 `org.gradle.java.home` 路径是否正确
3. File → Settings → Build → Gradle → Gradle JDK → 选择 JDK 17

### 问题 3: SDK 路径错误

**症状**: "SDK location not found" 或 "sdk.dir is missing"

**解决方案**:
1. 确认已安装 Android SDK
2. 检查 `local.properties` 中的 `sdk.dir` 路径
3. 路径中的反斜杠需要转义：`D\:\\Tools\\Sdk`

### 问题 4: 无法连接后端

**症状**: 登录失败、WebSocket 连接超时

**解决方案**:
1. 检查后端服务是否运行
2. 确认 `local.properties` 中的 API 地址正确
3. 模拟器使用 `10.0.2.2`，真机使用电脑的实际 IP
4. 检查防火墙是否阻止连接

**测试后端连通性**:
```powershell
# PowerShell 测试
Test-NetConnection YOUR_SERVER_IP -Port 8080
```

### 问题 5: 地图无法显示

**症状**: 地图区域空白或显示网格

**解决方案**:
1. 确认 `local.properties` 中的 `amap.key` 正确
2. 检查网络连接
3. 确认高德地图 SDK 已正确集成（libs 文件夹中有 AAR 文件）

### 问题 6: 语音识别失败

**症状**: 语音输入无响应

**解决方案**:
1. 确认 `local.properties` 中的 `iflytek.appid` 正确
2. 检查麦克风权限是否授予
3. 确认讯飞语音 SDK 已正确集成

---

## 🔐 权限说明

应用需要以下权限（已在 AndroidManifest.xml 中声明）：

- **INTERNET**: 网络访问（必需）
- **ACCESS_FINE_LOCATION**: 精确定位（地图和导航功能）
- **ACCESS_COARSE_LOCATION**: 粗略定位
- **RECORD_AUDIO**: 语音输入（讯飞语音识别）
- **CAMERA**: 拍照（图片识别功能）
- **READ_EXTERNAL_STORAGE**: 读取相册图片

首次运行时，应用会请求这些权限，请点击"允许"。

---

## 📊 项目结构说明

```
MyApplication/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/myapplication/
│   │   │   ├── core/              # 核心模块
│   │   │   │   ├── datastore/     # 数据存储
│   │   │   │   ├── network/       # 网络请求
│   │   │   │   ├── utils/         # 工具类
│   │   │   │   └── websocket/     # WebSocket 客户端
│   │   │   ├── data/              # 数据层
│   │   │   │   ├── model/         # 数据模型
│   │   │   │   └── repository/    # 数据仓库
│   │   │   ├── presentation/      # UI 层
│   │   │   │   ├── chat/          # 聊天界面
│   │   │   │   ├── home/          # 主页（地图）
│   │   │   │   ├── login/         # 登录注册
│   │   │   │   ├── order/         # 订单管理
│   │   │   │   └── profile/       # 个人中心
│   │   │   └── service/           # 后台服务
│   │   ├── res/                   # 资源文件
│   │   └── AndroidManifest.xml    # 应用配置
│   └── libs/                      # 第三方库
│       ├── amap-full-11.1.0.aar   # 高德地图 SDK
│       ├── bdasr_aipd_V3_*.aar    # 百度语音 SDK
│       └── Msc.jar                # 讯飞语音 SDK
├── gradle.properties              # Gradle 全局配置（包含 API Keys）
└── build.gradle.kts               # 项目构建配置
```

---

## 🎯 核心功能说明

### 1. 用户认证
- 手机号 + 验证码登录/注册
- JWT Token 自动管理
- 密码修改和找回

### 2. 智能对话助手
- WebSocket 实时通信
- 自然语言理解目的地
- 智能推荐附近地点

### 3. 地图与导航
- 高德地图集成
- 实时定位
- POI 搜索和展示
- 路线规划

### 4. 订单管理
- 创建打车订单
- 订单状态跟踪
- 历史订单查询

### 5. 多媒体功能
- 语音输入（讯飞）
- 图片识别
- 头像上传

---

## 🌐 网络配置注意事项

### 模拟器访问本地后端
```properties
api.baseUrl=http://10.0.2.2:8080/api/
websocket.url=ws://10.0.2.2:8080/ws/agent
```

### 真机访问本地后端
1. 查看电脑 IP: `ipconfig` (Windows) 或 `ifconfig` (Mac/Linux)
2. 修改配置:
   ```properties
   api.baseUrl=http://192.168.x.x:8080/api/
   websocket.url=ws://192.168.x.x:8080/ws/agent
   ```
3. 确保手机和电脑在同一 WiFi 网络

### 生产环境
```properties
api.baseUrl=https://your-domain.com/api/
websocket.url=wss://your-domain.com/ws/agent
```

---

## 📝 测试账号

如果后端支持模拟短信，可以使用任意手机号登录，验证码固定为：
```
验证码: 123456
```

或者联系后端管理员获取测试账号。

---

## 🆘 获取帮助

遇到问题时：

1. **查看日志**: 
   - Android Studio → Logcat
   - 过滤关键词: "ERROR", "Exception"

2. **检查网络**:
   ```powershell
   # 测试后端连通性
   Test-NetConnection YOUR_SERVER_IP -Port 8080
   ```

3. **清理重建**:
   ```bash
   gradlew clean
   gradlew build
   ```

4. **查阅文档**:
   - 项目根目录的 `INSTALL.md`
   - Android 官方文档: https://developer.android.com

---

## ✅ 验证安装成功

完成以下步骤即表示安装成功：

- [ ] 项目能在 Android Studio 中打开并无报错
- [ ] Gradle 同步成功
- [ ] 应用能编译通过
- [ ] 应用能安装到设备/模拟器
- [ ] 应用能正常启动
- [ ] 能成功登录（或看到登录界面）
- [ ] 地图能正常显示
- [ ] 能连接到后端服务

---

## 📞 技术支持

如有问题，请提供：
1. 错误截图或日志
2. Android Studio 版本
3. JDK 版本
4. 设备信息（模拟器/真机，Android 版本）

祝使用愉快！🎉
