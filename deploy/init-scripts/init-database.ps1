# =====================================================
# Database Initialization Script (standalone psql, no Docker)
# =====================================================
# Runs the SQL of one of the two supported initializations, in their order, against a NEW, empty database:
#
#   default        the Docker init. deploy/init-scripts/00-init-all.sh is read at run time, so this script runs
#                  exactly what it runs: the Flowable tables (create + the upgrade steps it lists), then every
#                  script it names, in the same order (schema, roles and users, demo function units, 90-post-seed
#                  with the access model switch 04 / 05). Initial System Administrator: developer.
#   -SkipDemoData  the k8s init (deploy/k8s/init-data/README.md): Flowable create, the k8s schema bundle, the
#                  k8s seed bundle (no demo units, no developer account, initial System Administrator: admin),
#                  then the Flowable 7.0.0 -> 7.2.0 migration. deploy/k8s/ps1/apply-workflow-station-istio-generated.ps1
#                  -InitializeDatabase uses this mode unless it is given -IncludeDemoData.
#   -DbSchema      target schema (search_path) for every script; it must exist. A few checks inside the scripts
#                  look at schema public only (Flowable widened-column check, id sequence alignment).
#   -ListOnly      print the scripts in run order and run nothing.
#
# 00-init-all.sh is read with the rules deploy/k8s/init-data/build-all-in-one.mjs uses: comment and echo lines are
# ignored; every /docker-entrypoint-initdb.d/<path> names a script, in order of appearance; a * in the file name is
# a glob over that directory, sorted by byte order (as bash in the postgres alpine image sorts it); a script named
# twice (the `if [ -f X ]` test, then `$PSQL -f X`) runs once.
#
# Every script runs with ON_ERROR_STOP and the first failure stops the run (the Docker init runs with set -e).
# Not for a database that already has the platform tables: Flowable create and the seeds are not meant to run twice.
# -DbUser is the platform's own database account (the services' user): 00-schema/101 sets the time zone default of
# the account that runs it, in this database (no database ownership needed), and the tables belong to that account.
#
# Usage:
#   .\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform_dev -DbUser platform_dev -DbPassword dev_password_123
#   .\init-database.ps1 -DbHost <host> -DbName <db> -DbUser <user> -DbPassword <password> -SkipDemoData
#   .\init-database.ps1 -ListOnly [-SkipDemoData]
# =====================================================

[CmdletBinding()]
param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "workflow_platform_dev",
    [string]$DbUser = "platform_dev",
    [string]$DbPassword = "",
    [string]$DbSchema = "",
    [switch]$SkipDemoData,
    [switch]$ListOnly
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$InitDataDir = Join-Path $ScriptDir "..\k8s\init-data"
$FlowableDir = Join-Path $InitDataDir "init-flowable"

function Write-Step { param([string]$Msg) Write-Host "`n[$((Get-Date).ToString('HH:mm:ss'))] $Msg" -ForegroundColor Cyan }

# The scripts 00-init-all.sh runs, in order, as paths relative to deploy/init-scripts.
function Get-DockerInitOrder {
    param([string[]]$ShLines)
    $order = [System.Collections.Generic.List[string]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $ShLines) {
        if ($line -match '^\s*#' -or $line -match '^\s*echo\b') { continue }
        foreach ($m in [regex]::Matches($line, '/docker-entrypoint-initdb\.d/([A-Za-z0-9._/*-]+)')) {
            foreach ($file in (Expand-InitPath $m.Groups[1].Value)) {
                if ($seen.Add($file)) { $order.Add($file) }
            }
        }
    }
    return $order
}

function Expand-InitPath {
    param([string]$Path)
    if ($Path -notmatch '\*') {
        if (-not (Test-Path -LiteralPath (Join-Path $ScriptDir $Path))) {
            throw "00-init-all.sh runs $Path, which does not exist"
        }
        return @($Path)
    }
    $dir = $Path.Substring(0, $Path.LastIndexOf('/'))
    $leaf = $Path.Substring($dir.Length + 1)
    $pattern = '^' + ((($leaf -split '\*') | ForEach-Object { [regex]::Escape($_) }) -join '[^/]*') + '$'
    $names = [string[]]@(Get-ChildItem -LiteralPath (Join-Path $ScriptDir $dir) -File |
        Where-Object { $_.Name -cmatch $pattern } | ForEach-Object { $_.Name })
    [Array]::Sort($names, [StringComparer]::Ordinal)
    return @($names | ForEach-Object { "$dir/$_" })
}

# Flowable vendor SQL the Docker init runs first (Step 0): create, then each step of FLOWABLE_UPGRADE_STEPS.
function Get-FlowableSteps {
    param([string[]]$ShLines)
    $steps = @()
    $inList = $false
    foreach ($line in $ShLines) {
        if (-not $inList) {
            if ($line -match '^FLOWABLE_UPGRADE_STEPS="') { $inList = $true }
            continue
        }
        if ($line -match '^"') { break }
        if ($line.Trim()) { $steps += $line.Trim() }
    }
    if ($steps.Count -eq 0) { throw "00-init-all.sh: FLOWABLE_UPGRADE_STEPS not found" }
    return $steps
}

function New-RunItem {
    param([string]$Label, [string]$File)
    if (-not (Test-Path -LiteralPath $File)) { throw "Missing: $File" }
    return [pscustomobject]@{ Label = $Label; File = (Resolve-Path -LiteralPath $File).Path }
}

function Get-RunPlan {
    $createSql = Join-Path $FlowableDir "create\flowable.postgres.all.create.sql"
    if ($SkipDemoData) {
        return @(
            (New-RunItem "init-flowable/create/flowable.postgres.all.create.sql" $createSql),
            (New-RunItem "init-platform-schema/all-in-one-for-gui.sql" (Join-Path $InitDataDir "init-platform-schema\all-in-one-for-gui.sql")),
            (New-RunItem "init-platform-seed/all-in-one-for-gui.sql" (Join-Path $InitDataDir "init-platform-seed\all-in-one-for-gui.sql")),
            (New-RunItem "init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql" (Join-Path $FlowableDir "upgrade\MIGRATE-7.0.0-to-7.2.0-for-gui.sql"))
        )
    }
    $sh = Get-Content -LiteralPath (Join-Path $ScriptDir "00-init-all.sh") -Encoding UTF8
    $plan = @(New-RunItem "init-flowable/create/flowable.postgres.all.create.sql" $createSql)
    foreach ($step in (Get-FlowableSteps $sh)) {
        $name = "flowable.postgres.upgradestep.$step.all.sql"
        $plan += New-RunItem "init-flowable/upgrade/$name" (Join-Path $FlowableDir "upgrade\$name")
    }
    foreach ($file in (Get-DockerInitOrder $sh)) {
        $plan += New-RunItem $file (Join-Path $ScriptDir ($file -replace '/', '\'))
    }
    return $plan
}

function Invoke-Psql {
    param([string[]]$PsqlArgs)
    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -v ON_ERROR_STOP=1 @PsqlArgs
    if ($LASTEXITCODE -ne 0) { throw "psql exited with $LASTEXITCODE" }
}

# =====================================================
$mode = if ($SkipDemoData) { "k8s init (-SkipDemoData)" } else { "Docker init (00-init-all.sh)" }
Write-Host ""
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Database Initialization: $mode" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "  Host: ${DbHost}:${DbPort}"
Write-Host "  Database: $DbName"
Write-Host "  User: $DbUser"
Write-Host "  Schema: $(if ($DbSchema) { $DbSchema } else { '(default search_path)' })"
Write-Host "=========================================" -ForegroundColor Yellow

try {
    $plan = Get-RunPlan
    if ($ListOnly) {
        $i = 0
        foreach ($item in $plan) { $i++; Write-Host ("{0,4}  {1}" -f $i, $item.Label) }
        exit 0
    }

    $null = Get-Command psql -ErrorAction Stop
    if ([string]::IsNullOrEmpty($DbPassword)) {
        Write-Host "  WARNING: No password provided. psql may prompt for password or use .pgpass file." -ForegroundColor Yellow
    }

    $previousPassword = $env:PGPASSWORD
    $previousOptions = $env:PGOPTIONS
    $env:PGPASSWORD = $DbPassword
    try {
        if ($DbSchema) {
            $exists = Invoke-Psql @('-t', '-A', '-c', "SELECT count(*) FROM pg_namespace WHERE nspname = '$($DbSchema -replace "'", "''")'")
            if (($exists | Out-String).Trim() -ne '1') { throw "Schema '$DbSchema' does not exist in $DbName; create it first" }
            $env:PGOPTIONS = "-c search_path=$DbSchema,public"
        }
        Write-Step "Running $($plan.Count) script(s)..."
        foreach ($item in $plan) {
            Write-Host "  Running $($item.Label)..."
            Invoke-Psql @('-q', '-f', $item.File)
        }
        $version = (Invoke-Psql @('-t', '-A', '-c', "SELECT value_ FROM act_ge_property WHERE name_ = 'schema.version'") | Out-String).Trim()
        Write-Host "  Flowable schema at: $version"
    } finally {
        $env:PGPASSWORD = $previousPassword
        $env:PGOPTIONS = $previousOptions
    }
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Database Initialization Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
if ($SkipDemoData) {
    Write-Host "  System Administrator: admin (90-post-seed/04, k8s variant); no demo function units, no developer account" -ForegroundColor White
} else {
    Write-Host "  System Administrator: developer / password (90-post-seed/04; admin and 44027893 hold no Admin role)" -ForegroundColor White
    Write-Host "  Demo function units and E2E users: see the summary at the end of 00-init-all.sh" -ForegroundColor White
}
Write-Host "  Change password after first login!" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Green
exit 0
