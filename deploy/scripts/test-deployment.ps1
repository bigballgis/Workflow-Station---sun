#!/usr/bin/env pwsh

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev", "sit", "uat", "prod")]
    [string]$Environment,
    
    [switch]$SkipHealthChecks,
    [switch]$Verbose
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Enable verbose output if requested
if ($Verbose) {
    $VerbosePreference = "Continue"
}

Write-Host "=== Deployment Test Script ===" -ForegroundColor Green
Write-Host "Environment: $Environment" -ForegroundColor Yellow

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

# Service endpoints to test
$Services = @(
    @{
        Name = "PostgreSQL"
        Port = [Environment]::GetEnvironmentVariable("POSTGRES_PORT")
        HealthCheck = "database"
        Container = "postgres-$Environment"
    },
    @{
        Name = "Redis"
        Port = [Environment]::GetEnvironmentVariable("REDIS_PORT")
        HealthCheck = "cache"
        Container = "redis-$Environment"
    },
    @{
        Name = "Kafka"
        Port = [Environment]::GetEnvironmentVariable("KAFKA_PORT")
        HealthCheck = "messaging"
        Container = "kafka-$Environment"
    },
    @{
        Name = "API Gateway"
        Port = [Environment]::GetEnvironmentVariable("API_GATEWAY_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('API_GATEWAY_PORT'))/actuator/health"
        Container = "api-gateway-$Environment"
    },
    @{
        Name = "Workflow Engine"
        Port = [Environment]::GetEnvironmentVariable("WORKFLOW_ENGINE_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('WORKFLOW_ENGINE_PORT'))/actuator/health"
        Container = "workflow-engine-$Environment"
    },
    @{
        Name = "User Portal Backend"
        Port = [Environment]::GetEnvironmentVariable("USER_PORTAL_BACKEND_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('USER_PORTAL_BACKEND_PORT'))/actuator/health"
        Container = "user-portal-backend-$Environment"
    },
    @{
        Name = "Developer Workstation Backend"
        Port = [Environment]::GetEnvironmentVariable("DEVELOPER_WORKSTATION_BACKEND_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('DEVELOPER_WORKSTATION_BACKEND_PORT'))/actuator/health"
        Container = "developer-workstation-backend-$Environment"
    },
    @{
        Name = "Admin Center Backend"
        Port = [Environment]::GetEnvironmentVariable("ADMIN_CENTER_BACKEND_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('ADMIN_CENTER_BACKEND_PORT'))/actuator/health"
        Container = "admin-center-backend-$Environment"
    },
    @{
        Name = "Admin Center Frontend"
        Port = [Environment]::GetEnvironmentVariable("ADMIN_CENTER_FRONTEND_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('ADMIN_CENTER_FRONTEND_PORT'))"
        Container = "admin-center-frontend-$Environment"
    },
    @{
        Name = "User Portal Frontend"
        Port = [Environment]::GetEnvironmentVariable("USER_PORTAL_FRONTEND_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('USER_PORTAL_FRONTEND_PORT'))"
        Container = "user-portal-frontend-$Environment"
    },
    @{
        Name = "Developer Workstation Frontend"
        Port = [Environment]::GetEnvironmentVariable("DEVELOPER_WORKSTATION_FRONTEND_PORT")
        HealthCheck = "http://localhost:$([Environment]::GetEnvironmentVariable('DEVELOPER_WORKSTATION_FRONTEND_PORT'))"
        Container = "developer-workstation-frontend-$Environment"
    }
)

function Test-ContainerStatus {
    param(
        [string]$ContainerName
    )
    
    try {
        $containerInfo = docker ps --filter "name=$ContainerName" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | Select-Object -Skip 1
        
        if ($containerInfo) {
            $status = ($containerInfo -split '\s+')[1]
            if ($status -like "*Up*") {
                return @{ Running = $true; Status = $status }
            } else {
                return @{ Running = $false; Status = $status }
            }
        } else {
            return @{ Running = $false; Status = "Not found" }
        }
    } catch {
        return @{ Running = $false; Status = "Error: $_" }
    }
}

function Test-HttpEndpoint {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 10
    )
    
    try {
        $response = Invoke-WebRequest -Uri $Url -TimeoutSec $TimeoutSeconds -UseBasicParsing
        return @{ 
            Success = $true
            StatusCode = $response.StatusCode
            ResponseTime = $null
        }
    } catch {
        return @{ 
            Success = $false
            StatusCode = $null
            Error = $_.Exception.Message
        }
    }
}

function Test-PortConnectivity {
    param(
        [string]$Host = "localhost",
        [int]$Port,
        [int]$TimeoutSeconds = 5
    )
    
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $asyncResult = $tcpClient.BeginConnect($Host, $Port, $null, $null)
        $wait = $asyncResult.AsyncWaitHandle.WaitOne($TimeoutSeconds * 1000, $false)
        
        if ($wait) {
            $tcpClient.EndConnect($asyncResult)
            $tcpClient.Close()
            return $true
        } else {
            $tcpClient.Close()
            return $false
        }
    } catch {
        return $false
    }
}

# Test deployment
Write-Host "`n=== Testing Deployment ===" -ForegroundColor Green

$TestResults = @{}
$OverallSuccess = $true

foreach ($service in $Services) {
    Write-Host "`nTesting $($service.Name)..." -ForegroundColor Yellow
    
    # Test container status
    $containerStatus = Test-ContainerStatus -ContainerName $service.Container
    Write-Host "  Container Status: $($containerStatus.Status)" -ForegroundColor $(if ($containerStatus.Running) { "Green" } else { "Red" })
    
    # Test port connectivity
    if ($service.Port) {
        $portOpen = Test-PortConnectivity -Port $service.Port
        Write-Host "  Port $($service.Port): $(if ($portOpen) { 'Open' } else { 'Closed' })" -ForegroundColor $(if ($portOpen) { "Green" } else { "Red" })
    }
    
    # Test health endpoint if available and not skipped
    $healthStatus = $null
    if (-not $SkipHealthChecks -and $service.HealthCheck -like "http*") {
        $healthResult = Test-HttpEndpoint -Url $service.HealthCheck
        $healthStatus = $healthResult.Success
        $statusText = if ($healthResult.Success) { 
            "Healthy (HTTP $($healthResult.StatusCode))" 
        } else { 
            "Unhealthy ($($healthResult.Error))" 
        }
        Write-Host "  Health Check: $statusText" -ForegroundColor $(if ($healthResult.Success) { "Green" } else { "Red" })
    }
    
    # Determine overall service status
    $serviceSuccess = $containerStatus.Running
    if ($service.Port) {
        $serviceSuccess = $serviceSuccess -and $portOpen
    }
    if ($healthStatus -ne $null) {
        $serviceSuccess = $serviceSuccess -and $healthStatus
    }
    
    $TestResults[$service.Name] = @{
        Container = $containerStatus.Running
        Port = if ($service.Port) { $portOpen } else { $null }
        Health = $healthStatus
        Overall = $serviceSuccess
    }
    
    if (-not $serviceSuccess) {
        $OverallSuccess = $false
    }
    
    Write-Host "  Overall: $(if ($serviceSuccess) { '✓ PASS' } else { '✗ FAIL' })" -ForegroundColor $(if ($serviceSuccess) { "Green" } else { "Red" })
}

# Summary
Write-Host "`n=== Test Summary ===" -ForegroundColor Green
Write-Host "Environment: $Environment" -ForegroundColor Yellow

$PassCount = 0
$FailCount = 0

foreach ($service in $Services) {
    $result = $TestResults[$service.Name]
    $status = if ($result.Overall) { "✓ PASS" } else { "✗ FAIL" }
    $color = if ($result.Overall) { "Green" } else { "Red" }
    
    Write-Host "  $($service.Name): $status" -ForegroundColor $color
    
    if ($result.Overall) {
        $PassCount++
    } else {
        $FailCount++
    }
}

Write-Host "`nResults: $PassCount passed, $FailCount failed" -ForegroundColor $(if ($OverallSuccess) { "Green" } else { "Yellow" })

# Access URLs
if ($OverallSuccess) {
    Write-Host "`n=== Access URLs ===" -ForegroundColor Green
    Write-Host "API Gateway: http://localhost:$([Environment]::GetEnvironmentVariable('API_GATEWAY_PORT'))" -ForegroundColor Cyan
    Write-Host "Admin Center: http://localhost:$([Environment]::GetEnvironmentVariable('ADMIN_CENTER_FRONTEND_PORT'))" -ForegroundColor Cyan
    Write-Host "User Portal: http://localhost:$([Environment]::GetEnvironmentVariable('USER_PORTAL_FRONTEND_PORT'))" -ForegroundColor Cyan
    Write-Host "Developer Workstation: http://localhost:$([Environment]::GetEnvironmentVariable('DEVELOPER_WORKSTATION_FRONTEND_PORT'))" -ForegroundColor Cyan
}

if ($OverallSuccess) {
    Write-Host "`n✓ All services are running successfully!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n✗ Some services are not running properly. Check the logs above." -ForegroundColor Red
    exit 1
}