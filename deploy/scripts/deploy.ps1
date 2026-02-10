# =====================================================
# Multi-Environment Deployment Script (PowerShell)
# =====================================================

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("dev", "sit", "uat", "prod")]
    [string]$Environment = "dev",
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("up", "down", "restart", "logs", "status", "test")]
    [string]$Action = "up",
    
    [Parameter(Mandatory=$false)]
    [string]$Services = "all",
    
    [Parameter(Mandatory=$false)]
    [switch]$Build,
    
    [Parameter(Mandatory=$false)]
    [switch]$Help
)

# Function to print colored output
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-ErrorMsg {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Function to show usage
function Show-Usage {
    Write-Host "Usage: .\deploy.ps1 [OPTIONS]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Environment ENV     Target environment (dev|sit|uat|prod) [default: dev]"
    Write-Host "  -Action ACTION       Action to perform (up|down|restart|logs|status|test) [default: up]"
    Write-Host "  -Services SERVICES   Services to deploy (all|backend|frontend|service-name) [default: all]"
    Write-Host "  -Build               Build images before deployment"
    Write-Host "  -Help                Show this help message"
    Write-Host ""
    Write-Host "Actions:"
    Write-Host "  up       Start services"
    Write-Host "  down     Stop and remove services"
    Write-Host "  restart  Restart services"
    Write-Host "  logs     Show service logs"
    Write-Host "  status   Show service status"
    Write-Host "  test     Test deployment health"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\deploy.ps1 -Environment dev -Action up                    # Start all dev services"
    Write-Host "  .\deploy.ps1 -Environment prod -Action up -Services backend # Start prod backend services"
    Write-Host "  .\deploy.ps1 -Environment sit -Action logs -Services workflow-engine # Show logs for workflow-engine in sit"
    Write-Host "  .\deploy.ps1 -Environment dev -Action down                  # Stop all dev services"
}

# Show help if requested
if ($Help) {
    Show-Usage
    exit 0
}

# Set working directory to project root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
Set-Location $ProjectRoot

# Environment-specific paths (use forward slashes for cross-platform compatibility)
$EnvDir = "deploy/environments/$Environment"
$EnvFile = "$EnvDir/.env"
$ComposeFile = "$EnvDir/docker-compose.$Environment.yml"

# Check if environment files exist
if (-not (Test-Path $EnvFile)) {
    Write-ErrorMsg "Environment file not found: $EnvFile"
    exit 1
}

if (-not (Test-Path $ComposeFile)) {
    Write-ErrorMsg "Docker Compose file not found: $ComposeFile"
    exit 1
}

Write-Info "Deploying to environment: $Environment"
Write-Info "Action: $Action"
Write-Info "Services: $Services"

# Load environment variables
Write-Info "Loading environment variables from $EnvFile"
Get-Content $EnvFile | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($key, $value, "Process")
}

# Load image versions if available
$VersionFile = "$EnvDir/.image-versions"
if (Test-Path $VersionFile) {
    Write-Info "Loading image versions from $VersionFile..."
    
    # Create a temporary .env file for docker-compose
    $tempEnvFile = Join-Path $EnvDir ".image-versions.env"
    $envContent = @()
    
    Get-Content $VersionFile | Where-Object { $_ -notmatch '^#' -and $_ -match ':' } | ForEach-Object {
        $imageName = $_.Trim()
        
        # Parse image name to create environment variable
        # Format: dev-service-name:timestamp -> IMAGE_TAG_SERVICE_NAME
        if ($imageName -match '^dev-(.+):([0-9]+-[0-9]+)$') {
            $serviceName = $matches[1].ToUpper().Replace('-', '_')
            
            # Special mapping for workflow-engine-core -> workflow-engine
            if ($serviceName -eq "WORKFLOW_ENGINE_CORE") {
                $serviceName = "WORKFLOW_ENGINE"
            }
            
            $envVarName = "IMAGE_TAG_$serviceName"
            $envContent += "$envVarName=$imageName"
            Write-Info "  Set $envVarName=$imageName"
        }
    }
    
    # Write to temporary env file
    $envContent | Out-File -FilePath $tempEnvFile -Encoding UTF8
    Write-Info "Created temporary env file: $tempEnvFile"
} else {
    Write-Warning "Image version file not found: $VersionFile"
    Write-Warning "Using default image tags (latest). Run build.ps1 first to create versioned images."
}

# Build images if requested
if ($Build) {
    Write-Info "Building images before deployment..."
    $BuildScript = "$ScriptDir\build.ps1"
    & $BuildScript -Environment $Environment -Services $Services
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMsg "Build failed"
        exit 1
    }
}

# Service selection
if ($Services -ne "all") {
    # Split services by comma or space
    $ServiceFilter = $Services -split '[,\s]+' | Where-Object { $_ -ne "" }
} else {
    $ServiceFilter = $null
}

# Execute action
switch ($Action) {
    "up" {
        Write-Info "Starting services..."
        
        # Use both the main .env file and the image versions env file
        $tempEnvFile = Join-Path $EnvDir ".image-versions.env"
        
        if ($ServiceFilter) {
            # Start specific services
            $serviceList = $ServiceFilter -join " "
            if (Test-Path $tempEnvFile) {
                $cmd = "docker compose -f `"$ComposeFile`" --env-file `"$EnvFile`" --env-file `"$tempEnvFile`" up -d $serviceList"
            } else {
                $cmd = "docker compose -f `"$ComposeFile`" --env-file `"$EnvFile`" up -d $serviceList"
            }
            Write-Info "Executing: $cmd"
            Invoke-Expression $cmd
        } else {
            # Start all services
            if (Test-Path $tempEnvFile) {
                Write-Info "Using image versions from: $tempEnvFile"
                $cmd = "docker compose -f `"$ComposeFile`" --env-file `"$EnvFile`" --env-file `"$tempEnvFile`" up -d"
            } else {
                Write-Warning "Image versions file not found, using defaults"
                $cmd = "docker compose -f `"$ComposeFile`" --env-file `"$EnvFile`" up -d"
            }
            Write-Info "Executing: $cmd"
            Invoke-Expression $cmd
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Services started successfully"
            
            # Show status after startup
            Write-Info "Service status:"
            Invoke-Expression "docker compose -f `"$ComposeFile`" ps"
        } else {
            Write-ErrorMsg "Failed to start services"
            exit 1
        }
    }
    
    "down" {
        Write-Info "Stopping services..."
        
        if ($ServiceFilter) {
            $serviceList = $ServiceFilter -join " "
            Invoke-Expression "docker compose -f `"$ComposeFile`" stop $serviceList"
            Invoke-Expression "docker compose -f `"$ComposeFile`" rm -f $serviceList"
        } else {
            Invoke-Expression "docker compose -f `"$ComposeFile`" down"
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Services stopped successfully"
        } else {
            Write-ErrorMsg "Failed to stop services"
            exit 1
        }
    }
    
    "restart" {
        Write-Info "Restarting services..."
        
        if ($ServiceFilter) {
            $serviceList = $ServiceFilter -join " "
            Invoke-Expression "docker compose -f `"$ComposeFile`" restart $serviceList"
        } else {
            Invoke-Expression "docker compose -f `"$ComposeFile`" restart"
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Services restarted successfully"
            
            # Show status after restart
            Write-Info "Service status:"
            Invoke-Expression "docker compose -f `"$ComposeFile`" ps"
        } else {
            Write-ErrorMsg "Failed to restart services"
            exit 1
        }
    }
    
    "logs" {
        Write-Info "Showing service logs..."
        
        if ($ServiceFilter) {
            $serviceList = $ServiceFilter -join " "
            Invoke-Expression "docker compose -f `"$ComposeFile`" logs -f --tail=100 $serviceList"
        } else {
            Invoke-Expression "docker compose -f `"$ComposeFile`" logs -f --tail=100"
        }
    }
    
    "status" {
        Write-Info "Service status:"
        Invoke-Expression "docker compose -f `"$ComposeFile`" ps"
        
        Write-Info "Service health:"
        Invoke-Expression "docker ps --filter `"label=com.docker.compose.project=workflow-platform-$Environment`" --format `"table {{.Names}}\t{{.Status}}\t{{.Ports}}`""
    }
    
    "test" {
        Write-Info "Testing deployment..."
        
        # Run deployment test script
        $TestScript = Join-Path $ScriptDir "test-deployment.ps1"
        
        if (Test-Path $TestScript) {
            Write-Info "Running deployment test script..."
            
            $testArgs = @(
                "-Environment", $Environment
            )
            
            & $TestScript @testArgs
            
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Deployment test passed"
            } else {
                Write-ErrorMsg "Deployment test failed"
                exit 1
            }
        } else {
            Write-Warning "Test script not found: $TestScript"
            
            # Fallback: basic status check
            Write-Info "Performing basic status check..."
            Invoke-Expression "docker compose -f `"$ComposeFile`" ps"
        }
    }
}

Write-Success "Deployment action '$Action' completed for environment: $Environment"