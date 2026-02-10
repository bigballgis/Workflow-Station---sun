# =====================================================
# Database Initialization Script (Standalone)
# =====================================================
# Run with psql directly (not via Docker).
#
# Usage:
#   .\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform_dev -DbUser platform_dev -DbPassword dev_password_123
# =====================================================

param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "workflow_platform_dev",
    [string]$DbUser = "platform_dev",
    [string]$DbPassword = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step { param([string]$Msg) Write-Host "`n[$((Get-Date).ToString('HH:mm:ss'))] $Msg" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Msg) Write-Host "  OK: $Msg" -ForegroundColor Green }
function Write-Fail { param([string]$Msg) Write-Host "  FAIL: $Msg" -ForegroundColor Red }

function Exec-Sql {
    param([string]$File, [string]$Desc)
    if (-not (Test-Path $File)) { Write-Fail "$Desc - not found: $File"; return $false }
    $env:PGPASSWORD = $DbPassword
    try {
        & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $File 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Ok $Desc; return $true }
        else { Write-Fail $Desc; return $false }
    } finally { $env:PGPASSWORD = "" }
}

<<<<<<< HEAD
# =====================================================
Write-Host ""
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Database Initialization" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Host: ${DbHost}:${DbPort}"
Write-Host "  Database: $DbName"
Write-Host "  User: $DbUser"
Write-Host "=========================================" -ForegroundColor Yellow

try { $null = Get-Command psql -ErrorAction Stop }
catch { Write-Fail "psql not found."; exit 1 }

# Step 1: Base schemas
Write-Step "Step 1/4: Creating base schemas..."
$schemas = @(
    "00-schema/01-platform-security-schema.sql",
    "00-schema/02-workflow-engine-schema.sql",
    "00-schema/03-user-portal-schema.sql",
    "00-schema/04-developer-workstation-schema.sql",
    "00-schema/05-admin-center-schema.sql"
)
foreach ($s in $schemas) {
    if (-not (Exec-Sql -File (Join-Path $ScriptDir $s) -Desc (Split-Path $s -Leaf))) { exit 1 }
=======
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
    Write-Info "Step 3/5: Creating test users..."
    Write-Host ""
    
    $usersFile = Join-Path $ScriptDir "01-admin\02-create-test-users.sql"
    if (-not (Execute-SqlFile -FilePath $usersFile -Description "Test users")) {
        exit 1
    }
    
    Write-Host ""
    Write-Success "Step 3/5: Test users created successfully"
    Write-Host ""
    
    # Step 4: Create role assignments
    Write-Info "Step 4/5: Creating role assignments..."
    Write-Host ""
    
    $assignmentsFile = Join-Path $ScriptDir "01-admin\05-create-role-assignments.sql"
    if (-not (Execute-SqlFile -FilePath $assignmentsFile -Description "Role assignments")) {
        exit 1
    }
    
    Write-Host ""
    Write-Success "Step 4/5: Role assignments created successfully"
    Write-Host ""
    
    # Step 5: Optional test data
    Write-Info "Step 5/5: Loading optional test data..."
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
>>>>>>> 782ced6 (fix deploy script and flyway)
}

# Step 2: Incremental migrations
Write-Step "Step 2/4: Applying incremental migrations..."
$migrations = @(
    "00-schema/06-add-deployment-rollback-columns.sql",
    "00-schema/07-add-action-definitions-table.sql",
    "00-schema/08-add-function-unit-versioning.sql",
    "00-schema/10-add-approval-order-column.sql",
    "00-schema/11-add-unique-enabled-constraint.sql",
    "00-schema/12-add-enabled-field-to-dw-function-units.sql"
)
foreach ($m in $migrations) {
    $path = Join-Path $ScriptDir $m
    if (Test-Path $path) { Exec-Sql -File $path -Desc (Split-Path $m -Leaf) | Out-Null }
}

# Step 3: Roles, groups, admin user, developer permissions
Write-Step "Step 3/4: Creating roles, groups, admin user, and developer permissions..."
Exec-Sql -File (Join-Path $ScriptDir "01-admin/01-create-roles-and-groups.sql") -Desc "Roles and virtual groups" | Out-Null
Exec-Sql -File (Join-Path $ScriptDir "01-admin/01-create-admin-only.sql") -Desc "Admin user" | Out-Null
Exec-Sql -File (Join-Path $ScriptDir "01-admin/02-init-developer-permissions.sql") -Desc "Developer permissions" | Out-Null

# Step 4: Test function unit
Write-Step "Step 4/4: Loading test function unit (Digital Lending V2 EN)..."
$fuScripts = @(
    "08-digital-lending-v2-en/00-create-virtual-groups.sql",
    "08-digital-lending-v2-en/01-create-digital-lending-complete.sql",
    "08-digital-lending-v2-en/02-insert-bpmn-process.sql",
    "08-digital-lending-v2-en/03-bind-actions.sql"
)
foreach ($f in $fuScripts) {
    $path = Join-Path $ScriptDir $f
    if (Test-Path $path) { Exec-Sql -File $path -Desc (Split-Path $f -Leaf) | Out-Null }
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Database Initialization Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Login: admin / password" -ForegroundColor White
Write-Host "  Change password after first login!" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Green
