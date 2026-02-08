# 同步所有内容（表单和 Action）到 Admin Center

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "同步所有内容到 Admin Center" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. 添加 ACTION 内容类型约束（如果还没有）
Write-Host "`n步骤 1: 添加 ACTION 内容类型约束..." -ForegroundColor Yellow
Get-Content deploy/init-scripts/08-digital-lending-v2-en/add-action-content-type.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev | Out-Null
Write-Host "✓ ACTION 内容类型约束已添加" -ForegroundColor Green

# 2. 同步表单
Write-Host "`n步骤 2: 同步表单配置..." -ForegroundColor Yellow
.\deploy\init-scripts\08-digital-lending-v2-en\sync-forms.ps1

# 3. 同步 Action 定义
Write-Host "`n步骤 3: 同步 Action 定义..." -ForegroundColor Yellow
Get-Content deploy/init-scripts/08-digital-lending-v2-en/sync-actions-sql.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# 4. 同步 Action 到 sys_action_definitions 表
Write-Host "`n步骤 4: 同步 Action 到 sys_action_definitions 表..." -ForegroundColor Yellow
Get-Content deploy/init-scripts/08-digital-lending-v2-en/sync-actions-to-sys-table.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# 5. 验证所有内容
Write-Host "`n步骤 5: 验证所有内容..." -ForegroundColor Yellow
$verifyQuery = @"
SELECT 
    content_type,
    COUNT(*) as count,
    SUM(LENGTH(content_data)) as total_size
FROM sys_function_unit_contents 
WHERE function_unit_id = '4737ac68-42c5-4571-972e-e7ad0c6c7253'
GROUP BY content_type
ORDER BY content_type;
"@
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $verifyQuery

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "所有内容同步完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`n提示: 请重启 User Portal 以加载最新配置:" -ForegroundColor Yellow
Write-Host "  docker restart platform-user-portal-dev" -ForegroundColor White
