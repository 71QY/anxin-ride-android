# ========================================
# Force Reinstall Script (Most Thorough)
# Solves code not updating issue
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Force Rebuild and Reinstall" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Stop Gradle Daemon
Write-Host "[1/6] Stopping Gradle Daemon..." -ForegroundColor Yellow
.\gradlew --stop | Out-Null
Start-Sleep -Seconds 2
Write-Host "   Done" -ForegroundColor Green
Write-Host ""

# 2. Uninstall old app from device
Write-Host "[2/6] Uninstalling old app..." -ForegroundColor Yellow
try {
    adb uninstall com.example.myapplication 2>$null
    Write-Host "   Old app uninstalled" -ForegroundColor Green
} catch {
    Write-Host "   App may not be installed, continuing..." -ForegroundColor Yellow
}
Write-Host ""

# 3. Clean project build directories
Write-Host "[3/6] Cleaning build directories..." -ForegroundColor Yellow
$pathsToRemove = @(
    "app\build",
    "build",
    ".gradle",
    "app\.kotlin"
)

foreach ($path in $pathsToRemove) {
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path -ErrorAction SilentlyContinue
        Write-Host "   Removed: $path" -ForegroundColor Green
    }
}
Write-Host ""

# 4. Clean Gradle global cache (optional but thorough)
Write-Host "[4/6] Cleaning Gradle transform cache..." -ForegroundColor Yellow
$gradleUserHome = "$env:USERPROFILE\.gradle\caches"
if (Test-Path $gradleUserHome) {
    # Only clean transforms cache, keep dependencies
    Get-ChildItem -Path $gradleUserHome -Directory | Where-Object { 
        $_.Name -like "transforms-*" 
    } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "   Gradle transform cache cleaned" -ForegroundColor Green
} else {
    Write-Host "   Gradle cache directory not found" -ForegroundColor Cyan
}
Write-Host ""

# 5. Rebuild and install
Write-Host "[5/6] Rebuilding Debug version..." -ForegroundColor Yellow
Write-Host "   This may take a few minutes..." -ForegroundColor Gray
.\gradlew clean assembleDebug --no-daemon

if ($LASTEXITCODE -eq 0) {
    Write-Host "   Build successful" -ForegroundColor Green
} else {
    Write-Host "   Build failed, check errors above" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 6. Install to device
Write-Host "[6/6] Installing app to device..." -ForegroundColor Yellow
.\gradlew installDebug --no-daemon

if ($LASTEXITCODE -eq 0) {
    Write-Host "   Installation successful" -ForegroundColor Green
} else {
    Write-Host "   Installation failed" -ForegroundColor Red
    exit 1
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "  Complete! App reinstalled" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Tips:" -ForegroundColor Cyan
Write-Host "   - You can now run the latest app on your device" -ForegroundColor White
Write-Host "   - If issues persist, restart Android Studio" -ForegroundColor White
Write-Host "   - For daily dev, use Run button (incremental build is faster)" -ForegroundColor White
Write-Host ""

