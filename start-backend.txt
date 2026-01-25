# =====================================================
# 启动后端服务脚本（Docker 模式）
# =====================================================

param(
    [switch]$NoBuild       # Skip build, use existing images
)

$ErrorActionPreference = "Stop"

$BASE_DIR = $PSScriptRoot
$networkName = "platform-network"

# Set environment variables
$env:POSTGRES_PASSWORD = "platform123"
$env:REDIS_PASSWORD = "redis123"
$env:JWT_SECRET = "your-256-bit-secret-key-for-development-only"
$env:ENCRYPTION_SECRET_KEY = "your-32-byte-aes-256-secret-key!!"

Write-Host "🚀 启动后端服务（Docker 模式）..." -ForegroundColor Cyan
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
    }
}

# Function to build Docker image
function Build-Image {
    param(
        [string]$Context,
        [string]$Dockerfile,
        [string]$ImageName
    )
    
    Write-Host "构建镜像: $ImageName..." -ForegroundColor Yellow
    docker build --platform linux/amd64 -f $Dockerfile -t $ImageName $Context
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ 错误: 构建镜像 $ImageName 失败" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ 镜像 $ImageName 构建成功" -ForegroundColor Green
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

# Check if Redis is running (required for backend services)
Write-Host "检查 Redis 服务..." -ForegroundColor Yellow
$redisExists = Container-Exists "platform-redis"
if (-not $redisExists) {
    Write-Host "⚠️  警告: Redis 未运行，请先运行 .\start-services.ps1 启动 Redis" -ForegroundColor Yellow
    Write-Host "或者等待脚本自动启动 Redis..." -ForegroundColor Gray
    
    # Start Redis
    Write-Host "启动 Redis..." -ForegroundColor Yellow
    docker run -d `
        --name platform-redis `
        --network $networkName `
        -p 6379:6379 `
        -v redis_data:/data `
        --restart unless-stopped `
        redis:7.2-alpine redis-server --appendonly yes --requirepass $env:REDIS_PASSWORD
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Redis 已启动" -ForegroundColor Green
        Wait-ForService "platform-redis" "redis-cli -a $env:REDIS_PASSWORD ping"
    } else {
        Write-Host "❌ 错误: 启动 Redis 失败" -ForegroundColor Red
        exit 1
    }
} else {
    $redisRunning = docker ps --filter "name=platform-redis" --format "{{.Names}}"
    if ($redisRunning -eq "platform-redis") {
        Write-Host "✅ Redis 正在运行" -ForegroundColor Green
    } else {
        Write-Host "启动 Redis 容器..." -ForegroundColor Yellow
        docker start platform-redis
        Wait-ForService "platform-redis" "redis-cli -a $env:REDIS_PASSWORD ping"
    }
}

# Step 1: Build platform modules (if needed)
if (-not $NoBuild) {
    Write-Host ""
    Write-Host "步骤 1: 构建 platform 模块..." -ForegroundColor Yellow
    Write-Host "运行 Maven 构建（这可能需要几分钟）..." -ForegroundColor Gray
    mvn clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ 错误: Maven 构建失败" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ Platform 模块构建成功" -ForegroundColor Green
}

# Step 2: Build backend services
Write-Host ""
Write-Host "步骤 2: 构建后端服务..." -ForegroundColor Yellow

if (-not $NoBuild) {
    Write-Host "打包后端 JAR 文件..." -ForegroundColor Gray
    mvn clean package -DskipTests -pl backend/workflow-engine-core,backend/admin-center,backend/user-portal,backend/developer-workstation,backend/api-gateway -am
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ 错误: 后端构建失败" -ForegroundColor Red
        exit 1
    }
}

# Build Docker images
Write-Host "构建后端 Docker 镜像..." -ForegroundColor Yellow

Build-Image "./backend/workflow-engine-core" "./backend/workflow-engine-core/Dockerfile" "workflow-engine:latest"
Build-Image "./backend/admin-center" "./backend/admin-center/Dockerfile" "admin-center:latest"
Build-Image "./backend/user-portal" "./backend/user-portal/Dockerfile" "user-portal:latest"
Build-Image "./backend/developer-workstation" "./backend/developer-workstation/Dockerfile" "developer-workstation:latest"
Build-Image "./backend/api-gateway" "./backend/api-gateway/Dockerfile" "api-gateway:latest"

# Step 3: Start backend services
Write-Host ""
Write-Host "步骤 3: 启动后端服务..." -ForegroundColor Yellow

# Start Workflow Engine
Remove-Container "platform-workflow-engine"
Write-Host "启动 Workflow Engine (端口 8081)..." -ForegroundColor Yellow
docker run -d `
    --name platform-workflow-engine `
    --network $networkName `
    -e SERVER_PORT=8080 `
    -e SPRING_PROFILES_ACTIVE=docker `
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/workflow_platform `
    -e SPRING_DATASOURCE_USERNAME=platform `
    -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
    -e SPRING_REDIS_HOST=redis `
    -e SPRING_REDIS_PORT=6379 `
    -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
    -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092 `
    -e ADMIN_CENTER_URL=http://admin-center:8080 `
    -e JWT_SECRET=$env:JWT_SECRET `
    -e ENCRYPTION_SECRET_KEY=$env:ENCRYPTION_SECRET_KEY `
    -p 8081:8080 `
    --restart unless-stopped `
    workflow-engine:latest

# Start Admin Center
Remove-Container "platform-admin-center"
Write-Host "启动 Admin Center (端口 8090)..." -ForegroundColor Yellow
docker run -d `
    --name platform-admin-center `
    --network $networkName `
    -e SERVER_PORT=8080 `
    -e SPRING_PROFILES_ACTIVE=docker `
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/workflow_platform `
    -e SPRING_DATASOURCE_USERNAME=platform `
    -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
    -e SPRING_REDIS_HOST=redis `
    -e SPRING_REDIS_PORT=6379 `
    -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
    -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092 `
    -e JWT_SECRET=$env:JWT_SECRET `
    -e ENCRYPTION_SECRET_KEY=$env:ENCRYPTION_SECRET_KEY `
    -p 8090:8080 `
    --restart unless-stopped `
    admin-center:latest

# Start User Portal
Remove-Container "platform-user-portal"
Write-Host "启动 User Portal (端口 8082)..." -ForegroundColor Yellow
docker run -d `
    --name platform-user-portal `
    --network $networkName `
    -e SERVER_PORT=8080 `
    -e SPRING_PROFILES_ACTIVE=docker `
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/workflow_platform `
    -e SPRING_DATASOURCE_USERNAME=platform `
    -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
    -e SPRING_REDIS_HOST=redis `
    -e SPRING_REDIS_PORT=6379 `
    -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
    -e ADMIN_CENTER_URL=http://admin-center:8080 `
    -e WORKFLOW_ENGINE_URL=http://workflow-engine:8080 `
    -e JWT_SECRET=$env:JWT_SECRET `
    -e ENCRYPTION_SECRET_KEY=$env:ENCRYPTION_SECRET_KEY `
    -p 8082:8080 `
    --restart unless-stopped `
    user-portal:latest

# Start Developer Workstation
Remove-Container "platform-developer-workstation"
Write-Host "启动 Developer Workstation (端口 8083)..." -ForegroundColor Yellow
docker run -d `
    --name platform-developer-workstation `
    --network $networkName `
    -e SERVER_PORT=8080 `
    -e SPRING_PROFILES_ACTIVE=docker `
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/workflow_platform `
    -e SPRING_DATASOURCE_USERNAME=platform `
    -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
    -e SPRING_REDIS_HOST=redis `
    -e SPRING_REDIS_PORT=6379 `
    -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
    -e ADMIN_CENTER_URL=http://admin-center:8080 `
    -e JWT_SECRET=$env:JWT_SECRET `
    -e ENCRYPTION_SECRET_KEY=$env:ENCRYPTION_SECRET_KEY `
    -p 8083:8080 `
    --restart unless-stopped `
    developer-workstation:latest

# Start API Gateway
Remove-Container "platform-api-gateway"
Write-Host "启动 API Gateway (端口 8080)..." -ForegroundColor Yellow
docker run -d `
    --name platform-api-gateway `
    --network $networkName `
    -e SPRING_PROFILES_ACTIVE=docker `
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/workflow_platform `
    -e SPRING_DATASOURCE_USERNAME=platform `
    -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
    -e SPRING_REDIS_HOST=redis `
    -e SPRING_REDIS_PORT=6379 `
    -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
    -e WORKFLOW_ENGINE_URL=http://workflow-engine:8080 `
    -e ADMIN_CENTER_URL=http://admin-center:8080 `
    -e USER_PORTAL_URL=http://user-portal:8080 `
    -e DEVELOPER_WORKSTATION_URL=http://developer-workstation:8080 `
    -e JWT_SECRET=$env:JWT_SECRET `
    -p 8080:8080 `
    --restart unless-stopped `
    api-gateway:latest

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ 所有后端服务已启动！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "服务访问地址：" -ForegroundColor Cyan
Write-Host "  - API Gateway:        http://localhost:8080" -ForegroundColor White
Write-Host "  - Workflow Engine:    http://localhost:8081" -ForegroundColor White
Write-Host "  - User Portal:        http://localhost:8082" -ForegroundColor White
Write-Host "  - Developer WS:       http://localhost:8083" -ForegroundColor White
Write-Host "  - Admin Center:       http://localhost:8090" -ForegroundColor White
Write-Host ""
Write-Host "查看日志：" -ForegroundColor Cyan
Write-Host "  docker logs -f platform-api-gateway" -ForegroundColor Gray
Write-Host "  docker logs -f platform-workflow-engine" -ForegroundColor Gray
Write-Host "  docker logs -f platform-admin-center" -ForegroundColor Gray
Write-Host ""
Write-Host "停止服务：" -ForegroundColor Cyan
Write-Host "  docker stop platform-api-gateway platform-workflow-engine platform-admin-center platform-user-portal platform-developer-workstation" -ForegroundColor Gray
Write-Host ""
