// 项目根目录 build.gradle.kts
plugins {
    // 应用插件到所有子模块（可选）
    id("com.android.application") version "8.1.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" apply false
}

//allprojects {
//    repositories {
//        google()
//        mavenCentral()
//        // 其他仓库
//    }
//}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}