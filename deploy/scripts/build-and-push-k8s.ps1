#!/usr/bin/env pwsh
# =====================================================
# Build & Push Docker Images to K8S Registry
# =====================================================
# Usage:
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -Services "workflow-engine,admin-center"
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests -SkipFrontend
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -JavaBaseImage "docker.m.daocloud.io/library/eclipse-temurin:17-jre"
# =====================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Registry,

    [string]$Tag = "latest",
    [string]$Services = "all",
    # Prefer a domestic mirror so builds do not depend on Docker Hub metadata during docker build.
    [string]$JavaBaseImage = "docker.m.daocloud.io/library/eclipse-temurin:17-jre",
    [switch]$SkipTests = $false,
    # Skip "mvn clean": incremental compile, much faster for repeat release builds.
    # All jars are still re-packaged and all images still built & pushed.
    [switch]$NoClean = $false,
    [switch]$SkipFrontend = $false,
    [switch]$SkipBackend = $false,
    [switch]$PushOnly = $false,
    [switch]$NoPush = $false,
    # Max services built/pushed in parallel (docker build/push run concurrently per service)
    [int]$MaxParallel = 4
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# Backend services (no API Gateway — it's bypassed)
$BackendServices = @(
    @{ Name = "workflow-engine-core"; Dir = "backend/workflow-engine-core" },
    @{ Name = "admin-center"; Dir = "backend/admin-center" },
    @{ Name = "developer-workstation"; Dir = "backend/developer-workstation" },
    @{ Name = "user-portal"; Dir = "backend/user-portal" }
)

# Frontend services
$FrontendServices = @(
    @{ Name = "admin-center-frontend"; Dir = "frontend/admin-center" },
    @{ Name = "user-portal-frontend"; Dir = "frontend/user-portal" },
    @{ Name = "developer-workstation-frontend"; Dir = "frontend/developer-workstation" },
    @{ Name = "platform-login-frontend"; Dir = "frontend/login" }
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
Write-Host "  MaxParallel: $MaxParallel"
Write-Host "  JavaBaseImage: $JavaBaseImage"
Write-Host "=========================================" -ForegroundColor Yellow

# 0. Pre-pull Java runtime base (avoids docker build hitting Docker Hub for FROM metadata)
if (-not $SkipBackend -and -not $PushOnly) {
    Write-Step "Pre-pulling Java base image..."
    docker image inspect $JavaBaseImage 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "Java base image already present locally (skipping pull)"
        $pulled = $true
    } else {
        $pulled = $false
    }
    for ($attempt = 1; -not $pulled -and $attempt -le 3; $attempt++) {
        Write-Host "   Pulling $JavaBaseImage (attempt $attempt/3)..." -ForegroundColor Gray
        docker pull $JavaBaseImage 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Java base image present locally"
            $pulled = $true
            break
        }
        if ($attempt -lt 3) {
            Write-Host "   Retrying in 5s..." -ForegroundColor DarkGray
            Start-Sleep -Seconds 5
        }
    }
    if (-not $pulled) {
        Write-Host "   WARNING: Pre-pull failed; build may still use a cached base image." -ForegroundColor Yellow
    }
}

# 1. Maven Build
if (-not $SkipBackend -and -not $PushOnly) {
    Write-Step "Building backend with Maven..."

    $modules = "backend/platform-common,backend/platform-cache,backend/platform-security,backend/platform-messaging," + (($selectedBackend | ForEach-Object { $_.Dir }) -join ",")
    # -T 1C builds independent modules in parallel (one thread per CPU core)
    $goals = if ($NoClean) { @("package") } else { @("clean", "package") }
    $mvnArgs = $goals + @("-T", "1C", "-pl", $modules, "-am")
    # -Dmaven.test.skip=true skips compiling tests too (plain -DskipTests still compiles them)
    if ($SkipTests) { $mvnArgs += "-Dmaven.test.skip=true" }

    Push-Location $ProjectRoot
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Fail "Maven build failed" }
    Pop-Location
    Write-Ok "Maven build complete"
}

# 2. Docker Build & Push (Backend) — parallel across services
if (-not $SkipBackend) {
    Write-Step "Building backend Docker images (parallel x$MaxParallel)..."

    $backendJobs = foreach ($svc in $selectedBackend) {
        Start-ThreadJob -ThrottleLimit $MaxParallel -Name $svc.Name -ArgumentList @(
            $svc.Name, (Join-Path $ProjectRoot $svc.Dir), "$Registry/$($svc.Name):$Tag",
            $JavaBaseImage, [bool]$PushOnly, [bool]$NoPush
        ) -ScriptBlock {
            param($Name, $ContextDir, $ImageName, $JavaBaseImage, $PushOnly, $NoPush)
            # Use a native PowerShell array (not a generic List) so this runs under
            # Constrained Language Mode, where [System.Collections.Generic.List[...]]::new() is blocked.
            $log = @()
            if (-not $PushOnly) {
                $log += ">> [$Name] docker build"
                docker build --build-arg "JAVA_BASE_IMAGE=$JavaBaseImage" --pull=false -t $ImageName $ContextDir 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                if ($LASTEXITCODE -ne 0) { return @{ Name = $Name; Ok = $false; Stage = "build"; Log = $log } }
            }
            if (-not $NoPush) {
                $log += ">> [$Name] docker push"
                docker push $ImageName 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                if ($LASTEXITCODE -ne 0) { return @{ Name = $Name; Ok = $false; Stage = "push"; Log = $log } }
            }
            return @{ Name = $Name; Ok = $true; Log = $log }
        }
    }

    $backendResults = $backendJobs | Receive-Job -Wait -AutoRemoveJob
    foreach ($r in $backendResults) {
        $r.Log | ForEach-Object { Write-Host "   $_" -ForegroundColor Gray }
        if ($r.Ok) { Write-Ok $r.Name } else { Write-Fail "Backend $($r.Stage) failed: $($r.Name)" }
    }
}

# 3. Docker Build & Push (Frontend — local npm build + Dockerfile.local) — parallel across services
if (-not $SkipFrontend) {
    Write-Step "Building frontend (local npm build + Docker, parallel x$MaxParallel)..."

    $frontendJobs = foreach ($svc in $selectedFrontend) {
        Start-ThreadJob -ThrottleLimit $MaxParallel -Name $svc.Name -ArgumentList @(
            $svc.Name, (Join-Path $ProjectRoot $svc.Dir), "$Registry/$($svc.Name):$Tag",
            [bool]$PushOnly, [bool]$NoPush
        ) -ScriptBlock {
            param($Name, $ContextDir, $ImageName, $PushOnly, $NoPush)
            # Use a native PowerShell array (not a generic List) so this runs under
            # Constrained Language Mode, where [System.Collections.Generic.List[...]]::new() is blocked.
            $log = @()
            if (-not $PushOnly) {
                Push-Location $ContextDir
                try {
                    # Skip npm install when node_modules is already up to date (mtime compare)
                    $marker = Join-Path $ContextDir "node_modules\.package-lock.json"
                    $manifests = @("package.json", "package-lock.json") |
                        ForEach-Object { Join-Path $ContextDir $_ } | Where-Object { Test-Path $_ }
                    $newestManifest = ($manifests | ForEach-Object { (Get-Item $_).LastWriteTime } | Measure-Object -Maximum).Maximum
                    if ((Test-Path $marker) -and ((Get-Item $marker).LastWriteTime -ge $newestManifest)) {
                        $log += "[$Name] node_modules up to date, skipping npm install"
                    } else {
                        $log += ">> [$Name] npm install"
                        npm install --prefer-offline --no-audit 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                        if ($LASTEXITCODE -ne 0) { Pop-Location; return @{ Name = $Name; Ok = $false; Stage = "npm install"; Log = $log } }
                    }
                    # Remove auto-generated dts files before build to avoid Windows file locking (errno -4094)
                    Remove-Item -Path "src/components.d.ts", "src/auto-imports.d.ts" -Force -ErrorAction SilentlyContinue
                    $log += ">> [$Name] vite build"
                    npx vite build 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                    if ($LASTEXITCODE -ne 0) { Pop-Location; return @{ Name = $Name; Ok = $false; Stage = "vite build"; Log = $log } }
                } finally {
                    Pop-Location
                }

                $log += ">> [$Name] docker build (Dockerfile.local)"
                docker build -f "$ContextDir/Dockerfile.local" -t $ImageName $ContextDir 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                if ($LASTEXITCODE -ne 0) { return @{ Name = $Name; Ok = $false; Stage = "docker build"; Log = $log } }
            }

            if (-not $NoPush) {
                $log += ">> [$Name] docker push"
                docker push $ImageName 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                if ($LASTEXITCODE -ne 0) { return @{ Name = $Name; Ok = $false; Stage = "push"; Log = $log } }
            }
            return @{ Name = $Name; Ok = $true; Log = $log }
        }
    }

    $frontendResults = $frontendJobs | Receive-Job -Wait -AutoRemoveJob
    foreach ($r in $frontendResults) {
        $r.Log | ForEach-Object { Write-Host "   $_" -ForegroundColor Gray }
        if ($r.Ok) { Write-Ok $r.Name } else { Write-Fail "Frontend $($r.Stage) failed: $($r.Name)" }
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
