# 上传项目到远程仓库 - 416last2.0版
Write-Host "=== 开始上传项目 ===" -ForegroundColor Green

# 切换到项目目录
Set-Location $PSScriptRoot

Write-Host "`n1. 检查当前状态..." -ForegroundColor Cyan
git status

Write-Host "`n2. 添加所有更改..." -ForegroundColor Cyan
git add .

Write-Host "`n3. 提交更改..." -ForegroundColor Cyan
$commitMessage = "416last2.0版 - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
git commit -m $commitMessage

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ 提交成功" -ForegroundColor Green
} else {
    Write-Host "`n✗ 没有需要提交的更改或提交失败" -ForegroundColor Yellow
}

Write-Host "`n4. 推送到远程仓库..." -ForegroundColor Cyan
git push origin master

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ 推送成功！" -ForegroundColor Green
    Write-Host "项目名称: 416last2.0版" -ForegroundColor Green
    Write-Host "提交时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Green
} else {
    Write-Host "`n✗ 推送失败" -ForegroundColor Red
    Write-Host "`n可能的原因:" -ForegroundColor Yellow
    Write-Host "1. 网络连接问题" -ForegroundColor Yellow
    Write-Host "2. 需要身份验证" -ForegroundColor Yellow
    Write-Host "3. 远程仓库有冲突" -ForegroundColor Yellow
    Write-Host "`n建议手动执行: git push origin master" -ForegroundColor Yellow
}

Write-Host "`n=== 操作完成 ===" -ForegroundColor Green
pause
