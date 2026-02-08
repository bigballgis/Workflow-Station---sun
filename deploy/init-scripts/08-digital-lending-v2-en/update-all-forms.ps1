# 批量更新所有表单为 form-create 格式

Write-Host "开始更新表单格式..." -ForegroundColor Cyan

# Form 22: Credit Check Form
Write-Host "`n更新 Credit Check Form (ID: 22)..." -ForegroundColor Green
$sql22 = @"
UPDATE dw_form_definitions
SET config_json = '{"rule":[{"type":"inputNumber","field":"credit_score","title":"Credit Score","validate":[{"required":true,"message":"Please enter credit score"}],"props":{"min":300,"max":850}},{"type":"inputNumber","field":"credit_history_years","title":"Credit History (Years)","validate":[{"required":true,"message":"Please enter credit history"}],"props":{"min":0}},{"type":"select","field":"payment_history","title":"Payment History","validate":[{"required":true,"message":"Please select payment history"}],"options":[{"label":"Excellent","value":"Excellent"},{"label":"Good","value":"Good"},{"label":"Fair","value":"Fair"},{"label":"Poor","value":"Poor"}]},{"type":"input","field":"remarks","title":"Remarks","props":{"type":"textarea","rows":3}}],"options":{"form":{"labelPosition":"top","size":"default","labelWidth":"150px"}}}'::jsonb
WHERE id = 22;
"@
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $sql22

# Form 23: Risk Assessment Form
Write-Host "`n更新 Risk Assessment Form (ID: 23)..." -ForegroundColor Green
$sql23 = @"
UPDATE dw_form_definitions
SET config_json = '{"rule":[{"type":"select","field":"risk_rating","title":"Risk Rating","validate":[{"required":true,"message":"Please select risk rating"}],"options":[{"label":"Low","value":"Low"},{"label":"Medium","value":"Medium"},{"label":"High","value":"High"}]},{"type":"input","field":"risk_factors","title":"Risk Factors","validate":[{"required":true,"message":"Please enter risk factors"}],"props":{"type":"textarea","rows":3}},{"type":"input","field":"mitigation_measures","title":"Mitigation Measures","props":{"type":"textarea","rows":3}}],"options":{"form":{"labelPosition":"top","size":"default","labelWidth":"150px"}}}'::jsonb
WHERE id = 23;
"@
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $sql23

# Form 24: Loan Approval Form
Write-Host "`n更新 Loan Approval Form (ID: 24)..." -ForegroundColor Green
$sql24 = @"
UPDATE dw_form_definitions
SET config_json = '{"rule":[{"type":"inputNumber","field":"approved_amount","title":"Approved Amount","validate":[{"required":true,"message":"Please enter approved amount"}],"props":{"min":0}},{"type":"inputNumber","field":"interest_rate","title":"Interest Rate (%)","validate":[{"required":true,"message":"Please enter interest rate"}],"props":{"min":0,"max":100,"precision":2}},{"type":"input","field":"approval_comments","title":"Comments","props":{"type":"textarea","rows":3}}],"options":{"form":{"labelPosition":"top","size":"default","labelWidth":"150px"}}}'::jsonb
WHERE id = 24;
"@
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $sql24

# Form 25: Loan Disbursement Form
Write-Host "`n更新 Loan Disbursement Form (ID: 25)..." -ForegroundColor Green
$sql25 = @"
UPDATE dw_form_definitions
SET config_json = '{"rule":[{"type":"inputNumber","field":"disbursement_amount","title":"Disbursement Amount","validate":[{"required":true,"message":"Please enter disbursement amount"}],"props":{"min":0}},{"type":"select","field":"disbursement_method","title":"Method","validate":[{"required":true,"message":"Please select disbursement method"}],"options":[{"label":"Bank Transfer","value":"Bank Transfer"},{"label":"Check","value":"Check"},{"label":"Cash","value":"Cash"}]},{"type":"input","field":"account_number","title":"Account Number","validate":[{"required":true,"message":"Please enter account number"}]}],"options":{"form":{"labelPosition":"top","size":"default","labelWidth":"150px"}}}'::jsonb
WHERE id = 25;
"@
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $sql25

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "所有表单格式更新完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

# 验证
Write-Host "`n验证表单格式..." -ForegroundColor Cyan
$verifyQuery = @"
SELECT id, form_name, 
       config_json->'rule' IS NOT NULL as has_rule,
       jsonb_array_length(config_json->'rule') as field_count
FROM dw_form_definitions 
WHERE function_unit_id = 10
ORDER BY id;
"@
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $verifyQuery
