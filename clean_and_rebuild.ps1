# PowerShell 清理脚本 - 解决代码不更新问题

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  清理 Gradle 缓存并重新构建" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 停止 Gradle Daemon
Write-Host "[1/5] 停止 Gradle Daemon..." -ForegroundColor Yellow
.\gradlew --stop
Write-Host "✅ Gradle Daemon 已停止" -ForegroundColor Green
Write-Host ""

# 2. 清理 build 目录
Write-Host "[2/5] 清理 build 目录..." -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build"
    Write-Host "✅ app\build 已删除" -ForegroundColor Green
} else {
    Write-Host "⚠️  app\build 不存在，跳过" -ForegroundColor Yellow
}
Write-Host ""

# 3. 清理 .gradle 缓存
Write-Host "[3/5] 清理 .gradle 缓存..." -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host "✅ .gradle 已删除" -ForegroundColor Green
} else {
    Write-Host "⚠️  .gradle 不存在，跳过" -ForegroundColor Yellow
}
Write-Host ""

# 4. Invalidate IDE Caches (需要手动操作)
Write-Host "[4/5] ⚠️  请在 Android Studio 中执行以下操作：" -ForegroundColor Yellow
Write-Host "    File → Invalidate Caches / Restart → Invalidate and Restart" -ForegroundColor Cyan
Write-Host ""
Write-Host "    按任意键继续..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
Write-Host ""

# 5. 重新构建
Write-Host "[5/5] 重新构建项目..." -ForegroundColor Yellow
.\gradlew clean build --no-daemon
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ 清理和构建完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "📌 重要提示：" -ForegroundColor Cyan
Write-Host "   1. 如果问题仍然存在，请重启 Android Studio" -ForegroundColor White
Write-Host "   2. 确保使用的是最新的代码（git pull）" -ForegroundColor White
Write-Host "   3. 检查 Logcat 过滤器是否设置为 'No Filters'" -ForegroundColor White
Write-Host ""
