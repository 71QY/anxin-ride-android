# 强制清理并重新编译脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  强制清理并重新编译" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 切换到项目目录
Set-Location "D:\Android_items\MyApplication"

# 1. 停止 Gradle Daemon
Write-Host "1. 停止 Gradle Daemon..." -ForegroundColor Yellow
.\gradlew.bat --stop

# 2. 清理构建缓存
Write-Host ""
Write-Host "2. 清理构建缓存..." -ForegroundColor Yellow
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue app\build
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue build

# 3. 清理 Gradle 缓存
Write-Host ""
Write-Host "3. 清理 Gradle 缓存..." -ForegroundColor Yellow
.\gradlew.bat clean

# 4. 重新编译 Debug 版本
Write-Host ""
Write-Host "4. 重新编译 Debug 版本..." -ForegroundColor Yellow
.\gradlew.bat :app:assembleDebug

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ 编译完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "请在 Android Studio 中点击 'Run' 重新安装到设备" -ForegroundColor Yellow
Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
