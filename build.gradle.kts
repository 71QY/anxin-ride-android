// 项目根目录 build.gradle.kts
plugins {
    id("com.android.application") version "8.1.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.22" apply false
    // Hilt 插件
    id("com.google.dagger.hilt.android") version "2.49" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

// ⭐ 添加子项目配置，确保所有模块使用一致的 Kotlin 版本
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
            )
        }
    }
    
    // ⭐ 添加 Hilt 支持到所有需要依赖注入的模块
    plugins.withId("com.google.dagger.hilt.android") {
        // Hilt 已应用
    }
}
