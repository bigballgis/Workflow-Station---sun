# Pre-pull base images via domestic mirror (extracted from build-and-deploy.ps1)
# Invocation: .\prepull-images.ps1 -JavaBaseImage <image>
param(
    [string]$JavaBaseImage = "docker.m.daocloud.io/library/eclipse-temurin:17-jre"
)

Write-Host "`n[0/4] Pre-pulling base images via domestic mirror..." -ForegroundColor Yellow

$images = @(
    @{ Mirror = $JavaBaseImage; Target = "eclipse-temurin:17-jre" },
    @{ Mirror = "docker.m.daocloud.io/library/nginx:alpine";                  Target = "nginx:alpine"                  },
    @{ Mirror = "docker.m.daocloud.io/library/postgres:16.5-alpine";          Target = "postgres:16.5-alpine"          },
    @{ Mirror = "docker.m.daocloud.io/library/redis:7.2-alpine";              Target = "redis:7.2-alpine"              },
    @{ Mirror = "docker.m.daocloud.io/confluentinc/cp-kafka:7.5.3";           Target = "confluentinc/cp-kafka:7.5.3"     },
    @{ Mirror = "docker.m.daocloud.io/n8nio/n8n:latest";                      Target = "docker.n8n.io/n8nio/n8n:latest" },
    @{ Mirror = "docker.m.daocloud.io/apache/superset:6.0.0";                 Target = "apache/superset:6.0.0"          }
)

foreach ($img in $images) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    docker image inspect $img.Target 2>$null | Out-Null
    $inspectExit = $LASTEXITCODE
    $ErrorActionPreference = $prev
    if ($inspectExit -eq 0) {
        Write-Host "  Already cached, skipping: $($img.Target)" -ForegroundColor DarkGray
        continue
    }

    $pulled = $false
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        Write-Host "  Pulling $($img.Target) from mirror (attempt $attempt/3)..."
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        docker pull $img.Mirror 2>&1 | Out-Null
        $pullExit = $LASTEXITCODE
        $ErrorActionPreference = $prev

        if ($pullExit -eq 0) {
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
        Write-Host "  Mirror unavailable. Trying original registry pull for $($img.Target)..." -ForegroundColor Yellow
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        docker pull $img.Target 2>&1 | Out-Null
        $directPullExit = $LASTEXITCODE
        $ErrorActionPreference = $prev

        if ($directPullExit -eq 0) {
            Write-Host "  OK (direct registry): $($img.Target)" -ForegroundColor Green
            continue
        }

        Write-Host "  Mirror and direct registry both unavailable. Restoring $($img.Target) from BuildKit cache..." -ForegroundColor Yellow
        $tmpFile = [System.IO.Path]::GetTempFileName() + ".Dockerfile"
        "FROM $($img.Target)" | Set-Content $tmpFile
        docker build --quiet -t $img.Target -f $tmpFile "$PSScriptRoot" 2>&1 | Out-Null
        Remove-Item $tmpFile -ErrorAction SilentlyContinue

        if ($LASTEXITCODE -eq 0) {
            Write-Host "  OK (BuildKit cache): $($img.Target)" -ForegroundColor Green
        } else {
            Write-Host "  WARNING: Could not pull or restore $($img.Target). Continuing — actual builds may still succeed via BuildKit cache." -ForegroundColor Yellow
        }
    }
}

Write-Host "  Base images ready." -ForegroundColor Green
