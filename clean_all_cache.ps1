# ==================== 彻底清理所有缓存脚本 ====================
Write-Host " 开始清理所有Gradle和构建缓存..." -ForegroundColor Cyan

# 1. 停止Gradle Daemon
Write-Host "`n📌 步骤1: 停止Gradle守护进程" -ForegroundColor Yellow
./gradlew --stop
Start-Sleep -Seconds 2

# 2. 清理项目构建目录
Write-Host "`n📌 步骤2: 清理项目build目录" -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Path "app\build" -Recurse -Force
    Write-Host "✅ 已清理 app\build" -ForegroundColor Green
}

# 3. 清理Gradle缓存
Write-Host "`n📌 步骤3: 清理Gradle缓存" -ForegroundColor Yellow
$gradleCache = "$env:USERPROFILE\.gradle\caches"
if (Test-Path $gradleCache) {
    Get-ChildItem -Path $gradleCache -Directory | Where-Object { 
        $_.Name -match "transforms|kotlin-dsl|build-cache" 
    } | Remove-Item -Recurse -Force
    Write-Host "✅ 已清理Gradle缓存目录" -ForegroundColor Green
}

# 4. 清理Kotlin增量编译缓存
Write-Host "`n📌 步骤4: 清理Kotlin编译缓存" -ForegroundColor Yellow
$kotlinCache = "app\build\kotlin"
if (Test-Path $kotlinCache) {
    Remove-Item -Path $kotlinCache -Recurse -Force
    Write-Host "✅ 已清理Kotlin缓存" -ForegroundColor Green
}

# 5. 清理Gradle配置缓存
Write-Host "`n📌 步骤5: 清理Gradle配置缓存" -ForegroundColor Yellow
$configCache = ".gradle\configuration-cache"
if (Test-Path $configCache) {
    Remove-Item -Path $configCache -Recurse -Force
    Write-Host "✅ 已清理配置缓存" -ForegroundColor Green
}

# 6. 清理IDE缓存
Write-Host "`n📌 步骤6: 清理IDE缓存" -ForegroundColor Yellow
$ideCache = ".idea\caches"
if (Test-Path $ideCache) {
    Remove-Item -Path $ideCache -Recurse -Force
    Write-Host "✅ 已清理IDE缓存" -ForegroundColor Green
}

Write-Host "`n✨ 所有缓存清理完成！" -ForegroundColor Green
Write-Host "`n⚠️  重要提示：" -ForegroundColor Red
Write-Host "1. 请在Android Studio中点击: File → Invalidate Caches → Invalidate and Restart" -ForegroundColor Yellow
Write-Host "2. 重新打开后，点击: Build → Clean Project" -ForegroundColor Yellow
Write-Host "3. 然后点击: Build → Rebuild Project" -ForegroundColor Yellow
Write-Host "4. 最后重新运行应用" -ForegroundColor Yellow
Write-Host "`n🎯 以后修改代码后，建议使用 './gradlew clean' 再运行" -ForegroundColor Cyan
