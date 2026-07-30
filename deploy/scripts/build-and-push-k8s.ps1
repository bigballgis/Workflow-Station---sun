#!/usr/bin/env pwsh
# =====================================================
# Build & Push Docker Images to K8S Registry
# =====================================================
# Usage:
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -Services "workflow-engine,admin-center"
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests -SkipFrontend
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -JavaBaseImage "docker.m.daocloud.io/library/eclipse-temurin:17-jre"
#   # host whose npm mirror cannot satisfy the AP workspace: reuse a carried-over
#   # activepieces/dist/packages/web-embed instead of installing and rebuilding it
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipApWorkspaceInstall
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
    # Air-gapped / partially-mirrored host: do not even attempt the Activepieces workspace
    # install or the builder-bundle build. Requires a prebuilt activepieces/dist/packages/
    # web-embed carried over from a host that can build it, which is then reused as-is.
    [switch]$SkipApWorkspaceInstall = $false,
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

# 3. Docker Build & Push (Frontend — local pnpm build + Dockerfile.local) — parallel across services
if (-not $SkipFrontend) {
    Write-Step "Building frontend (local pnpm build + Docker, parallel x$MaxParallel)..."

    # The developer-workstation frontend embeds the Activepieces builder. That bundle is
    # produced by the AP workspace (outside frontend/) into activepieces/dist/packages/
    # web-embed and is gitignored, so a clean checkout never has it; DW's pnpm `prebuild`
    # hook only COPIES it into public/. Nothing else builds it, so build it here — before
    # the per-service jobs start, since DW's build consumes it. Skip this and the image
    # ships without the bundle and 404s on /dev/service-task-builder/web.css.
    $needsApBuilder = @($selectedFrontend | Where-Object { $_.Name -eq "developer-workstation-frontend" }).Count -gt 0
    if ($needsApBuilder -and -not $PushOnly) {
        $apRootDir = Join-Path $ProjectRoot "activepieces"
        $apWebDir = Join-Path $apRootDir "packages/web"
        $embedMarker = Join-Path $ProjectRoot "activepieces/dist/packages/web-embed/ap-builder.mjs"

        # Install the AP workspace deps on the same terms the per-service jobs below use for
        # the four frontends (.modules.yaml mtime vs manifests), so a clean checkout needs no
        # manual prerequisite. Install at the workspace ROOT: this is a pnpm workspace and the
        # root install is what links packages/web's deps. Two things justify the extra noise
        # over the frontend installs — it is heavy (~3 GB on a clean checkout) and it is the
        # only step in this script that must reach a registry.
        # Get-Item needs -Force for this marker: a dot-prefixed name is a hidden file on
        # macOS/Linux, where Test-Path still says True but Get-Item throws "Could not find
        # item" without it. Harmless on Windows, where the name is not hidden at all.
        $apMarker = Join-Path $apRootDir "node_modules/.modules.yaml"
        $apManifests = @("package.json", "pnpm-lock.yaml") |
            ForEach-Object { Join-Path $apRootDir $_ } | Where-Object { Test-Path $_ }
        $apNewestManifest = ($apManifests | ForEach-Object { (Get-Item $_).LastWriteTime } | Measure-Object -Maximum).Maximum
        if ($SkipApWorkspaceInstall) {
            # For a host where the install is known to be impossible (private mirror missing or
            # quarantining packages): skip the doomed ~10-minute attempt outright and go
            # straight to whatever bundle was carried over.
            Write-Host "   [ap-builder] -SkipApWorkspaceInstall: not touching the AP workspace" -ForegroundColor Gray
        } elseif ((Test-Path $apMarker) -and ((Get-Item -Force $apMarker).LastWriteTime -ge $apNewestManifest)) {
            Write-Host "   [ap-builder] AP workspace deps up to date, skipping pnpm install" -ForegroundColor Gray
        } else {
            Write-Host "   >> [ap-builder] pnpm install (AP workspace, ~3 GB on a clean checkout, needs registry access; once per checkout)" -ForegroundColor Yellow
            Push-Location $apRootDir
            try {
                pnpm install --frozen-lockfile
                # Not fatal here: a host that cannot reach a registry may still carry a
                # prebuilt bundle, which the reuse path below accepts.
                if ($LASTEXITCODE -ne 0) { Write-Host "   WARNING: AP workspace pnpm install failed." -ForegroundColor Yellow }
            } finally {
                Pop-Location
            }
        }

        # An air-gapped build host that cannot install may instead carry the bundle over from
        # a machine that can build it. Reuse it in that case; only fail when there is neither
        # a way to build the bundle nor a bundle to reuse.
        $haveBundle = Test-Path $embedMarker
        # node_modules existing does not prove the workspace is COMPLETE — a fetch that died
        # partway (private mirror missing or quarantining a package) can leave the directory
        # behind, and then the vite build fails on missing deps. So treat the build as an
        # attempt, not a guarantee, and fall back to a carried-over bundle when it fails.
        $canTryBuild = (-not $SkipApWorkspaceInstall) -and (Test-Path (Join-Path $apWebDir "node_modules"))
        if (-not $canTryBuild -and -not $haveBundle) {
            Write-Fail "Activepieces workspace deps are missing ($apWebDir/node_modules) and there is no prebuilt bundle at $embedMarker. Fix the install (see BUILD_GUIDE 7.2 step 0 — its output is above), copy a prebuilt activepieces/dist/packages/web-embed over, or exclude developer-workstation-frontend with -Services."
        }
        $built = $false
        if ($canTryBuild) {
            Write-Host "   >> [ap-builder] vite build (web-embed)" -ForegroundColor Gray
            Push-Location $apWebDir
            try {
                pnpm exec vite build --config vite.embed.config.mts
                $built = $LASTEXITCODE -eq 0
            } finally {
                Pop-Location
            }
            if ($built) {
                Write-Ok "ap-builder (web-embed)"
            } elseif (-not $haveBundle) {
                Write-Fail "Activepieces web-embed build failed and there is no prebuilt bundle at $embedMarker to fall back on."
            }
        }
        if (-not $built) {
            # Reused, not rebuilt: whoever copied it in owns its freshness. Loud on purpose —
            # a stale bundle ships an outdated builder into the image and nothing else warns.
            Write-Host "   WARNING: REUSING the prebuilt bundle at $embedMarker without rebuilding it (AP workspace unusable here). Verify it matches this commit." -ForegroundColor Yellow
            Write-Ok "ap-builder (web-embed, reused)"
        }
    }

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
                    # Skip the install when node_modules is already up to date (mtime compare).
                    # .modules.yaml is pnpm's post-install marker, the counterpart of npm's
                    # node_modules/.package-lock.json.
                    $marker = Join-Path $ContextDir "node_modules/.modules.yaml"
                    $manifests = @("package.json", "pnpm-lock.yaml") |
                        ForEach-Object { Join-Path $ContextDir $_ } | Where-Object { Test-Path $_ }
                    $newestManifest = ($manifests | ForEach-Object { (Get-Item $_).LastWriteTime } | Measure-Object -Maximum).Maximum
                    if ((Test-Path $marker) -and ((Get-Item -Force $marker).LastWriteTime -ge $newestManifest)) {
                        $log += "[$Name] node_modules up to date, skipping pnpm install"
                    } else {
                        # --frozen-lockfile: a release build must fail on a lockfile that does
                        # not match package.json rather than quietly resolving something else.
                        $log += ">> [$Name] pnpm install"
                        pnpm install --frozen-lockfile 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                        if ($LASTEXITCODE -ne 0) { Pop-Location; return @{ Name = $Name; Ok = $false; Stage = "pnpm install"; Log = $log } }
                    }
                    # Remove auto-generated dts files before build to avoid Windows file locking (errno -4094)
                    Remove-Item -Path "src/components.d.ts", "src/auto-imports.d.ts" -Force -ErrorAction SilentlyContinue
                    # `pnpm run build`, not a bare vite call: the latter bypasses the
                    # `prebuild` hook, which is what copies the Activepieces builder bundle
                    # into DW's public/. All four frontends declare "build": "vite build",
                    # so this is equivalent for the others.
                    # SERVICE_TASK_BUILDER_REQUIRED makes DW's prebuild FAIL on a missing
                    # bundle rather than warn-and-continue — a release build must not
                    # silently produce an image without it.
                    if ($Name -eq "developer-workstation-frontend") { $env:SERVICE_TASK_BUILDER_REQUIRED = "1" }
                    $log += ">> [$Name] pnpm run build"
                    pnpm run build 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                    if ($LASTEXITCODE -ne 0) { Pop-Location; return @{ Name = $Name; Ok = $false; Stage = "pnpm run build"; Log = $log } }
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
