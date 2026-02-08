# 同步表单配置到 Admin Center
# 使用 PowerShell 逐个插入表单，避免 DO 块的编码问题

$functionUnitId = "4737ac68-42c5-4571-972e-e7ad0c6c7253"
$formIds = @(21, 22, 23, 24, 25)

Write-Host "开始同步表单配置..." -ForegroundColor Cyan
Write-Host "功能单元 ID: $functionUnitId" -ForegroundColor Yellow

foreach ($formId in $formIds) {
    Write-Host "`n处理表单 ID: $formId" -ForegroundColor Green
    
    # 获取表单配置
    $query = @"
SELECT json_build_object(
    'formName', form_name,
    'formType', form_type,
    'configJson', config_json
)::text
FROM dw_form_definitions 
WHERE id = $formId;
"@
    
    $formJson = docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -c $query
    $formJson = $formJson.Trim()
    
    if ([string]::IsNullOrWhiteSpace($formJson)) {
        Write-Host "  警告: 表单 $formId 不存在" -ForegroundColor Yellow
        continue
    }
    
    # 解析表单名称
    $formData = $formJson | ConvertFrom-Json
    $formName = $formData.formName
    $configJson = $formData.configJson | ConvertTo-Json -Depth 10 -Compress
    
    Write-Host "  表单名称: $formName" -ForegroundColor White
    
    # 转义单引号
    $configJsonEscaped = $configJson.Replace("'", "''")
    
    # 插入到 Admin Center - 直接存储 configJson
    $insertQuery = @"
INSERT INTO sys_function_unit_contents (
    id,
    function_unit_id,
    content_type,
    content_name,
    content_data,
    source_id,
    created_at
) VALUES (
    gen_random_uuid()::text,
    '$functionUnitId',
    'FORM',
    '$formName',
    '$configJsonEscaped',
    '$formId',
    CURRENT_TIMESTAMP
);
"@
    
    $result = docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $insertQuery 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ 成功导出表单: $formName" -ForegroundColor Green
    } else {
        Write-Host "  ✗ 导出失败: $result" -ForegroundColor Red
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "表单配置同步完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

# 验证结果
Write-Host "`n验证同步结果..." -ForegroundColor Cyan
$verifyQuery = @"
SELECT content_name, LENGTH(content_data) as data_size 
FROM sys_function_unit_contents 
WHERE function_unit_id = '$functionUnitId' 
AND content_type = 'FORM'
ORDER BY content_name;
"@

docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $verifyQuery
