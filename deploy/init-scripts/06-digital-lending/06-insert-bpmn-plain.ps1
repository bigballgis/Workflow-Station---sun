# =============================================================================
# Digital Lending System - Insert BPMN as Plain XML
# Inserts the BPMN process definition as plain XML (not base64 encoded)
# =============================================================================

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bpmnFile = Join-Path $scriptDir "digital-lending-process.bpmn"

# Read BPMN file
$bpmnXml = Get-Content $bpmnFile -Raw

# Escape single quotes for SQL
$bpmnXml = $bpmnXml.Replace("'", "''")

# Get function unit ID
$getFunctionUnitId = @"
SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING';
"@

$functionUnitId = $getFunctionUnitId | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t | ForEach-Object { $_.Trim() }

Write-Host "Function Unit ID: $functionUnitId"

# Delete existing BPMN
$deleteOld = @"
DELETE FROM dw_process_definitions 
WHERE function_unit_id = $functionUnitId;
"@

Write-Host "Deleting old BPMN definitions..."
$deleteOld | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# Insert new BPMN as plain XML
$insertSql = @"
INSERT INTO dw_process_definitions (function_unit_id, bpmn_xml, created_at, updated_at)
VALUES ($functionUnitId, '$bpmnXml', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
"@

Write-Host "Inserting BPMN as plain XML..."
Write-Host "BPMN file size: $($bpmnXml.Length) characters"

$insertSql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "SUCCESS: BPMN process inserted as plain XML" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "ERROR: Failed to insert BPMN" -ForegroundColor Red
    Write-Host ""
    exit 1
}
