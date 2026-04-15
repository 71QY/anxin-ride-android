Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ULTIMATE CLEAN & REBUILD" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Uninstall app from device
Write-Host "[Step 1] Uninstalling old app from device..." -ForegroundColor Yellow
adb uninstall com.example.myapplication
Start-Sleep -Seconds 2

# Step 2: Stop Gradle
Write-Host "[Step 2] Stopping Gradle Daemon..." -ForegroundColor Yellow
.\gradlew --stop
Start-Sleep -Seconds 3

# Step 3: Clean project
Write-Host "[Step 3] Cleaning project..." -ForegroundColor Yellow
.\gradlew clean
Start-Sleep -Seconds 2

# Step 4: Delete ALL build directories
Write-Host "[Step 4] Deleting all build directories..." -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build"
    Write-Host "  - Deleted app\build" -ForegroundColor Green
}
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host "  - Deleted .gradle" -ForegroundColor Green
}

# Step 5: Rebuild with FULL recompilation
Write-Host "[Step 5] Rebuilding with Compose Compiler 1.5.8..." -ForegroundColor Yellow
.\gradlew assembleDebug --no-daemon --rerun-tasks --no-build-cache

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    
    # Install new APK
    Write-Host "Installing new APK..." -ForegroundColor Cyan
    adb install app\build\outputs\apk\debug\app-debug.apk
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✅ App installed successfully!" -ForegroundColor Green
        Write-Host "🚀 Please test elder mode chat screen now" -ForegroundColor Cyan
    } else {
        Write-Host ""
        Write-Host "⚠️ Installation failed, please install manually:" -ForegroundColor Yellow
        Write-Host "   adb install app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor White
    }
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "BUILD FAILED" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
}
