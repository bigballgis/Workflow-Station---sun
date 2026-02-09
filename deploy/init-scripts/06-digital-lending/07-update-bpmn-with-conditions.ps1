# Update BPMN with Condition Expressions
# This script updates the digital lending BPMN process with proper condition expressions

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Update Digital Lending BPMN Process" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Get script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bpmnFile = Join-Path $scriptDir "digital-lending-process.bpmn"

if (-not (Test-Path $bpmnFile)) {
    Write-Host "ERROR: BPMN file not found: $bpmnFile" -ForegroundColor Red
    exit 1
}

# Read BPMN file
$bpmnContent = Get-Content $bpmnFile -Raw -Encoding UTF8
$bytes = [System.Text.Encoding]::UTF8.GetBytes($bpmnContent)
$base64 = [Convert]::ToBase64String($bytes)

Write-Host "BPMN file loaded: $bpmnFile" -ForegroundColor Green
Write-Host "File size: $($bpmnContent.Length) bytes" -ForegroundColor Gray
Write-Host ""

# Database connection parameters
$dbHost = $env:POSTGRES_HOST
if (-not $dbHost) { $dbHost = "localhost" }

$dbPort = $env:POSTGRES_PORT
if (-not $dbPort) { $dbPort = "5432" }

$dbName = $env:POSTGRES_DB
if (-not $dbName) { $dbName = "workflow_platform" }

$dbUser = $env:POSTGRES_USER
if (-not $dbUser) { $dbUser = "platform" }

$dbPassword = $env:POSTGRES_PASSWORD
if (-not $dbPassword) { $dbPassword = "platform123" }

Write-Host "Database connection:" -ForegroundColor Yellow
Write-Host "  Host: $dbHost" -ForegroundColor Gray
Write-Host "  Port: $dbPort" -ForegroundColor Gray
Write-Host "  Database: $dbName" -ForegroundColor Gray
Write-Host "  User: $dbUser" -ForegroundColor Gray
Write-Host ""

# Create SQL update statement
$sqlFile = Join-Path $scriptDir "07-update-bpmn-with-conditions.sql"

$sqlContent = @"
-- Update Digital Lending BPMN Process with Condition Expressions
-- This fixes the issue where approval_result variable cannot be resolved

DO `$`$
DECLARE
    v_process_id BIGINT;
    v_bpmn_data TEXT;
BEGIN
    -- Get the process ID for Digital Lending
    SELECT id INTO v_process_id
    FROM public.dw_process_definitions
    WHERE process_key = 'DigitalLendingProcess'
    AND is_deleted = FALSE
    ORDER BY version DESC
    LIMIT 1;

    IF v_process_id IS NULL THEN
        RAISE EXCEPTION 'Digital Lending process not found';
    END IF;

    RAISE NOTICE 'Updating process ID: %', v_process_id;

    -- Update the BPMN data
    v_bpmn_data := '$base64';

    UPDATE public.dw_process_definitions
    SET 
        data = v_bpmn_data,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_process_id;

    RAISE NOTICE 'BPMN process updated successfully';
    RAISE NOTICE 'Please redeploy the Digital Lending function unit for changes to take effect';

END `$`$;
"@

# Write SQL file
$sqlContent | Out-File -FilePath $sqlFile -Encoding UTF8 -NoNewline

Write-Host "SQL file created: $sqlFile" -ForegroundColor Green
Write-Host ""

# Execute SQL
Write-Host "Executing SQL update..." -ForegroundColor Yellow

$env:PGPASSWORD = $dbPassword
$psqlCommand = "psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -f `"$sqlFile`""

try {
    Invoke-Expression $psqlCommand
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "BPMN Update Completed Successfully!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "IMPORTANT: You must redeploy the Digital Lending function unit" -ForegroundColor Yellow
    Write-Host "for the changes to take effect in the workflow engine." -ForegroundColor Yellow
    Write-Host ""
} catch {
    Write-Host ""
    Write-Host "ERROR: Failed to execute SQL" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
