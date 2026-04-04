# 网络连通性测试脚本
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "  网络连通性测试" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# 后端服务器地址
$backendServer = "10.237.36.80"
$backendPort = "8080"

# 测试 1: Ping 后端服务器
Write-Host "[测试 1] Ping 后端服务器 $backendServer" -ForegroundColor Yellow
try {
    $pingResult = Test-Connection -ComputerName $backendServer -Count 2 -Quiet
    if ($pingResult) {
        Write-Host "✓ Ping 成功 - 可以访问 $backendServer" -ForegroundColor Green
    } else {
        Write-Host "✗ Ping 失败 - 无法访问 $backendServer" -ForegroundColor Red
        Write-Host ""
        Write-Host "可能的问题:" -ForegroundColor Red
        Write-Host "1. 后端服务器未启动或不在同一网络" -ForegroundColor Yellow
        Write-Host "2. 防火墙阻止了 ICMP 请求" -ForegroundColor Yellow
        Write-Host "3. 需要配置路由才能访问 10.237.x.x 网段" -ForegroundColor Yellow
    }
} catch {
    Write-Host "✗ Ping 测试出错：$_" -ForegroundColor Red
}

Write-Host ""

# 测试 2: 测试 HTTP API 端口
Write-Host "[测试 2] 测试 HTTP API 端口 ${backendServer}:${backendPort}" -ForegroundColor Yellow
try {
    $tcpTest = New-Object System.Net.Sockets.TcpClient
    $timeout = 3000
    $async = $tcpTest.BeginConnect($backendServer, $backendPort, $null, $null)
    $success = $async.AsyncWaitHandle.WaitOne($timeout, $false)
    $tcpTest.EndConnect($async)
    
    if ($success) {
        Write-Host "✓ 端口 $backendPort 可访问 - 后端服务可能正在运行" -ForegroundColor Green
    } else {
        Write-Host "✗ 端口 $backendPort 无法访问 - 连接超时" -ForegroundColor Red
        Write-Host ""
        Write-Host "可能的问题:" -ForegroundColor Red
        Write-Host "1. 后端服务未启动" -ForegroundColor Yellow
        Write-Host "2. 端口被防火墙阻止" -ForegroundColor Yellow
        Write-Host "3. 网络不可达" -ForegroundColor Yellow
    }
    $tcpTest.Close()
} catch {
    Write-Host "✗ TCP 连接测试出错：$_" -ForegroundColor Red
}

Write-Host ""

# 测试 3: 测试外网连接 (Gradle 依赖下载)
Write-Host "[测试 3] 测试外网连接 (Gradle 依赖下载)" -ForegroundColor Yellow
$testUrls = @(
    "https://mirrors.cloud.tencent.com",
    "https://maven.aliyun.com",
    "https://dl.google.com"
)

foreach ($url in $testUrls) {
    try {
        $response = Invoke-WebRequest -Uri $url -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "✓ $url - 可访问 (${response.StatusCode})" -ForegroundColor Green
        }
    } catch {
        Write-Host "✗ $url - 无法访问" -ForegroundColor Red
    }
}

Write-Host ""

# 显示本地网络配置
Write-Host "[网络配置] 本地 IP 地址" -ForegroundColor Yellow
$ipConfig = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.InterfaceAlias -notlike "*Loopback*"}
foreach ($ip in $ipConfig) {
    Write-Host "  $($ip.InterfaceAlias): $($ip.IPAddress)" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "  测试完成" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# 根据测试结果给出建议
Write-Host "建议:" -ForegroundColor White
Write-Host ""

if (-not (Test-Connection -ComputerName $backendServer -Count 1 -Quiet)) {
    Write-Host "1. 【重要】无法 Ping 通后端服务器 $backendServer" -ForegroundColor Red
    Write-Host "   - 请确认后端服务器是否正常运行" -ForegroundColor Yellow
    Write-Host "   - 请确认您的电脑是否能访问 10.237.x.x 网段" -ForegroundColor Yellow
    Write-Host "   - 可能需要配置 VPN 或网络路由" -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "2. 如果 Gradle 构建仍然卡顿，尝试以下方案：" -ForegroundColor Yellow
Write-Host "   a) 清除 Gradle 缓存后重新构建：" -ForegroundColor Cyan
Write-Host "      .\gradlew clean --refresh-dependencies" -ForegroundColor White
Write-Host ""
Write-Host "   b) 检查是否需要启用代理（如果使用梯子）：" -ForegroundColor Cyan
Write-Host "      取消注释 gradle.properties 中的代理配置" -ForegroundColor White
Write-Host ""
Write-Host "   c) 确认腾讯云/阿里云镜像源是否可用：" -ForegroundColor Cyan
Write-Host "      已在 settings.gradle.kts 中配置" -ForegroundColor White
