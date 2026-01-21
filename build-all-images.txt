# Workflow Platform - 批量构建 Docker 镜像脚本 (PowerShell)
# 用法: .\build-all-images.ps1 [version] [registry]

param(
    [string]$Version = "latest",
    [string]$Registry = "workflow-platform"
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Workflow Platform - Docker 镜像构建" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "版本: $Version" -ForegroundColor Yellow
Write-Host "仓库前缀: $Registry" -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Docker 是否运行
try {
    docker info | Out-Null
} catch {
    Write-Host "❌ 错误: Docker 未运行，请先启动 Docker" -ForegroundColor Red
    exit 1
}

# 启用 BuildKit
$env:DOCKER_BUILDKIT = "1"

# 构建后端服务
Write-Host "📦 开始构建后端服务..." -ForegroundColor Green
Write-Host ""

$backendServices = @(
    @{Name="API Gateway"; Path="./backend/api-gateway"; Tag="api-gateway"},
    @{Name="Workflow Engine"; Path="./backend/workflow-engine-core"; Tag="workflow-engine"},
    @{Name="Admin Center"; Path="./backend/admin-center"; Tag="admin-center"},
    @{Name="Developer Workstation"; Path="./backend/developer-workstation"; Tag="developer-workstation"},
    @{Name="User Portal"; Path="./backend/user-portal"; Tag="user-portal"}
)

$index = 1
foreach ($service in $backendServices) {
    Write-Host "  [$index/5] 构建 $($service.Name)..." -ForegroundColor Yellow
    $imageTag = "$Registry/$($service.Tag):$Version"
    
    try {
        docker build -t $imageTag $service.Path
        if ($LASTEXITCODE -ne 0) {
            throw "构建失败"
        }
    } catch {
        Write-Host "❌ $($service.Name) 构建失败" -ForegroundColor Red
        exit 1
    }
    $index++
}

Write-Host ""
Write-Host "✅ 所有后端服务构建完成！" -ForegroundColor Green
Write-Host ""

# 构建前端服务
Write-Host "📦 开始构建前端服务..." -ForegroundColor Green
Write-Host ""

$frontendServices = @(
    @{Name="Admin Center Frontend"; Path="./frontend/admin-center"; Tag="frontend-admin"},
    @{Name="Developer Workstation Frontend"; Path="./frontend/developer-workstation"; Tag="frontend-developer"},
    @{Name="User Portal Frontend"; Path="./frontend/user-portal"; Tag="frontend-portal"}
)

$index = 1
foreach ($service in $frontendServices) {
    Write-Host "  [$index/3] 构建 $($service.Name)..." -ForegroundColor Yellow
    $imageTag = "$Registry/$($service.Tag):$Version"
    
    try {
        docker build -t $imageTag $service.Path
        if ($LASTEXITCODE -ne 0) {
            throw "构建失败"
        }
    } catch {
        Write-Host "❌ $($service.Name) 构建失败" -ForegroundColor Red
        exit 1
    }
    $index++
}

Write-Host ""
Write-Host "✅ 所有前端服务构建完成！" -ForegroundColor Green
Write-Host ""

# 显示构建结果
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "✅ 所有镜像构建成功！" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "构建的镜像列表:" -ForegroundColor Yellow
docker images | Select-String $Registry | Select-String $Version

Write-Host ""
Write-Host "镜像统计:" -ForegroundColor Yellow
Write-Host "  后端服务: 5 个"
Write-Host "  前端服务: 3 个"
Write-Host "  总计: 8 个镜像"
Write-Host ""

Write-Host "下一步操作:" -ForegroundColor Cyan
Write-Host "  1. 查看镜像: docker images | Select-String $Registry"
Write-Host "  2. 测试镜像: docker-compose up -d"
Write-Host "  3. 推送到仓库: docker push $Registry/<service>:$Version"
Write-Host ""
