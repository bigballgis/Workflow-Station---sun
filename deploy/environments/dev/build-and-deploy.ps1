#!/usr/bin/env pwsh
# =====================================================
# Dev Environment - Build and Deploy Script
# =====================================================
# Usage:
#   .\build-and-deploy.ps1                    # Incremental build & deploy (skip fresh artifacts)
#   .\build-and-deploy.ps1 -Service admin-center  # Build & deploy only admin-center
#   .\build-and-deploy.ps1 -Service admin-center -SkipMaven  # Redeploy without Maven rebuild
#   .\build-and-deploy.ps1 -SkipMaven         # Skip Maven, rebuild Docker only
#   .\build-and-deploy.ps1 -SkipFrontend      # Skip frontend image builds
#   .\build-and-deploy.ps1 -SkipInfra         # Skip infra startup (PG/Redis already running)
#   .\build-and-deploy.ps1 -SkipImagePull     # Skip pre-pulling base images (if already cached)
#   .\build-and-deploy.ps1 -JavaBaseImage "docker.m.daocloud.io/library/eclipse-temurin:17-jre"  # Backend FROM / compose build-arg
#   .\build-and-deploy.ps1 -Clean             # Destroy volumes + full clean rebuild (Maven clean, all images)
#   .\build-and-deploy.ps1 -ServicesOnly      # Only restart backend+frontend (no Maven, no infra)
#   .\build-and-deploy.ps1 -SkipMavenClean    # Maven package without clean (avoids clean delete failures)
#   .\build-and-deploy.ps1 -RebuildServiceTaskBuilder  # Force-rebuild the AP Automation builder bundle
#   .\build-and-deploy.ps1 -ForceBuild        # Ignore freshness checks; rebuild everything once
#
# Incremental strategy (default, without -Clean / -ForceBuild):
#   - Skip Maven when backend JARs are newer than sources/poms
#   - Skip Vite when frontend dist is newer than sources/config
#   - Skip Docker build when image is newer than Dockerfile + build inputs
#   - Schema SQL runs only when file content SHA-256 changed (per Postgres volume)
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
    [switch]$SkipMavenClean,
    # Force-rebuild the vendored AP ServiceTask/Automation builder bundle even when present
    # (activepieces/dist/packages/web-embed). Pass this after changing the AP builder source
    # or host-config; otherwise the existing bundle is reused (the build is heavy).
    [switch]$RebuildServiceTaskBuilder,
    # Ignore artifact/image freshness; rebuild Maven/Vite/Docker once (still no -v unless -Clean).
    [switch]$ForceBuild
)

$ErrorActionPreference = "Stop"
$RootDir = Resolve-Path "$PSScriptRoot/../../.."
$ComposeFile = "$PSScriptRoot/docker-compose.dev.yml"
$EnvFile = "$PSScriptRoot/.env"

# Load .env into PowerShell session so $env:VAR works for docker exec / scripting.
# docker compose reads .env automatically, but inline docker exec commands do not.
if (Test-Path $EnvFile) {
    foreach ($line in Get-Content $EnvFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)') {
            $key = $Matches[1]
            $value = $Matches[2]
            # Only set if not already present in the environment (inline .env should not
            # override host-level env vars that the user explicitly exported).
            if (-not (Test-Path "env:$key")) {
                [Environment]::SetEnvironmentVariable($key, $value)
            }
        }
    }
}

# NOTE: the admin "Activepieces" launcher URL is injected at RUNTIME (not build time) via
# AP_BRIDGE_URL on the admin-center-frontend container (docker-entrypoint.sh -> config.js).
# See the compose service env. The frontend image is built once and promoted across envs,
# so a build-time value can't differ per environment.

# ==================== Service Registry ====================
# Maps compose service name -> Maven module path / frontend dir / type
$ServiceRegistry = @{
    "workflow-engine" = @{
        Maven = "backend/workflow-engine-core"
        Type  = "backend"
    }
    "admin-center" = @{
        Maven = "backend/admin-center"
        Type  = "backend"
    }
    "user-portal" = @{
        Maven = "backend/user-portal"
        Type  = "backend"
    }
    "developer-workstation" = @{
        Maven = "backend/developer-workstation"
        Type  = "backend"
    }
    "admin-center-frontend" = @{
        FrontendDir = "frontend/admin-center"
        Type        = "frontend"
    }
    "user-portal-frontend" = @{
        FrontendDir = "frontend/user-portal"
        Type        = "frontend"
    }
    "developer-workstation-frontend" = @{
        FrontendDir = "frontend/developer-workstation"
        Type        = "frontend"
    }
    "platform-login-frontend" = @{
        FrontendDir = "frontend/login"
        Type        = "frontend"
    }
    "edge-frontend" = @{
        Type = "edge"
    }
}

# Services whose start may be skipped when optional deps (private npm) fail.
# Superset is REQUIRED (fail-closed on missing URI / init); keep Activepieces optional.
$OptionalComposeServices = @('activepieces')

# Validate -Service parameter
if ($Service -and -not $ServiceRegistry.ContainsKey($Service)) {
    Write-Host "Unknown service: $Service" -ForegroundColor Red
    Write-Host "Valid services: $($ServiceRegistry.Keys -join ', ')" -ForegroundColor Yellow
    exit 1
}

function Get-ComposeContainerId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceName
    )

    $raw = docker compose -f $ComposeFile --env-file $EnvFile ps -q $ServiceName 2>$null
    if (-not $raw) { return $null }
    $id = ($raw -split "\r?\n" | Where-Object { $_ -match '\S' } | Select-Object -First 1)
    if ($id) { return $id.Trim() }
    return $null
}

function Get-ComposeContainerStatus {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ContainerId
    )

    $lb = [char]123 + [char]123
    $rb = [char]125 + [char]125
    $fmt = $lb + 'if .State.Health' + $rb + $lb + '.State.Health.Status' + $rb + $lb + 'else' + $rb + $lb + '.State.Status' + $rb + $lb + 'end' + $rb
    return docker inspect --format=$fmt $ContainerId 2>$null
}

function Wait-ForContainerHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceName,
        [Parameter(Mandatory = $true)]
        [string]$DisplayName,
        [int]$MaxRetries = 60,
        [int]$SleepSeconds = 2
    )

    Write-Host "  Waiting for $DisplayName ($ServiceName)..."
    $attempt = 0
    while ($attempt -lt $MaxRetries) {
        $containerId = Get-ComposeContainerId -ServiceName $ServiceName
        if ($containerId) {
            $status = Get-ComposeContainerStatus -ContainerId $containerId
            if ($status -eq "healthy") {
                Write-Host "    $DisplayName is healthy (container $containerId)." -ForegroundColor Green
                return $containerId
            }
            if ($status -eq "exited" -or $status -eq "dead") {
                throw "$DisplayName ($ServiceName) container stopped unexpectedly (status=$status, id=$containerId)"
            }
        }
        Start-Sleep -Seconds $SleepSeconds
        $attempt++
    }
    throw "$DisplayName ($ServiceName) failed to become healthy in time"
}

function Wait-ForContainerRunning {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceName,
        [Parameter(Mandatory = $true)]
        [string]$DisplayName,
        [int]$MaxRetries = 30,
        [int]$SleepSeconds = 2
    )

    Write-Host "  Waiting for $DisplayName ($ServiceName) to run..."
    $attempt = 0
    while ($attempt -lt $MaxRetries) {
        $containerId = Get-ComposeContainerId -ServiceName $ServiceName
        if ($containerId) {
            $lb = [char]123 + [char]123
            $rb = [char]125 + [char]125
            $fmt = $lb + '.State.Status' + $rb
            $status = docker inspect --format=$fmt $containerId 2>$null
            if ($status -eq "running") {
                Write-Host "    $DisplayName is running (container $containerId)." -ForegroundColor Green
                return $containerId
            }
            if ($status -eq "exited" -or $status -eq "dead") {
                throw "$DisplayName ($ServiceName) container stopped unexpectedly (status=$status, id=$containerId)"
            }
        }
        Start-Sleep -Seconds $SleepSeconds
        $attempt++
    }
    throw "$DisplayName ($ServiceName) failed to reach running state in time"
}

function Invoke-ComposeUpSequential {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$ServiceNames,
        [switch]$NoDeps,
        [switch]$ForceRecreate,
        [string[]]$OptionalServices = $OptionalComposeServices
    )

    foreach ($svcName in $ServiceNames) {
        if (-not $svcName) { continue }
        Write-Host "  Starting $svcName..." -ForegroundColor DarkGray
        $composeArgs = @('-f', $ComposeFile, '--env-file', $EnvFile, 'up', '-d')
        if ($NoDeps) { $composeArgs += '--no-deps' }
        if ($ForceRecreate) { $composeArgs += '--force-recreate' }
        $composeArgs += $svcName

        $started = $false
        for ($attempt = 1; $attempt -le 2; $attempt++) {
            if ($attempt -gt 1) {
                Write-Host "    Retrying $svcName (attempt $attempt/2) after compose/named-pipe glitch..." -ForegroundColor Yellow
                Start-Sleep -Seconds 3
            }
            docker compose @composeArgs
            if ($LASTEXITCODE -eq 0) {
                $started = $true
                break
            }
        }

        if (-not $started) {
            if ($OptionalServices -contains $svcName) {
                Write-Host "    WARNING: Skipping optional service $svcName (image missing or compose glitch)." -ForegroundColor Yellow
                continue
            }
            throw "Failed to start compose service: $svcName"
        }
    }
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

        # Try a lightweight fallback: strip known registry host prefix (e.g. ghcr.io/org/image -> org/image)
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

# Pull a base image with fallback mirrors. Returns the first pullable image name.
# Used for Java and nginx base images that different registries may host.
function Resolve-BaseImage {
    param(
        [Parameter(Mandatory=$true)]
        [string[]]$Candidates
    )

    foreach ($img in $Candidates) {
        Write-Host "  Trying base image: $img" -ForegroundColor DarkGray
        $pullOutput = docker pull $img 2>&1
        $pullExit = $LASTEXITCODE
        # Show last 3 lines of pull output for context (but keep function output stream clean)
        $pullOutput | Select-Object -Last 3 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        if ($pullExit -eq 0) {
            Write-Host "  Base image resolved: $img" -ForegroundColor Green
            return $img
        }
        Write-Host "  Pull failed for $img, trying next candidate..." -ForegroundColor Yellow
    }

    throw "Cannot pull any base image from candidates: $($Candidates -join ', '). Check Docker network/proxy."
}

# Build the vendored Activepieces "ServiceTask / Automation" builder bundle
# (activepieces/packages/web -> activepieces/dist/packages/web-embed). The DW frontend's
# `prebuild` hook (scripts/sync-service-task-builder.mjs) copies it into
# public/service-task-builder (gitignored), so a clean checkout has NOTHING to copy unless
# this runs first — the Automation tab would then report the builder assets unavailable.
# Heavy (full AP web bundle), so build only when the bundle is missing, or when forced.
#
# Returns $true only when it actually produced a new bundle. Callers MUST use that to
# force their vite step: the bundle reaches dist/ solely through DW's `prebuild` hook,
# which runs inside `npm run build`, and Test-FrontendDistFresh watches DW's own sources
# — not activepieces/dist/packages/web-embed. Ignore the return value and a rebuilt
# bundle is silently dropped whenever DW's own dist still looks fresh.
function Ensure-ServiceTaskBuilderBundle {
    param([switch]$Force)

    $embedMarker = Join-Path $RootDir "activepieces/dist/packages/web-embed/ap-builder.mjs"
    if (-not $Force -and (Test-Path $embedMarker)) {
        Write-Host "  ServiceTask builder bundle present (reuse; pass -RebuildServiceTaskBuilder to force)." -ForegroundColor DarkGray
        return $false
    }

    $webDir = Join-Path $RootDir "activepieces/packages/web"
    if (-not (Test-Path (Join-Path $webDir "vite.embed.config.mts"))) {
        Write-Host "  WARNING: activepieces embed config not found; DW Automation builder will be unavailable." -ForegroundColor Yellow
        return $false
    }

    Write-Host "  Building ServiceTask/Automation builder bundle (activepieces/packages/web, heavy)..." -ForegroundColor Yellow
    $built = $false
    Push-Location $webDir
    try {
        # X-4: npx/pnpm toolchain only, never bun.
        # Out-Host keeps vite's stdout off this function's output stream — without it the
        # build log would be collected into the return value alongside the boolean.
        npx vite build --config vite.embed.config.mts | Out-Host
        if ($LASTEXITCODE -ne 0) {
            # Non-fatal: the DW build still succeeds; the Automation tab just reports the
            # builder unavailable (the sync hook is deliberately tolerant). Flag it loudly.
            Write-Host "  WARNING: embed bundle build failed — DW Automation tab will lack the builder." -ForegroundColor Yellow
        } else {
            Write-Host "  ServiceTask builder bundle built." -ForegroundColor Green
            $built = $true
        }
    } finally {
        Pop-Location
    }
    return $built
}

# ==================== Incremental build helpers ====================

function Get-NewestFileTime {
    param([Parameter(Mandatory = $true)][string[]]$Paths)
    $newest = [datetime]::MinValue
    foreach ($p in $Paths) {
        if (-not $p -or -not (Test-Path -LiteralPath $p)) { continue }
        Get-ChildItem -LiteralPath $p -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '[\\/](node_modules|target|\.git|dist)[\\/]' -or $p -match 'dist$' } |
            ForEach-Object {
                if ($_.LastWriteTimeUtc -gt $newest) { $newest = $_.LastWriteTimeUtc }
            }
        $item = Get-Item -LiteralPath $p -ErrorAction SilentlyContinue
        if ($item -and $item.LastWriteTimeUtc -gt $newest) { $newest = $item.LastWriteTimeUtc }
    }
    return $newest
}

function Get-BackendJarPath {
    param([Parameter(Mandatory = $true)][string]$MavenModule)
    $target = Join-Path $RootDir "$MavenModule/target"
    if (-not (Test-Path $target)) { return $null }
    $jar = Get-ChildItem -Path $target -Filter "*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.jar.original" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($jar) { return $jar.FullName }
    return $null
}

function Test-BackendJarsFresh {
    $modules = @(
        "backend/platform-common",
        "backend/platform-cache",
        "backend/platform-security",
        "backend/platform-messaging",
        "backend/workflow-engine-core",
        "backend/admin-center",
        "backend/developer-workstation",
        "backend/user-portal"
    )
    foreach ($mod in $modules) {
        $jar = Get-BackendJarPath -MavenModule $mod
        if (-not $jar) { return $false }
        $jarTime = (Get-Item -LiteralPath $jar).LastWriteTimeUtc
        $srcDir = Join-Path $RootDir "$mod/src"
        $pom = Join-Path $RootDir "$mod/pom.xml"
        $srcTime = Get-NewestFileTime -Paths @($srcDir, $pom)
        if ($srcTime -gt $jarTime) { return $false }
    }
    $rootPom = Join-Path $RootDir "pom.xml"
    $oldestJar = $null
    foreach ($mod in $modules) {
        $jar = Get-BackendJarPath -MavenModule $mod
        $t = (Get-Item -LiteralPath $jar).LastWriteTimeUtc
        if ($null -eq $oldestJar -or $t -lt $oldestJar) { $oldestJar = $t }
    }
    if ((Test-Path $rootPom) -and ((Get-Item $rootPom).LastWriteTimeUtc -gt $oldestJar)) {
        return $false
    }
    return $true
}

function Test-FrontendDistFresh {
    param([Parameter(Mandatory = $true)][string]$FrontendDir)
    $feRoot = Join-Path $RootDir $FrontendDir
    $distIndex = Join-Path $feRoot "dist/index.html"
    if (-not (Test-Path -LiteralPath $distIndex)) { return $false }
    $distTime = (Get-Item -LiteralPath $distIndex).LastWriteTimeUtc
    $watch = @(
        (Join-Path $feRoot "src"),
        (Join-Path $feRoot "public"),
        (Join-Path $feRoot "index.html"),
        (Join-Path $feRoot "package.json"),
        (Join-Path $feRoot "package-lock.json"),
        (Join-Path $feRoot "vite.config.ts"),
        (Join-Path $feRoot "vite.config.js"),
        (Join-Path $feRoot "tsconfig.json"),
        (Join-Path $feRoot "tsconfig.app.json")
    )
    $srcTime = Get-NewestFileTime -Paths $watch
    return ($srcTime -le $distTime)
}

function Get-DockerImageCreatedUtc {
    param([Parameter(Mandatory = $true)][string]$ImageName)
    $raw = docker image inspect $ImageName --format "{{.Created}}" 2>$null
    if (-not $raw -or $LASTEXITCODE -ne 0) { return $null }
    try {
        return [datetime]::Parse($raw.Trim(), $null, [System.Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
    } catch {
        return $null
    }
}

function Test-DockerImageFresh {
    param(
        [Parameter(Mandatory = $true)][string]$ImageName,
        [Parameter(Mandatory = $true)][string[]]$InputPaths
    )
    $created = Get-DockerImageCreatedUtc -ImageName $ImageName
    if ($null -eq $created) { return $false }
    $inputTime = Get-NewestFileTime -Paths $InputPaths
    if ($inputTime -eq [datetime]::MinValue) { return $false }
    return ($inputTime -le $created)
}

function Resolve-SupersetPipConfFile {
    if ($env:SUPERSET_PIP_CONF_FILE -and (Test-Path -LiteralPath $env:SUPERSET_PIP_CONF_FILE)) {
        return (Resolve-Path -LiteralPath $env:SUPERSET_PIP_CONF_FILE).Path
    }
    $local = Join-Path $RootDir "deploy/superset/pip.conf"
    if (Test-Path -LiteralPath $local) {
        return (Resolve-Path -LiteralPath $local).Path
    }
    $example = Join-Path $RootDir "deploy/superset/pip.conf.example"
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) "hermes-superset-pip.conf"
    if (Test-Path -LiteralPath $example) {
        Copy-Item -LiteralPath $example -Destination $tmp -Force
    } else {
        @"
[global]
index-url = https://pypi.org/simple
trusted-host = pypi.org files.pythonhosted.org
"@ | Set-Content -LiteralPath $tmp -Encoding ascii
    }
    Write-Host "  Using ephemeral pip.conf for Superset build (copy pip.conf.example -> deploy/superset/pip.conf for Nexus)." -ForegroundColor DarkGray
    return $tmp
}

function Get-PostgresVolumeMarkerDir {
    $volName = docker volume ls -q 2>$null | Where-Object { $_ -match 'postgres_dev_data$' } | Select-Object -First 1
    if (-not $volName) {
        return (Join-Path $PSScriptRoot ".schema-migration-markers\_no_volume")
    }
    $created = docker volume inspect $volName --format "{{.CreatedAt}}" 2>$null
    if (-not $created) { $created = "unknown" }
    $safe = (($volName + "_" + $created) -replace '[^A-Za-z0-9._-]', '_')
    return (Join-Path $PSScriptRoot ".schema-migration-markers\$safe")
}

function Invoke-SchemaMigrations {
    param(
        [Parameter(Mandatory = $true)][string]$PostgresContainerId
    )

    $InitScriptsDir = Join-Path $RootDir "deploy/init-scripts/00-schema"
    if (-not (Test-Path $InitScriptsDir)) {
        Write-Host "  No schema dir at $InitScriptsDir — skipping." -ForegroundColor DarkGray
        return
    }

    $markerDir = Get-PostgresVolumeMarkerDir
    New-Item -ItemType Directory -Force -Path $markerDir | Out-Null
    Write-Host "  Schema migration markers: $markerDir" -ForegroundColor DarkGray

    # Orchestrators use psql \i includes; they are for host-side `psql -f` only.
    # This runner pipes SQL via stdin into docker exec, so \i cannot resolve paths —
    # and the numbered 01-*.sql / 06-*.sql files are already applied individually below
    # (same approach as 00-init-all.sh / init-database.ps1).
    $orchestratorNames = @(
        "00-init-all-schemas.sql",
        "00-init-all-schemas-standalone.sql"
    )

    $files = Get-ChildItem -Path $InitScriptsDir -Filter "*.sql" | Sort-Object Name
    foreach ($file in $files) {
        if ($orchestratorNames -contains $file.Name) {
            Write-Host "    skip (orchestrator) $($file.Name)" -ForegroundColor DarkGray
            continue
        }

        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $markerPath = Join-Path $markerDir ($file.Name + ".sha256")
        if ((Test-Path -LiteralPath $markerPath) -and ((Get-Content -LiteralPath $markerPath -Raw).Trim().ToLowerInvariant() -eq $hash)) {
            Write-Host "    skip (unchanged) $($file.Name)" -ForegroundColor DarkGray
            continue
        }

        Write-Host "    apply $($file.Name)" -ForegroundColor DarkGray
        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $output = Get-Content -LiteralPath $file.FullName -Raw | docker exec -i $PostgresContainerId `
            psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 2>&1
        $exit = $LASTEXITCODE
        $ErrorActionPreference = $prevEAP
        if ($exit -ne 0) {
            Write-Host $output -ForegroundColor Red
            throw "Schema migration failed: $($file.Name) (exit $exit). Aborting — will not treat as idempotent warning."
        }
        Set-Content -LiteralPath $markerPath -Value $hash -Encoding ascii -NoNewline
    }
    Write-Host "  Schema migrations complete." -ForegroundColor Green
}

function Invoke-SupersetBootstrap {
    $container = "platform-superset-dev"
    Write-Host "  Bootstrapping Superset (db upgrade + init)..." -ForegroundColor Yellow
    Wait-ForContainerHealth -ServiceName "superset-final" -DisplayName "Superset" -MaxRetries 90 -SleepSeconds 3

    docker exec $container superset db upgrade
    if ($LASTEXITCODE -ne 0) { throw "superset db upgrade failed" }

    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    docker exec $container superset fab create-admin `
        --username admin --firstname Admin --lastname User `
        --email admin@superset.com --password admin123 2>&1 | Out-Null
    $createExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEAP
    # create-admin exits non-zero when the user already exists — that is OK.
    if ($createExit -ne 0) {
        Write-Host "    create-admin exited $createExit (OK if admin already exists)." -ForegroundColor DarkGray
    }

    docker exec $container superset init
    if ($LASTEXITCODE -ne 0) { throw "superset init failed — aborting (no longer treated as warning)" }
    Write-Host "  Superset bootstrap complete." -ForegroundColor Green
}

$env:DOCKER_BUILDKIT = "1"
$env:SUPERSET_PIP_CONF_FILE = Resolve-SupersetPipConfFile

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
        # DW's Automation tab embeds the vendored AP builder; stage its bundle first so the
        # `prebuild` hook has something to copy into public/service-task-builder.
        # Return value discarded on purpose: this path always runs vite below, so there is
        # no freshness check to override.
        if ($Service -eq "developer-workstation-frontend") {
            $null = Ensure-ServiceTaskBuilderBundle -Force:$RebuildServiceTaskBuilder
        }
        $feDir = "$RootDir/$($svc.FrontendDir)"
        Push-Location $feDir
        try {
            # Plain `pnpm install` (no --frozen-lockfile): dev is where dependencies get
            # added, so the lockfile is allowed to move here. Release builds use
            # --frozen-lockfile instead (deploy/scripts/build-and-push-k8s.ps1).
            Write-Host "  pnpm install..." -ForegroundColor DarkGray
            $prev = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            pnpm install
            $installExit = $LASTEXITCODE
            $ErrorActionPreference = $prev
            if ($installExit -ne 0) { throw "pnpm install failed: $Service (exit code $installExit)" }
            # Remove auto-generated dts files before build to avoid Windows file locking (errno -4094)
            Remove-Item -Path "src/components.d.ts", "src/auto-imports.d.ts" -Force -ErrorAction SilentlyContinue
            # `pnpm run build` (not a bare vite call) so the DW `prebuild` hook runs and
            # stages the ServiceTask builder bundle; a no-op difference for the other frontends.
            pnpm run build
            if ($LASTEXITCODE -ne 0) { throw "pnpm run build failed: $Service" }
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
        # Resolve Java base image with fallback before single-service backend build
        $resolvedJavaImage = Resolve-BaseImage -Candidates @(
            $JavaBaseImage,
            "eclipse-temurin:17-jre",
            "docker.m.daocloud.io/library/eclipse-temurin:17-jre"
        )
        docker compose -f $ComposeFile --env-file $EnvFile build --build-arg "JAVA_BASE_IMAGE=$resolvedJavaImage" $Service
        if ($LASTEXITCODE -ne 0) { throw "Docker compose build failed for $Service" }
        docker compose -f $ComposeFile --env-file $EnvFile up -d --no-deps $Service
    }
    if ($LASTEXITCODE -ne 0) { throw "Failed to deploy $Service" }

    if ($svc.Type -eq "backend" -or $svc.Type -eq "edge") {
        Wait-ForContainerHealth -ServiceName $Service -DisplayName $Service
    } else {
        Wait-ForContainerRunning -ServiceName $Service -DisplayName $Service -MaxRetries 15
    }

    # Kong caches upstream A records; after backend recreate Docker DNS may change before Kong refreshes.
    if ($svc.Type -eq "backend") {
        Write-Host "  Restarting kong to refresh upstream DNS..." -ForegroundColor DarkGray
        docker compose -f $ComposeFile --env-file $EnvFile restart kong
        if ($LASTEXITCODE -ne 0) { throw "Failed to restart kong after deploying $Service" }
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

$env:SUPERSET_PIP_CONF_FILE = Resolve-SupersetPipConfFile
# (DOCKER_BUILDKIT already set above)

# Step 1: Maven build (incremental unless -Clean / -ForceBuild)
if (-not $SkipMaven) {
    $mavenFresh = (-not $Clean) -and (-not $ForceBuild) -and (Test-BackendJarsFresh)
    if ($mavenFresh) {
        Write-Host "`n[1/4] Skipping Maven (backend JARs are fresh)." -ForegroundColor DarkGray
    } else {
        Write-Host "`n[1/4] Building backend JARs (Maven)..." -ForegroundColor Yellow
        Push-Location $RootDir
        try {
            # Full `mvn clean` only with -Clean. Otherwise package (optionally without clean plugin wipe).
            if ($Clean -and -not $SkipMavenClean) {
                Write-Host "  Pre-clean: removing backend/*/target..." -ForegroundColor DarkGray
                Get-ChildItem -Path (Join-Path $RootDir "backend") -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                    $targetDir = Join-Path $_.FullName "target"
                    if (Test-Path -LiteralPath $targetDir) {
                        Remove-Item -LiteralPath $targetDir -Recurse -Force -ErrorAction SilentlyContinue
                    }
                }
            }

            $pl = "backend/platform-common,backend/platform-cache,backend/platform-security,backend/platform-messaging,backend/workflow-engine-core,backend/admin-center,backend/developer-workstation,backend/user-portal"
            if ($Clean -and -not $SkipMavenClean) {
                mvn clean package '-DskipTests' '-Dmaven.clean.failOnError=false' -pl $pl -am
            } else {
                mvn package '-DskipTests' '-Dmaven.clean.failOnError=false' -pl $pl -am
            }
            if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
            Write-Host "  Maven build complete." -ForegroundColor Green
        } finally {
            Pop-Location
        }
    }
} else {
    Write-Host "`n[1/4] Skipping Maven build" -ForegroundColor DarkGray
}

# Step 2: Build frontend (incremental Vite + Docker)
if (-not $SkipFrontend) {
    Write-Host "`n[2/4] Building frontend (incremental npm + Docker)..." -ForegroundColor Yellow

    $resolvedNginx = Resolve-BaseImage -Candidates @(
        "nginx:alpine",
        "docker.m.daocloud.io/library/nginx:alpine"
    )
    Write-Host "  Frontend base image resolved: $resolvedNginx" -ForegroundColor DarkGray

    $frontends = @(
        @{ Name = "admin-center-frontend"; Dir = "frontend/admin-center" },
        @{ Name = "user-portal-frontend"; Dir = "frontend/user-portal" },
        @{ Name = "developer-workstation-frontend"; Dir = "frontend/developer-workstation" },
        @{ Name = "platform-login-frontend"; Dir = "frontend/login" }
    )

    foreach ($fe in $frontends) {
        $feDir = "$RootDir/$($fe.Dir)"
        $imageName = "dev-$($fe.Name)"

        $builderRebuilt = $false
        if ($fe.Name -eq "developer-workstation-frontend") {
            $builderRebuilt = Ensure-ServiceTaskBuilderBundle -Force:$RebuildServiceTaskBuilder
        }

        # $builderRebuilt must be part of this: a fresh bundle lands in
        # activepieces/dist/packages/web-embed, which Test-FrontendDistFresh does not watch,
        # and it only reaches dist/ via the `prebuild` hook inside `npm run build`. Without
        # it, -RebuildServiceTaskBuilder rebuilds the bundle and then the vite step is
        # skipped as "fresh", so the image silently keeps the previous builder.
        $needVite = $Clean -or $ForceBuild -or $builderRebuilt -or -not (Test-FrontendDistFresh -FrontendDir $fe.Dir)
        if ($needVite) {
            Write-Host "  pnpm install & build $($fe.Name)..."
            Push-Location $feDir
            try {
                Write-Host "  pnpm install..." -ForegroundColor DarkGray
                $prev = $ErrorActionPreference
                $ErrorActionPreference = "Continue"
                pnpm install
                $installExit = $LASTEXITCODE
                $ErrorActionPreference = $prev
                if ($installExit -ne 0) { throw "pnpm install failed: $($fe.Name) (exit code $installExit)" }
                Remove-Item -Path "src/components.d.ts", "src/auto-imports.d.ts" -Force -ErrorAction SilentlyContinue
                pnpm run build
                if ($LASTEXITCODE -ne 0) { throw "pnpm run build failed: $($fe.Name)" }
            } finally {
                Pop-Location
            }
        } else {
            Write-Host "  Skipping Vite for $($fe.Name) (dist fresh)." -ForegroundColor DarkGray
        }

        $dockerInputs = @(
            (Join-Path $feDir "dist"),
            (Join-Path $feDir "Dockerfile.local"),
            (Join-Path $feDir "nginx.conf"),
            (Join-Path $feDir "docker-entrypoint.sh")
        )
        $needDocker = $Clean -or $ForceBuild -or -not (Test-DockerImageFresh -ImageName $imageName -InputPaths $dockerInputs)
        if ($needDocker) {
            $noCache = @()
            if ($Clean -or $ForceBuild) { $noCache = @("--no-cache") }
            Write-Host "  Docker build $($fe.Name) (Dockerfile.local)..."
            docker build @noCache -f "$feDir/Dockerfile.local" -t $imageName $feDir
            if ($LASTEXITCODE -ne 0) { throw "$($fe.Name) docker build failed" }
        } else {
            Write-Host "  Skipping Docker build for $($fe.Name) (image fresh)." -ForegroundColor DarkGray
        }
    }

    Write-Host "  Frontend images ready." -ForegroundColor Green
} else {
    Write-Host "`n[2/4] Skipping frontend build" -ForegroundColor DarkGray
}

# Step 3: Start infrastructure
if (-not $SkipInfra) {
    Write-Host "`n[3/4] Starting infrastructure (postgres, redis, kafka)..." -ForegroundColor Yellow

    $infraImages = @(
        "postgres:16.5-alpine",
        "redis:7.2-alpine",
        "confluentinc/cp-kafka:7.5.3"
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

    Invoke-ComposeUpSequential -ServiceNames @('postgres', 'redis', 'kafka')

    # Healthcheck requires PID 1 = postgres AND pg_isready (init scripts finished).
    $postgresContainerId = Wait-ForContainerHealth -ServiceName "postgres" -DisplayName "PostgreSQL" -MaxRetries 180

    Write-Host "  Running DB schema migrations (content-hashed, volume-scoped)..." -ForegroundColor DarkGray
    Invoke-SchemaMigrations -PostgresContainerId $postgresContainerId

    Wait-ForContainerHealth -ServiceName "redis" -DisplayName "Redis" -MaxRetries 20
    Wait-ForContainerHealth -ServiceName "kafka" -DisplayName "Kafka" -MaxRetries 30 -SleepSeconds 3

    Write-Host "  Infrastructure ready." -ForegroundColor Green
} else {
    Write-Host "`n[3/4] Skipping infrastructure start" -ForegroundColor DarkGray
}

# Step 4: Build and start all services
Write-Host "`n[4/4] Starting all services..." -ForegroundColor Yellow

# Resolve Java base image with fallback mirrors before the compose build.
$resolvedJavaImage = Resolve-BaseImage -Candidates @(
    $JavaBaseImage,
    "eclipse-temurin:17-jre",
    "docker.m.daocloud.io/library/eclipse-temurin:17-jre"
)
Write-Host "  Java base image resolved: $resolvedJavaImage" -ForegroundColor DarkGray

# Decide which compose services need an image rebuild (skip fresh ones).
$backendBuildSpecs = @(
    @{ Service = "workflow-engine"; Image = "dev-workflow-engine"; Module = "backend/workflow-engine-core" },
    @{ Service = "admin-center"; Image = "dev-admin-center"; Module = "backend/admin-center" },
    @{ Service = "user-portal"; Image = "dev-user-portal"; Module = "backend/user-portal" },
    @{ Service = "developer-workstation"; Image = "dev-developer-workstation"; Module = "backend/developer-workstation" }
)
$servicesToBuild = [System.Collections.Generic.List[string]]::new()
foreach ($spec in $backendBuildSpecs) {
    $jar = Get-BackendJarPath -MavenModule $spec.Module
    $dockerfile = Join-Path $RootDir "$($spec.Module)/Dockerfile"
    $inputs = @($dockerfile)
    if ($jar) { $inputs += $jar }
    if ($Clean -or $ForceBuild -or -not $jar -or -not (Test-DockerImageFresh -ImageName $spec.Image -InputPaths $inputs)) {
        $servicesToBuild.Add($spec.Service) | Out-Null
    } else {
        Write-Host "  Skipping Docker build for $($spec.Service) (image fresh)." -ForegroundColor DarkGray
    }
}

$supersetDockerfile = Join-Path $RootDir "deploy/superset/Dockerfile"
$supersetInputs = @(
    $supersetDockerfile,
    (Join-Path $RootDir "deploy/superset/superset_config.py"),
    (Join-Path $RootDir "deploy/superset/superset_security_manager.py"),
    $env:SUPERSET_PIP_CONF_FILE
)
if ($Clean -or $ForceBuild -or -not (Test-DockerImageFresh -ImageName "dev-superset" -InputPaths $supersetInputs)) {
    $servicesToBuild.Add("superset-final") | Out-Null
} else {
    Write-Host "  Skipping Docker build for superset-final (image fresh)." -ForegroundColor DarkGray
}

$apDockerfile = Join-Path $RootDir "activepieces/Dockerfile"
if ($Clean -or $ForceBuild -or -not (Test-DockerImageFresh -ImageName "activepieces:0.84.0-ee-removed" -InputPaths @($apDockerfile))) {
    $servicesToBuild.Add("activepieces") | Out-Null
} else {
    Write-Host "  Skipping Docker build for activepieces (image fresh)." -ForegroundColor DarkGray
}

$buildOk = $true
if ($servicesToBuild.Count -eq 0) {
    Write-Host "  No compose image builds required." -ForegroundColor DarkGray
} else {
    Write-Host "  Building services: $($servicesToBuild -join ', ')" -ForegroundColor Yellow
    $attemptedImages = @($resolvedJavaImage)
    $buildOk = $false
    while (-not $buildOk -and $attemptedImages.Count -le 3) {
        $tryImage = $attemptedImages[-1]
        if ($tryImage -ne $resolvedJavaImage) {
            Write-Host "  Retrying build with Java base image: $tryImage" -ForegroundColor Yellow
        }

        $toBuild = @($servicesToBuild.ToArray())
        docker compose -f $ComposeFile --env-file $EnvFile build --build-arg "JAVA_BASE_IMAGE=$tryImage" @toBuild
        if ($LASTEXITCODE -eq 0) {
            $buildOk = $true
            $resolvedJavaImage = $tryImage
            break
        }

        Write-Host "  docker compose build failed with image: $tryImage" -ForegroundColor Yellow

        # Fallback: drop optional services from this build batch only
        $required = @($servicesToBuild | Where-Object { $OptionalComposeServices -notcontains $_ })
        if ($required.Count -gt 0 -and $required.Count -lt $servicesToBuild.Count) {
            Write-Host "  Retrying without optional services ($($OptionalComposeServices -join ', '))..." -ForegroundColor Yellow
            docker compose -f $ComposeFile --env-file $EnvFile build --build-arg "JAVA_BASE_IMAGE=$tryImage" @required
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  Fallback build succeeded (optional services skipped for this build)." -ForegroundColor Green
                $buildOk = $true
                $resolvedJavaImage = $tryImage
                break
            }
        }

        $nextCandidates = @(
            "eclipse-temurin:17-jre",
            "docker.m.daocloud.io/library/eclipse-temurin:17-jre"
        ) | Where-Object { $_ -notin $attemptedImages }
        if ($nextCandidates.Count -gt 0) {
            $nextImage = $nextCandidates[0]
            Write-Host "  Trying alternative Java base image: $nextImage" -ForegroundColor Yellow
            docker pull $nextImage 2>&1 | Select-Object -Last 3
            if ($LASTEXITCODE -eq 0) {
                $attemptedImages += $nextImage
                continue
            }
            Write-Host "  Cannot pull $nextImage either." -ForegroundColor Yellow
        }
        break
    }
}

if (-not $buildOk) {
    Write-Host "  All build attempts failed." -ForegroundColor Red
    docker compose -f $ComposeFile --env-file $EnvFile ps
    throw "Docker compose image build failed (all fallbacks exhausted)"
}
if (-not $SkipImagePull) {
    Write-Host "`n[0.5] Pulling images listed in compose (sequential, retries)" -ForegroundColor Yellow
    $images = docker compose -f $ComposeFile --env-file $EnvFile config --images 2>$null | Sort-Object -Unique
    $failedImages = @()
    foreach ($img in $images) {
        # Skip locally built / optional images that may be unavailable offline
        if ($img -like 'dev-*' -or $img -like 'activepieces:*') { continue }
        if ($img -match 'superset|activepieces') {
            Write-Host "  Skipping optional image pull: $img" -ForegroundColor DarkGray
            continue
        }
        $ok = Pull-ImageWithRetry -Image $img -MaxAttempts 5
        if (-not $ok) { $failedImages += $img }
    }
    if ($failedImages.Count -gt 0) {
        Write-Host "  Some images failed to pull:" -ForegroundColor Red
        $failedImages | ForEach-Object { Write-Host "    $_" }
        Write-Host "  You may need to check Docker proxy/network or pull these images manually." -ForegroundColor Yellow
    } else {
        Write-Host "  All external images pulled." -ForegroundColor Green
    }
}

if ($ServicesOnly -or $SkipInfra) {
    Write-Host "  Starting only non-infra services (skip infra)..." -ForegroundColor Yellow
    $infra = @('postgres','redis','kafka')
    $allSvcs = docker compose -f $ComposeFile --env-file $EnvFile config --services 2>$null
    $startSvcs = $allSvcs | Where-Object { $infra -notcontains $_ }
    if ($startSvcs -and $startSvcs.Count -gt 0) {
        Invoke-ComposeUpSequential -ServiceNames $startSvcs -NoDeps
    } else {
        Write-Host "  No non-infra services to start." -ForegroundColor DarkGray
    }
} else {
    $allSvcs = docker compose -f $ComposeFile --env-file $EnvFile config --services 2>$null
    if ($allSvcs -and $allSvcs.Count -gt 0) {
        Invoke-ComposeUpSequential -ServiceNames $allSvcs
    } else {
        throw "No compose services found to start"
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "  docker compose failed. Current service status:" -ForegroundColor Red
    docker compose -f $ComposeFile --env-file $EnvFile ps
    throw "Docker compose service startup failed"
}

Write-Host "  Waiting for backend health checks..." -ForegroundColor Cyan
Wait-ForContainerHealth -ServiceName "workflow-engine" -DisplayName "Workflow Engine"
Wait-ForContainerHealth -ServiceName "admin-center" -DisplayName "Admin Center"
Wait-ForContainerHealth -ServiceName "user-portal" -DisplayName "User Portal"
Wait-ForContainerHealth -ServiceName "developer-workstation" -DisplayName "Developer Workstation"
Wait-ForContainerHealth -ServiceName "edge-frontend" -DisplayName "Edge frontend (single-origin)"

# Superset is required: URI fail-closed + bootstrap must succeed (no warning soft-pass).
Invoke-SupersetBootstrap

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
Write-Host "    BI (Superset):        http://localhost:$EdgePort/bi/"
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
Write-Host "  Superset (edge only):   http://localhost:$EdgePort/bi/"
Write-Host "  Superset health:        http://localhost:$EdgePort/bi/health"
Write-Host ""
# AP piece catalog is provisioned internally (AP_PIECES_SYNC_MODE=NONE): a fresh DB / -Clean
# starts EMPTY, so the DW Automation tab shows no pieces until piece_metadata is seeded.
$pgUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "platform_dev" }
$pgDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "workflow_platform_dev" }
Write-Host "Automation pieces (empty after a fresh DB / -Clean — seed to populate the catalog):" -ForegroundColor Yellow
Write-Host "  Get-Content ../../pieces/metadata/pieces-seed.sql | docker exec -i platform-postgres-dev psql -U $pgUser -d $pgDb"
Write-Host "  then restart AP:  docker restart platform-activepieces-dev   (piece registry is cached in-process)"
Write-Host ""
Write-Host "Commands:" -ForegroundColor DarkGray
Write-Host "  Logs:   docker compose -f docker-compose.dev.yml --env-file .env logs -f [service]"
Write-Host "  Superset logs: docker compose -f docker-compose.dev.yml --env-file .env logs -f superset-final"
Write-Host "  Stop:   docker compose -f docker-compose.dev.yml --env-file .env down"
Write-Host "  Reset:  .\build-and-deploy.ps1 -Clean"
Write-Host "  Force rebuild (no volume wipe): .\build-and-deploy.ps1 -ForceBuild"
