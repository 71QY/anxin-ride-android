# ADB 连接诊断脚本
Write-Host "=== ADB 连接诊断 ===" -ForegroundColor Cyan

# 1. 检查 ADB 版本
Write-Host "`n[1/5] 检查 ADB 版本..." -ForegroundColor Yellow
adb version

# 2. 检查 ADB 服务状态
Write-Host "`n[2/5] 检查 ADB 服务..." -ForegroundColor Yellow
$adbProcess = Get-Process adb -ErrorAction SilentlyContinue
if ($adbProcess) {
    Write-Host "✅ ADB 服务正在运行 (PID: $($adbProcess.Id))" -ForegroundColor Green
} else {
    Write-Host "❌ ADB 服务未运行" -ForegroundColor Red
    Write-Host "正在启动 ADB 服务..." -ForegroundColor Yellow
    adb start-server
}

# 3. 检查设备连接
Write-Host "`n[3/5] 检查设备连接..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$"
if ($devices) {
    Write-Host "✅ 设备已连接:" -ForegroundColor Green
    adb devices
} else {
    Write-Host "❌ 没有检测到设备" -ForegroundColor Red
    Write-Host "请检查：" -ForegroundColor Yellow
    Write-Host "  - USB 线是否连接良好" -ForegroundColor White
    Write-Host "  - 手机是否开启 USB 调试" -ForegroundColor White
    Write-Host "  - 是否授权了此电脑的 USB 调试" -ForegroundColor White
    exit 1
}

# 4. 检查应用状态
Write-Host "`n[4/5] 检查应用状态..." -ForegroundColor Yellow
$appRunning = adb shell pidof com.example.myapplication
if ($appRunning) {
    Write-Host "⚠️  应用正在运行 (PID: $appRunning)" -ForegroundColor Yellow
    Write-Host "正在停止应用..." -ForegroundColor Yellow
    adb shell am force-stop com.example.myapplication
    Start-Sleep -Seconds 1
    Write-Host "✅ 应用已停止" -ForegroundColor Green
} else {
    Write-Host "✅ 应用未运行" -ForegroundColor Green
}

# 5. 测试 ADB Shell
Write-Host "`n[5/5] 测试 ADB Shell..." -ForegroundColor Yellow
try {
    $result = adb shell echo "test" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ ADB Shell 正常" -ForegroundColor Green
    } else {
        Write-Host "❌ ADB Shell 异常" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ ADB Shell 连接失败" -ForegroundColor Red
}

Write-Host "`n=== 诊断完成 ===" -ForegroundColor Cyan
Write-Host "`n建议操作：" -ForegroundColor Yellow
Write-Host "1. 如果仍有问题，尝试更换 USB 线或 USB 端口" -ForegroundColor White
Write-Host "2. 重启手机和电脑" -ForegroundColor White
Write-Host "3. 更新 ADB 驱动" -ForegroundColor White
Write-Host "4. 在 Android Studio 中点击 'Sync Project with Gradle Files'" -ForegroundColor White
