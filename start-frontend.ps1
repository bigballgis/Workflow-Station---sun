# =====================================================
# 启动前端服务脚本（Windows PowerShell）
# =====================================================

$ErrorActionPreference = "Stop"

$BASE_DIR = $PSScriptRoot
$LOG_DIR = Join-Path $BASE_DIR "logs"

# 创建日志目录
if (-not (Test-Path $LOG_DIR)) {
    New-Item -ItemType Directory -Path $LOG_DIR | Out-Null
}

Write-Host "🎨 启动前端服务..." -ForegroundColor Cyan
Write-Host ""

# 检查 Node.js
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "❌ 错误: 未找到 Node.js，请先安装 Node.js 20+" -ForegroundColor Red
    exit 1
}

$nodeVersion = (node -v) -replace 'v', '' -split '\.' | Select-Object -First 1
if ([int]$nodeVersion -lt 20) {
    Write-Host "⚠️  警告: Node.js 版本过低，建议使用 Node.js 20+" -ForegroundColor Yellow
}

# 启动 Frontend Admin
Write-Host "1️⃣  启动 Frontend Admin (端口 3000)..." -ForegroundColor Yellow
$frontendAdminDir = Join-Path $BASE_DIR "frontend\admin-center"

if (-not (Test-Path (Join-Path $frontendAdminDir "node_modules"))) {
    Write-Host "   安装依赖..." -ForegroundColor Gray
    Set-Location $frontendAdminDir
    npm install
    Set-Location $BASE_DIR
}

$frontendAdminLog = Join-Path $LOG_DIR "frontend-admin.log"
$frontendAdminProcess = Start-Process -FilePath "npm" -ArgumentList "run", "dev" -WorkingDirectory $frontendAdminDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $frontendAdminLog -RedirectStandardError $frontendAdminLog
$frontendAdminPID = $frontendAdminProcess.Id
Write-Host "   PID: $frontendAdminPID" -ForegroundColor Gray
$frontendAdminPID | Out-File -FilePath (Join-Path $LOG_DIR "frontend-admin.pid") -NoNewline
Start-Sleep -Seconds 3

# 启动 Frontend Portal
Write-Host "2️⃣  启动 Frontend Portal (端口 3001)..." -ForegroundColor Yellow
$frontendPortalDir = Join-Path $BASE_DIR "frontend\user-portal"

if (-not (Test-Path (Join-Path $frontendPortalDir "node_modules"))) {
    Write-Host "   安装依赖..." -ForegroundColor Gray
    Set-Location $frontendPortalDir
    npm install
    Set-Location $BASE_DIR
}

$frontendPortalLog = Join-Path $LOG_DIR "frontend-portal.log"
$frontendPortalProcess = Start-Process -FilePath "npm" -ArgumentList "run", "dev" -WorkingDirectory $frontendPortalDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $frontendPortalLog -RedirectStandardError $frontendPortalLog
$frontendPortalPID = $frontendPortalProcess.Id
Write-Host "   PID: $frontendPortalPID" -ForegroundColor Gray
$frontendPortalPID | Out-File -FilePath (Join-Path $LOG_DIR "frontend-portal.pid") -NoNewline
Start-Sleep -Seconds 3

# 启动 Frontend Developer
Write-Host "3️⃣  启动 Frontend Developer (端口 3002)..." -ForegroundColor Yellow
$frontendDeveloperDir = Join-Path $BASE_DIR "frontend\developer-workstation"

if (-not (Test-Path (Join-Path $frontendDeveloperDir "node_modules"))) {
    Write-Host "   安装依赖..." -ForegroundColor Gray
    Set-Location $frontendDeveloperDir
    npm install
    Set-Location $BASE_DIR
}

$frontendDeveloperLog = Join-Path $LOG_DIR "frontend-developer.log"
$frontendDeveloperProcess = Start-Process -FilePath "npm" -ArgumentList "run", "dev" -WorkingDirectory $frontendDeveloperDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $frontendDeveloperLog -RedirectStandardError $frontendDeveloperLog
$frontendDeveloperPID = $frontendDeveloperProcess.Id
Write-Host "   PID: $frontendDeveloperPID" -ForegroundColor Gray
$frontendDeveloperPID | Out-File -FilePath (Join-Path $LOG_DIR "frontend-developer.pid") -NoNewline

Write-Host ""
Write-Host "✅ 所有前端服务已启动！" -ForegroundColor Green
Write-Host ""
Write-Host "服务访问地址：" -ForegroundColor Cyan
Write-Host "- Frontend Admin: http://localhost:3000" -ForegroundColor White
Write-Host "- Frontend Portal: http://localhost:3001" -ForegroundColor White
Write-Host "- Frontend Developer: http://localhost:3002" -ForegroundColor White
Write-Host ""
Write-Host "查看日志：" -ForegroundColor Cyan
Write-Host "  Get-Content $LOG_DIR\frontend-*.log -Tail 50 -Wait" -ForegroundColor Gray
Write-Host ""
Write-Host "停止服务：" -ForegroundColor Cyan
Write-Host "  .\stop-frontend.ps1" -ForegroundColor Gray
Write-Host ""
