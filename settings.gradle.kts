pluginManagement {
    repositories {
        // ⭐ 插件仓库使用国内镜像
        google()
        mavenCentral()
        gradlePluginPortal()
        // 备用国内镜像 - 优先使用腾讯云
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ⭐ 调整仓库顺序，避免腾讯云TLS问题
        google()
        mavenCentral()
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // 本地库
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "MyApplication"
include(":app")