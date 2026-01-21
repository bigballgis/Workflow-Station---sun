# =====================================================
# 启动项目前后端服务脚本（Windows PowerShell）
# =====================================================

$ErrorActionPreference = "Stop"

$BASE_DIR = $PSScriptRoot

Write-Host "🚀 开始启动项目服务..." -ForegroundColor Cyan
Write-Host ""

# 检查基础设施服务
Write-Host "📦 检查基础设施服务..." -ForegroundColor Yellow
try {
    $postgresStatus = docker ps --filter "name=platform-postgres" --format "{{.Status}}" 2>$null
    if ($postgresStatus -match "running") {
        Write-Host "✅ 基础设施服务已运行" -ForegroundColor Green
    } else {
        Write-Host "启动基础设施服务..." -ForegroundColor Yellow
        docker-compose up -d postgres redis kafka zookeeper
        Write-Host "等待服务就绪..." -ForegroundColor Gray
        Start-Sleep -Seconds 10
    }
} catch {
    Write-Host "⚠️  警告: 无法检查 Docker 服务状态，请确保 Docker Desktop 正在运行" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🔧 启动选项：" -ForegroundColor Cyan
Write-Host "1. 使用 Docker Compose 启动（推荐，需要先构建镜像）" -ForegroundColor White
Write-Host "2. 使用本地开发模式启动（需要 Java 17+ 和 Node.js 20+）" -ForegroundColor White
Write-Host ""
$choice = Read-Host "请选择启动方式 (1/2)"

switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "🐳 使用 Docker Compose 启动服务..." -ForegroundColor Cyan
        Write-Host "启动后端服务..." -ForegroundColor Yellow
        docker-compose --profile backend up -d
        
        Write-Host "等待后端服务启动..." -ForegroundColor Gray
        Start-Sleep -Seconds 15
        
        Write-Host "启动前端服务..." -ForegroundColor Yellow
        docker-compose --profile frontend up -d
        
        Write-Host ""
        Write-Host "✅ 所有服务已启动！" -ForegroundColor Green
        Write-Host ""
        Write-Host "服务访问地址：" -ForegroundColor Cyan
        Write-Host "- API Gateway: http://localhost:8080" -ForegroundColor White
        Write-Host "- Workflow Engine: http://localhost:8081" -ForegroundColor White
        Write-Host "- Admin Center: http://localhost:8090" -ForegroundColor White
        Write-Host "- User Portal: http://localhost:8082" -ForegroundColor White
        Write-Host "- Developer Workstation: http://localhost:8083" -ForegroundColor White
        Write-Host "- Frontend Admin: http://localhost:3000" -ForegroundColor White
        Write-Host "- Frontend Portal: http://localhost:3001" -ForegroundColor White
        Write-Host "- Frontend Developer: http://localhost:3002" -ForegroundColor White
    }
    "2" {
        Write-Host ""
        Write-Host "💻 使用本地开发模式启动服务..." -ForegroundColor Cyan
        Write-Host ""
        
        # 检查必要的工具
        $hasMaven = Get-Command mvn -ErrorAction SilentlyContinue
        $hasNode = Get-Command node -ErrorAction SilentlyContinue
        
        if (-not $hasMaven) {
            Write-Host "❌ 错误: 未找到 Maven，请先安装 Maven" -ForegroundColor Red
            exit 1
        }
        
        if (-not $hasNode) {
            Write-Host "❌ 错误: 未找到 Node.js，请先安装 Node.js 20+" -ForegroundColor Red
            exit 1
        }
        
        # 启动后端服务
        Write-Host "启动后端服务..." -ForegroundColor Yellow
        & "$BASE_DIR\start-backend.ps1"
        
        Write-Host ""
        Write-Host "等待后端服务启动..." -ForegroundColor Gray
        Start-Sleep -Seconds 10
        
        # 启动前端服务
        Write-Host "启动前端服务..." -ForegroundColor Yellow
        & "$BASE_DIR\start-frontend.ps1"
        
        Write-Host ""
        Write-Host "✅ 所有服务已启动！" -ForegroundColor Green
        Write-Host ""
        Write-Host "服务访问地址：" -ForegroundColor Cyan
        Write-Host "- API Gateway: http://localhost:8080" -ForegroundColor White
        Write-Host "- Workflow Engine: http://localhost:8081" -ForegroundColor White
        Write-Host "- Admin Center: http://localhost:8090" -ForegroundColor White
        Write-Host "- User Portal: http://localhost:8082" -ForegroundColor White
        Write-Host "- Developer Workstation: http://localhost:8083" -ForegroundColor White
        Write-Host "- Frontend Admin: http://localhost:3000" -ForegroundColor White
        Write-Host "- Frontend Portal: http://localhost:3001" -ForegroundColor White
        Write-Host "- Frontend Developer: http://localhost:3002" -ForegroundColor White
        Write-Host ""
        Write-Host "查看日志：" -ForegroundColor Cyan
        Write-Host "  Get-Content logs\*.log -Tail 50 -Wait" -ForegroundColor Gray
        Write-Host ""
        Write-Host "停止服务：" -ForegroundColor Cyan
        Write-Host "  .\stop-backend.ps1" -ForegroundColor Gray
        Write-Host "  .\stop-frontend.ps1" -ForegroundColor Gray
    }
    default {
        Write-Host "❌ 无效选择" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "📊 查看服务状态：" -ForegroundColor Cyan
Write-Host "  docker-compose ps" -ForegroundColor Gray
Write-Host ""
Write-Host "📝 查看服务日志：" -ForegroundColor Cyan
Write-Host "  docker-compose logs -f [service-name]" -ForegroundColor Gray
Write-Host ""
