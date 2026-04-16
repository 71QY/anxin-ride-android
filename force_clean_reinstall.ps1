# 强制清理并重新安装脚本
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  强制清理并重新安装应用" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 停止 Gradle 守护进程
Write-Host "[1/6] 停止 Gradle 守护进程..." -ForegroundColor Yellow
.\gradlew --stop
Start-Sleep -Seconds 2

# 2. 清理所有构建缓存
Write-Host "[2/6] 清理构建目录..." -ForegroundColor Yellow
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
.\gradlew clean

# 3. 卸载旧版本应用
Write-Host "[3/6] 卸载旧版本应用..." -ForegroundColor Yellow
adb uninstall com.example.myapplication
Start-Sleep -Seconds 2

# 4. 重新编译 Debug 版本
Write-Host "[4/6] 重新编译 Debug 版本..." -ForegroundColor Yellow
.\gradlew assembleDebug

# 5. 安装新版本
Write-Host "[5/6] 安装新版本..." -ForegroundColor Yellow
adb install app\build\outputs\apk\debug\app-debug.apk

# 6. 启动应用
Write-Host "[6/6] 启动应用..." -ForegroundColor Yellow
adb shell am start -n com.example.myapplication/.MainActivity

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ 完成！请查看 Logcat 日志" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "提示：" -ForegroundColor Cyan
Write-Host "1. 打开 Android Studio 的 Logcat 窗口" -ForegroundColor White
Write-Host "2. 过滤条件设置为: package:mine" -ForegroundColor White
Write-Host "3. 搜索关键词: checkElderMode" -ForegroundColor White
Write-Host "4. 观察是否有 '断开旧连接' 的日志（应该没有）" -ForegroundColor White
