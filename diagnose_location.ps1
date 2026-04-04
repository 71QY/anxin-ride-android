# 定位功能快速诊断脚本
# 使用方法：在 PowerShell 中运行 .\diagnose_location.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   定位功能快速诊断工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查日志文件是否已清理
Write-Host "[1/5] 检查日志文件..." -ForegroundColor Yellow
$logFiles = @(
    "build_error.log",
    "compile_debug.log", 
    "compile_error.log",
    "crash_log.txt",
    "kapt_error.log",
    "kapt_log.txt"
)

$foundLogs = @()
foreach ($file in $logFiles) {
    if (Test-Path $file) {
        $foundLogs += $file
    }
}

if ($foundLogs.Count -eq 0) {
    Write-Host "✅ 日志文件已清理完毕" -ForegroundColor Green
} else {
    Write-Host "❌ 发现以下日志文件未清理：" -ForegroundColor Red
    foreach ($file in $foundLogs) {
        Write-Host "   - $file" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "建议运行以下命令清理：" -ForegroundColor Yellow
    Write-Host "Remove-Item build_error.log, compile_debug.log, compile_error.log, crash_log.txt, kapt_error.log, kapt_log.txt -ErrorAction SilentlyContinue" -ForegroundColor Gray
}

Write-Host ""

# 2. 检查高德地图 Key 配置
Write-Host "[2/5] 检查高德地图 Key 配置..." -ForegroundColor Yellow
if (Test-Path "local.properties") {
    $content = Get-Content "local.properties" -Raw
    if ($content -match "amap\.key=(.+)") {
        $amapKey = $matches[1].Trim()
        Write-Host "✅ 高德地图 Key 已配置: $amapKey" -ForegroundColor Green
        
        # 检查 Key 是否为空
        if ([string]::IsNullOrWhiteSpace($amapKey)) {
            Write-Host "⚠️  警告：高德地图 Key 为空！" -ForegroundColor Yellow
            Write-Host "   请在 local.properties 中配置有效的 amap.key" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ 未找到 amap.key 配置" -ForegroundColor Red
        Write-Host "   请在 local.properties 中添加：amap.key=你的高德地图Key" -ForegroundColor Red
    }
} else {
    Write-Host "❌ local.properties 文件不存在" -ForegroundColor Red
}

Write-Host ""

# 3. 检查权限配置
Write-Host "[3/5] 检查 AndroidManifest.xml 权限配置..." -ForegroundColor Yellow
$manifestPath = "app\src\main\AndroidManifest.xml"
if (Test-Path $manifestPath) {
    $manifest = Get-Content $manifestPath -Raw
    
    $requiredPermissions = @(
        "ACCESS_FINE_LOCATION",
        "ACCESS_COARSE_LOCATION",
        "INTERNET",
        "ACCESS_NETWORK_STATE"
    )
    
    $missingPermissions = @()
    foreach ($perm in $requiredPermissions) {
        if ($manifest -notmatch $perm) {
            $missingPermissions += $perm
        }
    }
    
    if ($missingPermissions.Count -eq 0) {
        Write-Host "✅ 所有必需的权限已配置" -ForegroundColor Green
    } else {
        Write-Host "❌ 缺少以下权限：" -ForegroundColor Red
        foreach ($perm in $missingPermissions) {
            Write-Host "   - $perm" -ForegroundColor Red
        }
    }
} else {
    Write-Host "❌ AndroidManifest.xml 文件不存在" -ForegroundColor Red
}

Write-Host ""

# 4. 检查 Gradle 构建状态
Write-Host "[4/5] 检查 Gradle 构建状态..." -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Write-Host "✅ Gradle 缓存目录存在" -ForegroundColor Green
} else {
    Write-Host "⚠️  Gradle 缓存目录不存在（可能需要首次构建）" -ForegroundColor Yellow
}

Write-Host ""

# 5. 检查关键代码文件
Write-Host "[5/5] 检查关键代码文件..." -ForegroundColor Yellow
$criticalFiles = @(
    "app\src\main\java\com\example\myapplication\presentation\home\HomeViewModel.kt",
    "app\src\main\java\com\example\myapplication\presentation\home\HomeScreen.kt",
    "app\src\main\java\com\example\myapplication\map\MapViewComposable.kt"
)

$missingFiles = @()
foreach ($file in $criticalFiles) {
    if (-not (Test-Path $file)) {
        $missingFiles += $file
    }
}

if ($missingFiles.Count -eq 0) {
    Write-Host "✅ 所有关键代码文件存在" -ForegroundColor Green
} else {
    Write-Host "❌ 缺少以下关键文件：" -ForegroundColor Red
    foreach ($file in $missingFiles) {
        Write-Host "   - $file" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   诊断完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 提供下一步建议
Write-Host "📋 下一步操作建议：" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. 清理并重新构建项目：" -ForegroundColor White
Write-Host "   .\gradlew clean" -ForegroundColor Gray
Write-Host "   .\gradlew assembleDebug" -ForegroundColor Gray
Write-Host ""
Write-Host "2. 在 Android Studio 中运行应用，观察 Logcat 日志" -ForegroundColor White
Write-Host "   搜索关键词：'定位'、'HomeViewModel'、'Permission'" -ForegroundColor Gray
Write-Host ""
Write-Host "3. 检查手机设置：" -ForegroundColor White
Write-Host "   - 确认已授予定位权限" -ForegroundColor Gray
Write-Host "   - 确认 GPS 已开启" -ForegroundColor Gray
Write-Host "   - 确认网络连接正常" -ForegroundColor Gray
Write-Host ""
Write-Host "4. 查看详细排查指南：" -ForegroundColor White
Write-Host "   location_troubleshooting.md" -ForegroundColor Gray
Write-Host ""
