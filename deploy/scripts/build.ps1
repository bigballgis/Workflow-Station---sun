# =====================================================
# Multi-Environment Build Script (PowerShell)
# =====================================================

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("dev", "sit", "uat", "prod")]
    [string]$Environment = "dev",
    
    [Parameter(Mandatory=$false)]
    [string]$Services = "all",
    
    [Parameter(Mandatory=$false)]
    [string]$Tag = "latest",
    
    [Parameter(Mandatory=$false)]
    [string]$Registry = "",
    
    [Parameter(Mandatory=$false)]
    [switch]$Push,
    
    [Parameter(Mandatory=$false)]
    [switch]$NoFrontend,
    
    [Parameter(Mandatory=$false)]
    [switch]$NoBackend,
    
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
    Write-Host "Usage: .\build.ps1 [OPTIONS]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Environment ENV     Target environment (dev|sit|uat|prod) [default: dev]"
    Write-Host "  -Services SERVICES   Services to build (all|backend|frontend|service-name) [default: all]"
    Write-Host "  -Tag TAG            Docker image tag [default: latest]"
    Write-Host "  -Registry REGISTRY  Docker registry URL for pushing images"
    Write-Host "  -Push               Push images to registry after build"
    Write-Host "  -NoFrontend         Skip frontend build"
    Write-Host "  -NoBackend          Skip backend build"
    Write-Host "  -Help               Show this help message"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\build.ps1 -Environment dev                           # Build all services for dev environment"
    Write-Host "  .\build.ps1 -Environment prod -Services backend -Push  # Build backend services for prod and push"
    Write-Host "  .\build.ps1 -Environment sit -Services workflow-engine # Build only workflow-engine for sit"
    Write-Host "  .\build.ps1 -Environment uat -Tag v1.2.3 -Registry registry.com # Build with custom tag and registry"
}

# Show help if requested
if ($Help) {
    Show-Usage
    exit 0
}

# Set build flags
$BuildFrontend = -not $NoFrontend
$BuildBackend = -not $NoBackend
$PushImages = $Push.IsPresent

# Set working directory to project root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
Set-Location $ProjectRoot

Write-Info "Starting build for environment: $Environment"
Write-Info "Services: $Services"
Write-Info "Tag: $Tag"

# Load environment variables
$EnvFile = "deploy\environments\$Environment\.env"
if (Test-Path $EnvFile) {
    Write-Info "Loading environment variables from $EnvFile"
    Get-Content $EnvFile | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
        $key, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
} else {
    Write-ErrorMsg "Environment file not found: $EnvFile"
    exit 1
}

# Set image prefix
if ($Registry) {
    $ImagePrefix = "$Registry/"
} else {
    $ImagePrefix = "workflow-platform-"
}

# Backend services
$BackendServices = @(
    "workflow-engine-core",
    "admin-center",
    "user-portal",
    "developer-workstation",
    "api-gateway"
)

# Frontend services
$FrontendServices = @(
    "admin-center",
    "user-portal",
    "developer-workstation"
)

# Function to build backend service
function Build-BackendService {
    param([string]$Service)
    
    $ServiceDir = "backend\$Service"
    
    if (-not (Test-Path $ServiceDir)) {
        Write-ErrorMsg "Backend service directory not found: $ServiceDir"
        return $false
    }
    
    Write-Info "Building backend service: $Service"
    
    # Build with Maven first
    Write-Info "Running Maven build for $Service..."
    Push-Location $ServiceDir
    
    try {
        if ($Environment -eq "prod") {
            & mvn clean package -DskipTests -Pprod
        } else {
            & mvn clean package -DskipTests
        }
        
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed"
        }
    }
    catch {
        Write-ErrorMsg "Maven build failed for $Service"
        Pop-Location
        return $false
    }
    finally {
        Pop-Location
    }
    
    # Build Docker image
    $ImageName = "${ImagePrefix}${Service}:${Tag}"
    Write-Info "Building Docker image: $ImageName"
    
    $BuildDate = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    
    & docker build `
        --build-arg ENVIRONMENT="$Environment" `
        --build-arg BUILD_DATE="$BuildDate" `
        --build-arg VERSION="$Tag" `
        -t "$ImageName" `
        -f "$ServiceDir\Dockerfile" `
        "$ServiceDir"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Successfully built $ImageName"
        
        # Push image if requested
        if ($PushImages -and $Registry) {
            Write-Info "Pushing image: $ImageName"
            & docker push "$ImageName"
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Successfully pushed $ImageName"
            } else {
                Write-ErrorMsg "Failed to push $ImageName"
                return $false
            }
        }
        return $true
    } else {
        Write-ErrorMsg "Failed to build $ImageName"
        return $false
    }
}

# Function to build frontend service
function Build-FrontendService {
    param([string]$Service)
    
    $ServiceDir = "frontend\$Service"
    
    if (-not (Test-Path $ServiceDir)) {
        Write-ErrorMsg "Frontend service directory not found: $ServiceDir"
        return $false
    }
    
    Write-Info "Building frontend service: $Service"
    
    # Set environment-specific build args
    $BuildArgs = @()
    switch ($Service) {
        "admin-center" {
            $AdminCenterPort = [Environment]::GetEnvironmentVariable("ADMIN_CENTER_PORT")
            $WorkflowEnginePort = [Environment]::GetEnvironmentVariable("WORKFLOW_ENGINE_PORT")
            $BuildArgs += "--build-arg", "VITE_API_BASE_URL=http://localhost:$AdminCenterPort"
            $BuildArgs += "--build-arg", "VITE_WORKFLOW_ENGINE_URL=http://localhost:$WorkflowEnginePort"
        }
        "user-portal" {
            $UserPortalPort = [Environment]::GetEnvironmentVariable("USER_PORTAL_PORT")
            $ApiGatewayPort = [Environment]::GetEnvironmentVariable("API_GATEWAY_PORT")
            $BuildArgs += "--build-arg", "VITE_API_BASE_URL=http://localhost:$UserPortalPort"
            $BuildArgs += "--build-arg", "VITE_API_GATEWAY_URL=http://localhost:$ApiGatewayPort"
        }
        "developer-workstation" {
            $DeveloperWorkstationPort = [Environment]::GetEnvironmentVariable("DEVELOPER_WORKSTATION_PORT")
            $WorkflowEnginePort = [Environment]::GetEnvironmentVariable("WORKFLOW_ENGINE_PORT")
            $BuildArgs += "--build-arg", "VITE_API_BASE_URL=http://localhost:$DeveloperWorkstationPort"
            $BuildArgs += "--build-arg", "VITE_WORKFLOW_ENGINE_URL=http://localhost:$WorkflowEnginePort"
        }
    }
    
    # Build Docker image
    $ImageName = "${ImagePrefix}frontend-${Service}:${Tag}"
    Write-Info "Building Docker image: $ImageName"
    
    $BuildDate = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    
    $DockerArgs = @(
        "build",
        "--build-arg", "ENVIRONMENT=$Environment",
        "--build-arg", "BUILD_DATE=$BuildDate",
        "--build-arg", "VERSION=$Tag"
    )
    $DockerArgs += $BuildArgs
    $DockerArgs += @(
        "-t", "$ImageName",
        "-f", "$ServiceDir\Dockerfile",
        "$ServiceDir"
    )
    
    & docker @DockerArgs
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Successfully built $ImageName"
        
        # Push image if requested
        if ($PushImages -and $Registry) {
            Write-Info "Pushing image: $ImageName"
            & docker push "$ImageName"
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Successfully pushed $ImageName"
            } else {
                Write-ErrorMsg "Failed to push $ImageName"
                return $false
            }
        }
        return $true
    } else {
        Write-ErrorMsg "Failed to build $ImageName"
        return $false
    }
}

# Main build logic
$BuildSuccess = $true

if ($Services -eq "all") {
    # Build all services
    if ($BuildBackend) {
        Write-Info "Building all backend services..."
        foreach ($Service in $BackendServices) {
            if (-not (Build-BackendService $Service)) {
                $BuildSuccess = $false
            }
        }
    }
    
    if ($BuildFrontend) {
        Write-Info "Building all frontend services..."
        foreach ($Service in $FrontendServices) {
            if (-not (Build-FrontendService $Service)) {
                $BuildSuccess = $false
            }
        }
    }
} elseif ($Services -eq "backend") {
    # Build only backend services
    Write-Info "Building all backend services..."
    foreach ($Service in $BackendServices) {
        if (-not (Build-BackendService $Service)) {
            $BuildSuccess = $false
        }
    }
} elseif ($Services -eq "frontend") {
    # Build only frontend services
    Write-Info "Building all frontend services..."
    foreach ($Service in $FrontendServices) {
        if (-not (Build-FrontendService $Service)) {
            $BuildSuccess = $false
        }
    }
} else {
    # Build specific service
    if ($BackendServices -contains $Services) {
        if (-not (Build-BackendService $Services)) {
            $BuildSuccess = $false
        }
    } elseif ($FrontendServices -contains $Services) {
        if (-not (Build-FrontendService $Services)) {
            $BuildSuccess = $false
        }
    } else {
        Write-ErrorMsg "Unknown service: $Services"
        Write-Info "Available backend services: $($BackendServices -join ', ')"
        Write-Info "Available frontend services: $($FrontendServices -join ', ')"
        exit 1
    }
}

if ($BuildSuccess) {
    Write-Success "Build completed successfully for environment: $Environment"
    exit 0
} else {
    Write-ErrorMsg "Build failed for some services"
    exit 1
}