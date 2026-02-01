# =====================================================
# Environment Variables Extraction Script
# =====================================================

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("dev", "sit", "uat", "prod", "all")]
    [string]$Environment = "all",
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("json", "yaml", "env", "table")]
    [string]$Format = "table",
    
    [Parameter(Mandatory=$false)]
    [string]$OutputFile = "",
    
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

function Write-ErrorMsg {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Function to show usage
function Show-Usage {
    Write-Host "Usage: .\extract-env-vars.ps1 [OPTIONS]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Environment ENV     Target environment (dev|sit|uat|prod|all) [default: all]"
    Write-Host "  -Format FORMAT       Output format (json|yaml|env|table) [default: table]"
    Write-Host "  -OutputFile FILE     Output file path (optional)"
    Write-Host "  -Help                Show this help message"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\extract-env-vars.ps1                                    # Show all environments in table format"
    Write-Host "  .\extract-env-vars.ps1 -Environment dev -Format json     # Show dev environment in JSON format"
    Write-Host "  .\extract-env-vars.ps1 -Format yaml -OutputFile vars.yml # Export all environments to YAML file"
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

# Function to parse environment file
function Parse-EnvFile {
    param([string]$FilePath)
    
    $EnvVars = @{}
    
    if (Test-Path $FilePath) {
        Get-Content $FilePath | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
            $key, $value = $_ -split '=', 2
            $EnvVars[$key.Trim()] = $value.Trim()
        }
    }
    
    return $EnvVars
}

# Function to get all unique environment variable keys
function Get-AllEnvKeys {
    param([hashtable[]]$AllEnvs)
    
    $AllKeys = @()
    foreach ($env in $AllEnvs) {
        $AllKeys += $env.Keys
    }
    
    return ($AllKeys | Sort-Object | Get-Unique)
}

# Get environments to process
$Environments = @()
if ($Environment -eq "all") {
    $Environments = @("dev", "sit", "uat", "prod")
} else {
    $Environments = @($Environment)
}

# Parse all environment files
$AllEnvData = @{}
foreach ($env in $Environments) {
    $EnvFile = "deploy\environments\$env\.env"
    if (Test-Path $EnvFile) {
        $AllEnvData[$env] = Parse-EnvFile $EnvFile
        Write-Info "Loaded environment variables from $EnvFile"
    } else {
        Write-ErrorMsg "Environment file not found: $EnvFile"
    }
}

if ($AllEnvData.Count -eq 0) {
    Write-ErrorMsg "No environment files found"
    exit 1
}

# Generate output based on format
$Output = ""

switch ($Format) {
    "json" {
        $JsonData = @{}
        foreach ($env in $AllEnvData.Keys) {
            $JsonData[$env] = $AllEnvData[$env]
        }
        $Output = $JsonData | ConvertTo-Json -Depth 3
    }
    
    "yaml" {
        $Output = "# Environment Variables Configuration`n"
        foreach ($env in $AllEnvData.Keys | Sort-Object) {
            $Output += "`n$env`:`n"
            foreach ($key in $AllEnvData[$env].Keys | Sort-Object) {
                $value = $AllEnvData[$env][$key]
                $Output += "  $key`: `"$value`"`n"
            }
        }
    }
    
    "env" {
        foreach ($env in $AllEnvData.Keys | Sort-Object) {
            $Output += "# $env Environment`n"
            foreach ($key in $AllEnvData[$env].Keys | Sort-Object) {
                $value = $AllEnvData[$env][$key]
                $Output += "$key=$value`n"
            }
            $Output += "`n"
        }
    }
    
    "table" {
        # Get all unique keys
        $AllKeys = Get-AllEnvKeys $AllEnvData.Values
        
        # Create table data
        $TableData = @()
        foreach ($key in $AllKeys) {
            $Row = [PSCustomObject]@{
                Variable = $key
            }
            
            foreach ($env in $Environments) {
                if ($AllEnvData.ContainsKey($env)) {
                    $value = $AllEnvData[$env][$key]
                    if ($value) {
                        # Mask sensitive values
                        if ($key -match "(PASSWORD|SECRET|KEY)" -and $value.Length -gt 8) {
                            $value = $value.Substring(0, 4) + "****" + $value.Substring($value.Length - 4)
                        }
                        $Row | Add-Member -NotePropertyName $env.ToUpper() -NotePropertyValue $value
                    } else {
                        $Row | Add-Member -NotePropertyName $env.ToUpper() -NotePropertyValue "-"
                    }
                }
            }
            
            $TableData += $Row
        }
        
        # Display table
        if ($Environment -eq "all") {
            $TableData | Format-Table -AutoSize
        } else {
            $TableData | Select-Object Variable, $Environment.ToUpper() | Format-Table -AutoSize
        }
        return
    }
}

# Output to file or console
if ($OutputFile) {
    $Output | Out-File -FilePath $OutputFile -Encoding UTF8
    Write-Success "Environment variables exported to: $OutputFile"
} else {
    Write-Output $Output
}

# Summary
Write-Host ""
Write-Host "Environment Variables Summary:" -ForegroundColor Yellow
foreach ($env in $AllEnvData.Keys | Sort-Object) {
    $count = $AllEnvData[$env].Keys.Count
    Write-Host "  $env`: $count variables" -ForegroundColor Cyan
}

# Show categories
Write-Host ""
Write-Host "Variable Categories:" -ForegroundColor Yellow

$Categories = @{
    "Database" = @("POSTGRES_", "DB_")
    "Cache" = @("REDIS_")
    "Messaging" = @("KAFKA_")
    "Security" = @("JWT_", "ENCRYPTION_", "SECURITY_")
    "Server" = @("SERVER_", "_PORT")
    "Logging" = @("LOG_")
    "Monitoring" = @("MANAGEMENT_", "ACTUATOR_")
    "Application" = @("SPRING_", "ENVIRONMENT")
}

$AllKeys = Get-AllEnvKeys $AllEnvData.Values
foreach ($category in $Categories.Keys) {
    $categoryKeys = $AllKeys | Where-Object { 
        $key = $_
        $Categories[$category] | Where-Object { $key -like "*$_*" }
    }
    if ($categoryKeys) {
        Write-Host "  $category`: $($categoryKeys.Count) variables" -ForegroundColor Cyan
    }
}