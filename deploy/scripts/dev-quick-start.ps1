# =====================================================
# Development Environment Quick Start Script
# =====================================================

param(
    [Parameter(Mandatory=$false)]
    [switch]$Build,
    
    [Parameter(Mandatory=$false)]
    [switch]$Clean,
    
    [Parameter(Mandatory=$false)]
    [switch]$Help
)

# Function to print colored output
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-ErrorMsg {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Function to show usage
function Show-Usage {
    Write-Host "Usage: .\dev-quick-start.ps1 [OPTIONS]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Build    Build images before starting services"
    Write-Host "  -Clean    Clean up existing containers and volumes before starting"
    Write-Host "  -Help     Show this help message"
    Write-Host ""
    Write-Host "This script will:"
    Write-Host "  1. Set up the development environment"
    Write-Host "  2. Start all required services (database, cache, messaging)"
    Write-Host "  3. Start backend services"
    Write-Host "  4. Start frontend services"
    Write-Host "  5. Show service status and access URLs"
}

# Show help if requested
if ($Help) {
    Show-Usage
    exit 0
}

# Set working directory to project root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
Set-Location $ProjectRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Workflow Platform - DEV Quick Start  " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Clean up if requested
if ($Clean) {
    Write-Info "Cleaning up existing containers and volumes..."
    
    # Stop and remove containers
    & docker-compose -f deploy\environments\dev\docker-compose.dev.yml --env-file deploy\environments\dev\.env down -v --remove-orphans
    
    # Remove unused images
    & docker image prune -f
    
    Write-Success "Cleanup completed"
}

# Check if Docker is running
Write-Info "Checking Docker status..."
try {
    & docker version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker not running"
    }
    Write-Success "Docker is running"
} catch {
    Write-ErrorMsg "Docker is not running. Please start Docker Desktop and try again."
    exit 1
}

# Check if Docker Compose is available
Write-Info "Checking Docker Compose..."
try {
    & docker-compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose not available"
    }
    Write-Success "Docker Compose is available"
} catch {
    Write-ErrorMsg "Docker Compose is not available. Please install Docker Compose and try again."
    exit 1
}

# Build images if requested
if ($Build) {
    Write-Info "Building Docker images..."
    & .\deploy\scripts\build.ps1 -Environment dev
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMsg "Build failed"
        exit 1
    }
}

# Start infrastructure services first (database, cache, messaging)
Write-Info "Starting infrastructure services..."
& docker-compose -f deploy\environments\dev\docker-compose.dev.yml --env-file deploy\environments\dev\.env up -d postgres redis zookeeper kafka

# Wait for infrastructure services to be healthy
Write-Info "Waiting for infrastructure services to be ready..."
$MaxWaitTime = 120 # seconds
$WaitInterval = 5 # seconds
$ElapsedTime = 0

do {
    Start-Sleep -Seconds $WaitInterval
    $ElapsedTime += $WaitInterval
    
    $PostgresHealth = & docker inspect --format='{{.State.Health.Status}}' platform-postgres-dev 2>$null
    $RedisHealth = & docker inspect --format='{{.State.Health.Status}}' platform-redis-dev 2>$null
    
    Write-Info "Infrastructure status - Postgres: $PostgresHealth, Redis: $RedisHealth"
    
    if ($ElapsedTime -ge $MaxWaitTime) {
        Write-ErrorMsg "Infrastructure services failed to start within $MaxWaitTime seconds"
        exit 1
    }
} while ($PostgresHealth -ne "healthy" -or $RedisHealth -ne "healthy")

Write-Success "Infrastructure services are ready"

# Start backend services
Write-Info "Starting backend services..."
& docker-compose -f deploy\environments\dev\docker-compose.dev.yml --env-file deploy\environments\dev\.env up -d workflow-engine admin-center user-portal developer-workstation api-gateway

# Wait a bit for backend services to start
Write-Info "Waiting for backend services to start..."
Start-Sleep -Seconds 30

# Start frontend services
Write-Info "Starting frontend services..."
& docker-compose -f deploy\environments\dev\docker-compose.dev.yml --env-file deploy\environments\dev\.env up -d frontend-admin frontend-portal frontend-developer

# Show service status
Write-Info "Checking service status..."
& docker-compose -f deploy\environments\dev\docker-compose.dev.yml --env-file deploy\environments\dev\.env ps

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Development Environment Started!     " -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Load environment variables to show URLs
Get-Content deploy\environments\dev\.env | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($key, $value, "Process")
}

$ApiGatewayPort = [Environment]::GetEnvironmentVariable("API_GATEWAY_PORT")
$WorkflowEnginePort = [Environment]::GetEnvironmentVariable("WORKFLOW_ENGINE_PORT")
$AdminCenterPort = [Environment]::GetEnvironmentVariable("ADMIN_CENTER_PORT")
$UserPortalPort = [Environment]::GetEnvironmentVariable("USER_PORTAL_PORT")
$DeveloperWorkstationPort = [Environment]::GetEnvironmentVariable("DEVELOPER_WORKSTATION_PORT")
$FrontendAdminPort = [Environment]::GetEnvironmentVariable("FRONTEND_ADMIN_PORT")
$FrontendPortalPort = [Environment]::GetEnvironmentVariable("FRONTEND_PORTAL_PORT")
$FrontendDeveloperPort = [Environment]::GetEnvironmentVariable("FRONTEND_DEVELOPER_PORT")

Write-Host "Backend Services:" -ForegroundColor Yellow
Write-Host "  API Gateway:           http://localhost:$ApiGatewayPort"
Write-Host "  Workflow Engine:       http://localhost:$WorkflowEnginePort"
Write-Host "  Admin Center:          http://localhost:$AdminCenterPort"
Write-Host "  User Portal:           http://localhost:$UserPortalPort"
Write-Host "  Developer Workstation: http://localhost:$DeveloperWorkstationPort"
Write-Host ""

Write-Host "Frontend Applications:" -ForegroundColor Yellow
Write-Host "  Admin Center:          http://localhost:$FrontendAdminPort"
Write-Host "  User Portal:           http://localhost:$FrontendPortalPort"
Write-Host "  Developer Workstation: http://localhost:$FrontendDeveloperPort"
Write-Host ""

Write-Host "Infrastructure Services:" -ForegroundColor Yellow
Write-Host "  PostgreSQL:            localhost:5432 (platform/dev_password_123)"
Write-Host "  Redis:                 localhost:6379 (password: dev_redis_123)"
Write-Host "  Kafka:                 localhost:9092"
Write-Host ""

Write-Host "Useful Commands:" -ForegroundColor Cyan
Write-Host "  View logs:             .\deploy\scripts\deploy.ps1 -Environment dev -Action logs"
Write-Host "  Stop services:         .\deploy\scripts\deploy.ps1 -Environment dev -Action down"
Write-Host "  Restart services:      .\deploy\scripts\deploy.ps1 -Environment dev -Action restart"
Write-Host "  Service status:        .\deploy\scripts\deploy.ps1 -Environment dev -Action status"
Write-Host ""

Write-Success "Development environment is ready for use!"