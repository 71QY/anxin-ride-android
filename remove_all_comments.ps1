$projectRoot = "D:\Android_items\MyApplication\app\src\main\java\com\example\myapplication"
Write-Host "Cleaning all comments and logs..." -ForegroundColor Green
$ktFiles = Get-ChildItem -Path $projectRoot -Filter "*.kt" -Recurse
$totalFiles = $ktFiles.Count
$processedFiles = 0
foreach ($file in $ktFiles) {
    $processedFiles++
    Write-Host "[$processedFiles/$totalFiles] $($_.Name)" -NoNewline
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    $content = $content -replace '(?m)^\s*Log\.(d|e|w|i|v)\(.*?\);?\s*\r?\n', ''
    $content = $content -replace '(?m)^\s*//.*?\r?\n', ''
    $content = $content -replace '\s*//.*?$', ''
    $content = $content -replace '/\*[\s\S]*?\*/', ''
    $content = $content -replace '(\r?\n){3,}', "`r`n`r`n"
    if ($content -ne $originalContent) {
        [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.UTF8Encoding]::new($false))
        Write-Host " - Done" -ForegroundColor Yellow
    } else {
        Write-Host " - Skip" -ForegroundColor Gray
    }
}
Write-Host "`nCompleted! Processed $totalFiles files" -ForegroundColor Green
