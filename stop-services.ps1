# =====================================================
# 停止项目前后端服务脚本（Windows PowerShell）
# =====================================================

$BASE_DIR = $PSScriptRoot

Write-Host "🛑 停止项目服务..." -ForegroundColor Yellow
Write-Host ""

Write-Host "🔧 停止选项：" -ForegroundColor Cyan
Write-Host "1. 停止 Docker Compose 服务" -ForegroundColor White
Write-Host "2. 停止本地开发模式服务" -ForegroundColor White
Write-Host "3. 停止所有服务（Docker + 本地）" -ForegroundColor White
Write-Host ""
$choice = Read-Host "请选择停止方式 (1/2/3)"

switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "🐳 停止 Docker Compose 服务..." -ForegroundColor Yellow
        docker-compose --profile full down
        Write-Host "✅ Docker Compose 服务已停止" -ForegroundColor Green
    }
    "2" {
        Write-Host ""
        Write-Host "💻 停止本地开发模式服务..." -ForegroundColor Yellow
        
        # 停止后端服务
        if (Test-Path "$BASE_DIR\stop-backend.ps1") {
            & "$BASE_DIR\stop-backend.ps1"
        }
        
        # 停止前端服务
        if (Test-Path "$BASE_DIR\stop-frontend.ps1") {
            & "$BASE_DIR\stop-frontend.ps1"
        }
        
        Write-Host "✅ 本地开发模式服务已停止" -ForegroundColor Green
    }
    "3" {
        Write-Host ""
        Write-Host "🛑 停止所有服务..." -ForegroundColor Yellow
        
        # 停止 Docker Compose 服务
        Write-Host "停止 Docker Compose 服务..." -ForegroundColor Gray
        docker-compose --profile full down 2>$null
        
        # 停止本地后端服务
        Write-Host "停止本地后端服务..." -ForegroundColor Gray
        if (Test-Path "$BASE_DIR\stop-backend.ps1") {
            & "$BASE_DIR\stop-backend.ps1"
        }
        
        # 停止本地前端服务
        Write-Host "停止本地前端服务..." -ForegroundColor Gray
        if (Test-Path "$BASE_DIR\stop-frontend.ps1") {
            & "$BASE_DIR\stop-frontend.ps1"
        }
        
        Write-Host ""
        Write-Host "✅ 所有服务已停止" -ForegroundColor Green
    }
    default {
        Write-Host "❌ 无效选择" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
