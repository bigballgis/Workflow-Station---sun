# =============================================================================
# 插入数字贷款系统 V2 的 BPMN 流程
# =============================================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "插入 BPMN 流程到数字贷款系统 V2" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 读取 BPMN 文件
$bpmnFile = "digital-lending-process-v2.bpmn"
if (-not (Test-Path $bpmnFile)) {
    Write-Host "错误：找不到 BPMN 文件: $bpmnFile" -ForegroundColor Red
    exit 1
}

Write-Host "读取 BPMN 文件: $bpmnFile" -ForegroundColor Yellow
$bpmnContent = Get-Content $bpmnFile -Raw -Encoding UTF8

# 2. 查询功能单元 ID 和表单 ID
Write-Host "查询功能单元和表单 ID..." -ForegroundColor Yellow

$queryScript = @"
SELECT 
    fu.id as function_unit_id,
    (SELECT id FROM dw_form_definitions WHERE function_unit_id = fu.id AND form_name = 'Loan Application Form') as application_form_id,
    (SELECT id FROM dw_form_definitions WHERE function_unit_id = fu.id AND form_name = 'Loan Approval Form') as approval_form_id,
    (SELECT id FROM dw_form_definitions WHERE function_unit_id = fu.id AND form_name = 'Loan Disbursement Form') as disbursement_form_id
FROM dw_function_units fu
WHERE fu.code = 'DIGITAL_LENDING_V2';
"@

$result = $queryScript | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -A -F','

if ([string]::IsNullOrWhiteSpace($result)) {
    Write-Host "错误：找不到功能单元 DIGITAL_LENDING_V2" -ForegroundColor Red
    exit 1
}

$ids = $result.Split(',')
$functionUnitId = $ids[0]
$applicationFormId = $ids[1]
$approvalFormId = $ids[2]
$disbursementFormId = $ids[3]

Write-Host "  功能单元 ID: $functionUnitId" -ForegroundColor Green
Write-Host "  申请表单 ID: $applicationFormId" -ForegroundColor Green
Write-Host "  审批表单 ID: $approvalFormId" -ForegroundColor Green
Write-Host "  放款表单 ID: $disbursementFormId" -ForegroundColor Green
Write-Host ""

# 3. 查询动作 ID
Write-Host "查询动作 ID..." -ForegroundColor Yellow

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

Write-Host "  找到 $($actionMap.Count) 个动作" -ForegroundColor Green

# 4. 替换占位符
Write-Host ""
Write-Host "替换 BPMN 中的占位符..." -ForegroundColor Yellow

$bpmnContent = $bpmnContent -replace '{{APPLICATION_FORM_ID}}', $applicationFormId
$bpmnContent = $bpmnContent -replace '{{APPROVAL_FORM_ID}}', $approvalFormId
$bpmnContent = $bpmnContent -replace '{{DISBURSEMENT_FORM_ID}}', $disbursementFormId

# 替换动作 ID
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

Write-Host "  占位符替换完成" -ForegroundColor Green

# 5. 转义单引号
$bpmnContent = $bpmnContent -replace "'", "''"

# 6. 插入BPMN流程到数据库
Write-Host ""
Write-Host "插入 BPMN 流程到数据库..." -ForegroundColor Yellow

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
    Write-Host "BPMN 流程插入成功！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "下一步：" -ForegroundColor Cyan
    Write-Host "  1. 在开发者工作台部署功能单元" -ForegroundColor White
    Write-Host "  2. 在用户门户测试完整流程" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "错误：BPMN 流程插入失败" -ForegroundColor Red
    exit 1
}
