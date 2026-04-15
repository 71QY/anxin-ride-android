# 修复 TLS 问题并重新构建
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  开始修复 Gradle 构建问题" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. 停止 Gradle Daemon
Write-Host "`n[1/4] 停止 Gradle Daemon..." -ForegroundColor Yellow
.\gradlew.bat --stop
Start-Sleep -Seconds 3

# 2. 清理构建缓存
Write-Host "`n[2/4] 清理构建目录..." -ForegroundColor Yellow
if (Test-Path ".\app\build") {
    Remove-Item -Recurse -Force ".\app\build"
    Write-Host "  ✓ app/build 已删除" -ForegroundColor Green
}

if (Test-Path ".\.gradle\caches") {
    Remove-Item -Recurse -Force ".\.gradle\caches"
    Write-Host "  ✓ .gradle/caches 已删除" -ForegroundColor Green
}

# 3. 重新构建 Release 版本
Write-Host "`n[3/4] 开始重新构建 Release 版本..." -ForegroundColor Yellow
Write-Host "  使用腾讯云镜像,避免阿里云TLS问题" -ForegroundColor Gray
.\gradlew.bat assembleRelease --no-daemon --refresh-dependencies

# 4. 检查构建结果
if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[4/4] ✅ 构建成功!" -ForegroundColor Green
    Write-Host "`nAPK 文件位置:" -ForegroundColor Cyan
    if (Test-Path ".\app\build\outputs\apk\release\app-release.apk") {
        Write-Host "  📦 $(Resolve-Path .\app\build\outputs\apk\release\app-release.apk)" -ForegroundColor White
    }
} else {
    Write-Host "`n[4/4] ❌ 构建失败,请检查上方错误信息" -ForegroundColor Red
    Write-Host "`n建议操作:" -ForegroundColor Yellow
    Write-Host "  1. 检查网络连接" -ForegroundColor Gray
    Write-Host "  2. 确认防火墙未阻止腾讯云镜像" -ForegroundColor Gray
    Write-Host "  3. 尝试手动访问: https://mirrors.cloud.tencent.com" -ForegroundColor Gray
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
