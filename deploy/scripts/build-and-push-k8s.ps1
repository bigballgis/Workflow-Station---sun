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
#   # automation/dist/packages/web-embed instead of installing and rebuilding it
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipApWorkspaceInstall
#   # deployment without Activepieces: DW frontend ships without the Automation builder
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -NoServiceTaskBuilder
#   # AP image only, on the release tag (~25 min: pnpm install + turbo build + piece prewarm)
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -Services activepieces
#   # platform images only — WARNS: activepieces:v1.0.0 will not exist for this release
#   .\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipActivepieces
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
    # install or the builder-bundle build. Requires a prebuilt automation/dist/packages/
    # web-embed carried over from a host that can build it, which is then reused as-is.
    [switch]$SkipApWorkspaceInstall = $false,
    # Same idea for the four frontends: pack each image from a dist/ built elsewhere instead of
    # running pnpm install + vite here. For a host whose npm mirror quarantines or lacks
    # packages the frontend lockfiles pin (axios, vitest, …). Fails per service when its
    # dist/index.html is absent rather than packing an empty image.
    [switch]$UsePrebuiltFrontendDist = $false,
    # Deliberately ship developer-workstation-frontend WITHOUT the embedded Activepieces
    # builder: no bundle build, and DW's prebuild hook is allowed to warn-and-continue instead
    # of failing. For a deployment where Activepieces itself is not rolled out, so the
    # Automation tab has no backend to talk to anyway — it then reports the builder as
    # unavailable rather than 404ing on web.css. Default stays fail-closed.
    [switch]$NoServiceTaskBuilder = $false,
    [switch]$SkipBackend = $false,
    # Activepieces is built from THIS repo (automation/Dockerfile): EE-removed, de-bunned,
    # with the allowlisted pieces prewarmed into the last layer. Do NOT substitute the upstream
    # activepieces/activepieces image (nor mirror-thirdparty-images-k8s.ps1's legacy
    # activepieces entry) — that one has none of the three and cannot run air-gapped.
    # ⚠️ AP ships on the SAME tag as the platform, so skipping it leaves <Registry>/
    # activepieces:$Tag unpublished while activepieces.yaml/ap-bootstrap-job.yaml resolve
    # __IMAGE_TAG__ to exactly that tag ⇒ ImagePullBackOff. Only for a run whose images are
    # not being deployed as a set (the script warns).
    # 跳过 AP schema 契约校验。仅用于「明知契约已破、正在修」的临时构建 —— 常规发布绝不要用,
    # 它关掉的正是 2026-08 UAT 事故(admin-center 查 AP 已删列导致整页 500)的唯一防线。
    [switch]$SkipSchemaContractCheck = $false,
    [switch]$SkipActivepieces = $false,
    # Optional override; empty means "same tag as the platform images". The manifests use
    # __IMAGE_TAG__, so this must match what the apply script is given as -ImageTag.
    [string]$ApImageTag = "",
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
# Activepieces is not a $BackendServices/$FrontendServices entry: it is neither a Maven module
# nor a pnpm frontend and it has its own Dockerfile at the repo's automation/ root. It is
# still selectable by name so -Services activepieces works, and it ships on the platform tag.
$buildActivepieces = (-not $SkipActivepieces) -and
    (($Services -eq "all") -or (($Services -split ",") -contains "activepieces"))
$apTag = if ([string]::IsNullOrWhiteSpace($ApImageTag)) { $Tag } else { $ApImageTag }

Write-Host ""
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Build & Push to K8S Registry" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Registry: $Registry"
Write-Host "  Tag: $Tag"
Write-Host "  Services: $Services"
Write-Host "  Activepieces: $(if ($buildActivepieces) { "yes (:$apTag)" } else { 'SKIPPED — nothing will publish activepieces:$Tag' })"
Write-Host "  MaxParallel: $MaxParallel"
Write-Host "  JavaBaseImage: $JavaBaseImage"
Write-Host "=========================================" -ForegroundColor Yellow

if (-not $buildActivepieces) {
    # Worth shouting about: activepieces.yaml and ap-bootstrap-job.yaml resolve __IMAGE_TAG__
    # to the tag the apply script is given, so a release whose AP image was never pushed under
    # that tag comes up in ImagePullBackOff — and nothing before the deploy would have said so.
    Write-Host ""
    Write-Host "  WARNING: Activepieces is NOT being built/pushed in this run." -ForegroundColor Yellow
    Write-Host "           deploy/k8s/{activepieces,ap-bootstrap-job}.yaml pull" -ForegroundColor Yellow
    Write-Host "           $Registry/activepieces:<-ImageTag>. Deploying tag '$Tag' without" -ForegroundColor Yellow
    Write-Host "           publishing that image => ImagePullBackOff." -ForegroundColor Yellow
    Write-Host "           Re-run with -Services activepieces, or deploy an -ImageTag whose" -ForegroundColor Yellow
    Write-Host "           activepieces image already exists in the registry." -ForegroundColor Yellow
}

# 0. Pre-pull Java runtime base (avoids docker build hitting Docker Hub for FROM metadata)
# The @(...).Count guards below: a selection like -Services activepieces (or any frontend-only
# one) leaves $selectedBackend empty, and without them this pulls a 320 MB base image for
# nothing and then hands mvn a -pl list ending in a comma.
if (-not $SkipBackend -and -not $PushOnly -and @($selectedBackend).Count -gt 0) {
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
        # Deliberately unredirected — no `2>&1`, no Out-Null. This is a ~320 MB pull against a
        # mirror that may be slow or unreachable, and swallowing docker's output made a healthy
        # download and a hung connection look identical: several silent minutes, then a retry.
        # Letting docker write straight to the console keeps its live progress lines. Avoiding
        # `2>&1` also keeps Windows PowerShell from turning docker's stderr into a terminating
        # NativeCommandError under $ErrorActionPreference = "Stop".
        docker pull $JavaBaseImage
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Java base image present locally"
            $pulled = $true
            break
        }
        # Print the exit code: without it the only trace of a failed attempt was the next
        # "attempt N/3" line, which reads as a restart rather than a failure.
        Write-Host "   Pull attempt $attempt failed (docker exit $LASTEXITCODE)." -ForegroundColor Yellow
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
if (-not $SkipBackend -and -not $PushOnly -and @($selectedBackend).Count -gt 0) {
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
if (-not $SkipBackend -and @($selectedBackend).Count -gt 0) {
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
if (-not $SkipFrontend -and @($selectedFrontend).Count -gt 0) {
    Write-Step "Building frontend (local pnpm build + Docker, parallel x$MaxParallel)..."

    # The developer-workstation frontend embeds the Activepieces builder. That bundle is
    # produced by the AP workspace (outside frontend/) into automation/dist/packages/
    # web-embed and is gitignored, so a clean checkout never has it; DW's pnpm `prebuild`
    # hook only COPIES it into public/. Nothing else builds it, so build it here — before
    # the per-service jobs start, since DW's build consumes it. Skip this and the image
    # ships without the bundle and 404s on /dev/service-task-builder/web.css.
    # -UsePrebuiltFrontendDist makes this whole step moot: DW's dist/ already contains
    # service-task-builder/ (its prebuild hook copied the bundle in on the host that built it),
    # and that hook does not run here at all. Without this, a host carrying a complete dist would
    # still be told to produce a bundle it does not need.
    $needsApBuilder = (-not $UsePrebuiltFrontendDist) -and (-not $NoServiceTaskBuilder) -and
        @($selectedFrontend | Where-Object { $_.Name -eq "developer-workstation-frontend" }).Count -gt 0
    if ($NoServiceTaskBuilder) {
        Write-Host "   WARNING: -NoServiceTaskBuilder — nothing in this run touches Activepieces: no workspace install, no bundle build, and any bundle already on disk is kept OUT of the image (DW's prebuild hook clears public/service-task-builder). developer-workstation-frontend ships WITHOUT the Automation builder; the tab reports it as unavailable. Use only where Activepieces is not deployed." -ForegroundColor Yellow
    }
    if ($needsApBuilder -and -not $PushOnly) {
        $apRootDir = Join-Path $ProjectRoot "automation"
        $apWebDir = Join-Path $apRootDir "packages/web"
        $embedMarker = Join-Path $ProjectRoot "automation/dist/packages/web-embed/ap-builder.mjs"

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
            Write-Fail "Activepieces workspace deps are missing ($apWebDir/node_modules) and there is no prebuilt bundle at $embedMarker. Fix the install (see BUILD_GUIDE 7.2 step 0 — its output is above), copy a prebuilt automation/dist/packages/web-embed over, or exclude developer-workstation-frontend with -Services."
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
            [bool]$PushOnly, [bool]$NoPush, [bool]$UsePrebuiltFrontendDist, [bool]$NoServiceTaskBuilder
        ) -ScriptBlock {
            param($Name, $ContextDir, $ImageName, $PushOnly, $NoPush, $UsePrebuiltDist, $NoBuilder)
            # Use a native PowerShell array (not a generic List) so this runs under
            # Constrained Language Mode, where [System.Collections.Generic.List[...]]::new() is blocked.
            $log = @()
            if (-not $PushOnly -and $UsePrebuiltDist) {
                # Host whose npm mirror cannot satisfy this frontend's lockfile: the dist/ was
                # built elsewhere and carried over, so install and build are both skipped and the
                # image is packed from what is on disk. index.html is the marker of a real vite
                # build; without it there is nothing to pack, and packing an empty dist would
                # produce an image that only fails in a browser.
                if (-not (Test-Path (Join-Path $ContextDir "dist/index.html"))) {
                    $log += "[$Name] -UsePrebuiltFrontendDist but no dist/index.html in $ContextDir"
                    return @{ Name = $Name; Ok = $false; Stage = "prebuilt dist missing"; Log = $log }
                }
                # Loud on purpose: whoever carried the dist in owns its freshness, and nothing
                # else in this script can tell a current dist from a month-old one.
                $log += "[$Name] WARNING: -UsePrebuiltFrontendDist — packing the existing dist/ as-is, no pnpm install, no vite build. Verify it matches this commit."
                # No vite run here means DW's prebuild hook never runs either, so the SKIP
                # variable below cannot act. A dist carried over from a host that DID build the
                # bundle still contains it — drop it so -NoServiceTaskBuilder means the same
                # thing on both paths. Only a build artifact is removed, never the source.
                if ($NoBuilder -and $Name -eq "developer-workstation-frontend") {
                    $stale = Join-Path $ContextDir "dist/service-task-builder"
                    if (Test-Path $stale) {
                        Remove-Item -Path $stale -Recurse -Force
                        $log += "[$Name] -NoServiceTaskBuilder: removed the Activepieces builder bundle from the carried-over dist/ ($stale)"
                    }
                }
            }
            if (-not $PushOnly -and -not $UsePrebuiltDist) {
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
                    # Thread jobs share the host process environment, so this variable also
                    # outlives a previous run in the same shell — clear it explicitly rather
                    # than merely not setting it, or -NoServiceTaskBuilder would still hard-fail
                    # after a normal run in the same session.
                    # SERVICE_TASK_BUILDER_SKIP is the other half of -NoServiceTaskBuilder:
                    # clearing REQUIRED only stops the hard failure, it does not stop the hook
                    # from copying a bundle that is already on disk (automation/dist/ from an
                    # earlier build, or public/service-task-builder from an earlier run) — and
                    # then the image ships the AP builder anyway, which is exactly what this
                    # switch is meant to leave out. SKIP makes the hook remove the destination
                    # instead. Both variables are set explicitly in both directions: thread jobs
                    # share the host process environment, so a previous run in the same shell
                    # would otherwise leak its value into this one.
                    if ($Name -eq "developer-workstation-frontend") {
                        if ($NoBuilder) {
                            $env:SERVICE_TASK_BUILDER_REQUIRED = $null
                            $env:SERVICE_TASK_BUILDER_SKIP = "1"
                        } else {
                            $env:SERVICE_TASK_BUILDER_REQUIRED = "1"
                            $env:SERVICE_TASK_BUILDER_SKIP = $null
                        }
                    }
                    $log += ">> [$Name] pnpm run build"
                    pnpm run build 2>&1 | ForEach-Object { $log += "[$Name] $_" }
                    if ($LASTEXITCODE -ne 0) { Pop-Location; return @{ Name = $Name; Ok = $false; Stage = "pnpm run build"; Log = $log } }
                } finally {
                    Pop-Location
                }
            }

            # Outside the build block on purpose: with -UsePrebuiltFrontendDist there is no
            # install and no vite run, but the image must still be packed from the carried-over
            # dist/ — skipping this would push a tag that was never built.
            if (-not $PushOnly) {
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

# 4. Activepieces image (in-repo source, own tag) — LAST and sequential on purpose.
#
# Last: it is the longest single build in this script (full pnpm install + turbo build of
# web/engine/api/worker + the piece prewarm layer), and nothing else depends on the IMAGE.
# What developer-workstation-frontend needs from Activepieces is the embed bundle, which comes
# from the SOURCE tree in step 3 — so putting this first would only delay the eight platform
# images behind ~25 minutes.
#
# Sequential and unredirected: a single long build where docker's live output is the only
# progress signal. Capturing it into a log array (as the parallel per-service jobs do) would
# make a healthy 25-minute build and a hung one look identical.
if ($buildActivepieces) {
    Write-Step "Building Activepieces image from in-repo source..."

    $apContext = Join-Path $ProjectRoot "automation"
    $apImage = "$Registry/activepieces:$apTag"

    if (-not (Test-Path (Join-Path $apContext "Dockerfile"))) {
        Write-Fail "No Dockerfile at $apContext — the vendored Activepieces tree is missing. Exclude it with -SkipActivepieces if this deployment ships without Activepieces."
    }
    # Both build stages run `pnpm install --frozen-lockfile`, so a lockfile that does not match
    # the workspace manifests fails the build minutes in. Catch it here instead.
    if (-not (Test-Path (Join-Path $apContext "pnpm-lock.yaml"))) {
        Write-Fail "No pnpm-lock.yaml at $apContext — the image build needs the committed lockfile (both stages use --frozen-lockfile)."
    }

    # --- 发布门禁：AP schema 契约 -------------------------------------------------
    # 2026-08 UAT 事故：AP 0.88 的迁移 DropPlatformPieceFilters1809000000000 删掉了
    # platform.filteredPieceNames，而 admin-center 仍在 SELECT 它（AP 与平台共库，
    # admin-center 用裸 SQL 直查 AP 私有表），结果 Automation Pieces 整页 500。
    # 那条迁移自带 breaking = true 标记，但当时没有任何环节消费它 —— 现在有了。
    #
    # 在 build 之前跑：构建要 20 分钟以上，让它先失败远好过推完镜像才发现。
    # 校验器只读、不连数据库，靠 AP 的 TypeORM 实体判断；实体与真实 schema 的等价性
    # 由 AP 自己的 check-migrations 保证。
    if (-not $SkipSchemaContractCheck) {
        Write-Host "   preflight: AP schema 契约校验..." -ForegroundColor Gray
        $apiDir = Join-Path $apContext "packages/server/api"
        $checker = Join-Path $ProjectRoot "deploy/scripts/check-ap-schema-contract.ts"
        Push-Location $apiDir
        try {
            npx ts-node --transpile-only -r tsconfig-paths/register -P tsconfig.app.json $checker
            $checkExit = $LASTEXITCODE
        }
        finally { Pop-Location }
        if ($checkExit -ne 0) {
            Write-Fail @"
AP schema 契约校验失败(见上)。admin-center 依赖的 AP 列已不存在 —— 这会在运行时打掉
Admin Center 的 Automation 页面,而不是在编译期报错。

不要用 -SkipSchemaContractCheck 绕过它来发版:那正是 2026-08 UAT 事故的复现路径。
按上面的提示改 SQL + 契约,或补一条 HERMES 迁移。
"@
        }
        Write-Ok "AP schema 契约校验通过"
    }

    if (-not $PushOnly) {
        Write-Host "   docker build -t $apImage $apContext" -ForegroundColor Gray
        Write-Host "   (this one is slow: pnpm install + turbo build + piece prewarm)" -ForegroundColor DarkGray
        docker build -t $apImage $apContext
        if ($LASTEXITCODE -ne 0) { Write-Fail "Activepieces build failed (docker exit $LASTEXITCODE)" }
        Write-Ok "activepieces image built ($apImage)"
    }

    if (-not $NoPush) {
        Write-Host "   docker push $apImage" -ForegroundColor Gray
        docker push $apImage
        if ($LASTEXITCODE -ne 0) { Write-Fail "Activepieces push failed (docker exit $LASTEXITCODE)" }
        Write-Ok "activepieces image pushed"
    }
}

# Summary
Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Build & Push Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Images: $Registry/*:$Tag" -ForegroundColor White
if ($buildActivepieces) {
    Write-Host "        $Registry/activepieces:$apTag" -ForegroundColor White
}
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Update deploy/k8s/configmap-*.yaml with DB/Redis hosts" -ForegroundColor White
Write-Host "  2. Update deploy/k8s/secret-*.yaml with real credentials" -ForegroundColor White
Write-Host "  3. Deploy: .\deploy\k8s\deploy.ps1 -Environment sit -Tag $Tag" -ForegroundColor White
if ($buildActivepieces) {
    Write-Host "  4. Activepieces ships only the piece RUNTIME half. Seed the metadata half once" -ForegroundColor White
    Write-Host "     per environment: deploy/pieces/metadata/pieces-seed.sql (ap-bootstrap-job's" -ForegroundColor White
    Write-Host "     ap-provision-db initContainer does this and invalidates AP's registry cache)." -ForegroundColor White
}
