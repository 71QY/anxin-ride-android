# 网络连通性测试脚本
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  前后端网络连通性测试" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

$backendIp = "10.237.36.80"
$backendPort = 8080

Write-Host "后端服务器：$backendIp`:$backendPort" -ForegroundColor Yellow
Write-Host ""

# 测试 1: Ping 测试
Write-Host "[测试 1] Ping 后端服务器..." -ForegroundColor Green
try {
    $pingResult = Test-Connection -ComputerName $backendIp -Count 2 -Quiet
    if ($pingResult) {
        Write-Host "✅ PING 成功 - 可以访问 $backendIp" -ForegroundColor Green
    } else {
        Write-Host "❌ PING 失败 - 无法访问 $backendIp" -ForegroundColor Red
        Write-Host "   请检查:" -ForegroundColor Yellow
        Write-Host "   1. 后端服务器是否开机" -ForegroundColor Yellow
        Write-Host "   2. 两台电脑是否在同一 WiFi/局域网" -ForegroundColor Yellow
        Write-Host "   3. 防火墙设置" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ PING 异常 - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 2: 端口测试
Write-Host "[测试 2] 测试 8080 端口..." -ForegroundColor Green
try {
    $tcpClient = New-Object System.Net.Sockets.TcpClient
    $timeout = $tcpClient.BeginConnect($backendIp, $backendPort, $null, $null)
    $success = $timeout.AsyncWaitHandle.WaitOne(3000)
    
    if ($success) {
        Write-Host "✅ 端口 $backendPort 开放 - 后端服务可能正在运行" -ForegroundColor Green
    } else {
        Write-Host "❌ 端口 $backendPort 无法连接" -ForegroundColor Red
        Write-Host "   请检查:" -ForegroundColor Yellow
        Write-Host "   1. 后端服务是否启动（访问 http://localhost:8080/api/）" -ForegroundColor Yellow
        Write-Host "   2. Windows 防火墙是否允许 8080 端口" -ForegroundColor Yellow
        Write-Host "   3. 后端服务监听的 IP 地址（应该是 0.0.0.0 或 $backendIp）" -ForegroundColor Yellow
    }
    $tcpClient.Close()
} catch {
    Write-Host "❌ 端口测试异常 - $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 测试 3: HTTP API 测试
Write-Host "[测试 3] 测试 HTTP API..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri "http://$backendIp`:$backendPort/api/" -UseBasicParsing -TimeoutSec 5
    Write-Host "✅ API 响应成功 - 状态码：$($response.StatusCode)" -ForegroundColor Green
    Write-Host "   响应内容预览：" -ForegroundColor Cyan
    Write-Host "   $($response.Content.Substring(0, [Math]::Min(100, $response.Content.Length)))..." -ForegroundColor Gray
} catch {
    Write-Host "❌ API 请求失败 - $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "   HTTP 状态码：$($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
    }
}
Write-Host ""

# 测试 4: ADB 设备检查
Write-Host "[测试 4] 检查 ADB 设备连接..." -ForegroundColor Green
try {
    $adbOutput = adb devices 2>&1 | Out-String
    if ($adbOutput -match "device") {
        Write-Host "✅ ADB 设备已连接" -ForegroundColor Green
        Write-Host "   设备列表:" -ForegroundColor Cyan
        Write-Host "   $adbOutput" -ForegroundColor Gray
        
        # 尝试在手机上 ping 后端
        Write-Host "`n   [子测试] 从手机 ping 后端..." -ForegroundColor Cyan
        $phonePing = adb shell "ping -c 2 $backendIp" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "   ✅ 手机可以访问后端服务器" -ForegroundColor Green
        } else {
            Write-Host "   ❌ 手机无法访问后端服务器" -ForegroundColor Red
            Write-Host "   建议执行：adb reverse tcp:8080 tcp:8080" -ForegroundColor Yellow
        }
    } else {
        Write-Host "⚠️  未检测到 ADB 设备 - 请检查 USB 连接和开发者选项" -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠️  ADB 命令执行失败 - 请确保已安装 ADB 工具" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  测试完成" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
