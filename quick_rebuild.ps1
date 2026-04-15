# ========================================
# Quick Rebuild Script (Daily Development)
# Faster than force_reinstall.ps1
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Quick Clean and Rebuild" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Stop Gradle Daemon
Write-Host "[1/4] Stopping Gradle Daemon..." -ForegroundColor Yellow
.\gradlew --stop | Out-Null
Start-Sleep -Seconds 1
Write-Host "   Done" -ForegroundColor Green
Write-Host ""

# 2. Clean app build directory
Write-Host "[2/4] Cleaning app/build..." -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
    Write-Host "   Done" -ForegroundColor Green
} else {
    Write-Host "   Skipped (not found)" -ForegroundColor Cyan
}
Write-Host ""

# 3. Clean Kotlin cache
Write-Host "[3/4] Cleaning Kotlin cache..." -ForegroundColor Yellow
$kotlinPaths = @("app\.kotlin", ".kotlin")
foreach ($path in $kotlinPaths) {
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path -ErrorAction SilentlyContinue
    }
}
Write-Host "   Done" -ForegroundColor Green
Write-Host ""

# 4. Rebuild and install
Write-Host "[4/4] Rebuilding and installing..." -ForegroundColor Yellow
.\gradlew clean installDebug --no-daemon

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Success! Ready to run" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Build failed, check errors above" -ForegroundColor Red
    exit 1
}
Write-Host ""
