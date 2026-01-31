# =====================================================
# 启动基础设施服务脚本（本地 PostgreSQL + Redis）
# =====================================================

$ErrorActionPreference = "Stop"

$BASE_DIR = $PSScriptRoot

Write-Host "📦 检查基础设施服务状态..." -ForegroundColor Cyan
Write-Host ""

# Function to check if port is listening
function Test-Port {
    param([int]$Port)
    $connection = Test-NetConnection -ComputerName localhost -Port $Port -WarningAction SilentlyContinue
    return $connection.TcpTestSucceeded
}

# Check PostgreSQL
Write-Host "检查 PostgreSQL (端口 5432)..." -ForegroundColor Yellow
if (Test-Port 5432) {
    Write-Host "✅ PostgreSQL 已运行" -ForegroundColor Green
} else {
    Write-Host "❌ PostgreSQL 未运行" -ForegroundColor Red
    Write-Host "   请确保 PostgreSQL 已安装并启动" -ForegroundColor Yellow
    Write-Host "   macOS: brew services start postgresql" -ForegroundColor Gray
    Write-Host "   Windows: 启动 PostgreSQL 服务" -ForegroundColor Gray
    exit 1
}

# Check Redis
Write-Host "检查 Redis (端口 6379)..." -ForegroundColor Yellow
if (Test-Port 6379) {
    Write-Host "✅ Redis 已运行" -ForegroundColor Green
} else {
    Write-Host "❌ Redis 未运行" -ForegroundColor Red
    Write-Host "   请确保 Redis 已安装并启动" -ForegroundColor Yellow
    Write-Host "   macOS: brew services start redis" -ForegroundColor Gray
    Write-Host "   Windows: 启动 Redis 服务" -ForegroundColor Gray
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ 基础设施服务检查完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "服务信息：" -ForegroundColor Cyan
Write-Host "  - PostgreSQL:   localhost:5432" -ForegroundColor White
Write-Host "  - Redis:        localhost:6379" -ForegroundColor White
Write-Host ""
Write-Host "数据库连接信息：" -ForegroundColor Cyan
Write-Host "  - 数据库名:     workflow_platform" -ForegroundColor White
Write-Host "  - 用户名:       platform" -ForegroundColor White
Write-Host "  - 密码:         platform123" -ForegroundColor White
Write-Host ""
Write-Host "如需启动本地服务：" -ForegroundColor Cyan
Write-Host "  macOS:" -ForegroundColor Gray
Write-Host "    brew services start postgresql" -ForegroundColor Gray
Write-Host "    brew services start redis" -ForegroundColor Gray
Write-Host ""
Write-Host "  Windows:" -ForegroundColor Gray
Write-Host "    启动 PostgreSQL 服务" -ForegroundColor Gray
Write-Host "    启动 Redis 服务" -ForegroundColor Gray
Write-Host ""
