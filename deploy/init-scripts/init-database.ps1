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

if ([string]::IsNullOrEmpty($DbPassword)) {
    Write-Host "  WARNING: No password provided. psql may prompt for password or use .pgpass file." -ForegroundColor Yellow
}

# Step 1: Base schemas
Write-Step "Step 1/6: Creating base schemas..."
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
Write-Step "Step 2/6: Applying incremental migrations..."
$migrations = @(
    "00-schema/06-add-deployment-rollback-columns.sql",
    "00-schema/07-add-action-definitions-table.sql",
    "00-schema/08-add-function-unit-versioning.sql",
    "00-schema/10-add-approval-order-column.sql",
    "00-schema/11-add-unique-enabled-constraint.sql",
    "00-schema/12-add-enabled-field-to-dw-function-units.sql",
    "00-schema/13-add-notification-table.sql",
    "00-schema/15-bi-management-schema.sql",
    "00-schema/16-add-decision-and-relations-tables.sql",
    "00-schema/17-add-lock-version-to-user-portal-tables.sql",
    "00-schema/18-add-lock-version-to-form-definitions.sql",
    "00-schema/19-add-up-change-history.sql",
    "00-schema/20-add-members-table.sql",
    "00-schema/21-add-rt-relation-tables.sql",
    "00-schema/22-add-lock-version-to-sys-roles.sql",
    "00-schema/23-widen-up-process-instance-business-key.sql"
)
foreach ($m in $migrations) {
    $path = Join-Path $ScriptDir $m
    if (Test-Path $path) { Exec-Sql -File $path -Desc (Split-Path $m -Leaf) | Out-Null }
}

# Step 3: Roles, groups, admin user
Write-Step "Step 3/6: Creating roles, groups, and admin user..."
Exec-Sql -File (Join-Path $ScriptDir "01-admin/01-create-roles-and-groups.sql") -Desc "Roles and virtual groups" | Out-Null
Exec-Sql -File (Join-Path $ScriptDir "01-admin/01-create-admin-only.sql") -Desc "Admin user" | Out-Null
Exec-Sql -File (Join-Path $ScriptDir "01-admin/02-init-developer-permissions.sql") -Desc "Developer permissions" | Out-Null
Exec-Sql -File (Join-Path $ScriptDir "01-admin/03-sync-role-tables.sql") -Desc "Sync role tables" | Out-Null
Exec-Sql -File (Join-Path $ScriptDir "01-admin/04-admin-permissions.sql") -Desc "Admin permissions" | Out-Null

# Step 4: Wipe all function units (matches Docker init path)
Write-Step "Step 4/6: Wiping all function units (developer + deployed catalog)..."
$wipePath = Join-Path $ScriptDir "99-maintenance/00-wipe-all-function-units.sql"
if (Test-Path $wipePath) {
    if (-not (Exec-Sql -File $wipePath -Desc "Wipe function units")) { exit 1 }
} else {
    Write-Host "  SKIP: wipe script not found at $wipePath" -ForegroundColor Yellow
}

# Step 5: Digital Lending V2 EN only
Write-Step "Step 5/6: Loading Digital Lending V2 EN..."
$fuScripts = @(
    "08-digital-lending-v2-en/00-create-virtual-groups.sql",
    "08-digital-lending-v2-en/01-create-digital-lending-complete.sql",
    "08-digital-lending-v2-en/02-insert-bpmn-process.sql",
    "08-digital-lending-v2-en/03-bind-actions.sql",
    "08-digital-lending-v2-en/04-merge-loan-application-subforms.sql"
)
foreach ($f in $fuScripts) {
    $path = Join-Path $ScriptDir $f
    if (Test-Path $path) {
        if (-not (Exec-Sql -File $path -Desc (Split-Path $f -Leaf))) { exit 1 }
    }
}

Write-Step "Step 6/6: Finished."

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Database Initialization Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Login: admin / password" -ForegroundColor White
Write-Host "  Change password after first login!" -ForegroundColor Yellow
Write-Host "  Demo function unit: Digital Lending V2 (EN) only." -ForegroundColor White
Write-Host "=========================================" -ForegroundColor Green
