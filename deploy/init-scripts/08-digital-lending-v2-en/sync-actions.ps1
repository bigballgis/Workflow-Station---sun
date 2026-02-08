# 同步 Action 定义到 Admin Center
# 将 Developer Workstation 的 action 定义同步到 Admin Center 的 sys_function_unit_contents

$functionUnitId = "4737ac68-42c5-4571-972e-e7ad0c6c7253"

Write-Host "开始同步 Action 定义..." -ForegroundColor Cyan
Write-Host "功能单元 ID: $functionUnitId" -ForegroundColor Yellow

# 1. 清理已存在的 ACTION 内容
Write-Host "`n清理已存在的 ACTION 内容..." -ForegroundColor Yellow
$cleanupQuery = @"
DELETE FROM sys_function_unit_contents 
WHERE function_unit_id = '$functionUnitId' 
AND content_type = 'ACTION';
"@
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $cleanupQuery

# 2. 获取所有 action 定义
Write-Host "`n获取 Action 定义..." -ForegroundColor Yellow
$getActionsQuery = @"
SELECT id, action_name, action_type, config_json, icon, button_color, description, is_default
FROM dw_action_definitions 
WHERE function_unit_id = 10
ORDER BY id;
"@

$actionsJson = docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -A -F '|' -c $getActionsQuery

if ([string]::IsNullOrWhiteSpace($actionsJson)) {
    Write-Host "警告: 没有找到 action 定义" -ForegroundColor Yellow
    exit 1
}

# 3. 逐个插入 action
$actionCount = 0
$actionsJson -split "`n" | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line)) {
        return
    }
    
    $parts = $line -split '\|'
    if ($parts.Length -lt 8) {
        Write-Host "警告: 跳过无效行: $line" -ForegroundColor Yellow
        return
    }
    
    $actionId = $parts[0]
    $actionName = $parts[1]
    $actionType = $parts[2]
    $configJson = $parts[3]
    $icon = $parts[4]
    $buttonColor = $parts[5]
    $description = $parts[6]
    $isDefault = $parts[7]
    
    Write-Host "`n处理 Action: $actionName (ID: $actionId)" -ForegroundColor Green
    
    # 构建完整的 action 配置
    $actionData = @{
        actionName = $actionName
        actionType = $actionType
        config = if ([string]::IsNullOrWhiteSpace($configJson)) { @{} } else { $configJson | ConvertFrom-Json }
        icon = $icon
        buttonColor = $buttonColor
        description = $description
        isDefault = ($isDefault -eq 't')
    } | ConvertTo-Json -Depth 10 -Compress
    
    # 转义单引号
    $actionDataEscaped = $actionData.Replace("'", "''")
    
    # 插入到 Admin Center
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
    'ACTION',
    '$actionName',
    '$actionDataEscaped',
    '$actionId',
    CURRENT_TIMESTAMP
);
"@
    
    $result = docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $insertQuery 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ 成功同步 Action: $actionName" -ForegroundColor Green
        $actionCount++
    } else {
        Write-Host "  ✗ 同步失败: $result" -ForegroundColor Red
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Action 定义同步完成！共同步 $actionCount 个 Action" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

# 4. 验证结果
Write-Host "`n验证同步结果..." -ForegroundColor Cyan
$verifyQuery = @"
SELECT content_name, LENGTH(content_data) as data_size 
FROM sys_function_unit_contents 
WHERE function_unit_id = '$functionUnitId' 
AND content_type = 'ACTION'
ORDER BY content_name;
"@

docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $verifyQuery
