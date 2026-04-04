# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ⭐ Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-dontwarn dagger.hilt.android.internal.managers.ViewComponentFactory
-dontwarn dagger.hilt.internal.aggregatedroot.codegen.**
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
# ⭐ Hilt 编译时优化 (减少反射开销)
-keepclassmembers class * {
    @dagger.hilt.android.internal.managers.FragmentComponentManger <methods>;
}

# ⭐ Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ⭐ Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ⭐ 数据模型
-keep class com.example.myapplication.data.model.** { *; }

# ⭐ OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ⭐ Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# ⭐ 讯飞 SDK (新增)
-keep class com.iflytek.** { *; }
-dontwarn com.iflytek.**
-keep class com.iflytek.cloud.** { *; }
-keepattributes *Annotation*
-keep class com.iflytek.cloud.** {
    <methods>;
    <fields>;
}

# ⭐ 高德地图 SDK (新增)
-keep class com.amap.api.** { *; }
-dontwarn com.amap.api.**
-keep class com.amap.api.services.** { *; }
-keep class com.amap.api.maps.** { *; }
-keep class com.amap.api.location.** { *; }
-keepattributes *Annotation*
-keep class com.amap.api.** {
    <methods>;
    <fields>;
}

# ⭐ Coil
-keep class coil.** { *; }
-dontwarn coil.**

# ⭐ Compose
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }
