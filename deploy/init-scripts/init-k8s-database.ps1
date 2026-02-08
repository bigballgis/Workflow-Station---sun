# =====================================================
# K8S Database Initialization Script
# =====================================================
# Initializes database for K8S deployment:
#   1. All schemas (base + migrations)
#   2. System roles and virtual groups
#   3. Admin user ONLY (other users created manually)
#   4. Optional: test function unit data
#
# Usage:
#   .\init-k8s-database.ps1 -DbHost <host> -DbPort 5432 -DbName workflow_platform -DbUser postgres -DbPassword <pwd>
#   .\init-k8s-database.ps1 -DbHost <host> -DbPassword <pwd> -IncludeTestFunctionUnit
# =====================================================

param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "workflow_platform",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "",
    [switch]$IncludeTestFunctionUnit = $false
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step { param([string]$Msg) Write-Host "`n[$((Get-Date).ToString('HH:mm:ss'))] $Msg" -ForegroundColor Cyan }
function Write-Ok { param([string]$Msg) Write-Host "  OK: $Msg" -ForegroundColor Green }
function Write-Fail { param([string]$Msg) Write-Host "  FAIL: $Msg" -ForegroundColor Red }

function Exec-Sql {
    param([string]$File, [string]$Desc)
    
    if (-not (Test-Path $File)) {
        Write-Fail "$Desc - file not found: $File"
        return $false
    }
    
    $env:PGPASSWORD = $DbPassword
    try {
        $output = & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $File 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Ok $Desc
            return $true
        } else {
            Write-Fail "$Desc"
            $output | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
            return $false
        }
    } finally {
        $env:PGPASSWORD = ""
    }
}

# =====================================================
# Main
# =====================================================
Write-Host ""
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  K8S Database Initialization" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Host: ${DbHost}:${DbPort}"
Write-Host "  Database: $DbName"
Write-Host "  User: $DbUser"
Write-Host "  Test FU: $IncludeTestFunctionUnit"
Write-Host "=========================================" -ForegroundColor Yellow

# Check psql
try { $null = Get-Command psql -ErrorAction Stop }
catch { Write-Fail "psql not found. Install PostgreSQL client tools."; exit 1 }

# Step 1: Base schemas
Write-Step "Step 1/4: Creating base schemas..."
$schemas = @(
    @{ File = "00-schema/01-platform-security-schema.sql"; Desc = "Platform Security (sys_*)" },
    @{ File = "00-schema/02-workflow-engine-schema.sql"; Desc = "Workflow Engine (wf_*)" },
    @{ File = "00-schema/03-user-portal-schema.sql"; Desc = "User Portal (up_*)" },
    @{ File = "00-schema/04-developer-workstation-schema.sql"; Desc = "Developer Workstation (dw_*)" },
    @{ File = "00-schema/05-admin-center-schema.sql"; Desc = "Admin Center (admin_*)" }
)
foreach ($s in $schemas) {
    $path = Join-Path $ScriptDir $s.File
    if (-not (Exec-Sql -File $path -Desc $s.Desc)) { exit 1 }
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
    if (Test-Path $path) {
        Exec-Sql -File $path -Desc (Split-Path $m -Leaf) | Out-Null
    }
}

# Step 3: Roles, groups, admin user
Write-Step "Step 3/4: Creating roles, groups, and admin user..."
$adminScripts = @(
    @{ File = "01-admin/01-create-roles-and-groups.sql"; Desc = "System roles and virtual groups" },
    @{ File = "01-admin/04-restructure-developer-roles.sql"; Desc = "Developer workstation roles" },
    @{ File = "01-admin/01-create-admin-only.sql"; Desc = "Admin user (admin/password)" }
)
foreach ($a in $adminScripts) {
    $path = Join-Path $ScriptDir $a.File
    if (-not (Exec-Sql -File $path -Desc $a.Desc)) { exit 1 }
}

# Step 4: Optional test function unit
if ($IncludeTestFunctionUnit) {
    Write-Step "Step 4/4: Loading test function unit (Digital Lending EN)..."
    $fuScripts = @(
        "08-digital-lending-v2-en/00-create-virtual-groups.sql",
        "08-digital-lending-v2-en/01-create-digital-lending-complete.sql",
        "08-digital-lending-v2-en/03-bind-actions.sql"
    )
    foreach ($f in $fuScripts) {
        $path = Join-Path $ScriptDir $f
        if (Test-Path $path) {
            Exec-Sql -File $path -Desc (Split-Path $f -Leaf) | Out-Null
        } else {
            Write-Host "  SKIP: $f (not found)" -ForegroundColor DarkYellow
        }
    }
} else {
    Write-Step "Step 4/4: Skipping test function unit (use -IncludeTestFunctionUnit to include)"
}

# Done
Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Database Initialization Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Login: admin / password" -ForegroundColor White
Write-Host "  Change password after first login!" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Green
