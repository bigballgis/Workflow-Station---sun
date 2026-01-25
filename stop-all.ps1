# Stop All Services Script
# This script stops all running Docker containers

param(
    [switch]$RemoveVolumes,  # Also remove volumes (WARNING: deletes data)
    [switch]$InfraOnly,       # Only stop infrastructure
    [switch]$RemoveNetwork    # Also remove network
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Workflow Platform - Stop All Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Network name
$networkName = "platform-network"

# Function to stop and remove container
function Stop-Container {
    param([string]$ContainerName)
    
    $exists = docker ps -a --filter "name=$ContainerName" --format "{{.Names}}"
    if ($exists -eq $ContainerName) {
        Write-Host "Stopping container: $ContainerName..." -ForegroundColor Yellow
        docker stop $ContainerName | Out-Null
        docker rm $ContainerName | Out-Null
        Write-Host "Container $ContainerName stopped and removed" -ForegroundColor Green
    } else {
        Write-Host "Container $ContainerName not found" -ForegroundColor Gray
    }
}

if ($InfraOnly) {
    Write-Host "Stopping infrastructure services..." -ForegroundColor Yellow
    Stop-Container "platform-postgres"
    Stop-Container "platform-redis"
    Stop-Container "platform-zookeeper"
    Stop-Container "platform-kafka"
    Write-Host "Infrastructure services stopped!" -ForegroundColor Green
    exit 0
}

Write-Host "Stopping all services..." -ForegroundColor Yellow

# Stop frontend services
Write-Host ""
Write-Host "Stopping frontend services..." -ForegroundColor Yellow
Stop-Container "platform-frontend-admin"
Stop-Container "platform-frontend-portal"
Stop-Container "platform-frontend-developer"

# Stop backend services
Write-Host ""
Write-Host "Stopping backend services..." -ForegroundColor Yellow
Stop-Container "platform-api-gateway"
Stop-Container "platform-developer-workstation"
Stop-Container "platform-user-portal"
Stop-Container "platform-admin-center"
Stop-Container "platform-workflow-engine"

# Stop infrastructure services
Write-Host ""
Write-Host "Stopping infrastructure services..." -ForegroundColor Yellow
Stop-Container "platform-kafka"
Stop-Container "platform-zookeeper"
Stop-Container "platform-redis"
Stop-Container "platform-postgres"

# Remove volumes if requested
if ($RemoveVolumes) {
    Write-Host ""
    Write-Host "WARNING: This will remove all data volumes!" -ForegroundColor Red
    $confirm = Read-Host "Are you sure? (yes/no)"
    if ($confirm -eq "yes") {
        Write-Host "Removing volumes..." -ForegroundColor Yellow
        docker volume rm postgres_data redis_data zookeeper_data zookeeper_log kafka_data 2>&1 | Out-Null
        Write-Host "Volumes removed!" -ForegroundColor Green
    } else {
        Write-Host "Volume removal cancelled." -ForegroundColor Yellow
    }
}

# Remove network if requested
if ($RemoveNetwork) {
    Write-Host ""
    Write-Host "Removing network: $networkName..." -ForegroundColor Yellow
    docker network rm $networkName 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Network removed!" -ForegroundColor Green
    } else {
        Write-Host "Network may not exist or is still in use" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  All Services Stopped!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To restart services, run: .\start-all.ps1" -ForegroundColor Gray
Write-Host ""
