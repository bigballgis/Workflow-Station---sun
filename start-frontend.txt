# =====================================================
# 启动前端服务脚本（Docker 模式）
# =====================================================

param(
    [switch]$NoBuild       # Skip build, use existing images
)

$ErrorActionPreference = "Stop"

$BASE_DIR = $PSScriptRoot
$networkName = "platform-network"

Write-Host "🎨 启动前端服务（Docker 模式）..." -ForegroundColor Cyan
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

# Create network
Ensure-Network

# Step 1: Build frontend images
if (-not $NoBuild) {
    Write-Host "步骤 1: 构建前端 Docker 镜像..." -ForegroundColor Yellow
    
    $adminCenterContext = Join-Path $BASE_DIR "frontend\admin-center"
    $userPortalContext = Join-Path $BASE_DIR "frontend\user-portal"
    $developerContext = Join-Path $BASE_DIR "frontend\developer-workstation"
    
    Build-Image $adminCenterContext (Join-Path $adminCenterContext "Dockerfile") "frontend-admin:latest"
    Build-Image $userPortalContext (Join-Path $userPortalContext "Dockerfile") "frontend-portal:latest"
    Build-Image $developerContext (Join-Path $developerContext "Dockerfile") "frontend-developer:latest"
} else {
    Write-Host "步骤 1: 跳过构建，使用已有镜像..." -ForegroundColor Yellow
}

# Step 2: Start frontend services
Write-Host ""
Write-Host "步骤 2: 启动前端服务..." -ForegroundColor Yellow

# Start Frontend Admin
Remove-Container "platform-frontend-admin"
Write-Host "启动 Frontend Admin (端口 3000)..." -ForegroundColor Yellow
docker run -d `
    --name platform-frontend-admin `
    --network $networkName `
    -p 3000:80 `
    --restart unless-stopped `
    frontend-admin:latest

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 错误: 启动 Frontend Admin 失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Frontend Admin 已启动" -ForegroundColor Green

# Start Frontend Portal
Remove-Container "platform-frontend-portal"
Write-Host "启动 Frontend Portal (端口 3001)..." -ForegroundColor Yellow
docker run -d `
    --name platform-frontend-portal `
    --network $networkName `
    -p 3001:80 `
    --restart unless-stopped `
    frontend-portal:latest

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 错误: 启动 Frontend Portal 失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Frontend Portal 已启动" -ForegroundColor Green

# Start Frontend Developer
Remove-Container "platform-frontend-developer"
Write-Host "启动 Frontend Developer (端口 3002)..." -ForegroundColor Yellow
docker run -d `
    --name platform-frontend-developer `
    --network $networkName `
    -p 3002:80 `
    --restart unless-stopped `
    frontend-developer:latest

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 错误: 启动 Frontend Developer 失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Frontend Developer 已启动" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ 所有前端服务已启动！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "服务访问地址：" -ForegroundColor Cyan
Write-Host "  - Frontend Admin:    http://localhost:3000" -ForegroundColor White
Write-Host "  - Frontend Portal:   http://localhost:3001" -ForegroundColor White
Write-Host "  - Frontend Developer: http://localhost:3002" -ForegroundColor White
Write-Host ""
Write-Host "查看日志：" -ForegroundColor Cyan
Write-Host "  docker logs -f platform-frontend-admin" -ForegroundColor Gray
Write-Host "  docker logs -f platform-frontend-portal" -ForegroundColor Gray
Write-Host "  docker logs -f platform-frontend-developer" -ForegroundColor Gray
Write-Host ""
Write-Host "停止服务：" -ForegroundColor Cyan
Write-Host "  docker stop platform-frontend-admin platform-frontend-portal platform-frontend-developer" -ForegroundColor Gray
Write-Host "  docker rm platform-frontend-admin platform-frontend-portal platform-frontend-developer" -ForegroundColor Gray
Write-Host ""
