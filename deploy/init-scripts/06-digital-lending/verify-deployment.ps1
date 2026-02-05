# =============================================================================
# Digital Lending System - Deployment Verification Script
# Verifies that all components are correctly installed
# =============================================================================

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Digital Lending - Deployment Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$allPassed = $true

# Test 1: Function Unit
Write-Host "Test 1: Checking Function Unit..." -ForegroundColor Yellow
$result1 = @"
SELECT id, code, name, status 
FROM dw_function_units 
WHERE code = 'DIGITAL_LENDING';
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result1 -match "DIGITAL_LENDING") {
    Write-Host "  ✓ Function Unit exists" -ForegroundColor Green
} else {
    Write-Host "  ✗ Function Unit NOT found" -ForegroundColor Red
    $allPassed = $false
}

# Test 2: Tables
Write-Host "Test 2: Checking Tables..." -ForegroundColor Yellow
$result2 = @"
SELECT COUNT(*) as table_count
FROM dw_table_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result2 -match "7") {
    Write-Host "  ✓ All 7 tables created" -ForegroundColor Green
} else {
    Write-Host "  ✗ Expected 7 tables, found: $result2" -ForegroundColor Red
    $allPassed = $false
}

# Test 3: Forms
Write-Host "Test 3: Checking Forms..." -ForegroundColor Yellow
$result3 = @"
SELECT COUNT(*) as form_count
FROM dw_form_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result3 -match "5") {
    Write-Host "  ✓ All 5 forms created" -ForegroundColor Green
} else {
    Write-Host "  ✗ Expected 5 forms, found: $result3" -ForegroundColor Red
    $allPassed = $false
}

# Test 4: Actions
Write-Host "Test 4: Checking Actions..." -ForegroundColor Yellow
$result4 = @"
SELECT COUNT(*) as action_count
FROM dw_action_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result4 -match "12") {
    Write-Host "  ✓ All 12 actions created" -ForegroundColor Green
} else {
    Write-Host "  ✗ Expected 12 actions, found: $result4" -ForegroundColor Red
    $allPassed = $false
}

# Test 5: FORM_POPUP Actions
Write-Host "Test 5: Checking FORM_POPUP Actions..." -ForegroundColor Yellow
$result5 = @"
SELECT COUNT(*) as popup_count
FROM dw_action_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING')
AND action_type = 'FORM_POPUP';
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result5 -match "4") {
    Write-Host "  ✓ All 4 FORM_POPUP actions created" -ForegroundColor Green
} else {
    Write-Host "  ✗ Expected 4 FORM_POPUP actions, found: $result5" -ForegroundColor Red
    $allPassed = $false
}

# Test 6: BPMN Process
Write-Host "Test 6: Checking BPMN Process..." -ForegroundColor Yellow
$result6 = @"
SELECT id, function_unit_id, LENGTH(bpmn_xml) as bpmn_length
FROM dw_process_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result6 -match "\d+") {
    Write-Host "  ✓ BPMN process exists" -ForegroundColor Green
} else {
    Write-Host "  ✗ BPMN process NOT found" -ForegroundColor Red
    $allPassed = $false
}

# Test 7: Action Bindings in BPMN
Write-Host "Test 7: Checking Action Bindings in BPMN..." -ForegroundColor Yellow
$result7 = @"
SELECT 
    CASE 
        WHEN convert_from(decode(bpmn_xml, 'base64'), 'UTF8') LIKE '%actionIds%' 
        THEN 'YES'
        ELSE 'NO'
    END as has_bindings
FROM dw_process_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result7 -match "YES") {
    Write-Host "  ✓ Action bindings found in BPMN" -ForegroundColor Green
} else {
    Write-Host "  ✗ Action bindings NOT found in BPMN" -ForegroundColor Red
    $allPassed = $false
}

# Test 8: Form Bindings in BPMN
Write-Host "Test 8: Checking Form Bindings in BPMN..." -ForegroundColor Yellow
$result8 = @"
SELECT 
    CASE 
        WHEN convert_from(decode(bpmn_xml, 'base64'), 'UTF8') LIKE '%formId%' 
        THEN 'YES'
        ELSE 'NO'
    END as has_form_bindings
FROM dw_process_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t

if ($result8 -match "YES") {
    Write-Host "  ✓ Form bindings found in BPMN" -ForegroundColor Green
} else {
    Write-Host "  ✗ Form bindings NOT found in BPMN" -ForegroundColor Red
    $allPassed = $false
}

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

if ($allPassed) {
    Write-Host "✓ ALL TESTS PASSED!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Digital Lending System is correctly installed!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Open Developer Workstation: http://localhost:3002" -ForegroundColor Yellow
    Write-Host "  2. Navigate to Function Units" -ForegroundColor Yellow
    Write-Host "  3. Find 'Digital Lending System'" -ForegroundColor Yellow
    Write-Host "  4. Click 'Deploy' to activate" -ForegroundColor Yellow
    Write-Host "  5. Test in User Portal: http://localhost:3001" -ForegroundColor Yellow
    Write-Host ""
} else {
    Write-Host "✗ SOME TESTS FAILED" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please review the errors above and:" -ForegroundColor Yellow
    Write-Host "  1. Check if all scripts ran successfully" -ForegroundColor White
    Write-Host "  2. Re-run: .\00-run-all.ps1" -ForegroundColor White
    Write-Host "  3. Check database connection" -ForegroundColor White
    Write-Host ""
}

Write-Host "Detailed Component List:" -ForegroundColor Cyan
Write-Host ""

# List all actions
Write-Host "Actions:" -ForegroundColor Yellow
@"
SELECT id, action_name, action_type
FROM dw_action_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING')
ORDER BY id;
"@ | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
