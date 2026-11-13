# 把项目打包成「发给组员」的两个文件：dist\PlanTogether-src.zip 和 dist\app-debug.apk
# 用法：powershell -ExecutionPolicy Bypass -File scripts\package-for-team.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "dist"
$stage = Join-Path $env:TEMP "plantogether-src-stage"

Write-Host "项目目录：$root"

# 1. 准备干净的空目录
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
New-Item -ItemType Directory -Path $dist -Force | Out-Null

# 2. 只复制源码（排除构建产物、本地工具、本地数据库、IDE 配置）
$excludeDirs = @(
    (Join-Path $root ".tools"),
    (Join-Path $root "dist"),
    (Join-Path $root ".git"),
    (Join-Path $root ".idea"),
    (Join-Path $root "backend\data"),
    (Join-Path $root "backend\build"),
    (Join-Path $root "backend\.gradle"),
    (Join-Path $root "backend\.kotlin"),
    (Join-Path $root "android\build"),
    (Join-Path $root "android\.gradle"),
    (Join-Path $root "android\.kotlin"),
    (Join-Path $root "android\.idea"),
    (Join-Path $root "android\app\build")
)
$excludeFiles = @("local.properties", "*.log", "*.hprof")

$robocopyArgs = @($root, $stage, "/E", "/NFL", "/NDL", "/NJH", "/NJS", "/NP")
if ($excludeDirs.Count -gt 0) { $robocopyArgs += "/XD"; $robocopyArgs += $excludeDirs }
if ($excludeFiles.Count -gt 0) { $robocopyArgs += "/XF"; $robocopyArgs += $excludeFiles }

& robocopy @robocopyArgs | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy 失败，exit=$LASTEXITCODE" }

# 3. 压缩
$zip = Join-Path $dist "PlanTogether-src.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zip -CompressionLevel Optimal
Remove-Item -Recurse -Force $stage

# 4. 复制 APK（如果已经构建过）
$apk = Join-Path $root "android\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Copy-Item $apk (Join-Path $dist "app-debug.apk") -Force
} else {
    Write-Host "未找到 APK，先构建：gradlew -p android assembleDebug" -ForegroundColor Yellow
}

Get-ChildItem $dist | ForEach-Object {
    "{0}  {1:N1} MB" -f $_.Name, ($_.Length / 1MB)
}
Write-Host "完成：$dist" -ForegroundColor Green
