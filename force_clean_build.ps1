# 强制清理所有Gradle和Kotlin缓存
Write-Host "🧹 开始清理Gradle和Kotlin缓存..." -ForegroundColor Cyan

# 1. 停止Gradle守护进程
Write-Host "`n⏹️  停止Gradle守护进程..." -ForegroundColor Yellow
.\gradlew.bat --stop
Start-Sleep -Seconds 2

# 2. 删除build目录
Write-Host "`n🗑️  删除build目录..." -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build"
    Write-Host "✅ app\build 已删除" -ForegroundColor Green
}

if (Test-Path "build") {
    Remove-Item -Recurse -Force "build"
    Write-Host "✅ build 已删除" -ForegroundColor Green
}

# 3. 删除.gradle缓存
Write-Host "`n🗑️  删除.gradle缓存..." -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host "✅ .gradle 已删除" -ForegroundColor Green
}

# 4. 删除Kotlin增量编译缓存
Write-Host "`n🗑️  删除Kotlin增量编译缓存..." -ForegroundColor Yellow
$kotlinCacheDirs = @(
    "app\.kotlin",
    ".kotlin"
)

foreach ($dir in $kotlinCacheDirs) {
    if (Test-Path $dir) {
        Remove-Item -Recurse -Force $dir
        Write-Host "✅ $dir 已删除" -ForegroundColor Green
    }
}

# 5. 删除IDE缓存（如果存在）
Write-Host "`n🗑️  删除IDE缓存..." -ForegroundColor Yellow
if (Test-Path ".idea\caches") {
    Remove-Item -Recurse -Force ".idea\caches"
    Write-Host "✅ .idea\caches 已删除" -ForegroundColor Green
}

Write-Host "`n✨ 清理完成！" -ForegroundColor Green
Write-Host "`n📦 开始重新构建..." -ForegroundColor Cyan
Write-Host "请在Android Studio中执行: File → Invalidate Caches / Restart`n" -ForegroundColor Yellow
