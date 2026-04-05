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
        Log.d(TAG, "Note: Hilt generated code is created at compile time, not on device")
        Log.d(TAG, "Please check in Android Studio:")
        Log.d(TAG, "  app -> build -> generated -> source -> kapt -> debug")
        Log.d(TAG, "  Or check Build window for compilation output")
        
        Log.d(TAG, "")
        Log.d(TAG, "=== Hilt Configuration Diagnosis ===")
        Log.d(TAG, "1. Confirm root project build.gradle.kts has Hilt plugin")
        Log.d(TAG, "   id(\"com.google.dagger.hilt.android\") version \"2.44\" apply false")
        Log.d(TAG, "")
        Log.d(TAG, "2. Confirm app/build.gradle.kts has kapt dependencies:")
        Log.d(TAG, "   kapt(\"com.google.dagger:hilt-android-compiler:2.44\")")
        Log.d(TAG, "   kapt(\"com.google.dagger:hilt-compiler:2.44\")")
        Log.d(TAG, "")
        Log.d(TAG, "3. Confirm app/build.gradle.kts has applied plugins:")
        Log.d(TAG, "   id(\"org.jetbrains.kotlin.kapt\")")
        Log.d(TAG, "")
        Log.d(TAG, "4. Perform the following actions:")
        Log.d(TAG, "   - File -> Invalidate Caches... -> Invalidate and Restart")
        Log.d(TAG, "   - Build -> Clean Project")
        Log.d(TAG, "   - Build -> Rebuild Project")
        Log.d(TAG, "=======================")
    }
    
    fun getProjectRootDir(applicationDataDir: File): File {
        // applicationInfo.dataDir 通常是 /data/data/package.name
        // 我们不需要再检查手机上的路径了
        return applicationDataDir
    }
}
