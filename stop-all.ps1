# Stop All Services Script
# This script stops all running Docker Compose services

param(
    [switch]$RemoveVolumes,  # Also remove volumes (WARNING: deletes data)
    [switch]$InfraOnly       # Only stop infrastructure
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Workflow Platform - Stop All Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if ($InfraOnly) {
    Write-Host "Stopping infrastructure services..." -ForegroundColor Yellow
    docker-compose stop postgres redis zookeeper kafka
    Write-Host "Infrastructure services stopped!" -ForegroundColor Green
    exit 0
}

Write-Host "Stopping all services..." -ForegroundColor Yellow

if ($RemoveVolumes) {
    Write-Host "WARNING: This will also remove all data volumes!" -ForegroundColor Red
    $confirm = Read-Host "Are you sure? (yes/no)"
    if ($confirm -eq "yes") {
        docker-compose --profile full down -v
        Write-Host "All services stopped and volumes removed!" -ForegroundColor Green
    } else {
        Write-Host "Cancelled." -ForegroundColor Yellow
    }
} else {
    docker-compose --profile full down
    Write-Host "All services stopped!" -ForegroundColor Green
}

Write-Host ""
Write-Host "To restart services, run: .\start-all.ps1" -ForegroundColor Gray
Write-Host ""
