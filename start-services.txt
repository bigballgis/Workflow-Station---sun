# =====================================================
# 启动基础设施服务脚本（仅 Redis）
# =====================================================

$ErrorActionPreference = "Stop"

$BASE_DIR = $PSScriptRoot
$networkName = "platform-network"

# Set environment variables
$env:REDIS_PASSWORD = "redis123"

Write-Host "📦 启动基础设施服务（Redis）..." -ForegroundColor Cyan
Write-Host ""

# Check if Docker is running
$dockerRunning = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 错误: Docker 未运行，请先启动 Docker Desktop" -ForegroundColor Red
    exit 1
}

# Function to create network if it doesn't exist
function Ensure-Network {
    $networkExists = docker network ls --filter "name=$networkName" --format "{{.Name}}"
    if (-not $networkExists) {
        Write-Host "创建 Docker 网络: $networkName..." -ForegroundColor Yellow
        docker network create $networkName
        if ($LASTEXITCODE -ne 0) {
            Write-Host "❌ 错误: 创建网络失败" -ForegroundColor Red
            exit 1
        }
        Write-Host "✅ 网络 $networkName 创建成功" -ForegroundColor Green
    } else {
        Write-Host "✅ 网络 $networkName 已存在" -ForegroundColor Green
    }
}

# Function to check if container exists
function Container-Exists {
    param([string]$ContainerName)
    $exists = docker ps -a --filter "name=$ContainerName" --format "{{.Names}}"
    return ($exists -eq $ContainerName)
}

# Function to remove container if exists
function Remove-Container {
    param([string]$ContainerName)
    if (Container-Exists $ContainerName) {
        Write-Host "移除已存在的容器: $ContainerName..." -ForegroundColor Yellow
        docker rm -f $ContainerName | Out-Null
    }
}

# Function to wait for service to be healthy
function Wait-ForService {
    param(
        [string]$ContainerName,
        [string]$CheckCommand,
        [int]$MaxRetries = 30,
        [int]$RetryInterval = 2
    )
    
    Write-Host "等待 $ContainerName 就绪..." -ForegroundColor Gray
    $retryCount = 0
    while ($retryCount -lt $MaxRetries) {
        $result = docker exec $ContainerName $CheckCommand 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ $ContainerName 已就绪" -ForegroundColor Green
            return $true
        }
        $retryCount++
        Write-Host "等待 $ContainerName... ($retryCount/$MaxRetries)" -ForegroundColor Gray
        Start-Sleep -Seconds $RetryInterval
    }
    Write-Host "❌ 错误: $ContainerName 启动失败" -ForegroundColor Red
    return $false
}

# Create network
Ensure-Network

# Start Redis
Write-Host ""
Write-Host "启动 Redis..." -ForegroundColor Yellow

Remove-Container "platform-redis"

Write-Host "启动 Redis 容器 (端口 6379)..." -ForegroundColor Yellow
docker run -d `
    --name platform-redis `
    --network $networkName `
    -p 6379:6379 `
    -v redis_data:/data `
    --restart unless-stopped `
    redis:7.2-alpine redis-server --appendonly yes --requirepass $env:REDIS_PASSWORD

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 错误: 启动 Redis 失败" -ForegroundColor Red
    exit 1
}

# Wait for Redis to be ready
Wait-ForService "platform-redis" "redis-cli -a $env:REDIS_PASSWORD ping"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ Redis 服务已启动！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "服务信息：" -ForegroundColor Cyan
Write-Host "  - Redis:        localhost:6379" -ForegroundColor White
Write-Host "  - 密码:         $env:REDIS_PASSWORD" -ForegroundColor White
Write-Host ""
Write-Host "查看日志：" -ForegroundColor Cyan
Write-Host "  docker logs -f platform-redis" -ForegroundColor Gray
Write-Host ""
Write-Host "测试连接：" -ForegroundColor Cyan
Write-Host "  docker exec -it platform-redis redis-cli -a $env:REDIS_PASSWORD ping" -ForegroundColor Gray
Write-Host ""
Write-Host "停止服务：" -ForegroundColor Cyan
Write-Host "  docker stop platform-redis" -ForegroundColor Gray
Write-Host "  docker rm platform-redis" -ForegroundColor Gray
Write-Host ""
