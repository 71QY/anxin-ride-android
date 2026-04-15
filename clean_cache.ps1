Write-Host "Cleaning Gradle cache..." -ForegroundColor Green
Write-Host ""

# Stop Gradle Daemon
Write-Host "Step 1/5: Stopping Gradle Daemon..." -ForegroundColor Yellow
try {
    .\gradlew --stop
    Write-Host "   OK: Gradle Daemon stopped" -ForegroundColor Green
} catch {
    Write-Host "   WARN: Failed to stop Gradle Daemon, continuing..." -ForegroundColor Yellow
}
Write-Host ""

# Clean app/build directory
Write-Host "Step 2/5: Cleaning app/build..." -ForegroundColor Yellow
if (Test-Path ".\app\build") {
    Remove-Item -Recurse -Force ".\app\build"
    Write-Host "   OK: app/build cleaned" -ForegroundColor Green
} else {
    Write-Host "   INFO: app/build not found, skipping" -ForegroundColor Cyan
}
Write-Host ""

Write-Host "Step 3/5: Cleaning root build..." -ForegroundColor Yellow
if (Test-Path ".\build") {
    Remove-Item -Recurse -Force ".\build"
    Write-Host "   OK: build cleaned" -ForegroundColor Green
} else {
    Write-Host "   INFO: build not found, skipping" -ForegroundColor Cyan
}
Write-Host ""

# Clean .gradle cache
Write-Host "Step 4/5: Cleaning .gradle cache..." -ForegroundColor Yellow
if (Test-Path ".\.gradle") {
    Remove-Item -Recurse -Force ".\.gradle"
    Write-Host "   OK: .gradle cleaned" -ForegroundColor Green
} else {
    Write-Host "   INFO: .gradle not found, skipping" -ForegroundColor Cyan
}
Write-Host ""

# Clean Kotlin compile cache
Write-Host "Step 5/5: Cleaning Kotlin cache..." -ForegroundColor Yellow
$kotlinCachePaths = @(
    ".\app\build\kotlin",
    ".\app\build\tmp\kotlin-classes",
    ".\.kotlin"
)
foreach ($path in $kotlinCachePaths) {
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path
        Write-Host "   OK: Cleaned $path" -ForegroundColor Green
    }
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "DONE: Cache cleaned successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "   1. In Android Studio: File -> Sync Project with Gradle Files" -ForegroundColor White
Write-Host "   2. Wait for sync to complete" -ForegroundColor White
Write-Host "   3. Run your project" -ForegroundColor White
Write-Host ""
Write-Host "If issues persist:" -ForegroundColor Yellow
Write-Host "   - Check gradle.properties (cache should be enabled)" -ForegroundColor White
Write-Host "   - Use: File -> Invalidate Caches / Restart" -ForegroundColor White
Write-Host ""
