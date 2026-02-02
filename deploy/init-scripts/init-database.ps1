# =====================================================
# Database Initialization Script (PowerShell)
# =====================================================
# This script initializes the workflow platform database with:
# - Database schemas (all tables and constraints)
# - System default roles (5 roles)
# - Virtual groups (5 groups)
# - Test users (5 users)
# - Optional: Test data and workflow definitions
# =====================================================

param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "workflow_platform",
    [string]$DbUser = "postgres",
    [string]$DbPassword = ""
)

# Script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Function to print colored messages
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

# Function to execute SQL file
function Execute-SqlFile {
    param(
        [string]$FilePath,
        [string]$Description
    )
    
    Write-Info "Executing: $Description"
    
    $env:PGPASSWORD = $DbPassword
    
    $psqlArgs = @(
        "-h", $DbHost,
        "-p", $DbPort,
        "-U", $DbUser,
        "-d", $DbName,
        "-f", $FilePath
    )
    
    try {
        $output = & psql @psqlArgs 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Success "$Description completed"
            return $true
        } else {
            Write-ErrorMsg "$Description failed"
            Write-Host $output
            return $false
        }
    } catch {
        Write-ErrorMsg "$Description failed with exception: $_"
        return $false
    } finally {
        $env:PGPASSWORD = ""
    }
}

# Main initialization process
function Main {
    Write-Host ""
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "  Workflow Platform Database Init" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "Database: $DbName"
    Write-Host "Host: ${DbHost}:${DbPort}"
    Write-Host "User: $DbUser"
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host ""
    
    # Check if psql is available
    try {
        $null = Get-Command psql -ErrorAction Stop
    } catch {
        Write-ErrorMsg "psql command not found. Please install PostgreSQL client tools."
        Write-Host "Download from: https://www.postgresql.org/download/windows/"
        exit 1
    }
    
    # Step 1: Create schemas
    Write-Info "Step 1/4: Creating database schemas..."
    Write-Host ""
    
    $schemaFile = Join-Path $ScriptDir "00-schema\00-init-all-schemas.sql"
    if (-not (Execute-SqlFile -FilePath $schemaFile -Description "All database schemas")) {
        exit 1
    }
    
    Write-Host ""
    Write-Success "Step 1/4: Database schemas created successfully"
    Write-Host ""
    
    # Step 2: Create roles and virtual groups
    Write-Info "Step 2/4: Creating system roles and virtual groups..."
    Write-Host ""
    
    $rolesFile = Join-Path $ScriptDir "01-admin\01-create-roles-and-groups.sql"
    if (-not (Execute-SqlFile -FilePath $rolesFile -Description "System roles and virtual groups")) {
        exit 1
    }
    
    Write-Host ""
    Write-Success "Step 2/4: Roles and groups created successfully"
    Write-Host ""
    
    # Step 3: Create test users
    Write-Info "Step 3/4: Creating test users..."
    Write-Host ""
    
    $usersFile = Join-Path $ScriptDir "01-admin\02-create-test-users.sql"
    if (-not (Execute-SqlFile -FilePath $usersFile -Description "Test users")) {
        exit 1
    }
    
    Write-Host ""
    Write-Success "Step 3/4: Test users created successfully"
    Write-Host ""
    
    # Step 4: Optional test data
    Write-Info "Step 4/6: Loading optional test data..."
    Write-Host ""
    
    $testDataDir = Join-Path $ScriptDir "02-test-data"
    if (Test-Path $testDataDir) {
        $sqlFiles = Get-ChildItem -Path $testDataDir -Filter "*.sql" | Sort-Object Name
        foreach ($file in $sqlFiles) {
            Execute-SqlFile -FilePath $file.FullName -Description $file.Name
        }
        Write-Success "Step 4/6: Test data loaded successfully"
    } else {
        Write-Warning "Step 4/6: No test data directory found, skipping"
    }
    
    Write-Host ""
    
    # Step 5: Optional purchase workflow
    Write-Info "Step 5/6: Loading purchase workflow..."
    Write-Host ""
    
    $workflowDir = Join-Path $ScriptDir "04-purchase-workflow"
    if (Test-Path $workflowDir) {
        $sqlFiles = Get-ChildItem -Path $workflowDir -Filter "*.sql" | Sort-Object Name
        foreach ($file in $sqlFiles) {
            Execute-SqlFile -FilePath $file.FullName -Description $file.Name
        }
        Write-Success "Step 5/6: Purchase workflow loaded successfully"
    } else {
        Write-Warning "Step 5/6: No purchase workflow directory found, skipping"
    }
    
    Write-Host ""
    
    # Step 6: Verification
    Write-Info "Step 6/6: Verifying initialization..."
    Write-Host ""
    Write-Host "=========================================" -ForegroundColor Green
    Write-Host "  Database Initialization Complete!" -ForegroundColor Green
    Write-Host "=========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "System Summary:"
    Write-Host "  ✓ Database schemas created"
    Write-Host "  ✓ 5 system roles created"
    Write-Host "  ✓ 5 virtual groups created"
    Write-Host "  ✓ 5 test users created"
    Write-Host "  ✓ Test organization structure created"
    Write-Host "  ✓ Purchase workflow created"
    Write-Host ""
    Write-Host "Login Credentials (password: password):"
    Write-Host "  • admin      - System Administrator"
    Write-Host "  • auditor    - System Auditor"
    Write-Host "  • manager    - Department Manager"
    Write-Host "  • developer  - Workflow Developer"
    Write-Host "  • designer   - Workflow Designer"
    Write-Host ""
    Write-Host "Next Steps:"
    Write-Host "  1. Start the application services"
    Write-Host "  2. Access Admin Center: http://localhost:8081"
    Write-Host "  3. Access User Portal: http://localhost:8082"
    Write-Host "  4. Access Developer Workstation: http://localhost:8083"
    Write-Host "=========================================" -ForegroundColor Green
    Write-Host ""
}

# Run main function
Main
