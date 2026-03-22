#!/usr/bin/env pwsh
# =====================================================
# Dev Environment - Build and Deploy Script
# =====================================================
# Usage:
#   .\build-and-deploy.ps1                    # Full build & deploy
#   .\build-and-deploy.ps1 -SkipMaven         # Skip Maven, rebuild Docker only
#   .\build-and-deploy.ps1 -SkipFrontend      # Skip frontend image builds
#   .\build-and-deploy.ps1 -SkipInfra         # Skip infra startup (PG/Redis already running)
#   .\build-and-deploy.ps1 -SkipImagePull     # Skip pre-pulling base images (if already cached)
#   .\build-and-deploy.ps1 -Clean             # Destroy everything and rebuild
#   .\build-and-deploy.ps1 -ServicesOnly      # Only restart backend+frontend (no Maven, no infra)

param(
    [switch]$SkipMaven,
    [switch]$SkipFrontend,
    [switch]$SkipInfra,
    [switch]$SkipImagePull,
    [switch]$Clean,
    [switch]$ServicesOnly
)

$ErrorActionPreference = "Stop"
$RootDir = Resolve-Path "$PSScriptRoot/../../.."
$ComposeFile = "$PSScriptRoot/docker-compose.dev.yml"
$EnvFile = "$PSScriptRoot/.env"

function Wait-ForContainerHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ContainerName,

        [Parameter(Mandatory = $true)]
        [string]$DisplayName,

        [int]$MaxRetries = 60,
        [int]$SleepSeconds = 2
    )

    Write-Host "  Waiting for $DisplayName..."

    $attempt = 0
    while ($attempt -lt $MaxRetries) {
        $status = docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $ContainerName 2>$null

        if ($status -eq "healthy") {
            Write-Host "    $DisplayName is healthy." -ForegroundColor Green
            return
        }

        if ($status -eq "exited" -or $status -eq "dead") {
            throw "$DisplayName container stopped unexpectedly"
        }

        Start-Sleep -Seconds $SleepSeconds
        $attempt++
    }

    throw "$DisplayName failed to become healthy in time"
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Dev Environment Build & Deploy" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Project root: $RootDir"

if ($ServicesOnly) {
    $SkipMaven = $true
    $SkipFrontend = $true
    $SkipInfra = $true
}

# Step 0a: Pre-pull base images via domestic mirror (avoids Docker Hub connectivity issues)
if (-not $SkipImagePull) {
    Write-Host "`n[0/4] Pre-pulling base images via domestic mirror..." -ForegroundColor Yellow

    # Images required by this project, mapped to their domestic mirror equivalents
    $images = @(
        @{ Mirror = "docker.m.daocloud.io/library/eclipse-temurin:17-jre-alpine"; Target = "eclipse-temurin:17-jre-alpine" },
        @{ Mirror = "docker.m.daocloud.io/library/nginx:alpine";                  Target = "nginx:alpine"                  },
        @{ Mirror = "docker.m.daocloud.io/library/postgres:16.5-alpine";          Target = "postgres:16.5-alpine"          },
        @{ Mirror = "docker.m.daocloud.io/library/redis:7.2-alpine";              Target = "redis:7.2-alpine"              }
    )

    foreach ($img in $images) {
        # Skip if target image is already cached locally
        $exists = docker image inspect $img.Target 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Already cached, skipping: $($img.Target)" -ForegroundColor DarkGray
            continue
        }

        # Try mirror pull with up to 3 attempts (mirrors can be transiently unavailable)
        $pulled = $false
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            Write-Host "  Pulling $($img.Target) from mirror (attempt $attempt/3)..."
            docker pull $img.Mirror 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                docker tag $img.Mirror $img.Target 2>&1 | Out-Null
                Write-Host "  OK (mirror): $($img.Target)" -ForegroundColor Green
                $pulled = $true
                break
            }
            if ($attempt -lt 3) {
                Write-Host "  Retrying in 5s..." -ForegroundColor DarkGray
                Start-Sleep -Seconds 5
            }
        }

        if (-not $pulled) {
            # Mirror unavailable — try to restore from BuildKit cache via a minimal build.
            # This works when the image layers already exist in the local BuildKit cache
            # from a previous build (even if the image manifest was removed from the store).
            Write-Host "  Mirror unavailable. Restoring $($img.Target) from BuildKit cache..." -ForegroundColor Yellow
            $tmpFile = [System.IO.Path]::GetTempFileName() + ".Dockerfile"
            "FROM $($img.Target)" | Set-Content $tmpFile
            docker build --quiet -t $img.Target -f $tmpFile "$PSScriptRoot" 2>&1 | Out-Null
            Remove-Item $tmpFile -ErrorAction SilentlyContinue

            if ($LASTEXITCODE -eq 0) {
                Write-Host "  OK (BuildKit cache): $($img.Target)" -ForegroundColor Green
            } else {
                # Both mirror and cache failed — warn but do NOT abort.
                # The subsequent docker build commands will attempt to use BuildKit cache
                # on their own; if they also fail the real error will surface there.
                Write-Host "  WARNING: Could not pull or restore $($img.Target). Continuing — actual builds may still succeed via BuildKit cache." -ForegroundColor Yellow
            }
        }
    }

    Write-Host "  Base images ready." -ForegroundColor Green
} else {
    Write-Host "`n[0/4] Skipping image pre-pull" -ForegroundColor DarkGray
}

# Step 0b: Clean
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
            # Temporarily allow non-zero stderr from npm (warnings are printed to stderr
            # and would cause PowerShell Stop mode to abort the script)
            $prev = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            npm install --prefer-offline --no-audit 2>&1 | Out-Null
            $npmExit = $LASTEXITCODE
            $ErrorActionPreference = $prev
            if ($npmExit -ne 0) { throw "npm install failed: $($fe.Name)" }
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

# Step 3: Start infrastructure (postgres, redis, kafka, n8n)
if (-not $SkipInfra) {
    Write-Host "`n[3/4] Starting infrastructure (postgres, redis, kafka, n8n)..." -ForegroundColor Yellow
    docker compose -f $ComposeFile --env-file $EnvFile up -d postgres redis kafka n8n
    
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
    
    Write-Host "  Waiting for kafka..."
    $retries = 0
    while ($retries -lt 30) {
        $health = docker inspect --format='{{.State.Health.Status}}' platform-kafka-dev 2>$null
        if ($health -eq "healthy") { break }
        Start-Sleep -Seconds 3
        $retries++
    }
    if ($health -ne "healthy") { throw "Kafka failed to become healthy" }
    
    Write-Host "  Waiting for n8n..."
    $retries = 0
    while ($retries -lt 20) {
        $health = docker inspect --format='{{.State.Health.Status}}' platform-n8n-dev 2>$null
        if ($health -eq "healthy") { break }
        Start-Sleep -Seconds 3
        $retries++
    }
    if ($health -ne "healthy") {
        Write-Host "  ⚠️  N8N not healthy yet, continuing (it may take longer on first start)..." -ForegroundColor Yellow
    }
    
    Write-Host "  Infrastructure ready." -ForegroundColor Green
} else {
    Write-Host "`n[3/4] Skipping infrastructure start" -ForegroundColor DarkGray
}

# Step 4: Build and start all services
Write-Host "`n[4/4] Starting all services..." -ForegroundColor Yellow
docker compose -f $ComposeFile --env-file $EnvFile up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Host "  docker compose failed. Current service status:" -ForegroundColor Red
    docker compose -f $ComposeFile --env-file $EnvFile ps
    throw "Docker compose service startup failed"
}

Write-Host "  Waiting for backend health checks..." -ForegroundColor Cyan
Wait-ForContainerHealth -ContainerName "platform-workflow-engine-dev" -DisplayName "Workflow Engine"
Wait-ForContainerHealth -ContainerName "platform-admin-center-dev" -DisplayName "Admin Center"
Wait-ForContainerHealth -ContainerName "platform-user-portal-dev" -DisplayName "User Portal"
Wait-ForContainerHealth -ContainerName "platform-developer-workstation-dev" -DisplayName "Developer Workstation"

Write-Host "  Current service status:" -ForegroundColor Cyan
docker compose -f $ComposeFile --env-file $EnvFile ps

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
Write-Host "  Kafka:                  localhost:9092"
Write-Host "  N8N:                    http://localhost:5678"
Write-Host ""
Write-Host "Commands:" -ForegroundColor DarkGray
Write-Host "  Logs:   docker compose -f docker-compose.dev.yml --env-file .env logs -f [service]"
Write-Host "  Stop:   docker compose -f docker-compose.dev.yml --env-file .env down"
Write-Host "  Reset:  .\build-and-deploy.ps1 -Clean"
