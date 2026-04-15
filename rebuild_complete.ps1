Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Complete Rebuild for VerifyError Fix" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Stop Gradle
Write-Host "[1/5] Stopping Gradle Daemon..." -ForegroundColor Yellow
.\gradlew --stop
Start-Sleep -Seconds 3

# Clean
Write-Host "[2/5] Cleaning project..." -ForegroundColor Yellow
.\gradlew clean
Start-Sleep -Seconds 2

# Delete build folders
Write-Host "[3/5] Deleting build directories..." -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build"
    Write-Host "  - Deleted app\build" -ForegroundColor Green
}
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host "  - Deleted .gradle" -ForegroundColor Green
}

# Delete local build cache
Write-Host "[4/5] Deleting local cache..." -ForegroundColor Yellow
$localAppData = $env:LOCALAPPDATA
$gradleCache = "$localAppData\Google\AndroidStudio*\caches"
Get-ChildItem -Path $gradleCache -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*transform*" -or $_.Name -like "*compile*" } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "  - Cache cleaned" -ForegroundColor Green

# Rebuild
Write-Host "[5/5] Rebuilding with new Compose Compiler..." -ForegroundColor Yellow
.\gradlew assembleDebug --no-daemon --rerun-tasks --info

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "SUCCESS! Build completed." -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "1. Uninstall the app from device/emulator" -ForegroundColor White
    Write-Host "2. Reinstall and run" -ForegroundColor White
    Write-Host "3. Test elder mode chat screen" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "BUILD FAILED" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "Please check the error messages above" -ForegroundColor Yellow
}
