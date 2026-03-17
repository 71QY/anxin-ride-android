pluginManagement {
    repositories {
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
        maven { setUrl("https://mirrors.cloud.tencent.com/maven/") }
        maven { setUrl("https://mirrors.cloud.tencent.com/google/") }
        google()
        mavenCentral()
    }
}
rootProject.name = "MyApplication"
include(":app")