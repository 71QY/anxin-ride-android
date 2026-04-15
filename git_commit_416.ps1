# Git 提交脚本 - 416最终版

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Git 提交 - 416最终版" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 切换到项目目录
Set-Location "D:\Android_items\MyApplication"

# 查看当前状态
Write-Host "1. 检查 Git 状态..." -ForegroundColor Yellow
git status

Write-Host ""
Write-Host "2. 添加所有修改的文件..." -ForegroundColor Yellow
git add .

Write-Host ""
Write-Host "3. 提交代码..." -ForegroundColor Yellow
git commit -m "416最终版"

Write-Host ""
Write-Host "4. 推送到远程仓库..." -ForegroundColor Yellow
git push

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ 提交完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "按任意键退出..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
