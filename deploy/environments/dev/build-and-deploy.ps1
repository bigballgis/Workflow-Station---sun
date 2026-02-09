#!/usr/bin/env pwsh
# =====================================================
# Dev Environment - Build and Deploy Script
# =====================================================
# Usage: .\build-and-deploy.ps1 [-SkipMaven] [-SkipFrontend] [-SkipInfra] [-Clean]

param(
    [switch]$SkipMaven,
    [switch]$SkipFrontend,
    [switch]$SkipInfra,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$RootDir = Resolve-Path "$PSScriptRoot/../../.."

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Dev Environment Build & Deploy" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Project root: $RootDir"

# Step 0: Clean if requested
if ($Clean) {
    Write-Host "`n[0/4] Cleaning old containers and volumes..." -ForegroundColor Yellow
    docker compose -f "$PSScriptRoot/docker-compose.dev.yml" --env-file "$PSScriptRoot/.env" down -v --remove-orphans
    Write-Host "  Done." -ForegroundColor Green
}

# Step 1: Maven build
if (-not $SkipMaven) {
    Write-Host "`n[1/4] Building backend JARs (Maven)..." -ForegroundColor Yellow
    Push-Location $RootDir
    try {
        mvn clean package -DskipTests -pl backend/platform-common,backend/platform-cache,backend/platform-security,backend/platform-messaging,backend/api-gateway,backend/workflow-engine-core,backend/admin-center,backend/developer-workstation,backend/user-portal -am
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
        Write-Host "  Maven build complete." -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host "`n[1/4] Skipping Maven build (--SkipMaven)" -ForegroundColor DarkGray
}

# Step 2: Build frontend Docker images
if (-not $SkipFrontend) {
    Write-Host "`n[2/4] Building frontend Docker images..." -ForegroundColor Yellow
    
    Write-Host "  Building admin-center-frontend..."
    docker build -t dev-admin-center-frontend "$RootDir/frontend/admin-center"
    if ($LASTEXITCODE -ne 0) { throw "admin-center-frontend build failed" }
    
    Write-Host "  Building user-portal-frontend..."
    docker build -t dev-user-portal-frontend "$RootDir/frontend/user-portal"
    if ($LASTEXITCODE -ne 0) { throw "user-portal-frontend build failed" }
    
    Write-Host "  Building developer-workstation-frontend..."
    docker build -t dev-developer-workstation-frontend "$RootDir/frontend/developer-workstation"
    if ($LASTEXITCODE -ne 0) { throw "developer-workstation-frontend build failed" }
    
    Write-Host "  Frontend images built." -ForegroundColor Green
} else {
    Write-Host "`n[2/4] Skipping frontend build (--SkipFrontend)" -ForegroundColor DarkGray
}

# Step 3: Start infrastructure (postgres, redis, kafka)
if (-not $SkipInfra) {
    Write-Host "`n[3/4] Starting infrastructure (postgres, redis, kafka)..." -ForegroundColor Yellow
    docker compose -f "$PSScriptRoot/docker-compose.dev.yml" --env-file "$PSScriptRoot/.env" up -d postgres redis zookeeper kafka
    
    Write-Host "  Waiting for postgres to be healthy..."
    $retries = 0
    while ($retries -lt 30) {
        $health = docker inspect --format='{{.State.Health.Status}}' platform-postgres-dev 2>$null
        if ($health -eq "healthy") { break }
        Start-Sleep -Seconds 2
        $retries++
    }
    if ($health -ne "healthy") { throw "Postgres failed to become healthy" }
    
    Write-Host "  Waiting for redis to be healthy..."
    $retries = 0
    while ($retries -lt 20) {
        $health = docker inspect --format='{{.State.Health.Status}}' platform-redis-dev 2>$null
        if ($health -eq "healthy") { break }
        Start-Sleep -Seconds 2
        $retries++
    }
    if ($health -ne "healthy") { throw "Redis failed to become healthy" }
    
    Write-Host "  Infrastructure ready." -ForegroundColor Green
} else {
    Write-Host "`n[3/4] Skipping infrastructure start (--SkipInfra)" -ForegroundColor DarkGray
}

# Step 4: Build and start all services
Write-Host "`n[4/4] Building and starting all services..." -ForegroundColor Yellow
docker compose -f "$PSScriptRoot/docker-compose.dev.yml" --env-file "$PSScriptRoot/.env" up -d --build

Write-Host "`n========================================" -ForegroundColor Green
Write-Host " Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Services:" -ForegroundColor Cyan
Write-Host "  Workflow Engine:        http://localhost:8081"
Write-Host "  Admin Center API:       http://localhost:8090"
Write-Host "  User Portal API:        http://localhost:8082"
Write-Host "  Developer Workstation:  http://localhost:8083"
Write-Host "  API Gateway:            http://localhost:8080"
Write-Host ""
Write-Host "Frontend:" -ForegroundColor Cyan
Write-Host "  Admin Center:           http://localhost:3000"
Write-Host "  User Portal:            http://localhost:3001"
Write-Host "  Developer Workstation:  http://localhost:3002"
Write-Host ""
Write-Host "Infrastructure:" -ForegroundColor Cyan
Write-Host "  PostgreSQL:             localhost:5432"
Write-Host "  Redis:                  localhost:6379"
Write-Host "  Kafka:                  localhost:9092"
Write-Host ""
Write-Host "Useful commands:" -ForegroundColor DarkGray
Write-Host "  View logs:    docker compose -f docker-compose.dev.yml --env-file .env logs -f [service]"
Write-Host "  Stop all:     docker compose -f docker-compose.dev.yml --env-file .env down"
Write-Host "  Clean reset:  .\build-and-deploy.ps1 -Clean"
