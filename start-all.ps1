# Start All Services Script
# This script builds Docker images and starts all services using docker run

# ========================================
# UTF-8 编码配置（解决中文乱码）
# ========================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'

# Set UTF-8 encoding for Windows only
if ($IsWindows -or $PSVersionTable.PSVersion.Major -lt 6) {
    try {
        chcp 65001 | Out-Null
    } catch {
        # Ignore chcp errors on non-Windows systems
    }
}

param(
    [switch]$Build,        # Force rebuild all images
    [switch]$InfraOnly,    # Only start infrastructure (postgres, redis)
    [switch]$BackendOnly,  # Only start backend services
    [switch]$FrontendOnly, # Only start frontend services
    [switch]$NoBuild       # Skip build, use existing images
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Workflow Platform - Start All Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if Docker is running
$dockerRunning = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# Set environment variables
$env:POSTGRES_PASSWORD = "platform123"
$env:REDIS_PASSWORD = "redis123"
$env:JWT_SECRET_KEY = "workflow-engine-jwt-secret-key-2026"
$env:ENCRYPTION_KEY = "workflow-aes-256-encryption-key!"

# Network name
$networkName = "platform-network"

# Function to create network if it doesn't exist
function Ensure-Network {
    $networkExists = docker network ls --filter "name=$networkName" --format "{{.Name}}"
    if (-not $networkExists) {
        Write-Host "Creating Docker network: $networkName..." -ForegroundColor Yellow
        docker network create $networkName
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Error: Failed to create network" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "Network $networkName already exists" -ForegroundColor Gray
    }
}

# Function to build Docker image
function Build-Image {
    param(
        [string]$Context,
        [string]$Dockerfile,
        [string]$ImageName
    )
    
    Write-Host "Building image: $ImageName..." -ForegroundColor Yellow
    docker build -f $Dockerfile -t $ImageName $Context
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Failed to build image $ImageName" -ForegroundColor Red
        exit 1
    }
    Write-Host "Image $ImageName built successfully" -ForegroundColor Green
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
        Write-Host "Removing existing container: $ContainerName..." -ForegroundColor Yellow
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
    
    Write-Host "Waiting for $ContainerName to be ready..." -ForegroundColor Gray
    $retryCount = 0
    while ($retryCount -lt $MaxRetries) {
        $result = docker exec $ContainerName $CheckCommand 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "$ContainerName is ready!" -ForegroundColor Green
            return $true
        }
        $retryCount++
        Write-Host "Waiting for $ContainerName... ($retryCount/$MaxRetries)" -ForegroundColor Gray
        Start-Sleep -Seconds $RetryInterval
    }
    Write-Host "Error: $ContainerName failed to start" -ForegroundColor Red
    return $false
}

# Create network
Ensure-Network

if ($InfraOnly) {
    Write-Host "Starting infrastructure services only..." -ForegroundColor Yellow
    
    # Start PostgreSQL
    Remove-Container "platform-postgres"
    Write-Host "Starting PostgreSQL..." -ForegroundColor Yellow
    docker run -d `
        --name platform-postgres `
        --network $networkName `
        -e POSTGRES_DB=workflow_platform `
        -e POSTGRES_USER=platform `
        -e POSTGRES_PASSWORD=$env:POSTGRES_PASSWORD `
        -p 5432:5432 `
        -v postgres_data:/var/lib/postgresql/data `
        -v "${PWD}/deploy/init-scripts/00-schema/01-schema.sql:/docker-entrypoint-initdb.d/00-01-schema.sql" `
        -v "${PWD}/deploy/init-scripts/01-admin/01-admin-user.sql:/docker-entrypoint-initdb.d/01-01-admin-user.sql" `
        -v "${PWD}/deploy/init-scripts/02-test-data/01-organization.sql:/docker-entrypoint-initdb.d/02-01-organization.sql" `
        -v "${PWD}/deploy/init-scripts/02-test-data/02-organization-detail.sql:/docker-entrypoint-initdb.d/02-02-organization-detail.sql" `
        -v "${PWD}/deploy/init-scripts/02-test-data/03-users.sql:/docker-entrypoint-initdb.d/02-03-users.sql" `
        -v "${PWD}/deploy/init-scripts/02-test-data/04-role-assignments.sql:/docker-entrypoint-initdb.d/02-04-role-assignments.sql" `
        -v "${PWD}/deploy/init-scripts/02-test-data/04-department-managers.sql:/docker-entrypoint-initdb.d/02-05-department-managers.sql" `
        -v "${PWD}/deploy/init-scripts/02-test-data/05-virtual-groups.sql:/docker-entrypoint-initdb.d/02-06-virtual-groups.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-01-function-unit.sql:/docker-entrypoint-initdb.d/04-01-function-unit.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-02-tables.sql:/docker-entrypoint-initdb.d/04-02-tables.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-03-fields-main-fixed.sql:/docker-entrypoint-initdb.d/04-03-fields-main-fixed.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-04-fields-sub-fixed.sql:/docker-entrypoint-initdb.d/04-04-fields-sub-fixed.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-05-fk-relations.sql:/docker-entrypoint-initdb.d/04-05-fk-relations.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-06-forms-fixed.sql:/docker-entrypoint-initdb.d/04-06-forms-fixed.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-07-actions-fixed.sql:/docker-entrypoint-initdb.d/04-07-actions-fixed.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-08-process-fixed-v2-corrected.sql:/docker-entrypoint-initdb.d/04-08-process-fixed-v2-corrected.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-09-form-bindings-fixed.sql:/docker-entrypoint-initdb.d/04-09-form-bindings-fixed.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-10-form-configs.sql:/docker-entrypoint-initdb.d/04-10-form-configs.sql" `
        -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-11-action-configs.sql:/docker-entrypoint-initdb.d/04-11-action-configs.sql" `
        --restart unless-stopped `
        postgres:16.5-alpine
    
    # Start Redis
    Remove-Container "platform-redis"
    Write-Host "Starting Redis..." -ForegroundColor Yellow
    docker run -d `
        --name platform-redis `
        --network $networkName `
        -e REDIS_PASSWORD=$env:REDIS_PASSWORD `
        -p 6379:6379 `
        -v redis_data:/data `
        --restart unless-stopped `
        redis:7.2-alpine redis-server --appendonly yes --requirepass $env:REDIS_PASSWORD
    
    Write-Host ""
    Write-Host "Infrastructure services started!" -ForegroundColor Green
    Write-Host "  - PostgreSQL: localhost:5432" -ForegroundColor White
    Write-Host "  - Redis: localhost:6379" -ForegroundColor White
    exit 0
}

# Step 1: Build platform modules (if needed)
if (-not $NoBuild) {
    Write-Host "Step 1: Building platform modules..." -ForegroundColor Yellow
    Write-Host "Running Maven build (this may take a few minutes)..." -ForegroundColor Gray
    mvn clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Maven build failed" -ForegroundColor Red
        exit 1
    }
    Write-Host "Platform modules built successfully" -ForegroundColor Green
}

# Step 2: Start infrastructure
Write-Host ""
Write-Host "Step 2: Starting infrastructure services..." -ForegroundColor Yellow

# Start PostgreSQL
Remove-Container "platform-postgres"
Write-Host "Starting PostgreSQL..." -ForegroundColor Yellow
docker run -d `
    --name platform-postgres `
    --network $networkName `
    -e POSTGRES_DB=workflow_platform `
    -e POSTGRES_USER=platform `
    -e POSTGRES_PASSWORD=$env:POSTGRES_PASSWORD `
    -p 5432:5432 `
    -v postgres_data:/var/lib/postgresql/data `
    -v "${PWD}/deploy/init-scripts/00-schema/01-schema.sql:/docker-entrypoint-initdb.d/00-01-schema.sql" `
    -v "${PWD}/deploy/init-scripts/01-admin/01-admin-user.sql:/docker-entrypoint-initdb.d/01-01-admin-user.sql" `
    -v "${PWD}/deploy/init-scripts/02-test-data/01-organization.sql:/docker-entrypoint-initdb.d/02-01-organization.sql" `
    -v "${PWD}/deploy/init-scripts/02-test-data/02-organization-detail.sql:/docker-entrypoint-initdb.d/02-02-organization-detail.sql" `
    -v "${PWD}/deploy/init-scripts/02-test-data/03-users.sql:/docker-entrypoint-initdb.d/02-03-users.sql" `
    -v "${PWD}/deploy/init-scripts/02-test-data/04-role-assignments.sql:/docker-entrypoint-initdb.d/02-04-role-assignments.sql" `
    -v "${PWD}/deploy/init-scripts/02-test-data/04-department-managers.sql:/docker-entrypoint-initdb.d/02-05-department-managers.sql" `
    -v "${PWD}/deploy/init-scripts/02-test-data/05-virtual-groups.sql:/docker-entrypoint-initdb.d/02-06-virtual-groups.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-01-function-unit.sql:/docker-entrypoint-initdb.d/04-01-function-unit.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-02-tables.sql:/docker-entrypoint-initdb.d/04-02-tables.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-03-fields-main-fixed.sql:/docker-entrypoint-initdb.d/04-03-fields-main-fixed.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-04-fields-sub-fixed.sql:/docker-entrypoint-initdb.d/04-04-fields-sub-fixed.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-05-fk-relations.sql:/docker-entrypoint-initdb.d/04-05-fk-relations.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-06-forms-fixed.sql:/docker-entrypoint-initdb.d/04-06-forms-fixed.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-07-actions-fixed.sql:/docker-entrypoint-initdb.d/04-07-actions-fixed.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-08-process-fixed-v2-corrected.sql:/docker-entrypoint-initdb.d/04-08-process-fixed-v2-corrected.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-09-form-bindings-fixed.sql:/docker-entrypoint-initdb.d/04-09-form-bindings-fixed.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-10-form-configs.sql:/docker-entrypoint-initdb.d/04-10-form-configs.sql" `
    -v "${PWD}/deploy/init-scripts/04-purchase-workflow/04-11-action-configs.sql:/docker-entrypoint-initdb.d/04-11-action-configs.sql" `
    --restart unless-stopped `
    postgres:16.5-alpine

# Start Redis
Remove-Container "platform-redis"
Write-Host "Starting Redis..." -ForegroundColor Yellow
docker run -d `
    --name platform-redis `
    --network $networkName `
    -p 6379:6379 `
    -v redis_data:/data `
    --restart unless-stopped `
    redis:7.2-alpine redis-server --appendonly yes --requirepass $env:REDIS_PASSWORD

# Wait for infrastructure
Write-Host "Waiting for infrastructure to be ready..." -ForegroundColor Gray
Wait-ForService "platform-postgres" "pg_isready -U platform -d workflow_platform"
Wait-ForService "platform-redis" "redis-cli -a $env:REDIS_PASSWORD ping"

# Step 3: Build backend services
if ($BackendOnly -or (-not $FrontendOnly)) {
    Write-Host ""
    Write-Host "Step 3: Building backend services..." -ForegroundColor Yellow
    
    if (-not $NoBuild) {
        Write-Host "Building backend JAR files..." -ForegroundColor Gray
        mvn clean package -DskipTests -pl backend/workflow-engine-core,backend/admin-center,backend/user-portal,backend/developer-workstation,backend/api-gateway -am
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Error: Backend build failed" -ForegroundColor Red
            exit 1
        }
    }
    
    # Build Docker images
    Write-Host "Building Docker images for backend services..." -ForegroundColor Yellow
    
    Build-Image "./backend/workflow-engine-core" "./backend/workflow-engine-core/Dockerfile" "workflow-engine:latest"
    Build-Image "./backend/admin-center" "./backend/admin-center/Dockerfile" "admin-center:latest"
    Build-Image "./backend/user-portal" "./backend/user-portal/Dockerfile" "user-portal:latest"
    Build-Image "./backend/developer-workstation" "./backend/developer-workstation/Dockerfile" "developer-workstation:latest"
    Build-Image "./backend/api-gateway" "./backend/api-gateway/Dockerfile" "api-gateway:latest"
    
    # Step 4: Start backend services
    Write-Host ""
    Write-Host "Step 4: Starting backend services..." -ForegroundColor Yellow
    
    # Start Workflow Engine
    Remove-Container "platform-workflow-engine"
    Write-Host "Starting Workflow Engine..." -ForegroundColor Yellow
    docker run -d `
        --name platform-workflow-engine `
        --network $networkName `
        -e SERVER_PORT=8091 `
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx `
        -e SPRING_DATASOURCE_USERNAME=platform `
        -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
        -e SPRING_REDIS_HOST=platform-redis `
        -e SPRING_REDIS_PORT=6379 `
        -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin `
        -e JWT_SECRET_KEY=$env:JWT_SECRET_KEY `
        -e ENCRYPTION_KEY=$env:ENCRYPTION_KEY `
        -p 8091:8091 `
        --restart unless-stopped `
        workflow-engine:latest
    
    # Start Admin Center
    Remove-Container "platform-admin-center"
    Write-Host "Starting Admin Center..." -ForegroundColor Yellow
    docker run -d `
        --name platform-admin-center `
        --network $networkName `
        -e SERVER_PORT=8092 `
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx `
        -e SPRING_DATASOURCE_USERNAME=platform `
        -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
        -e SPRING_REDIS_HOST=platform-redis `
        -e SPRING_REDIS_PORT=6379 `
        -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
        -e JWT_SECRET_KEY=$env:JWT_SECRET_KEY `
        -e ENCRYPTION_KEY=$env:ENCRYPTION_KEY `
        -p 8092:8092 `
        --restart unless-stopped `
        admin-center:latest
    
    # Start User Portal
    Remove-Container "platform-user-portal"
    Write-Host "Starting User Portal..." -ForegroundColor Yellow
    docker run -d `
        --name platform-user-portal `
        --network $networkName `
        -e SERVER_PORT=8093 `
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx `
        -e SPRING_DATASOURCE_USERNAME=platform `
        -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
        -e SPRING_REDIS_HOST=platform-redis `
        -e SPRING_REDIS_PORT=6379 `
        -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin `
        -e WORKFLOW_ENGINE_URL=http://platform-workflow-engine:8091 `
        -e JWT_SECRET_KEY=$env:JWT_SECRET_KEY `
        -e ENCRYPTION_KEY=$env:ENCRYPTION_KEY `
        -p 8093:8093 `
        --restart unless-stopped `
        user-portal:latest
    
    # Start Developer Workstation
    Remove-Container "platform-developer-workstation"
    Write-Host "Starting Developer Workstation..." -ForegroundColor Yellow
    docker run -d `
        --name platform-developer-workstation `
        --network $networkName `
        -e SERVER_PORT=8094 `
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx `
        -e SPRING_DATASOURCE_USERNAME=platform `
        -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
        -e SPRING_REDIS_HOST=platform-redis `
        -e SPRING_REDIS_PORT=6379 `
        -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin `
        -e JWT_SECRET_KEY=$env:JWT_SECRET_KEY `
        -e ENCRYPTION_KEY=$env:ENCRYPTION_KEY `
        -p 8094:8094 `
        --restart unless-stopped `
        developer-workstation:latest
    
    # Start API Gateway
    Remove-Container "platform-api-gateway"
    Write-Host "Starting API Gateway..." -ForegroundColor Yellow
    docker run -d `
        --name platform-api-gateway `
        --network $networkName `
        -e SERVER_PORT=8090 `
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx `
        -e SPRING_DATASOURCE_USERNAME=platform `
        -e SPRING_DATASOURCE_PASSWORD=$env:POSTGRES_PASSWORD `
        -e SPRING_REDIS_HOST=platform-redis `
        -e SPRING_REDIS_PORT=6379 `
        -e SPRING_REDIS_PASSWORD=$env:REDIS_PASSWORD `
        -e WORKFLOW_ENGINE_URL=http://platform-workflow-engine:8091 `
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin `
        -e USER_PORTAL_URL=http://platform-user-portal:8093/api/portal `
        -e DEVELOPER_WORKSTATION_URL=http://platform-developer-workstation:8094 `
        -e JWT_SECRET_KEY=$env:JWT_SECRET_KEY `
        -p 8090:8090 `
        --restart unless-stopped `
        api-gateway:latest
    
    Write-Host "Backend services started!" -ForegroundColor Green
}

# Step 5: Build and start frontend services
if ($FrontendOnly -or (-not $BackendOnly)) {
    Write-Host ""
    Write-Host "Step 5: Building frontend services..." -ForegroundColor Yellow
    
    # Build Docker images
    Build-Image "./frontend/admin-center" "./frontend/admin-center/Dockerfile" "frontend-admin:latest"
    Build-Image "./frontend/user-portal" "./frontend/user-portal/Dockerfile" "frontend-portal:latest"
    Build-Image "./frontend/developer-workstation" "./frontend/developer-workstation/Dockerfile" "frontend-developer:latest"
    
    # Step 6: Start frontend services
    Write-Host ""
    Write-Host "Step 6: Starting frontend services..." -ForegroundColor Yellow
    
    # Start Frontend Admin
    Remove-Container "platform-frontend-admin"
    Write-Host "Starting Frontend Admin..." -ForegroundColor Yellow
    docker run -d `
        --name platform-frontend-admin `
        --network $networkName `
        -p 3000:80 `
        --restart unless-stopped `
        frontend-admin:latest
    
    # Start Frontend Portal
    Remove-Container "platform-frontend-portal"
    Write-Host "Starting Frontend Portal..." -ForegroundColor Yellow
    docker run -d `
        --name platform-frontend-portal `
        --network $networkName `
        -p 3001:80 `
        --restart unless-stopped `
        frontend-portal:latest
    
    # Start Frontend Developer
    Remove-Container "platform-frontend-developer"
    Write-Host "Starting Frontend Developer..." -ForegroundColor Yellow
    docker run -d `
        --name platform-frontend-developer `
        --network $networkName `
        -p 3002:80 `
        --restart unless-stopped `
        frontend-developer:latest
    
    Write-Host "Frontend services started!" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  All Services Started Successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Service URLs:" -ForegroundColor Yellow
Write-Host "  Infrastructure:" -ForegroundColor White
Write-Host "    - PostgreSQL:     localhost:5432" -ForegroundColor Gray
Write-Host "    - Redis:          localhost:6379" -ForegroundColor Gray
Write-Host ""
Write-Host "  Backend Services:" -ForegroundColor White
Write-Host "    - API Gateway:    http://localhost:8090" -ForegroundColor Gray
Write-Host "    - Workflow Engine: http://localhost:8091" -ForegroundColor Gray
Write-Host "    - Admin Center:   http://localhost:8092" -ForegroundColor Gray
Write-Host "    - User Portal:    http://localhost:8093" -ForegroundColor Gray
Write-Host "    - Developer WS:   http://localhost:8094" -ForegroundColor Gray
Write-Host ""
Write-Host "  Frontend Applications:" -ForegroundColor White
Write-Host "    - Admin Center:   http://localhost:3000" -ForegroundColor Gray
Write-Host "    - User Portal:    http://localhost:3001" -ForegroundColor Gray
Write-Host "    - Developer WS:   http://localhost:3002" -ForegroundColor Gray
Write-Host ""
Write-Host "Commands:" -ForegroundColor Yellow
Write-Host "  View logs:    docker logs -f [container-name]" -ForegroundColor Gray
Write-Host "  Stop all:     .\stop-all.ps1" -ForegroundColor Gray
Write-Host "  List containers: docker ps" -ForegroundColor Gray
Write-Host ""
