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
Write-Step "Step 1/8: Creating base schemas..."
$schemas = @(
    "00-schema/01-platform-security-schema.sql",
    "00-schema/02-workflow-engine-schema.sql",
    "00-schema/03-user-portal-schema.sql",
    "00-schema/04-developer-workstation-schema.sql",
    "00-schema/05-admin-center-schema.sql"
)
foreach ($s in $schemas) {
    if (-not (Exec-Sql -File (Join-Path $ScriptDir $s) -Desc (Split-Path $s -Leaf))) { exit 1 }
}

# Step 2: Incremental migrations
Write-Step "Step 2/8: Applying incremental migrations..."
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

# Step 3: Roles, groups, admin user
Write-Step "Step 3/8: Creating roles, groups, and admin user..."
Exec-Sql -File (Join-Path $ScriptDir "01-admin/01-create-roles-and-groups.sql") -Desc "Roles and virtual groups" | Out-Null
Exec-Sql -File (Join-Path $ScriptDir "01-admin/01-create-admin-only.sql") -Desc "Admin user" | Out-Null

# Step 4: Test function unit
Write-Step "Step 4/8: Loading test function unit (Digital Lending V2 EN)..."
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

# Step 5: Simple Approval Workflow
Write-Step "Step 5/8: Loading Simple Approval Workflow..."
$saScripts = @(
    "10-simple-approval/00-create-simple-approval.sql",
    "10-simple-approval/01-create-tables.sql",
    "10-simple-approval/02-create-bpmn-process.sql",
    "10-simple-approval/03-form-table-bindings.sql"
)
foreach ($f in $saScripts) {
    $path = Join-Path $ScriptDir $f
    if (Test-Path $path) { Exec-Sql -File $path -Desc (Split-Path $f -Leaf) | Out-Null }
}

# Step 6: Procurement Workflow
Write-Step "Step 6/8: Loading Procurement Workflow..."
$pwScripts = @(
    "13-procurement-workflow/00-create-function-unit.sql",
    "13-procurement-workflow/01-create-tables.sql",
    "13-procurement-workflow/02-create-bpmn-process.sql",
    "13-procurement-workflow/03-form-table-bindings.sql"
)
foreach ($f in $pwScripts) {
    $path = Join-Path $ScriptDir $f
    if (Test-Path $path) { Exec-Sql -File $path -Desc (Split-Path $f -Leaf) | Out-Null }
}

# Step 7: Travel Expense Reimbursement
Write-Step "Step 7/8: Loading Travel Expense Reimbursement..."
$teScripts = @(
    "14-travel-expense-reimbursement/00-create-function-unit.sql",
    "14-travel-expense-reimbursement/01-create-tables.sql",
    "14-travel-expense-reimbursement/02-create-bpmn-process.sql",
    "14-travel-expense-reimbursement/03-form-table-bindings.sql",
    "14-travel-expense-reimbursement/04-update-n8n-action-config.sql"
)
foreach ($f in $teScripts) {
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
