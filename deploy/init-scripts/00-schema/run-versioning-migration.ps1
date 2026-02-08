#!/usr/bin/env pwsh
# =====================================================
# Run Function Unit Versioning Migration
# Executes the versioning migration scripts in the correct order
# =====================================================

param(
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$DbName = "workflow_platform_dev",
    [string]$DbUser = "postgres",
    [switch]$Rollback = $false,
    [switch]$Test = $false
)

# Color output functions
function Write-Success {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Green
}

function Write-Info {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Cyan
}

function Write-Warning {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host $Message -ForegroundColor Red
}

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Set PGPASSWORD environment variable (will prompt if not set)
if (-not $env:PGPASSWORD) {
    $SecurePassword = Read-Host "Enter PostgreSQL password for user '$DbUser'" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
    $env:PGPASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

# Connection string
$PsqlCmd = "psql -h $DbHost -p $DbPort -U $DbUser -d $DbName"

Write-Info "=========================================="
Write-Info "Function Unit Versioning Migration"
Write-Info "=========================================="
Write-Info "Database: $DbName"
Write-Info "Host: $DbHost:$DbPort"
Write-Info "User: $DbUser"
Write-Info ""

if ($Test) {
    # Run test script
    Write-Info "Running migration tests..."
    $TestScript = Join-Path $ScriptDir "test-versioning-migration.sql"
    
    if (-not (Test-Path $TestScript)) {
        Write-Error "Test script not found: $TestScript"
        exit 1
    }
    
    $result = & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $TestScript 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "✓ All tests passed!"
        Write-Info ""
        Write-Info "The migration scripts are ready to be applied."
        Write-Info "Run this script without -Test flag to apply the migration."
    } else {
        Write-Error "✗ Tests failed!"
        Write-Error $result
        exit 1
    }
}
elseif ($Rollback) {
    # Rollback migration
    Write-Warning "=========================================="
    Write-Warning "WARNING: ROLLBACK OPERATION"
    Write-Warning "=========================================="
    Write-Warning "This will remove all versioning columns and data!"
    Write-Warning ""
    $confirmation = Read-Host "Are you sure you want to rollback? (yes/no)"
    
    if ($confirmation -ne "yes") {
        Write-Info "Rollback cancelled."
        exit 0
    }
    
    Write-Info "Rolling back data initialization..."
    $RollbackDataScript = Join-Path $ScriptDir "09-initialize-function-unit-versions-rollback.sql"
    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $RollbackDataScript
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "✗ Data rollback failed!"
        exit 1
    }
    Write-Success "✓ Data rollback completed"
    
    Write-Info "Rolling back schema changes..."
    $RollbackSchemaScript = Join-Path $ScriptDir "08-add-function-unit-versioning-rollback.sql"
    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $RollbackSchemaScript
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "✗ Schema rollback failed!"
        exit 1
    }
    Write-Success "✓ Schema rollback completed"
    
    Write-Success ""
    Write-Success "=========================================="
    Write-Success "Rollback completed successfully!"
    Write-Success "=========================================="
}
else {
    # Forward migration
    Write-Info "Step 1: Applying schema changes..."
    $SchemaScript = Join-Path $ScriptDir "08-add-function-unit-versioning.sql"
    
    if (-not (Test-Path $SchemaScript)) {
        Write-Error "Schema script not found: $SchemaScript"
        exit 1
    }
    
    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $SchemaScript
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "✗ Schema migration failed!"
        exit 1
    }
    Write-Success "✓ Schema changes applied"
    
    Write-Info ""
    Write-Info "Step 2: Initializing version data..."
    $DataScript = Join-Path $ScriptDir "09-initialize-function-unit-versions.sql"
    
    if (-not (Test-Path $DataScript)) {
        Write-Error "Data script not found: $DataScript"
        exit 1
    }
    
    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $DataScript
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "✗ Data initialization failed!"
        exit 1
    }
    Write-Success "✓ Version data initialized"
    
    Write-Success ""
    Write-Success "=========================================="
    Write-Success "Migration completed successfully!"
    Write-Success "=========================================="
    Write-Info ""
    Write-Info "Next steps:"
    Write-Info "1. Verify the migration with: .\run-versioning-migration.ps1 -Test"
    Write-Info "2. Review the changes in your database"
    Write-Info "3. Update application code to use versioning features"
    Write-Info ""
    Write-Info "For more information, see: VERSIONING_MIGRATION_README.md"
}

# Clear password from environment
$env:PGPASSWORD = $null
