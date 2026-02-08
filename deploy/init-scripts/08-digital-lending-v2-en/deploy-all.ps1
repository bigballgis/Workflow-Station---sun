# =============================================================================
# Digital Lending System V2 - One-Click Deployment Script (English Version)
# Uses file copy method to avoid pipeline transmission issues
# =============================================================================

param(
    [switch]$SkipVirtualGroups = $false
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Digital Lending System V2 - Deployment" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Docker container
Write-Host "Checking Docker container status..." -ForegroundColor Yellow
$containers = docker ps --format "{{.Names}}" | Select-String "platform-postgres-dev"
if (-not $containers) {
    Write-Host "Error: platform-postgres-dev container is not running" -ForegroundColor Red
    Write-Host "Please start Docker container first" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Docker container is running" -ForegroundColor Green
Write-Host ""

# Step 1: Create virtual groups
if (-not $SkipVirtualGroups) {
    Write-Host "Step 1/4: Creating virtual groups..." -ForegroundColor Cyan
    
    # Copy file to container
    docker cp 00-create-virtual-groups.sql platform-postgres-dev:/tmp/00-create-virtual-groups-en.sql
    
    # Execute in container
    docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/00-create-virtual-groups-en.sql
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Virtual group creation failed" -ForegroundColor Red
        exit 1
    }
    
    # Clean up temporary file
    docker exec platform-postgres-dev rm /tmp/00-create-virtual-groups-en.sql
    
    Write-Host "  ✓ Virtual groups created successfully" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "Step 1/4: Skipping virtual group creation (using -SkipVirtualGroups parameter)" -ForegroundColor Yellow
    Write-Host ""
}

# Step 2: Create function unit
Write-Host "Step 2/4: Creating function unit (tables, forms, actions)..." -ForegroundColor Cyan

# Copy file to container
docker cp 01-create-digital-lending-complete.sql platform-postgres-dev:/tmp/01-create-digital-lending-complete-en.sql

# Execute in container
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/01-create-digital-lending-complete-en.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Function unit creation failed" -ForegroundColor Red
    exit 1
}

# Clean up temporary file
docker exec platform-postgres-dev rm /tmp/01-create-digital-lending-complete-en.sql

Write-Host "  ✓ Function unit created successfully" -ForegroundColor Green
Write-Host ""

# Step 3: Insert BPMN process
Write-Host "Step 3/4: Inserting BPMN process..." -ForegroundColor Cyan
.\02-insert-bpmn-process.ps1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: BPMN process insertion failed" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ BPMN process inserted successfully" -ForegroundColor Green
Write-Host ""

# Step 4: Verify action bindings
Write-Host "Step 4/4: Verifying action bindings..." -ForegroundColor Cyan

# Copy file to container
docker cp 03-bind-actions.sql platform-postgres-dev:/tmp/03-bind-actions-en.sql

# Execute in container
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/03-bind-actions-en.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Action binding verification failed" -ForegroundColor Red
    exit 1
}

# Clean up temporary file
docker exec platform-postgres-dev rm /tmp/03-bind-actions-en.sql

Write-Host "  ✓ Action bindings verified successfully" -ForegroundColor Green
Write-Host ""

# Complete
Write-Host "========================================" -ForegroundColor Green
Write-Host "Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Digital Lending System V2 has been successfully deployed to the database" -ForegroundColor White
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Access Developer Workstation" -ForegroundColor White
Write-Host "   URL: http://localhost:3002" -ForegroundColor Gray
Write-Host "   Find 'Digital Lending System V2' and click 'Deploy' button" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Test in User Portal" -ForegroundColor White
Write-Host "   URL: http://localhost:3001" -ForegroundColor Gray
Write-Host "   Submit loan application and test complete workflow" -ForegroundColor Gray
Write-Host ""
Write-Host "3. View Documentation" -ForegroundColor White
Write-Host "   README.md - Complete system documentation" -ForegroundColor Gray
Write-Host "   QUICK_START.md - Quick start guide" -ForegroundColor Gray
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
