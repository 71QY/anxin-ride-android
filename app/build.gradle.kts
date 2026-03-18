plugins {
    id("com.android.application") version "8.1.0"
    id("org.jetbrains.kotlin.android") version "1.9.0"
    // Kotlin 注解处理器（Hilt 需要）
    id("org.jetbrains.kotlin.kapt") version "1.9.0"
    // Hilt 插件
    id("com.google.dagger.hilt.android") version "2.48"
    // Kotlin 序列化插件（用于 @Serializable）
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // 如果需要支持 x86_64 模拟器，保留此项
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    // 指定与 Kotlin 1.9.0 兼容的 Compose 编译器版本
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
    }
}

dependencies {
    // ==================== Compose 基础 ====================
    // 使用 BOM 统一管理 Compose 版本
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ==================== 基础 AndroidX ====================
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")

    // ==================== 网络请求 ====================
    // Retrofit + Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // OkHttp 核心库和日志拦截器（用于调试）
    implementation("com.squareup.okhttp3:okhttp:4.11.0")          // ✅ 新增：OkHttp 核心库
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")

    // ==================== 权限处理 ====================
    implementation("com.google.accompanist:accompanist-permissions:0.30.1")

    // ==================== 导航 ====================
    implementation("androidx.navigation:navigation-compose:2.5.3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.0.0-alpha05")

    // ==================== 数据序列化 ====================
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // ==================== Hilt 依赖注入 ====================
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ==================== Compose 生命周期扩展 ====================
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // ==================== 本地 JAR 包（讯飞、高德等） ====================
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // ==================== 单元测试 ====================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Hilt 配置
kapt {
    correctErrorTypes = true
}