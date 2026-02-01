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

# Environment-specific paths
$EnvDir = "deploy\environments\$Environment"
$EnvFile = "$EnvDir\.env"
$ComposeFile = "$EnvDir\docker-compose.$Environment.yml"

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

# Docker Compose command base
$ComposeCmd = @("docker-compose", "-f", $ComposeFile, "--env-file", $EnvFile)

# Service selection
if ($Services -ne "all") {
    $ServiceFilter = $Services
} else {
    $ServiceFilter = $null
}

# Execute action
switch ($Action) {
    "up" {
        Write-Info "Starting services..."
        $CmdArgs = $ComposeCmd + @("up", "-d")
        if ($ServiceFilter) {
            $CmdArgs += $ServiceFilter
        }
        & $CmdArgs[0] $CmdArgs[1..($CmdArgs.Length-1)]
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Services started successfully"
            
            # Show status after startup
            Write-Info "Service status:"
            & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] ps
        } else {
            Write-ErrorMsg "Failed to start services"
            exit 1
        }
    }
    
    "down" {
        Write-Info "Stopping services..."
        if ($ServiceFilter) {
            & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] stop $ServiceFilter
            & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] rm -f $ServiceFilter
        } else {
            & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] down
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
        $CmdArgs = $ComposeCmd + @("restart")
        if ($ServiceFilter) {
            $CmdArgs += $ServiceFilter
        }
        & $CmdArgs[0] $CmdArgs[1..($CmdArgs.Length-1)]
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Services restarted successfully"
            
            # Show status after restart
            Write-Info "Service status:"
            & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] ps
        } else {
            Write-ErrorMsg "Failed to restart services"
            exit 1
        }
    }
    
    "logs" {
        Write-Info "Showing service logs..."
        $CmdArgs = $ComposeCmd + @("logs", "-f", "--tail=100")
        if ($ServiceFilter) {
            $CmdArgs += $ServiceFilter
        }
        & $CmdArgs[0] $CmdArgs[1..($CmdArgs.Length-1)]
    }
    
    "status" {
        Write-Info "Service status:"
        & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] ps
        
        Write-Info "Service health:"
        & docker ps --filter "label=com.docker.compose.project=workflow-platform-$Environment" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
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
            & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] ps
        }
    }
}

Write-Success "Deployment action '$Action' completed for environment: $Environment"