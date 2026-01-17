# Start All Services Script
# This script builds and starts all services using Docker Compose

param(
    [switch]$Build,        # Force rebuild all images
    [switch]$InfraOnly,    # Only start infrastructure (postgres, redis, kafka)
    [switch]$BackendOnly,  # Only start backend services
    [switch]$FrontendOnly, # Only start frontend services
    [switch]$NoBuild       # Skip build, use existing images
)

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

if ($InfraOnly) {
    Write-Host "Starting infrastructure services only..." -ForegroundColor Yellow
    docker-compose up -d postgres redis zookeeper kafka
    Write-Host ""
    Write-Host "Infrastructure services started!" -ForegroundColor Green
    Write-Host "  - PostgreSQL: localhost:5432" -ForegroundColor White
    Write-Host "  - Redis: localhost:6379" -ForegroundColor White
    Write-Host "  - Kafka: localhost:9092" -ForegroundColor White
    exit 0
}

# Step 1: Start infrastructure
Write-Host "Step 1: Starting infrastructure services..." -ForegroundColor Yellow
docker-compose up -d postgres redis
Write-Host "Waiting for infrastructure to be ready..." -ForegroundColor Gray

# Wait for PostgreSQL
$maxRetries = 30
$retryCount = 0
while ($retryCount -lt $maxRetries) {
    $pgReady = docker exec platform-postgres pg_isready -U platform -d workflow_platform 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "PostgreSQL is ready!" -ForegroundColor Green
        break
    }
    $retryCount++
    Write-Host "Waiting for PostgreSQL... ($retryCount/$maxRetries)" -ForegroundColor Gray
    Start-Sleep -Seconds 2
}

if ($retryCount -eq $maxRetries) {
    Write-Host "Error: PostgreSQL failed to start" -ForegroundColor Red
    exit 1
}

if ($BackendOnly -or $FrontendOnly) {
    # Selective start
    if ($BackendOnly) {
        Write-Host ""
        Write-Host "Step 2: Building and starting backend services..." -ForegroundColor Yellow
        
        if (-not $NoBuild) {
            Write-Host "Building backend services (this may take a few minutes)..." -ForegroundColor Gray
            mvn clean package -DskipTests -pl backend/admin-center,backend/user-portal,backend/developer-workstation,backend/workflow-engine-core,backend/api-gateway -am
        }
        
        docker-compose --profile backend up -d --build
        Write-Host "Backend services started!" -ForegroundColor Green
    }
    
    if ($FrontendOnly) {
        Write-Host ""
        Write-Host "Step 2: Building and starting frontend services..." -ForegroundColor Yellow
        docker-compose --profile frontend up -d --build
        Write-Host "Frontend services started!" -ForegroundColor Green
    }
} else {
    # Full start
    Write-Host ""
    Write-Host "Step 2: Building backend services..." -ForegroundColor Yellow
    
    if (-not $NoBuild) {
        Write-Host "Running Maven build (this may take a few minutes)..." -ForegroundColor Gray
        mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Error: Maven build failed" -ForegroundColor Red
            exit 1
        }
    }
    
    Write-Host ""
    Write-Host "Step 3: Starting all services with Docker Compose..." -ForegroundColor Yellow
    
    if ($Build) {
        docker-compose --profile full up -d --build
    } else {
        docker-compose --profile full up -d
    }
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
Write-Host "    - API Gateway:    http://localhost:8080" -ForegroundColor Gray
Write-Host "    - Workflow Engine: http://localhost:8081" -ForegroundColor Gray
Write-Host "    - User Portal:    http://localhost:8082" -ForegroundColor Gray
Write-Host "    - Developer WS:   http://localhost:8083" -ForegroundColor Gray
Write-Host "    - Admin Center:   http://localhost:8090" -ForegroundColor Gray
Write-Host ""
Write-Host "  Frontend Applications:" -ForegroundColor White
Write-Host "    - Admin Center:   http://localhost:3000" -ForegroundColor Gray
Write-Host "    - User Portal:    http://localhost:3001" -ForegroundColor Gray
Write-Host "    - Developer WS:   http://localhost:3002" -ForegroundColor Gray
Write-Host ""
Write-Host "Commands:" -ForegroundColor Yellow
Write-Host "  View logs:    docker-compose logs -f [service-name]" -ForegroundColor Gray
Write-Host "  Stop all:     docker-compose --profile full down" -ForegroundColor Gray
Write-Host "  Restart:      docker-compose --profile full restart" -ForegroundColor Gray
Write-Host ""
