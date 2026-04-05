# 安心出行 - Android 应用安装说明

## 📱 项目简介

安心出行是一款基于 Kotlin 和 Jetpack Compose 开发的 Android 出行应用,提供地图定位、即时通讯、订单管理等功能。

---

## 🔧 环境要求

### 开发环境
- **JDK**: 17 或更高版本
- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **Gradle**: 8.0+
- **Kotlin**: 1.9.0+
- **最低 SDK**: API 26 (Android 8.0)
- **目标 SDK**: API 34 (Android 14)

### 运行环境
- Android 8.0 及以上系统
- 至少 2GB RAM
- 50MB 可用存储空间

---

## 📋 前置配置

### 1. 第三方服务密钥配置

在使用项目前,需要先申请以下第三方服务的 API Key:

#### 高德地图配置
1. 访问 [高德开放平台](https://console.amap.com/dev/index)
2. 创建应用并获取:
   - **Android 端 Key** (用于地图SDK)
   - **Web 服务端 Key** (用于地理编码等Web服务)

#### 讯飞语音配置
1. 访问 [讯飞开放平台](https://www.xfyun.cn/)
2. 创建应用并获取:
   - **AppID** (用于语音识别功能)

### 2. 配置文件修改

在 `local.properties` 文件中添加以下配置:

```properties
# 高德地图 Android 端 Key
amap.key=你的高德Android_Key

# 高德地图 Web 服务端 Key
amap.web.key=你的高德Web_Key

# 讯飞语音 AppID
iflytek.appid=你的讯飞AppID
```

或者在 `build.gradle` 中配置:

```gradle
android {
    defaultConfig {
        // ...
        manifestPlaceholders = [
            AMAP_KEY: "你的高德Android_Key",
            IFLYTEK_APPID: "你的讯飞AppID"
        ]
    }
}
```

---

## 🚀 安装步骤

### 方法一:使用 Android Studio (推荐)

1. **克隆/下载项目**
   ```bash
   # 如果使用 Git
   git clone <repository-url>
   cd MyApplication
   ```

2. **打开项目**
   - 启动 Android Studio
   - 选择 "Open an Existing Project"
   - 选择项目根目录 `D:/Android_items/MyApplication`

3. **同步 Gradle**
   - 等待 Android Studio 自动同步 Gradle
   - 如果未自动同步,点击 `File > Sync Project with Gradle Files`

4. **配置签名(可选)**
   - 调试版本可使用默认 debug 签名
   - 发布版本需配置 release 签名

5. **连接设备/模拟器**
   - 连接真机并开启 USB 调试
   - 或创建 AVD 模拟器(API 26+)

6. **运行应用**
   - 点击工具栏的绿色运行按钮 ▶️
   - 或使用快捷键 `Shift + F10` (Windows/Linux)

### 方法二:命令行构建

1. **进入项目根目录**
   ```bash
   cd D:/Android_items/MyApplication
   ```

2. **清理项目**
   ```bash
   gradlew clean
   ```

3. **编译 Debug 版本**
   ```bash
   gradlew assembleDebug
   ```

4. **安装到设备**
   ```bash
   gradlew installDebug
   ```

5. **生成的 APK 位置**
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📦 构建变体

### Debug 版本
- 包含调试信息
- 启用日志输出
- 自动签名

```bash
gradlew assembleDebug
```

### Release 版本
- 代码混淆和优化
- 禁用日志输出
- 需要配置签名

```bash
gradlew assembleRelease
```

配置 release 签名 (`app/build.gradle`):

```gradle
android {
    signingConfigs {
        release {
            storeFile file("your_keystore.jks")
            storePassword "your_store_password"
            keyAlias "your_key_alias"
            keyPassword "your_key_password"
        }
    }
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release
        }
    }
}
```

---

## 🔑 权限说明

应用需要在 `AndroidManifest.xml` 中声明以下权限:

### 运行时权限
- **位置权限**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
  - 用于地图定位和导航功能
- **存储权限**: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` (Android 13-)
  - 用于头像上传和文件保存
- **媒体权限**: `READ_MEDIA_IMAGES` (Android 13+)
  - 用于选择头像图片
- **相机权限**: `CAMERA`
  - 用于拍照功能
- **录音权限**: `RECORD_AUDIO`
  - 用于语音输入

### 普通权限
- **网络权限**: `INTERNET`, `ACCESS_NETWORK_STATE`
- **前台服务**: `FOREGROUND_SERVICE`
- **悬浮窗**: `SYSTEM_ALERT_WINDOW`

---

## ⚙️ 主要依赖

### 核心框架
- **Jetpack Compose**: 现代化 UI 工具包
- **Hilt**: 依赖注入
- **Coroutines & Flow**: 异步编程

### 功能模块
- **Coil**: 图片加载
- **高德地图 SDK**: 地图和定位
- **讯飞语音 SDK**: 语音识别
- **Retrofit + OkHttp**: 网络请求
- **Room**: 本地数据库

### 完整依赖列表
见 `app/build.gradle` 文件的 `dependencies` 块

---

## 🐛 常见问题

### 1. Gradle 同步失败
**解决方案**:
- 检查网络连接
- 配置国内 Maven 镜像(阿里云)
- 清除 Gradle 缓存: `gradlew clean --refresh-dependencies`

### 2. 地图无法显示
**检查项**:
- 确认高德 Key 配置正确
- 检查 SHA1 指纹是否与高德控制台一致
- 确认网络权限已授予

### 3. 定位失败
**解决方案**:
- 确认位置权限已授予
- 检查 GPS 是否开启
- 确保在高德控制台配置了正确的包名和签名

### 4. 编译错误 "Unresolved reference"
**解决方案**:
- 执行 `File > Invalidate Caches / Restart`
- 重新同步 Gradle
- 检查导入语句是否完整

### 5. 应用闪退
**排查步骤**:
- 查看 Logcat 日志
- 确认所有第三方 Key 已正确配置
- 检查是否有未处理的空指针异常

---

## 📝 开发建议

### IDE 设置
1. 启用自动导入: `Settings > Editor > General > Auto Import`
2. 配置代码格式化: `Settings > Editor > Code Style > Kotlin`
3. 启用实时模板和代码补全

### 调试技巧
1. 使用 Logcat 过滤日志: `package:com.example.myapplication`
2. 启用 StrictMode 检测性能问题
3. 使用 Layout Inspector 调试 Compose UI

### 性能优化
1. 启用 R8 代码压缩
2. 使用 ProGuard 规则优化体积
3. 图片资源使用 WebP 格式

---

## 📄 项目结构

```
MyApplication/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/myapplication/
│   │   │   ├── data/          # 数据层
│   │   │   ├── domain/        # 业务逻辑层
│   │   │   ├── presentation/  # UI 层
│   │   │   └── di/           # 依赖注入
│   │   ├── res/              # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle              # 项目级配置
├── settings.gradle
└── local.properties          # 本地配置(不提交到Git)
```

---

## 🔄 更新日志

### v1.0.0
- ✅ 用户认证系统
- ✅ 地图定位功能
- ✅ 个人中心
- ✅ 订单管理
- ✅ 紧急联系人
- ✅ 实名认证
- ✅ 头像上传与裁剪

---

## 📞 技术支持

如遇到问题,请:
1. 查看本文档的常见问题部分
2. 检查 Logcat 错误日志
3. 确认所有配置项已正确填写
4. 联系开发团队

---

## ⚠️ 注意事项

1. **不要将 `local.properties` 提交到版本控制系统**
2. 定期更新第三方 SDK 到最新版本
3. 发布前务必进行完整的测试
4. 遵守高德和讯飞的使用条款和配额限制
5. 保护用户隐私,对敏感数据进行加密处理

---

## 📜 开源协议

本项目仅供学习和参考使用。

---

**最后更新时间**: 2024年
