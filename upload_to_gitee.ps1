# 上传项目到 Gitee 仓库
Write-Host "=== 开始上传项目到 Gitee ===" -ForegroundColor Green

# 切换到项目目录
Set-Location $PSScriptRoot

Write-Host "`n1. 检查 Git 状态..." -ForegroundColor Cyan
git status

Write-Host "`n2. 添加所有更改..." -ForegroundColor Cyan
git add .

Write-Host "`n3. 提交更改..." -ForegroundColor Cyan
$commitMessage = "更新项目 - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
git commit -m $commitMessage

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ 提交成功" -ForegroundColor Green
} else {
    Write-Host "`n✗ 没有需要提交的更改或提交失败" -ForegroundColor Yellow
}

Write-Host "`n4. 检查远程仓库配置..." -ForegroundColor Cyan
git remote -v

Write-Host "`n5. 推送到 travel3 仓库..." -ForegroundColor Cyan
git push gitee master

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ 推送成功！" -ForegroundColor Green
    Write-Host "仓库地址: https://gitee.com/namee11/travel3.git" -ForegroundColor Green
    Write-Host "提交时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Green
} else {
    Write-Host "`n✗ 推送失败" -ForegroundColor Red
    Write-Host "`n可能的原因:" -ForegroundColor Yellow
    Write-Host "1. 网络连接问题" -ForegroundColor Yellow
    Write-Host "2. 需要身份验证（请使用 Gitee 账号密码或令牌）" -ForegroundColor Yellow
    Write-Host "3. 远程仓库有冲突" -ForegroundColor Yellow
    Write-Host "`n建议:" -ForegroundColor Yellow
    Write-Host "- 检查网络连接" -ForegroundColor Yellow
    Write-Host "- 确认 Gitee 账号权限" -ForegroundColor Yellow
    Write-Host "- 手动执行: git push gitee master" -ForegroundColor Yellow
}

Write-Host "`n=== 操作完成 ===" -ForegroundColor Green
pause
