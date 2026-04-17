plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.parcelize")
    // ⭐ 新增：Hilt 插件（必须！）
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 旧后端地址 (A): val apiBaseUrl = project.findProperty("api.baseUrl")?.toString() ?: "http://10.241.75.80:8080/api/"
        // 中间后端地址 (B): val apiBaseUrl = project.findProperty("api.baseUrl")?.toString() ?: "http://192.168.189.57:8080/api/"
        // 新后端地址 (C):
        val apiBaseUrl = project.findProperty("api.baseUrl")?.toString() ?: "http://192.168.189.80:8080/api/"
        // 旧后端地址 (A): val websocketUrl = project.findProperty("websocket.url")?.toString() ?: "ws://10.241.75.80:8080/ws/agent"
        // 中间后端地址 (B): val websocketUrl = project.findProperty("websocket.url")?.toString() ?: "ws://192.168.189.57:8080/ws/agent"
        // 新后端地址 (C):
        val websocketUrl = project.findProperty("websocket.url")?.toString() ?: "ws://192.168.189.80:8080/ws/agent"
        val amapKey = project.findProperty("amap.key")?.toString() ?: ""
        val iflytekAppid = project.findProperty("iflytek.appid")?.toString() ?: ""
        val baiduAppId = project.findProperty("baidu.app.id")?.toString() ?: ""
        val baiduApiKey = project.findProperty("baidu.api.key")?.toString() ?: ""
        val baiduSecretKey = project.findProperty("baidu.secret.key")?.toString() ?: ""
        
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "WEBSOCKET_URL", "\"$websocketUrl\"")
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
        buildConfigField("String", "IFLYTEK_APPID", "\"$iflytekAppid\"")
        buildConfigField("String", "BAIDU_APP_ID", "\"$baiduAppId\"")
        buildConfigField("String", "BAIDU_API_KEY", "\"$baiduApiKey\"")
        buildConfigField("String", "BAIDU_SECRET_KEY", "\"$baiduSecretKey\"")
        
        manifestPlaceholders["AMAP_KEY"] = amapKey
        
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
        jvmToolchain(17)
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
        // ⭐ 优化：禁用不需要的 Build Features，减少编译时间
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"  // ⭐ 与 Kotlin 1.9.22 兼容
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // ⭐ 优化：使用 BOM 管理版本，避免硬编码
    implementation(libs.androidx.compose.material.icons.extended)
    
    // ⭐ Lifecycle 和 ViewModel（用于 collectAsStateWithLifecycle）
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    
    // ⭐ Coil 图片加载库
    implementation(libs.coil.compose)
    
    implementation(libs.accompanist.permissions)
    implementation(files("libs\\Msc.jar"))
    implementation(files("libs\\amap-full-11.1.0.aar"))
    // ⭐ 新增：百度语音识别 SDK
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)
    implementation(libs.gson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    // ⭐ Hilt 编译器依赖
    kapt(libs.hilt.android.compiler)
}

// ⭐ kapt 配置 - 添加 Hilt 优化
kapt {
    correctErrorTypes = true
    useBuildCache = true  // ⭐ 启用构建缓存
    
    javacOptions {
        option("-Adagger.fastInit=enabled")
        option("-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true")
        option("-Xmaxerrs", "500")
    }
}

// ⚠️ 已移除：KotlinCompile 配置，由根目录 build.gradle.kts 统一管理
// 避免配置冲突导致 VerifyError
