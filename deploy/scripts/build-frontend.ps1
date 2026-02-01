#!/usr/bin/env pwsh

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev", "sit", "uat", "prod")]
    [string]$Environment,
    
    [switch]$SkipBuild,
    [switch]$LocalOnly,
    [switch]$Verbose
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Enable verbose output if requested
if ($Verbose) {
    $VerbosePreference = "Continue"
}

Write-Host "=== Frontend Build Script ===" -ForegroundColor Green
Write-Host "Environment: $Environment" -ForegroundColor Yellow
Write-Host "Skip Build: $SkipBuild" -ForegroundColor Yellow
Write-Host "Local Only: $LocalOnly" -ForegroundColor Yellow

# Get script directory and project root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$EnvDir = Join-Path $ProjectRoot "deploy\environments\$Environment"

Write-Verbose "Script Directory: $ScriptDir"
Write-Verbose "Project Root: $ProjectRoot"
Write-Verbose "Environment Directory: $EnvDir"

# Check if environment directory exists
if (-not (Test-Path $EnvDir)) {
    Write-Error "Environment directory not found: $EnvDir"
    exit 1
}

# Load environment variables
$EnvFile = Join-Path $EnvDir ".env"
if (-not (Test-Path $EnvFile)) {
    Write-Error "Environment file not found: $EnvFile"
    exit 1
}

Write-Host "Loading environment variables from: $EnvFile" -ForegroundColor Cyan

# Parse .env file and set environment variables
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^([^#][^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        # Remove quotes if present
        $value = $value -replace '^["'']|["'']$', ''
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
        Write-Verbose "Set $name=$value"
    }
}

# Frontend applications to build
$FrontendApps = @(
    @{
        Name = "admin-center"
        Path = Join-Path $ProjectRoot "frontend\admin-center"
        Port = [Environment]::GetEnvironmentVariable("ADMIN_CENTER_FRONTEND_PORT")
    },
    @{
        Name = "user-portal"
        Path = Join-Path $ProjectRoot "frontend\user-portal"
        Port = [Environment]::GetEnvironmentVariable("USER_PORTAL_FRONTEND_PORT")
    },
    @{
        Name = "developer-workstation"
        Path = Join-Path $ProjectRoot "frontend\developer-workstation"
        Port = [Environment]::GetEnvironmentVariable("DEVELOPER_WORKSTATION_FRONTEND_PORT")
    }
)

function Build-FrontendApp {
    param(
        [string]$AppName,
        [string]$AppPath,
        [string]$Port
    )
    
    Write-Host "`n=== Building $AppName ===" -ForegroundColor Green
    
    if (-not (Test-Path $AppPath)) {
        Write-Error "Frontend app directory not found: $AppPath"
        return $false
    }
    
    # Change to app directory
    Push-Location $AppPath
    
    try {
        # Check if package.json exists
        if (-not (Test-Path "package.json")) {
            Write-Error "package.json not found in $AppPath"
            return $false
        }
        
        # Set environment variables for build
        [Environment]::SetEnvironmentVariable("VITE_API_BASE_URL", "http://localhost:$([Environment]::GetEnvironmentVariable('API_GATEWAY_PORT'))", "Process")
        [Environment]::SetEnvironmentVariable("VITE_ENVIRONMENT", $Environment, "Process")
        
        Write-Host "Environment variables for build:" -ForegroundColor Cyan
        Write-Host "  VITE_API_BASE_URL: $([Environment]::GetEnvironmentVariable('VITE_API_BASE_URL'))" -ForegroundColor Gray
        Write-Host "  VITE_ENVIRONMENT: $([Environment]::GetEnvironmentVariable('VITE_ENVIRONMENT'))" -ForegroundColor Gray
        
        if (-not $SkipBuild) {
            # Install dependencies
            Write-Host "Installing dependencies..." -ForegroundColor Yellow
            npm ci --silent
            if ($LASTEXITCODE -ne 0) {
                Write-Error "Failed to install dependencies for $AppName"
                return $false
            }
            
            # Build the application
            Write-Host "Building application..." -ForegroundColor Yellow
            npm run build
            if ($LASTEXITCODE -ne 0) {
                Write-Error "Failed to build $AppName"
                return $false
            }
            
            Write-Host "✓ Successfully built $AppName" -ForegroundColor Green
        } else {
            Write-Host "Skipping build for $AppName (SkipBuild flag set)" -ForegroundColor Yellow
        }
        
        # Build Docker image if not LocalOnly
        if (-not $LocalOnly) {
            Write-Host "Building Docker image for $AppName..." -ForegroundColor Yellow
            
            $ImageTag = "${AppName}-frontend:${Environment}"
            docker build -t $ImageTag .
            
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Failed to build Docker image for $AppName, but continuing..."
                return $false
            }
            
            Write-Host "✓ Successfully built Docker image: $ImageTag" -ForegroundColor Green
        }
        
        return $true
        
    } catch {
        Write-Error "Error building $AppName`: $_"
        return $false
    } finally {
        Pop-Location
    }
}

# Build all frontend applications
$BuildResults = @{}
$OverallSuccess = $true

foreach ($app in $FrontendApps) {
    $success = Build-FrontendApp -AppName $app.Name -AppPath $app.Path -Port $app.Port
    $BuildResults[$app.Name] = $success
    if (-not $success) {
        $OverallSuccess = $false
    }
}

# Summary
Write-Host "`n=== Build Summary ===" -ForegroundColor Green
foreach ($app in $FrontendApps) {
    $status = if ($BuildResults[$app.Name]) { "✓ SUCCESS" } else { "✗ FAILED" }
    $color = if ($BuildResults[$app.Name]) { "Green" } else { "Red" }
    Write-Host "  $($app.Name): $status" -ForegroundColor $color
}

if ($OverallSuccess) {
    Write-Host "`n✓ All frontend applications built successfully!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n✗ Some frontend applications failed to build. Check the logs above." -ForegroundColor Red
    exit 1
}