# =============================================================================
# Insert BPMN Process for Digital Lending System V2 (English Version)
# =============================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Insert BPMN Process for Digital Lending System V2" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Read BPMN file
$bpmnFile = "digital-lending-process-v2-en.bpmn"
if (-not (Test-Path $bpmnFile)) {
    Write-Host "Error: BPMN file not found: $bpmnFile" -ForegroundColor Red
    exit 1
}

Write-Host "Reading BPMN file: $bpmnFile" -ForegroundColor Yellow
$bpmnContent = Get-Content $bpmnFile -Raw -Encoding UTF8

# 2. Query function unit ID and form IDs
Write-Host "Querying function unit and form IDs..." -ForegroundColor Yellow

$queryScript = @"
SELECT 
    fu.id as function_unit_id,
    (SELECT id FROM dw_form_definitions WHERE function_unit_id = fu.id AND form_name = 'Loan Application Form') as application_form_id,
    (SELECT id FROM dw_form_definitions WHERE function_unit_id = fu.id AND form_name = 'Loan Approval Form') as approval_form_id,
    (SELECT id FROM dw_form_definitions WHERE function_unit_id = fu.id AND form_name = 'Loan Disbursement Form') as disbursement_form_id
FROM dw_function_units fu
WHERE fu.code = 'DIGITAL_LENDING_V2_EN';
"@

$result = $queryScript | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -A -F','

if ([string]::IsNullOrWhiteSpace($result)) {
    Write-Host "Error: Function unit DIGITAL_LENDING_V2_EN not found" -ForegroundColor Red
    exit 1
}

$ids = $result.Split(',')
$functionUnitId = $ids[0]
$applicationFormId = $ids[1]
$approvalFormId = $ids[2]
$disbursementFormId = $ids[3]

Write-Host "  Function Unit ID: $functionUnitId" -ForegroundColor Green
Write-Host "  Application Form ID: $applicationFormId" -ForegroundColor Green
Write-Host "  Approval Form ID: $approvalFormId" -ForegroundColor Green
Write-Host "  Disbursement Form ID: $disbursementFormId" -ForegroundColor Green
Write-Host ""

# 3. Query action IDs
Write-Host "Querying action IDs..." -ForegroundColor Yellow

$actionQuery = @"
SELECT action_name, id 
FROM dw_action_definitions 
WHERE function_unit_id = $functionUnitId
ORDER BY id;
"@

$actionResults = $actionQuery | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -A -F'|'

$actionMap = @{}
foreach ($line in $actionResults -split "`n") {
    if (-not [string]::IsNullOrWhiteSpace($line)) {
        $parts = $line.Trim().Split('|')
        if ($parts.Length -eq 2) {
            $actionMap[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
}

Write-Host "  Found $($actionMap.Count) actions" -ForegroundColor Green

# 4. Replace placeholders
Write-Host ""
Write-Host "Replacing placeholders in BPMN..." -ForegroundColor Yellow

$bpmnContent = $bpmnContent -replace '{{APPLICATION_FORM_ID}}', $applicationFormId
$bpmnContent = $bpmnContent -replace '{{APPROVAL_FORM_ID}}', $approvalFormId
$bpmnContent = $bpmnContent -replace '{{DISBURSEMENT_FORM_ID}}', $disbursementFormId

# Replace action IDs
$bpmnContent = $bpmnContent -replace '{{ACTION_SUBMIT}}', $actionMap['Submit Application']
$bpmnContent = $bpmnContent -replace '{{ACTION_WITHDRAW}}', $actionMap['Withdraw Application']
$bpmnContent = $bpmnContent -replace '{{ACTION_VERIFY_DOCS}}', $actionMap['Verify Documents']
$bpmnContent = $bpmnContent -replace '{{ACTION_APPROVE}}', $actionMap['Approve']
$bpmnContent = $bpmnContent -replace '{{ACTION_REJECT}}', $actionMap['Reject']
$bpmnContent = $bpmnContent -replace '{{ACTION_CREDIT_CHECK}}', $actionMap['Perform Credit Check']
$bpmnContent = $bpmnContent -replace '{{ACTION_VIEW_CREDIT}}', $actionMap['View Credit Report']
$bpmnContent = $bpmnContent -replace '{{ACTION_ASSESS_RISK}}', $actionMap['Assess Risk']
$bpmnContent = $bpmnContent -replace '{{ACTION_LOW_RISK}}', $actionMap['Mark as Low Risk']
$bpmnContent = $bpmnContent -replace '{{ACTION_HIGH_RISK}}', $actionMap['Mark as High Risk']
$bpmnContent = $bpmnContent -replace '{{ACTION_REQUEST_INFO}}', $actionMap['Request Additional Info']
$bpmnContent = $bpmnContent -replace '{{ACTION_DISBURSE}}', $actionMap['Process Disbursement']
$bpmnContent = $bpmnContent -replace '{{ACTION_VERIFY_ACCOUNT}}', $actionMap['Verify Account']

Write-Host "  Placeholder replacement completed" -ForegroundColor Green

# 5. Escape single quotes
$bpmnContent = $bpmnContent -replace "'", "''"

# 6. Insert BPMN process into database
Write-Host ""
Write-Host "Inserting BPMN process into database..." -ForegroundColor Yellow

$insertScript = @"
INSERT INTO dw_process_definitions (
    function_unit_id,
    function_unit_version_id,
    bpmn_xml,
    created_at,
    updated_at
) VALUES (
    $functionUnitId,
    $functionUnitId,
    '$bpmnContent',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
"@

$insertScript | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "BPMN Process Inserted Successfully!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Deploy function unit in Developer Workstation" -ForegroundColor White
    Write-Host "  2. Test complete workflow in User Portal" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "Error: BPMN process insertion failed" -ForegroundColor Red
    exit 1
}
