#!/usr/bin/env pwsh
# =====================================================
# Dev Environment - Build and Deploy Script
# =====================================================
# Usage:
#   .\build-and-deploy.ps1                    # Full build & deploy
#   .\build-and-deploy.ps1 -SkipMaven         # Skip Maven, rebuild Docker only
#   .\build-and-deploy.ps1 -SkipFrontend      # Skip frontend image builds
#   .\build-and-deploy.ps1 -SkipInfra         # Skip infra startup (PG/Redis already running)
#   .\build-and-deploy.ps1 -Clean             # Destroy everything and rebuild
#   .\build-and-deploy.ps1 -ServicesOnly      # Only restart backend+frontend (no Maven, no infra)

param(
    [switch]$SkipMaven,
    [switch]$SkipFrontend,
    [switch]$SkipInfra,
    [switch]$Clean,
    [switch]$ServicesOnly
)

$ErrorActionPreference = "Stop"
$RootDir = Resolve-Path "$PSScriptRoot/../../.."
$ComposeFile = "$PSScriptRoot/docker-compose.dev.yml"
$EnvFile = "$PSScriptRoot/.env"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Dev Environment Build & Deploy" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Project root: $RootDir"

if ($ServicesOnly) {
    $SkipMaven = $true
    $SkipFrontend = $true
    $SkipInfra = $true
}

# Step 0: Clean
if ($Clean) {
    Write-Host "`n[0/4] Cleaning old containers and volumes..." -ForegroundColor Yellow
    docker compose -f $ComposeFile --env-file $EnvFile down -v --remove-orphans
    Write-Host "  Done." -ForegroundColor Green
}

# Step 1: Maven build
if (-not $SkipMaven) {
    Write-Host "`n[1/4] Building backend JARs (Maven)..." -ForegroundColor Yellow
    Push-Location $RootDir
    try {
        mvn clean package -DskipTests -pl backend/platform-common,backend/platform-cache,backend/platform-security,backend/platform-messaging,backend/workflow-engine-core,backend/admin-center,backend/developer-workstation,backend/user-portal -am
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
        Write-Host "  Maven build complete." -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host "`n[1/4] Skipping Maven build" -ForegroundColor DarkGray
}

# Step 2: Build frontend (npm build locally, then Docker image with Dockerfile.local)
if (-not $SkipFrontend) {
    Write-Host "`n[2/4] Building frontend (local npm build + Docker)..." -ForegroundColor Yellow
    
    $frontends = @(
        @{ Name = "admin-center-frontend"; Dir = "frontend/admin-center" },
        @{ Name = "user-portal-frontend"; Dir = "frontend/user-portal" },
        @{ Name = "developer-workstation-frontend"; Dir = "frontend/developer-workstation" }
    )
    
    foreach ($fe in $frontends) {
        $feDir = "$RootDir/$($fe.Dir)"
        
        # npm install + build locally (Docker multi-stage build not used)
        Write-Host "  npm install & build $($fe.Name)..."
        Push-Location $feDir
        try {
            npm install --prefer-offline --no-audit 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "npm install failed: $($fe.Name)" }
            npx vite build
            if ($LASTEXITCODE -ne 0) { throw "vite build failed: $($fe.Name)" }
        } finally {
            Pop-Location
        }
        
        # Docker image copies pre-built dist/ only
        Write-Host "  Docker build $($fe.Name) (Dockerfile.local)..."
        docker build -f "$feDir/Dockerfile.local" -t "dev-$($fe.Name)" $feDir
        if ($LASTEXITCODE -ne 0) { throw "$($fe.Name) docker build failed" }
    }
    
    Write-Host "  Frontend images built." -ForegroundColor Green
} else {
    Write-Host "`n[2/4] Skipping frontend build" -ForegroundColor DarkGray
}

# Step 3: Start infrastructure (postgres, redis)
if (-not $SkipInfra) {
    Write-Host "`n[3/4] Starting infrastructure (postgres, redis)..." -ForegroundColor Yellow
    docker compose -f $ComposeFile --env-file $EnvFile up -d postgres redis
    
    Write-Host "  Waiting for postgres..."
    $retries = 0
    while ($retries -lt 30) {
        $health = docker inspect --format='{{.State.Health.Status}}' platform-postgres-dev 2>$null
        if ($health -eq "healthy") { break }
        Start-Sleep -Seconds 2
        $retries++
    }
    if ($health -ne "healthy") { throw "Postgres failed to become healthy" }
    
    Write-Host "  Waiting for redis..."
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
    Write-Host "`n[3/4] Skipping infrastructure start" -ForegroundColor DarkGray
}

# Step 4: Build and start all services
Write-Host "`n[4/4] Starting all services..." -ForegroundColor Yellow
docker compose -f $ComposeFile --env-file $EnvFile up -d --build

Write-Host "`n========================================" -ForegroundColor Green
Write-Host " Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Backend:" -ForegroundColor Cyan
Write-Host "  Workflow Engine:        http://localhost:8081"
Write-Host "  Admin Center:           http://localhost:8090"
Write-Host "  User Portal:            http://localhost:8082"
Write-Host "  Developer Workstation:  http://localhost:8083"
Write-Host ""
Write-Host "Frontend:" -ForegroundColor Cyan
Write-Host "  Admin Center:           http://localhost:3000"
Write-Host "  User Portal:            http://localhost:3001"
Write-Host "  Developer Workstation:  http://localhost:3002"
Write-Host ""
Write-Host "Infrastructure:" -ForegroundColor Cyan
Write-Host "  PostgreSQL:             localhost:5432"
Write-Host "  Redis:                  localhost:6379"
Write-Host ""
Write-Host "Commands:" -ForegroundColor DarkGray
Write-Host "  Logs:   docker compose -f docker-compose.dev.yml --env-file .env logs -f [service]"
Write-Host "  Stop:   docker compose -f docker-compose.dev.yml --env-file .env down"
Write-Host "  Reset:  .\build-and-deploy.ps1 -Clean"
