# =====================================================
# 启动后端服务脚本（Windows PowerShell - 本地开发模式）
# =====================================================

$ErrorActionPreference = "Stop"

$BASE_DIR = $PSScriptRoot
$LOG_DIR = Join-Path $BASE_DIR "logs"

# 创建日志目录
if (-not (Test-Path $LOG_DIR)) {
    New-Item -ItemType Directory -Path $LOG_DIR | Out-Null
}

Write-Host "🚀 启动后端服务..." -ForegroundColor Cyan
Write-Host ""

# 检查基础设施服务
Write-Host "📦 检查基础设施服务..." -ForegroundColor Yellow
try {
    $postgresStatus = docker ps --filter "name=platform-postgres" --format "{{.Status}}" 2>$null
    if ($postgresStatus -match "healthy") {
        Write-Host "✅ PostgreSQL 已就绪" -ForegroundColor Green
    } else {
        Write-Host "⚠️  警告: PostgreSQL 可能未就绪，请等待..." -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠️  警告: 无法检查 Docker 服务状态" -ForegroundColor Yellow
}

# 检查 Maven
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "❌ 错误: 未找到 Maven，请先安装 Maven" -ForegroundColor Red
    exit 1
}

# 启动 API Gateway
Write-Host "1️⃣  启动 API Gateway (端口 8080)..." -ForegroundColor Yellow
$apiGatewayDir = Join-Path $BASE_DIR "backend\api-gateway"
$apiGatewayLog = Join-Path $LOG_DIR "api-gateway.log"
$apiGatewayProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WorkingDirectory $apiGatewayDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $apiGatewayLog -RedirectStandardError $apiGatewayLog
$apiGatewayPID = $apiGatewayProcess.Id
Write-Host "   PID: $apiGatewayPID" -ForegroundColor Gray
$apiGatewayPID | Out-File -FilePath (Join-Path $LOG_DIR "api-gateway.pid") -NoNewline
Start-Sleep -Seconds 5

# 启动 Workflow Engine
Write-Host "2️⃣  启动 Workflow Engine (端口 8081)..." -ForegroundColor Yellow
$workflowEngineDir = Join-Path $BASE_DIR "backend\workflow-engine-core"
$workflowEngineLog = Join-Path $LOG_DIR "workflow-engine.log"
$workflowEngineProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WorkingDirectory $workflowEngineDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $workflowEngineLog -RedirectStandardError $workflowEngineLog
$workflowEnginePID = $workflowEngineProcess.Id
Write-Host "   PID: $workflowEnginePID" -ForegroundColor Gray
$workflowEnginePID | Out-File -FilePath (Join-Path $LOG_DIR "workflow-engine.pid") -NoNewline
Start-Sleep -Seconds 5

# 启动 Admin Center
Write-Host "3️⃣  启动 Admin Center (端口 8090)..." -ForegroundColor Yellow
$adminCenterDir = Join-Path $BASE_DIR "backend\admin-center"
$adminCenterLog = Join-Path $LOG_DIR "admin-center.log"
$adminCenterProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WorkingDirectory $adminCenterDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $adminCenterLog -RedirectStandardError $adminCenterLog
$adminCenterPID = $adminCenterProcess.Id
Write-Host "   PID: $adminCenterPID" -ForegroundColor Gray
$adminCenterPID | Out-File -FilePath (Join-Path $LOG_DIR "admin-center.pid") -NoNewline
Start-Sleep -Seconds 5

# 启动 Developer Workstation
Write-Host "4️⃣  启动 Developer Workstation (端口 8083)..." -ForegroundColor Yellow
$devWorkstationDir = Join-Path $BASE_DIR "backend\developer-workstation"
$devWorkstationLog = Join-Path $LOG_DIR "developer-workstation.log"
$devWorkstationProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WorkingDirectory $devWorkstationDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $devWorkstationLog -RedirectStandardError $devWorkstationLog
$devWorkstationPID = $devWorkstationProcess.Id
Write-Host "   PID: $devWorkstationPID" -ForegroundColor Gray
$devWorkstationPID | Out-File -FilePath (Join-Path $LOG_DIR "developer-workstation.pid") -NoNewline
Start-Sleep -Seconds 5

# 启动 User Portal
Write-Host "5️⃣  启动 User Portal (端口 8082)..." -ForegroundColor Yellow
$userPortalDir = Join-Path $BASE_DIR "backend\user-portal"
$userPortalLog = Join-Path $LOG_DIR "user-portal.log"
$userPortalProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WorkingDirectory $userPortalDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $userPortalLog -RedirectStandardError $userPortalLog
$userPortalPID = $userPortalProcess.Id
Write-Host "   PID: $userPortalPID" -ForegroundColor Gray
$userPortalPID | Out-File -FilePath (Join-Path $LOG_DIR "user-portal.pid") -NoNewline

Write-Host ""
Write-Host "✅ 所有后端服务已启动！" -ForegroundColor Green
Write-Host ""
Write-Host "服务访问地址：" -ForegroundColor Cyan
Write-Host "- API Gateway: http://localhost:8080" -ForegroundColor White
Write-Host "- Workflow Engine: http://localhost:8081" -ForegroundColor White
Write-Host "- Admin Center: http://localhost:8090" -ForegroundColor White
Write-Host "- User Portal: http://localhost:8082" -ForegroundColor White
Write-Host "- Developer Workstation: http://localhost:8083" -ForegroundColor White
Write-Host ""
Write-Host "查看日志：" -ForegroundColor Cyan
Write-Host "  Get-Content $LOG_DIR\*.log -Tail 50 -Wait" -ForegroundColor Gray
Write-Host ""
Write-Host "停止服务：" -ForegroundColor Cyan
Write-Host "  .\stop-backend.ps1" -ForegroundColor Gray
Write-Host ""
