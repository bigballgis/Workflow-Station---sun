# Update BPMN XML with String action IDs for Submit Application task
# Date: 2026-02-06

$bpmnPath = "deploy/init-scripts/06-digital-lending/digital-lending-process.bpmn"

Write-Host "Reading BPMN file..." -ForegroundColor Cyan
# Read BPMN file
$bpmnContent = Get-Content -Path $bpmnPath -Raw -Encoding UTF8

# Escape single quotes for SQL
$bpmnEscaped = $bpmnContent -replace "'", "''"

# Create SQL to update dw_process_definitions
$sql = @"
UPDATE dw_process_definitions
SET 
    bpmn_xml = '$bpmnEscaped',
    updated_at = CURRENT_TIMESTAMP
WHERE 
    function_unit_id = 4;

-- Verify the update
SELECT 
    id,
    function_unit_id,
    LENGTH(bpmn_xml) as xml_length,
    updated_at
FROM dw_process_definitions
WHERE function_unit_id = 4;
"@

# Execute SQL
Write-Host "Updating BPMN XML in database..." -ForegroundColor Cyan
$sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

Write-Host "`nBPMN XML updated successfully!" -ForegroundColor Green
Write-Host "Please refresh the Developer Workstation to see the changes." -ForegroundColor Yellow
