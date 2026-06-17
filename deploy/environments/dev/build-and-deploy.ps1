#!/usr/bin/env pwsh
# =====================================================
# Dev Environment - Build and Deploy Script
# =====================================================
# Usage:
#   .\build-and-deploy.ps1                    # Full build & deploy
#   .\build-and-deploy.ps1 -Service admin-center  # Build & deploy only admin-center
#   .\build-and-deploy.ps1 -Service admin-center -SkipMaven  # Redeploy without Maven rebuild
#   .\build-and-deploy.ps1 -SkipMaven         # Skip Maven, rebuild Docker only
#   .\build-and-deploy.ps1 -SkipFrontend      # Skip frontend image builds
#   .\build-and-deploy.ps1 -SkipInfra         # Skip infra startup (PG/Redis already running)
#   .\build-and-deploy.ps1 -SkipImagePull     # Skip pre-pulling base images (if already cached)
#   .\build-and-deploy.ps1 -JavaBaseImage "docker.m.daocloud.io/library/eclipse-temurin:17-jre"  # Backend FROM / compose build-arg
#   .\build-and-deploy.ps1 -Clean             # Destroy everything and rebuild
#   .\build-and-deploy.ps1 -ServicesOnly      # Only restart backend+frontend (no Maven, no infra)
#   .\build-and-deploy.ps1 -SkipMavenClean    # Maven package without clean (avoids clean delete failures)
#
# Valid -Service values:
#   Backend:  workflow-engine, admin-center, user-portal, developer-workstation
#   Frontend: admin-center-frontend, user-portal-frontend, developer-workstation-frontend, platform-login-frontend
#   Edge:     edge-frontend (nginx single-origin — no Maven/npm; restarts container from compose)

param(
    [string]$Service,
    # Passed to backend Docker builds (JAVA_BASE_IMAGE); default uses DaoCloud mirror to reduce Docker Hub dependency.
    [string]$JavaBaseImage = "docker.m.daocloud.io/library/eclipse-temurin:17-jre",
    [switch]$SkipMaven,
    [switch]$SkipFrontend,
    [switch]$SkipInfra,
    [switch]$SkipImagePull,
    [switch]$Clean,
    [switch]$ServicesOnly,
    # Skip "mvn clean" only (still runs package). Use when clean fails on locked/corrupt target dirs.
    [switch]$SkipMavenClean
)

$ErrorActionPreference = "Stop"
$RootDir = Resolve-Path "$PSScriptRoot/../../.."
$ComposeFile = "$PSScriptRoot/docker-compose.dev.yml"
$EnvFile = "$PSScriptRoot/.env"

# ==================== Service Registry ====================
# Maps compose service name -> Maven module path, container name, type
$ServiceRegistry = @{
    "workflow-engine" = @{
        Maven     = "backend/workflow-engine-core"
        Container = "platform-workflow-engine-dev"
        Type      = "backend"
    }
    "admin-center" = @{
        Maven     = "backend/admin-center"
        Container = "platform-admin-center-dev"
        Type      = "backend"
    }
    "user-portal" = @{
        Maven     = "backend/user-portal"
        Container = "platform-user-portal-dev"
        Type      = "backend"
    }
    "developer-workstation" = @{
        Maven     = "backend/developer-workstation"
        Container = "platform-developer-workstation-dev"
        Type      = "backend"
    }
    "admin-center-frontend" = @{
        FrontendDir = "frontend/admin-center"
        Container   = "platform-admin-center-frontend-dev"
        Type        = "frontend"
    }
    "user-portal-frontend" = @{
        FrontendDir = "frontend/user-portal"
        Container   = "platform-user-portal-frontend-dev"
        Type        = "frontend"
    }
    "developer-workstation-frontend" = @{
        FrontendDir = "frontend/developer-workstation"
        Container   = "platform-developer-workstation-frontend-dev"
        Type        = "frontend"
    }
    "platform-login-frontend" = @{
        FrontendDir = "frontend/login"
        Container   = "platform-login-frontend-dev"
        Type        = "frontend"
    }
    "edge-frontend" = @{
        Container = "platform-edge-frontend-dev"
        Type      = "edge"
    }
}

# Validate -Service parameter
if ($Service -and -not $ServiceRegistry.ContainsKey($Service)) {
    Write-Host "Unknown service: $Service" -ForegroundColor Red
    Write-Host "Valid services: $($ServiceRegistry.Keys -join ', ')" -ForegroundColor Yellow
    exit 1
}

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
        $lb = [char]123 + [char]123
        $rb = [char]125 + [char]125
        $fmt = $lb + 'if .State.Health' + $rb + $lb + '.State.Health.Status' + $rb + $lb + 'else' + $rb + $lb + '.State.Status' + $rb + $lb + 'end' + $rb
        $status = docker inspect --format=$fmt $ContainerName 2>$null
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

# Pull image with retries and simple fallback rules (sequential, reduces concurrent registry load)
function Pull-ImageWithRetry {
    param(
        [Parameter(Mandatory=$true)] [string]$Image,
        [int]$MaxAttempts = 5
    )

    $attempt = 0
    while ($attempt -lt $MaxAttempts) {
        Write-Host "  Pulling image: $Image (attempt $([int]($attempt+1))/$MaxAttempts)" -ForegroundColor DarkGray
        docker pull $Image
        if ($LASTEXITCODE -eq 0) { Write-Host "    Pulled: $Image" -ForegroundColor Green; return $true }

        # Try a lightweight fallback: strip known registry host prefix (e.g. docker.n8n.io/... -> namespace/name)
        if ($Image -match '^[^/]+/([^/]+/[^:]+(:.*)?)$') {
            $short = $Matches[1]
            if ($short -and $short -ne $Image) {
                    Write-Host "    Fallback pull: $short" -ForegroundColor DarkGray
                    docker pull $short
                    if ($LASTEXITCODE -eq 0) {
                        Write-Host "    Pulled fallback: $short" -ForegroundColor Green
                        # Tag fallback image back to original name so compose finds it
                        docker tag $short $Image 2>$null
                        if ($LASTEXITCODE -eq 0) { Write-Host "    Tagged $short -> $Image" -ForegroundColor DarkGray }
                        return $true
                    }
                }
        }

        $attempt++
        $backoff = [Math]::Min(30, 5 * $attempt)
        Write-Host "    Pull failed, sleeping $backoff seconds before retry..." -ForegroundColor Yellow
        Start-Sleep -Seconds $backoff
    }
    Write-Host "    Failed to pull image: $Image after $MaxAttempts attempts" -ForegroundColor Red
    return $false
}

# ==================== Single Service Mode ====================
if ($Service) {
    $svc = $ServiceRegistry[$Service]
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " Single Service: $Service" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    if ($svc.Type -eq "backend" -and -not $SkipMaven) {
        Write-Host "`n[1/2] Building $Service (Maven)..." -ForegroundColor Yellow
        Push-Location $RootDir
        try {
            mvn clean package '-Dmaven.test.skip=true' '-pl' $svc.Maven -am
            if ($LASTEXITCODE -ne 0) { throw "Maven build failed for $Service" }
            Write-Host "  Maven build complete." -ForegroundColor Green
        } finally {
            Pop-Location
        }
    } elseif ($svc.Type -eq "frontend" -and -not $SkipFrontend) {
        Write-Host "`n[1/2] Building $Service (npm + Docker)..." -ForegroundColor Yellow
        $feDir = "$RootDir/$($svc.FrontendDir)"
        Push-Location $feDir
        try {
            $prev = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            npm install --prefer-offline --no-audit 2>&1 | Out-Null
            $npmExit = $LASTEXITCODE
            $ErrorActionPreference = $prev
            if ($npmExit -ne 0) { throw "npm install failed: $Service" }
            # Remove auto-generated dts files before build to avoid Windows file locking (errno -4094)
            Remove-Item -Path "src/components.d.ts", "src/auto-imports.d.ts" -Force -ErrorAction SilentlyContinue
            npx vite build
            if ($LASTEXITCODE -ne 0) { throw "vite build failed: $Service" }
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "`n[1/2] Skipping build step" -ForegroundColor DarkGray
    }

    Write-Host "`n[2/2] Deploying $Service..." -ForegroundColor Yellow
    # 前端 Dockerfile.local 仅 COPY dist；compose 单独 --build 时常命中缓存层，容器内仍是旧资源，表现为「部署了但页面没变」
    if ($svc.Type -eq "frontend") {
        docker compose -f $ComposeFile --env-file $EnvFile build --no-cache $Service
        if ($LASTEXITCODE -ne 0) { throw "Docker build failed for $Service" }
        docker compose -f $ComposeFile --env-file $EnvFile up -d --no-deps $Service
    } elseif ($svc.Type -eq "edge") {
        # 仅 nginx:alpine + 挂载 nginx-edge.conf；无镜像构建，改配置后 up 会按 compose 重建/重启
        docker compose -f $ComposeFile --env-file $EnvFile up -d --no-deps --force-recreate $Service
    } else {
        docker compose -f $ComposeFile --env-file $EnvFile build --build-arg "JAVA_BASE_IMAGE=$JavaBaseImage" $Service
        if ($LASTEXITCODE -ne 0) { throw "Docker compose build failed for $Service" }
        docker compose -f $ComposeFile --env-file $EnvFile up -d --no-deps $Service
    }
    if ($LASTEXITCODE -ne 0) { throw "Failed to deploy $Service" }

    if ($svc.Type -eq "backend" -or $svc.Type -eq "edge") {
        Wait-ForContainerHealth -ContainerName $svc.Container -DisplayName $Service
    } else {
        Start-Sleep -Seconds 3
        $lb = [char]123 + [char]123
        $rb = [char]125 + [char]125
        $fmt = $lb + '.State.Status' + $rb
        $status = docker inspect --format=$fmt $svc.Container 2>$null
        if ($status -eq "running") {
            Write-Host "  $Service is running." -ForegroundColor Green
        } else {
            Write-Host "  WARNING: $Service status: $status" -ForegroundColor Yellow
        }
    }

    Write-Host "`n========================================" -ForegroundColor Green
    Write-Host " $Service deployed successfully!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    docker compose -f $ComposeFile --env-file $EnvFile ps $Service
    exit 0
}

# ==================== Full Deploy Mode ====================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Dev Environment Build & Deploy" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Project root: $RootDir"

if ($ServicesOnly) {
    $SkipMaven = $true
    $SkipFrontend = $true
    $SkipInfra = $true
}

# Step 0a: Pre-pull base images via domestic mirror
# Note: prefer in-script sequential pulls (prepull-images.ps1 may fail to parse in some shells)
if (-not $SkipImagePull) {
    Write-Host "`n[0/4] Pre-pull disabled external script; continuing with in-script pulls" -ForegroundColor DarkGray
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
        # Remove backend/*/target before Maven — avoids maven-clean-plugin "Failed to delete target"
        # on macOS (IDE file locks, iCloud duplicates like "classes 2", odd permissions).
        if (-not $SkipMavenClean) {
            Write-Host "  Pre-clean: removing backend/*/target..." -ForegroundColor DarkGray
            Get-ChildItem -Path (Join-Path $RootDir "backend") -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $targetDir = Join-Path $_.FullName "target"
                if (Test-Path -LiteralPath $targetDir) {
                    Remove-Item -LiteralPath $targetDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }

        $pl = "backend/platform-common,backend/platform-cache,backend/platform-security,backend/platform-messaging,backend/workflow-engine-core,backend/admin-center,backend/developer-workstation,backend/user-portal"
        # Quote -D... for PowerShell — unquoted `-Dmaven...` is split into a bogus lifecycle phase.
        # maven.clean.failOnError=false: if clean still hits an undeletable file, continue (pre-clean usually fixes it).
        if ($SkipMavenClean) {
            mvn package '-DskipTests' '-Dmaven.clean.failOnError=false' -pl $pl -am
        } else {
            mvn clean package '-DskipTests' '-Dmaven.clean.failOnError=false' -pl $pl -am
        }
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
        Write-Host "  Maven build complete." -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host "`n[1/4] Skipping Maven build" -ForegroundColor DarkGray
}

# Step 2: Build frontend
if (-not $SkipFrontend) {
    Write-Host "`n[2/4] Building frontend (local npm build + Docker)..." -ForegroundColor Yellow
    
    $frontends = @(
        @{ Name = "admin-center-frontend"; Dir = "frontend/admin-center" },
        @{ Name = "user-portal-frontend"; Dir = "frontend/user-portal" },
        @{ Name = "developer-workstation-frontend"; Dir = "frontend/developer-workstation" },
        @{ Name = "platform-login-frontend"; Dir = "frontend/login" }
    )
    
    foreach ($fe in $frontends) {
        $feDir = "$RootDir/$($fe.Dir)"
        
        Write-Host "  npm install & build $($fe.Name)..."
        Push-Location $feDir
        try {
            $prev = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            npm install --prefer-offline --no-audit 2>&1 | Out-Null
            $npmExit = $LASTEXITCODE
            $ErrorActionPreference = $prev
            if ($npmExit -ne 0) { throw "npm install failed: $($fe.Name)" }
            # Remove auto-generated dts files before build to avoid Windows file locking (errno -4094)
            Remove-Item -Path "src/components.d.ts", "src/auto-imports.d.ts" -Force -ErrorAction SilentlyContinue
            npx vite build
            if ($LASTEXITCODE -ne 0) { throw "vite build failed: $($fe.Name)" }
        } finally {
            Pop-Location
        }
        
        Write-Host "  Docker build $($fe.Name) (Dockerfile.local, --no-cache)..."
        docker build --no-cache -f "$feDir/Dockerfile.local" -t "dev-$($fe.Name)" $feDir
        if ($LASTEXITCODE -ne 0) { throw "$($fe.Name) docker build failed" }
    }
    
    Write-Host "  Frontend images built." -ForegroundColor Green
} else {
    Write-Host "`n[2/4] Skipping frontend build" -ForegroundColor DarkGray
}

# Step 3: Start infrastructure
if (-not $SkipInfra) {
    Write-Host "`n[3/4] Starting infrastructure (postgres, redis, kafka, n8n)..." -ForegroundColor Yellow

    # Pre-pull infra images sequentially to avoid concurrent registry failures
    $infraImages = @(
        "postgres:16.5-alpine",
        "redis:7.2-alpine",
        "confluentinc/cp-kafka:7.5.3",
        "docker.n8n.io/n8nio/n8n:latest"
    )
    $failedInfra = @()
    foreach ($img in $infraImages) {
        $ok = Pull-ImageWithRetry -Image $img -MaxAttempts 5
        if (-not $ok) { $failedInfra += $img }
    }
    if ($failedInfra.Count -gt 0) {
        Write-Host "  Failed to pull infra images: $($failedInfra -join ', ')" -ForegroundColor Red
        Write-Host "  Will still attempt to start infra; if pulls fail compose will exit with error." -ForegroundColor Yellow
    }

    docker compose -f $ComposeFile --env-file $EnvFile up -d postgres redis kafka n8n
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  docker compose up failed during infrastructure startup." -ForegroundColor Red
        Write-Host "  This usually means one or more required images could not be pulled." -ForegroundColor Red
        Write-Host "  Check Docker network/proxy settings or ensure the mirror is reachable." -ForegroundColor Yellow
        docker compose -f $ComposeFile --env-file $EnvFile ps
        throw "Docker compose infra startup failed"
    }

    # First boot runs large init-scripts (demo data + Flowable schema); allow more time.
    Wait-ForContainerHealth -ContainerName "platform-postgres-dev" -DisplayName "PostgreSQL" -MaxRetries 180
    Wait-ForContainerHealth -ContainerName "platform-redis-dev" -DisplayName "Redis" -MaxRetries 20
    Wait-ForContainerHealth -ContainerName "platform-kafka-dev" -DisplayName "Kafka" -MaxRetries 30 -SleepSeconds 3

    Write-Host "  Waiting for n8n..."
    $retries = 0
    while ($retries -lt 20) {
        $lb = [char]123 + [char]123
        $rb = [char]125 + [char]125
        $fmt = $lb + '.State.Health.Status' + $rb
        $health = docker inspect --format=$fmt platform-n8n-dev 2>$null
        if ($health -eq "healthy") { break }
        Start-Sleep -Seconds 3
        $retries++
    }
    if ($health -ne "healthy") {
        Write-Host "  N8N not healthy yet, continuing (it may take longer on first start)..." -ForegroundColor Yellow
    }
    
    Write-Host "  Infrastructure ready." -ForegroundColor Green
} else {
    Write-Host "`n[3/4] Skipping infrastructure start" -ForegroundColor DarkGray
}

# Step 4: Build and start all services
Write-Host "`n[4/4] Starting all services..." -ForegroundColor Yellow
docker compose -f $ComposeFile --env-file $EnvFile build --build-arg "JAVA_BASE_IMAGE=$JavaBaseImage"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  docker compose build failed. Attempting fallback: rebuild excluding superset-final..." -ForegroundColor Yellow
    # Try rebuilding excluding heavy superset-final service to allow other services to come up
    $svcList = docker compose -f $ComposeFile --env-file $EnvFile config --services 2>$null | Where-Object { $_ -ne 'superset-final' }
    if ($svcList -and $svcList.Count -gt 0) {
        Write-Host "  Rebuilding services: $($svcList -join ', ')" -ForegroundColor DarkGray
        docker compose -f $ComposeFile --env-file $EnvFile build --build-arg "JAVA_BASE_IMAGE=$JavaBaseImage" $svcList
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  Fallback build also failed." -ForegroundColor Red
            docker compose -f $ComposeFile --env-file $EnvFile ps
            throw "Docker compose image build failed (including fallback)"
        } else {
            Write-Host "  Fallback build succeeded (superset-final skipped)." -ForegroundColor Green
        }
    } else {
        Write-Host "  Could not determine service list for fallback." -ForegroundColor Red
        docker compose -f $ComposeFile --env-file $EnvFile ps
        throw "Docker compose image build failed"
    }
}
if (-not $SkipImagePull) {
    Write-Host "`n[0.5] Pulling images listed in compose (sequential, retries)" -ForegroundColor Yellow
    $images = docker compose -f $ComposeFile --env-file $EnvFile config --images 2>$null | Sort-Object -Unique
    $failedImages = @()
    foreach ($img in $images) {
        # Skip local dev tags (built locally)
        if ($img -like 'dev-*') { continue }
        $ok = Pull-ImageWithRetry -Image $img -MaxAttempts 5
        if (-not $ok) { $failedImages += $img }
    }
    if ($failedImages.Count -gt 0) {
        Write-Host "  Some images failed to pull:" -ForegroundColor Red
        $failedImages | ForEach-Object { Write-Host "    $_" }
        Write-Host "  You may need to check Docker proxy/network or pull these images manually." -ForegroundColor Yellow
        # Continue so fallback rebuild can still proceed for local images, but flag error for full up
    } else {
        Write-Host "  All external images pulled." -ForegroundColor Green
    }
}

if ($ServicesOnly -or $SkipInfra) {
    Write-Host "  Starting only non-infra services (skip infra)..." -ForegroundColor Yellow
    $infra = @('postgres','redis','kafka','n8n','superset-final')
    $allSvcs = docker compose -f $ComposeFile --env-file $EnvFile config --services 2>$null
    $startSvcs = $allSvcs | Where-Object { $infra -notcontains $_ }
    if ($startSvcs -and $startSvcs.Count -gt 0) {
        docker compose -f $ComposeFile --env-file $EnvFile up -d --no-deps $startSvcs
    } else {
        Write-Host "  No non-infra services to start." -ForegroundColor DarkGray
    }
} else {
    docker compose -f $ComposeFile --env-file $EnvFile up -d
}

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
Wait-ForContainerHealth -ContainerName "platform-edge-frontend-dev" -DisplayName "Edge frontend (single-origin)"

Write-Host "  Current service status:" -ForegroundColor Cyan
docker compose -f $ComposeFile --env-file $EnvFile ps

Write-Host "`n========================================" -ForegroundColor Green
Write-Host " Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

$EdgePort = "3000"
$AdminFePort = "3100"
$PortalFePort = "3101"
$DevFePort = "3102"
$LoginFePort = "3110"
if (Test-Path $EnvFile) {
    foreach ($line in Get-Content $EnvFile) {
        if ($line -match '^\s*EDGE_FRONTEND_PORT\s*=\s*(\S+)') {
            $EdgePort = $Matches[1]
        }
        if ($line -match '^\s*ADMIN_CENTER_FRONTEND_PORT\s*=\s*(\S+)') { $AdminFePort = $Matches[1] }
        if ($line -match '^\s*USER_PORTAL_FRONTEND_PORT\s*=\s*(\S+)') { $PortalFePort = $Matches[1] }
        if ($line -match '^\s*DEVELOPER_WORKSTATION_FRONTEND_PORT\s*=\s*(\S+)') { $DevFePort = $Matches[1] }
        if ($line -match '^\s*PLATFORM_LOGIN_FRONTEND_PORT\s*=\s*(\S+)') { $LoginFePort = $Matches[1] }
    }
}

Write-Host "Single-origin entry (SSO / daily use):" -ForegroundColor Yellow
Write-Host "  Edge (all SPAs + API):  http://localhost:$EdgePort/"
Write-Host "    Login:                http://localhost:$EdgePort/login/"
Write-Host "    Admin:                http://localhost:$EdgePort/admin/"
Write-Host "    Portal:               http://localhost:$EdgePort/portal/"
Write-Host "    Developer:            http://localhost:$EdgePort/dev/"
Write-Host "    N8N:                  http://localhost:$EdgePort/n8n/"
Write-Host "    API (via Kong):       http://localhost:$EdgePort/api/"
Write-Host ""

Write-Host "Backend:" -ForegroundColor Cyan
Write-Host "  Workflow Engine:        http://localhost:8081"
Write-Host "  Admin Center:           http://localhost:8090"
Write-Host "  User Portal:            http://localhost:8082"
Write-Host "  Developer Workstation:  http://localhost:8083"
Write-Host ""
Write-Host "Frontend (direct ports, optional / debugging):" -ForegroundColor DarkGray
Write-Host "  Admin Center:           http://localhost:$AdminFePort"
Write-Host "  User Portal:            http://localhost:$PortalFePort"
Write-Host "  Developer Workstation:  http://localhost:$DevFePort"
Write-Host "  Platform Login:         http://localhost:$LoginFePort"
Write-Host ""
Write-Host "Infrastructure:" -ForegroundColor Cyan
Write-Host "  PostgreSQL:             localhost:5432"
Write-Host "  Redis:                  localhost:6379"
Write-Host "  Kafka:                  localhost:9092"
Write-Host "  N8N (edge):             http://localhost:$EdgePort/n8n/"
Write-Host "  N8N (direct):           http://localhost:5678"
Write-Host "  Superset (direct):      http://localhost:8088/superset/welcome/"
Write-Host "  Superset (edge):        http://localhost:$EdgePort/superset/"
Write-Host ""
Write-Host "Commands:" -ForegroundColor DarkGray
Write-Host "  Logs:   docker compose -f docker-compose.dev.yml --env-file .env logs -f [service]"
Write-Host "  Superset logs: docker compose -f docker-compose.dev.yml --env-file .env logs -f superset-final"
Write-Host "  Stop:   docker compose -f docker-compose.dev.yml --env-file .env down"
Write-Host "  Reset:  .\build-and-deploy.ps1 -Clean"
