# =============================================================================
# Insert BPMN Process for Digital Lending System
# This script reads the BPMN file and inserts it into the database
# =============================================================================

param(
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$DbName = "workflow_platform_dev",
    [string]$DbUser = "platform_dev",
    [string]$DbPassword = "dev_password_123"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Digital Lending - Insert BPMN Process" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Get script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Read BPMN file
$bpmnFile = Join-Path $scriptDir "digital-lending-process.bpmn"
if (-not (Test-Path $bpmnFile)) {
    Write-Host "ERROR: BPMN file not found: $bpmnFile" -ForegroundColor Red
    exit 1
}

Write-Host "Reading BPMN file: $bpmnFile" -ForegroundColor Yellow
$bpmnContent = Get-Content $bpmnFile -Raw -Encoding UTF8

# Escape single quotes for SQL
$bpmnContentEscaped = $bpmnContent.Replace("'", "''")

Write-Host "BPMN file size: $($bpmnContent.Length) characters" -ForegroundColor Green
Write-Host ""

# Create SQL script
$sqlScript = @"
DO `$`$
DECLARE
    v_function_unit_id BIGINT;
    v_process_id BIGINT;
BEGIN
    -- Get Function Unit ID
    SELECT id INTO v_function_unit_id 
    FROM public.dw_function_units 
    WHERE code = 'DIGITAL_LENDING';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit DIGITAL_LENDING not found. Please run 01-create-digital-lending.sql first.';
    END IF;

    RAISE NOTICE 'Inserting BPMN process for Function Unit ID: %', v_function_unit_id;

    -- Insert Process Definition
    INSERT INTO public.dw_process_definitions (
        function_unit_id,
        bpmn_xml
    ) VALUES (
        v_function_unit_id,
        '$bpmnContentEscaped'
    ) RETURNING id INTO v_process_id;

    RAISE NOTICE 'Process inserted with ID: %', v_process_id;
    RAISE NOTICE 'BPMN XML length: % characters', LENGTH('$bpmnContentEscaped');
    
    RAISE NOTICE '========================================';
    RAISE NOTICE 'BPMN Process Inserted Successfully!';
    RAISE NOTICE '========================================';
END `$`$;
"@

# Save SQL to temp file
$tempSqlFile = Join-Path $env:TEMP "insert-bpmn-process.sql"
$sqlScript | Out-File -FilePath $tempSqlFile -Encoding UTF8

Write-Host "Executing SQL script..." -ForegroundColor Yellow

# Set PGPASSWORD environment variable
$env:PGPASSWORD = $DbPassword

# Execute SQL using psql
$psqlCommand = "psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f `"$tempSqlFile`""

try {
    $output = Invoke-Expression $psqlCommand 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "SUCCESS!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host $output
        Write-Host ""
        Write-Host "Next step: Run 03-bind-actions.sql" -ForegroundColor Cyan
    } else {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Red
        Write-Host "ERROR!" -ForegroundColor Red
        Write-Host "========================================" -ForegroundColor Red
        Write-Host $output
        exit 1
    }
} catch {
    Write-Host ""
    Write-Host "ERROR: Failed to execute SQL script" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    # Clean up
    Remove-Item $tempSqlFile -ErrorAction SilentlyContinue
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
