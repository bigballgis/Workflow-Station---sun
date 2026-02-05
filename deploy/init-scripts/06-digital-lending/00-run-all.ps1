# =============================================================================
# Digital Lending System - Complete Setup Script
# Runs all initialization scripts in sequence
# =============================================================================

param(
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$DbName = "workflow_platform_dev",
    [string]$DbUser = "platform_dev",
    [string]$DbPassword = "dev_password_123"
)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Digital Lending System - Complete Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Set PGPASSWORD environment variable
$env:PGPASSWORD = $DbPassword

# Step 1: Create tables, forms, and actions
Write-Host "Step 1: Creating tables, forms, and actions..." -ForegroundColor Yellow
$sql1 = Join-Path $scriptDir "01-create-digital-lending.sql"
$psqlCommand = "psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f `"$sql1`""
$output1 = Invoke-Expression $psqlCommand 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR in Step 1!" -ForegroundColor Red
    Write-Host $output1
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "Step 1 completed successfully!" -ForegroundColor Green
Write-Host ""

# Step 2: Insert BPMN process (with embedded action bindings)
Write-Host "Step 2: Inserting BPMN process with action bindings..." -ForegroundColor Yellow
$ps2 = Join-Path $scriptDir "insert-bpmn-base64.ps1"
Push-Location $scriptDir
& .\insert-bpmn-base64.ps1

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR in Step 2!" -ForegroundColor Red
    Pop-Location
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    exit 1
}

Pop-Location
Write-Host "Step 2 completed successfully!" -ForegroundColor Green
Write-Host ""

# Step 3: Update form configurations
Write-Host "Step 3: Updating form configurations..." -ForegroundColor Yellow
$sql3 = Join-Path $scriptDir "04-update-form-configs.sql"
$psqlCommand = "psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f `"$sql3`""
$output3 = Invoke-Expression $psqlCommand 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR in Step 3!" -ForegroundColor Red
    Write-Host $output3
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "Step 3 completed successfully!" -ForegroundColor Green
Write-Host ""

# Step 4: Create virtual groups
Write-Host "Step 4: Creating virtual groups..." -ForegroundColor Yellow
$sql4 = Join-Path $scriptDir "05-create-virtual-groups.sql"
$psqlCommand = "psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f `"$sql4`""
$output4 = Invoke-Expression $psqlCommand 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR in Step 4!" -ForegroundColor Red
    Write-Host $output4
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "Step 4 completed successfully!" -ForegroundColor Green
Write-Host ""

# Clean up
Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue

# Success message
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "✓ SETUP COMPLETE!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Digital Lending System has been successfully initialized!" -ForegroundColor Green
Write-Host ""
Write-Host "What was created:" -ForegroundColor Cyan
Write-Host "  ✓ 1 Function Unit: Digital Lending System" -ForegroundColor White
Write-Host "  ✓ 7 Tables: Loan Application, Applicant Info, Financial Info," -ForegroundColor White
Write-Host "              Collateral, Credit Check, Approval History, Documents" -ForegroundColor White
Write-Host "  ✓ 5 Forms: Application, Credit Check (POPUP), Risk Assessment (POPUP)," -ForegroundColor White
Write-Host "             Approval, Disbursement" -ForegroundColor White
Write-Host "  ✓ 1 BPMN Process: 8 tasks, 3 gateways, with embedded action bindings" -ForegroundColor White
Write-Host "  ✓ 12 Actions: Including FORM_POPUP, API_CALL, APPROVE, REJECT" -ForegroundColor White
Write-Host "  ✓ Action Bindings: Embedded directly in BPMN XML" -ForegroundColor White
Write-Host "  ✓ 4 Virtual Groups: Document Verifiers, Credit Officers, Risk Officers, Finance Team" -ForegroundColor White
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "  1. Open Developer Workstation: http://localhost:3002" -ForegroundColor Yellow
Write-Host "  2. Navigate to Function Units" -ForegroundColor Yellow
Write-Host "  3. Find 'Digital Lending System'" -ForegroundColor Yellow
Write-Host "  4. Click 'Deploy' to activate the function unit" -ForegroundColor Yellow
Write-Host "  5. Open User Portal: http://localhost:3001" -ForegroundColor Yellow
Write-Host "  6. Start a new loan application to test the workflow" -ForegroundColor Yellow
Write-Host ""
Write-Host "Key Features to Test:" -ForegroundColor Cyan
Write-Host "  • Form Popup Actions (Credit Check, Risk Assessment)" -ForegroundColor White
Write-Host "  • Multi-stage approval workflow" -ForegroundColor White
Write-Host "  • Document verification" -ForegroundColor White
Write-Host "  • Credit score integration" -ForegroundColor White
Write-Host "  • Risk-based routing" -ForegroundColor White
Write-Host "  • Loan disbursement process" -ForegroundColor White
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
