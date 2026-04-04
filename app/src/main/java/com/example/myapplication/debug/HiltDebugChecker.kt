package com.example.myapplication.debug

import android.util.Log
import java.io.File

object HiltDebugChecker {
    private const val TAG = "HiltDebugChecker"
    
    /**
     * 检查 Hilt 生成的代码文件
     * @param projectRootDir 项目根目录（通常是 applicationInfo.dataDir 的父目录）
     */
    fun checkGeneratedFiles(projectRootDir: File) {
        // ⭐ 修改：不再检查运行时路径，改为提示开发者查看编译输出
        Log.d(TAG, "⚠️ 注意：Hilt 生成的代码在编译时生成，不在手机上")
        Log.d(TAG, "请在 Android Studio 中查看:")
        Log.d(TAG, "  app -> build -> generated -> source -> kapt -> debug")
        Log.d(TAG, "  或者查看 Build 窗口的编译输出")
        
        // ⭐ 新增：提供编译时诊断信息
        Log.d(TAG, "")
        Log.d(TAG, "=== Hilt 配置诊断 ===")
        Log.d(TAG, "1. 确认根项目 build.gradle.kts 已添加 Hilt 插件")
        Log.d(TAG, "   id(\"com.google.dagger.hilt.android\") version \"2.44\" apply false")
        Log.d(TAG, "")
        Log.d(TAG, "2. 确认 app/build.gradle.kts 已添加 kapt 依赖:")
        Log.d(TAG, "   kapt(\"com.google.dagger:hilt-android-compiler:2.44\")")
        Log.d(TAG, "   kapt(\"com.google.dagger:hilt-compiler:2.44\")")
        Log.d(TAG, "")
        Log.d(TAG, "3. 确认 app/build.gradle.kts 已应用插件:")
        Log.d(TAG, "   id(\"org.jetbrains.kotlin.kapt\")")
        Log.d(TAG, "")
        Log.d(TAG, "4. 执行以下操作:")
        Log.d(TAG, "   - File -> Invalidate Caches... -> Invalidate and Restart")
        Log.d(TAG, "   - Build -> Clean Project")
        Log.d(TAG, "   - Build -> Rebuild Project")
        Log.d(TAG, "=======================")
    }
    
    /**
     * 便捷方法：从 Application Context 获取项目根目录
     */
    fun getProjectRootDir(applicationDataDir: File): File {
        // applicationInfo.dataDir 通常是 /data/data/package.name
        // 我们不需要再检查手机上的路径了
        return applicationDataDir
    }
}
