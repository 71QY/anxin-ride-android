# ADB Offline 快速修复脚本
Write-Host "=== ADB Offline 快速修复 ===" -ForegroundColor Cyan

# 1. 停止所有 ADB 进程
Write-Host "`n[1/6] 停止 ADB 进程..." -ForegroundColor Yellow
taskkill /F /IM adb.exe 2>$null
Start-Sleep -Seconds 2

# 2. 清理旧密钥
Write-Host "[2/6] 清理旧密钥..." -ForegroundColor Yellow
if (Test-Path "$env:USERPROFILE\.android\adbkey") {
    Remove-Item "$env:USERPROFILE\.android\adbkey" -Force
    Write-Host "  ✅ 已删除 adbkey" -ForegroundColor Green
}
if (Test-Path "$env:USERPROFILE\.android\adbkey.pub") {
    Remove-Item "$env:USERPROFILE\.android\adbkey.pub" -Force
    Write-Host "  ✅ 已删除 adbkey.pub" -ForegroundColor Green
}

# 3. 启动 ADB
Write-Host "[3/6] 启动 ADB 服务..." -ForegroundColor Yellow
adb start-server
Start-Sleep -Seconds 3

# 4. 检查设备
Write-Host "[4/6] 检查设备状态..." -ForegroundColor Yellow
$devicesOutput = adb devices
Write-Host $devicesOutput

# 5. 判断状态
if ($devicesOutput -match "offline") {
    Write-Host "`n❌ 设备仍然 offline" -ForegroundColor Red
    Write-Host "`n请在手机上执行：" -ForegroundColor Yellow
    Write-Host "  1. 设置 → 开发者选项 → 撤销 USB 调试授权" -ForegroundColor White
    Write-Host "  2. 关闭 USB 调试，等待 3 秒，再开启" -ForegroundColor White
    Write-Host "  3. 拔掉 USB 线，重新插入" -ForegroundColor White
    Write-Host "  4. 当弹出授权对话框时，勾选'始终允许'并点击'确定'" -ForegroundColor White
    
    Write-Host "`n等待 10 秒后重试..." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
    
    Write-Host "`n再次检查设备..." -ForegroundColor Yellow
    adb devices
    
} elseif ($devicesOutput -match "unauthorized") {
    Write-Host "`n⚠️  设备未授权" -ForegroundColor Yellow
    Write-Host "请在手机上点击'允许 USB 调试'" -ForegroundColor White
    
} elseif ($devicesOutput -match "device`n") {
    Write-Host "`n✅ 设备连接成功！" -ForegroundColor Green
    Write-Host "现在可以运行应用了" -ForegroundColor Green
    
} else {
    Write-Host "`n❌ 未检测到设备" -ForegroundColor Red
    Write-Host "请检查：" -ForegroundColor Yellow
    Write-Host "  - USB 线是否连接良好" -ForegroundColor White
    Write-Host "  - 手机是否开启 USB 调试" -ForegroundColor White
    Write-Host "  - 是否需要安装 USB 驱动" -ForegroundColor White
}

Write-Host "`n=== 修复完成 ===" -ForegroundColor Cyan
