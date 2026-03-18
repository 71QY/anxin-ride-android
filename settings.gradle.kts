pluginManagement {
    repositories {
        // 将 JitPack 放在最前（虽然插件通常不从 JitPack 下载，但保留无妨）
        maven { url = uri("https://jitpack.io") }
        maven { setUrl("https://mirrors.cloud.tencent.com/maven/") }
        maven { setUrl("https://mirrors.cloud.tencent.com/google/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // JitPack 放在首位，确保优先从 JitPack 下载
        maven { url = uri("https://jitpack.io") }
        maven { setUrl("https://mirrors.cloud.tencent.com/maven/") }
        maven { setUrl("https://mirrors.cloud.tencent.com/google/") }
        google()
        mavenCentral()
    }
}
rootProject.name = "MyApplication"
include(":app")