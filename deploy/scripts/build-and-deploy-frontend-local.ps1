# Build and Deploy Frontend Locally
# This script builds frontend locally and creates Docker images with pre-built dist

param(
    [string]$Frontend = "all"  # all, admin, user, developer
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Frontend Local Build & Deploy Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$rootDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$frontendDir = Join-Path $rootDir "frontend"

function Build-Frontend {
    param(
        [string]$Name,
        [string]$Path,
        [string]$ServiceName
    )
    
    Write-Host "[$Name] Starting build..." -ForegroundColor Yellow
    
    # Check if node_modules exists
    $nodeModulesPath = Join-Path $Path "node_modules"
    if (-not (Test-Path $nodeModulesPath)) {
        Write-Host "[$Name] Installing dependencies..." -ForegroundColor Yellow
        Push-Location $Path
        npm install
        Pop-Location
    }
    
    # Build
    Write-Host "[$Name] Building with Vite..." -ForegroundColor Yellow
    Push-Location $Path
    npx vite build
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[$Name] Build failed!" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
    
    # Check dist exists
    $distPath = Join-Path $Path "dist"
    if (-not (Test-Path $distPath)) {
        Write-Host "[$Name] dist directory not found!" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "[$Name] Build completed successfully!" -ForegroundColor Green
    
    # Build Docker image
    Write-Host "[$Name] Building Docker image..." -ForegroundColor Yellow
    Push-Location $Path
    docker build -f Dockerfile.local -t "dev-$ServiceName" .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[$Name] Docker build failed!" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
    
    Write-Host "[$Name] Docker image built successfully!" -ForegroundColor Green
    Write-Host ""
}

# Build based on parameter
if ($Frontend -eq "all" -or $Frontend -eq "admin") {
    Build-Frontend -Name "Admin Center" -Path (Join-Path $frontendDir "admin-center") -ServiceName "admin-center-frontend"
}

if ($Frontend -eq "all" -or $Frontend -eq "user") {
    Build-Frontend -Name "User Portal" -Path (Join-Path $frontendDir "user-portal") -ServiceName "user-portal-frontend"
}

if ($Frontend -eq "all" -or $Frontend -eq "developer") {
    Build-Frontend -Name "Developer Workstation" -Path (Join-Path $frontendDir "developer-workstation") -ServiceName "developer-workstation-frontend"
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Deploying to Docker Compose..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Deploy with docker-compose
$composeDir = Join-Path $rootDir "deploy\environments\dev"
Push-Location $composeDir

if ($Frontend -eq "all") {
    Write-Host "Restarting all frontend services..." -ForegroundColor Yellow
    docker-compose -f docker-compose.dev.yml up -d admin-center-frontend user-portal-frontend developer-workstation-frontend
} elseif ($Frontend -eq "admin") {
    Write-Host "Restarting admin-center-frontend..." -ForegroundColor Yellow
    docker-compose -f docker-compose.dev.yml up -d admin-center-frontend
} elseif ($Frontend -eq "user") {
    Write-Host "Restarting user-portal-frontend..." -ForegroundColor Yellow
    docker-compose -f docker-compose.dev.yml up -d user-portal-frontend
} elseif ($Frontend -eq "developer") {
    Write-Host "Restarting developer-workstation-frontend..." -ForegroundColor Yellow
    docker-compose -f docker-compose.dev.yml up -d developer-workstation-frontend
}

Pop-Location

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Deployment completed!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Access URLs:" -ForegroundColor Cyan
if ($Frontend -eq "all" -or $Frontend -eq "admin") {
    Write-Host "  Admin Center: http://localhost:3000" -ForegroundColor White
}
if ($Frontend -eq "all" -or $Frontend -eq "user") {
    Write-Host "  User Portal: http://localhost:3001" -ForegroundColor White
}
if ($Frontend -eq "all" -or $Frontend -eq "developer") {
    Write-Host "  Developer Workstation: http://localhost:3002" -ForegroundColor White
}
Write-Host ""
