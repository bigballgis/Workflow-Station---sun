#!/usr/bin/env pwsh
# =====================================================
# Build & Push Docker Images to K8S Registry
# =====================================================
# Usage:
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -Services "workflow-engine,admin-center"
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests -SkipFrontend
# =====================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Registry,

    [string]$Tag = "latest",
    [string]$Services = "all",
    [switch]$SkipTests = $false,
    [switch]$SkipFrontend = $false,
    [switch]$SkipBackend = $false,
    [switch]$PushOnly = $false,
    [switch]$NoPush = $false
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# Backend services (no API Gateway — it's bypassed)
$BackendServices = @(
    @{ Name = "workflow-engine"; Dir = "backend/workflow-engine-core" },
    @{ Name = "admin-center"; Dir = "backend/admin-center" },
    @{ Name = "developer-workstation"; Dir = "backend/developer-workstation" },
    @{ Name = "user-portal"; Dir = "backend/user-portal" }
)

# Frontend services
$FrontendServices = @(
    @{ Name = "admin-center-frontend"; Dir = "frontend/admin-center" },
    @{ Name = "user-portal-frontend"; Dir = "frontend/user-portal" },
    @{ Name = "developer-workstation-frontend"; Dir = "frontend/developer-workstation" }
)

function Write-Step { param([string]$Msg) Write-Host "`n>> $Msg" -ForegroundColor Cyan }
function Write-Ok { param([string]$Msg) Write-Host "   OK: $Msg" -ForegroundColor Green }
function Write-Fail { param([string]$Msg) Write-Host "   FAIL: $Msg" -ForegroundColor Red; exit 1 }

# Filter services
$selectedBackend = if ($Services -eq "all") { $BackendServices } else {
    $names = $Services -split ","
    $BackendServices | Where-Object { $names -contains $_.Name }
}
$selectedFrontend = if ($Services -eq "all") { $FrontendServices } else {
    $names = $Services -split ","
    $FrontendServices | Where-Object { $names -contains $_.Name }
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Build & Push to K8S Registry" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Registry: $Registry"
Write-Host "  Tag: $Tag"
Write-Host "  Services: $Services"
Write-Host "=========================================" -ForegroundColor Yellow

# 1. Maven Build
if (-not $SkipBackend -and -not $PushOnly) {
    Write-Step "Building backend with Maven..."

    $modules = ($selectedBackend | ForEach-Object { $_.Dir }) -join ","
    $mvnArgs = @("clean", "package", "-pl", $modules, "-am")
    if ($SkipTests) { $mvnArgs += "-DskipTests" }

    Push-Location $ProjectRoot
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Fail "Maven build failed" }
    Pop-Location
    Write-Ok "Maven build complete"
}

# 2. Docker Build & Push (Backend)
if (-not $SkipBackend) {
    Write-Step "Building backend Docker images..."

    foreach ($svc in $selectedBackend) {
        $imageName = "$Registry/$($svc.Name):$Tag"
        $contextDir = Join-Path $ProjectRoot $svc.Dir

        if (-not $PushOnly) {
            Write-Host "   Building $($svc.Name)..." -ForegroundColor Gray
            docker build -t $imageName $contextDir
            if ($LASTEXITCODE -ne 0) { Write-Fail "Docker build failed: $($svc.Name)" }
            docker tag $imageName "$Registry/$($svc.Name):latest"
        }

        if (-not $NoPush) {
            Write-Host "   Pushing $imageName..." -ForegroundColor Gray
            docker push $imageName
            if ($LASTEXITCODE -ne 0) { Write-Fail "Docker push failed: $imageName" }
            docker push "$Registry/$($svc.Name):latest"
        }

        Write-Ok $svc.Name
    }
}

# 3. Docker Build & Push (Frontend)
if (-not $SkipFrontend) {
    Write-Step "Building frontend Docker images..."

    foreach ($svc in $selectedFrontend) {
        $imageName = "$Registry/$($svc.Name):$Tag"
        $contextDir = Join-Path $ProjectRoot $svc.Dir

        if (-not $PushOnly) {
            Write-Host "   Building $($svc.Name)..." -ForegroundColor Gray
            docker build -t $imageName $contextDir
            if ($LASTEXITCODE -ne 0) { Write-Fail "Docker build failed: $($svc.Name)" }
            docker tag $imageName "$Registry/$($svc.Name):latest"
        }

        if (-not $NoPush) {
            Write-Host "   Pushing $imageName..." -ForegroundColor Gray
            docker push $imageName
            if ($LASTEXITCODE -ne 0) { Write-Fail "Docker push failed: $imageName" }
            docker push "$Registry/$($svc.Name):latest"
        }

        Write-Ok $svc.Name
    }
}

# Summary
Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Build & Push Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Images: $Registry/*:$Tag" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Update deploy/k8s/configmap-*.yaml with DB/Redis hosts" -ForegroundColor White
Write-Host "  2. Update deploy/k8s/secret-*.yaml with real credentials" -ForegroundColor White
Write-Host "  3. Deploy: .\deploy\k8s\deploy.ps1 -Environment sit -Tag $Tag" -ForegroundColor White
