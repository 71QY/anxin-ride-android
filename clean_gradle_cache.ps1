# Clean Gradle cache and build artifacts to fix code not updating issue

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Cleaning Gradle Cache" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Stop Gradle Daemon
Write-Host "[1/5] Stopping Gradle Daemon..." -ForegroundColor Yellow
.\gradlew --stop
Start-Sleep -Seconds 2

# 2. Clean project build artifacts
Write-Host "[2/5] Cleaning build directories..." -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build"
    Write-Host "  [OK] app\build deleted" -ForegroundColor Green
}
if (Test-Path "build") {
    Remove-Item -Recurse -Force "build"
    Write-Host "  [OK] build deleted" -ForegroundColor Green
}

# 3. Clean .gradle cache
Write-Host "[3/5] Cleaning .gradle cache..." -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host "  [OK] .gradle deleted" -ForegroundColor Green
}

# 4. Clean Kotlin compilation cache
Write-Host "[4/5] Cleaning Kotlin cache..." -ForegroundColor Yellow
$kotlinCaches = @(
    "app\build\tmp\kotlin-classes",
    "app\.kotlin",
    ".kotlin"
)
foreach ($cache in $kotlinCaches) {
    if (Test-Path $cache) {
        Remove-Item -Recurse -Force $cache
        Write-Host "  [OK] $cache deleted" -ForegroundColor Green
    }
}

# 5. Note about Android Studio cache
Write-Host "[5/5] Optional: Clean Android Studio cache manually" -ForegroundColor Yellow
Write-Host "  Path: File -> Invalidate Caches / Restart -> Invalidate and Restart" -ForegroundColor Gray

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  [SUCCESS] Cleanup Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "1. In Android Studio: Build -> Rebuild Project" -ForegroundColor White
Write-Host "2. Or run: .\gradlew clean build" -ForegroundColor White
Write-Host "3. Uninstall old APK from device" -ForegroundColor White
Write-Host "4. Reinstall and run" -ForegroundColor White
Write-Host ""
