Write-Host "Starting to fix VerifyError..." -ForegroundColor Cyan

# Step 1: Stop Gradle Daemon
Write-Host "`nStep 1: Stopping Gradle Daemon" -ForegroundColor Yellow
.\gradlew --stop
Start-Sleep -Seconds 2

# Step 2: Clean project
Write-Host "Step 2: Cleaning project" -ForegroundColor Yellow
.\gradlew clean
Start-Sleep -Seconds 2

# Step 3: Delete all build directories
Write-Host "Step 3: Deleting all build directories" -ForegroundColor Yellow
Get-ChildItem -Path . -Include build -Recurse -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "Build directories deleted" -ForegroundColor Green

# Step 4: Delete .gradle cache
Write-Host "Step 4: Deleting .gradle cache" -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle" -ErrorAction SilentlyContinue
    Write-Host ".gradle cache deleted" -ForegroundColor Green
}

# Step 5: Delete app/build directory
Write-Host "Step 5: Deleting app/build directory" -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
    Write-Host "app/build directory deleted" -ForegroundColor Green
}

# Step 6: Rebuild
Write-Host "`nStep 6: Rebuilding project" -ForegroundColor Yellow
.\gradlew assembleDebug --no-daemon --rerun-tasks

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nBuild successful! VerifyError should be fixed" -ForegroundColor Green
    Write-Host "You can now run the app again" -ForegroundColor Cyan
} else {
    Write-Host "`nBuild failed, please check error messages" -ForegroundColor Red
}

Write-Host "`nTip: If problem persists, try:" -ForegroundColor Yellow
Write-Host "   1. Restart Android Studio" -ForegroundColor White
Write-Host "   2. File -> Invalidate Caches / Restart" -ForegroundColor White
Write-Host "   3. Check ChatScreen.kt for syntax errors" -ForegroundColor White
