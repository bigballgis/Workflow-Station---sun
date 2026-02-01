#!/usr/bin/env pwsh

# =====================================================
# Multi-Environment Status Check Script
# =====================================================

param(
    [switch]$Verbose,
    [switch]$Test,
    [switch]$Help
)

# Set error action preference
$ErrorActionPreference = "Continue"

# Enable verbose output if requested
if ($Verbose) {
    $VerbosePreference = "Continue"
}

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

function Show-Usage {
    Write-Host "Usage: .\status-all.ps1 [OPTIONS]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Test      Run health tests for all environments"
    Write-Host "  -Verbose   Show detailed output"
    Write-Host "  -Help      Show this help message"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\status-all.ps1                    # Check status of all environments"
    Write-Host "  .\status-all.ps1 -Test              # Check status and run health tests"
    Write-Host "  .\status-all.ps1 -Verbose           # Show detailed status information"
}

if ($Help) {
    Show-Usage
    exit 0
}

Write-Host "=== Multi-Environment Status Check ===" -ForegroundColor Green
Write-Host "Checking status of all environments..." -ForegroundColor Yellow

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

# Environments to check
$Environments = @("dev", "sit", "uat", "prod")

# Results tracking
$Results = @{}

foreach ($env in $Environments) {
    Write-Host "`n=== Environment: $env ===" -ForegroundColor Cyan
    
    $EnvDir = Join-Path $ProjectRoot "deploy\environments\$env"
    $EnvFile = Join-Path $EnvDir ".env"
    $ComposeFile = Join-Path $EnvDir "docker-compose.$env.yml"
    
    # Check if environment files exist
    $EnvExists = Test-Path $EnvFile
    $ComposeExists = Test-Path $ComposeFile
    
    Write-Host "Environment file: $(if ($EnvExists) { '✓ Found' } else { '✗ Missing' })" -ForegroundColor $(if ($EnvExists) { "Green" } else { "Red" })
    Write-Host "Compose file: $(if ($ComposeExists) { '✓ Found' } else { '✗ Missing' })" -ForegroundColor $(if ($ComposeExists) { "Green" } else { "Red" })
    
    if (-not $EnvExists -or -not $ComposeExists) {
        $Results[$env] = @{
            Status = "Configuration Missing"
            Services = 0
            Running = 0
            Healthy = 0
        }
        continue
    }
    
    # Check running services
    try {
        $ComposeCmd = @("docker-compose", "-f", $ComposeFile, "--env-file", $EnvFile)
        
        # Get service status
        $ServiceStatus = & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] ps --format json 2>$null | Where-Object { $_ -ne "" }
        
        if ($ServiceStatus) {
            try {
                # Handle multiple JSON objects
                $Services = @()
                foreach ($line in $ServiceStatus) {
                    if ($line.Trim()) {
                        $Services += $line | ConvertFrom-Json
                    }
                }
                
                $TotalServices = $Services.Count
                $RunningServices = ($Services | Where-Object { $_.State -eq "running" }).Count
            } catch {
                Write-Verbose "JSON parsing failed, falling back to simple format"
                # Fallback to simple ps format
                $SimpleStatus = & $ComposeCmd[0] $ComposeCmd[1..($ComposeCmd.Length-1)] ps 2>$null
                if ($SimpleStatus) {
                    $Services = $SimpleStatus | Select-Object -Skip 1 | Where-Object { $_ -ne "" }
                    $TotalServices = $Services.Count
                    $RunningServices = ($Services | Where-Object { $_ -match "Up" }).Count
                } else {
                    $TotalServices = 0
                    $RunningServices = 0
                }
            }
            
            Write-Host "Services: $RunningServices/$TotalServices running" -ForegroundColor $(if ($RunningServices -eq $TotalServices -and $TotalServices -gt 0) { "Green" } elseif ($RunningServices -gt 0) { "Yellow" } else { "Red" })
            
            if ($Verbose -and $Services) {
                Write-Host "Service Details:" -ForegroundColor Gray
                foreach ($service in $Services) {
                    $statusColor = switch ($service.State) {
                        "running" { "Green" }
                        "exited" { "Red" }
                        default { "Yellow" }
                    }
                    Write-Host "  $($service.Service): $($service.State)" -ForegroundColor $statusColor
                }
            }
        } else {
            $TotalServices = 0
            $RunningServices = 0
            Write-Host "Services: No services found" -ForegroundColor Gray
        }
        
        # Run health test if requested
        $HealthyServices = 0
        if ($Test -and $RunningServices -gt 0) {
            Write-Host "Running health tests..." -ForegroundColor Yellow
            
            $TestScript = Join-Path $ScriptDir "test-deployment.ps1"
            if (Test-Path $TestScript) {
                $testResult = & $TestScript -Environment $env -SkipHealthChecks 2>$null
                if ($LASTEXITCODE -eq 0) {
                    $HealthyServices = $RunningServices
                    Write-Host "Health tests: ✓ Passed" -ForegroundColor Green
                } else {
                    Write-Host "Health tests: ✗ Failed" -ForegroundColor Red
                }
            } else {
                Write-Host "Health tests: Test script not found" -ForegroundColor Yellow
            }
        }
        
        $Results[$env] = @{
            Status = if ($RunningServices -eq $TotalServices -and $TotalServices -gt 0) { "Running" } elseif ($RunningServices -gt 0) { "Partial" } else { "Stopped" }
            Services = $TotalServices
            Running = $RunningServices
            Healthy = $HealthyServices
        }
        
    } catch {
        Write-ErrorMsg "Error checking environment $env`: $_"
        $Results[$env] = @{
            Status = "Error"
            Services = 0
            Running = 0
            Healthy = 0
        }
    }
}

# Summary
Write-Host "`n=== Summary ===" -ForegroundColor Green

$TotalEnvironments = $Environments.Count
$RunningEnvironments = 0
$PartialEnvironments = 0
$StoppedEnvironments = 0
$ErrorEnvironments = 0

foreach ($env in $Environments) {
    $result = $Results[$env]
    $statusColor = switch ($result.Status) {
        "Running" { "Green"; $RunningEnvironments++ }
        "Partial" { "Yellow"; $PartialEnvironments++ }
        "Stopped" { "Red"; $StoppedEnvironments++ }
        "Error" { "Magenta"; $ErrorEnvironments++ }
        default { "Gray"; $ErrorEnvironments++ }
    }
    
    $statusText = "$($result.Status)"
    if ($result.Services -gt 0) {
        $statusText += " ($($result.Running)/$($result.Services) services)"
    }
    if ($Test -and $result.Healthy -gt 0) {
        $statusText += " [$($result.Healthy) healthy]"
    }
    
    Write-Host "  $env`: $statusText" -ForegroundColor $statusColor
}

Write-Host "`nEnvironment Status:" -ForegroundColor Yellow
Write-Host "  Running: $RunningEnvironments" -ForegroundColor Green
Write-Host "  Partial: $PartialEnvironments" -ForegroundColor Yellow
Write-Host "  Stopped: $StoppedEnvironments" -ForegroundColor Red
Write-Host "  Errors: $ErrorEnvironments" -ForegroundColor Magenta

# Access URLs for running environments
$RunningEnvs = $Environments | Where-Object { $Results[$_].Status -eq "Running" }
if ($RunningEnvs.Count -gt 0) {
    Write-Host "`n=== Access URLs ===" -ForegroundColor Green
    
    foreach ($env in $RunningEnvs) {
        Write-Host "`n$env Environment:" -ForegroundColor Cyan
        
        # Load environment variables to get ports
        $EnvFile = Join-Path $ProjectRoot "deploy\environments\$env\.env"
        $EnvVars = @{}
        
        Get-Content $EnvFile | ForEach-Object {
            if ($_ -match '^([^#][^=]+)=(.*)$') {
                $name = $matches[1].Trim()
                $value = $matches[2].Trim() -replace '^["'']|["'']$', ''
                $EnvVars[$name] = $value
            }
        }
        
        if ($EnvVars["API_GATEWAY_PORT"]) {
            Write-Host "  API Gateway: http://localhost:$($EnvVars['API_GATEWAY_PORT'])" -ForegroundColor Gray
        }
        if ($EnvVars["ADMIN_CENTER_FRONTEND_PORT"]) {
            Write-Host "  Admin Center: http://localhost:$($EnvVars['ADMIN_CENTER_FRONTEND_PORT'])" -ForegroundColor Gray
        }
        if ($EnvVars["USER_PORTAL_FRONTEND_PORT"]) {
            Write-Host "  User Portal: http://localhost:$($EnvVars['USER_PORTAL_FRONTEND_PORT'])" -ForegroundColor Gray
        }
        if ($EnvVars["DEVELOPER_WORKSTATION_FRONTEND_PORT"]) {
            Write-Host "  Developer Workstation: http://localhost:$($EnvVars['DEVELOPER_WORKSTATION_FRONTEND_PORT'])" -ForegroundColor Gray
        }
    }
}

# Exit with appropriate code
if ($ErrorEnvironments -gt 0) {
    exit 2
} elseif ($StoppedEnvironments -gt 0) {
    exit 1
} else {
    exit 0
}