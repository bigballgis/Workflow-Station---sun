# 部署 Digital Lending System V2 (EN) - 版本 1.0.2
# 包含自动禁用旧版本和表单格式修复

param(
    [switch]$SkipVirtualGroups
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "部署 Digital Lending System V2 (EN) v1.0.2" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 步骤 1: 创建虚拟组（如果需要）
if (-not $SkipVirtualGroups) {
    Write-Host "`n步骤 1: 创建虚拟组..." -ForegroundColor Green
    Get-Content deploy/init-scripts/08-digital-lending-v2-en/00-create-virtual-groups.sql -Raw | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
    if ($LASTEXITCODE -ne 0) {
        Write-Host "虚拟组创建失败" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "`n步骤 1: 跳过虚拟组创建" -ForegroundColor Yellow
}

# 步骤 2: 修改版本号为 1.0.2
Write-Host "`n步骤 2: 准备部署脚本（版本 1.0.2）..." -ForegroundColor Green
$sqlContent = Get-Content deploy/init-scripts/08-digital-lending-v2-en/01-create-digital-lending-complete.sql -Raw
$sqlContent = $sqlContent -replace "version,\s*\n\s*is_active,\s*\n\s*deployed_at,\s*\n\s*created_by,\s*\n\s*created_at,\s*\n\s*updated_at\s*\)\s*VALUES\s*\(\s*\n\s*'DIGITAL_LENDING_V2_EN',\s*\n\s*'Digital Lending System V2 \(EN\)',\s*\n\s*'[^']*',\s*\n\s*'DRAFT',\s*\n\s*'1\.0\.0'", "version,`n        is_active,`n        deployed_at,`n        created_by,`n        created_at,`n        updated_at`n    ) VALUES (`n        'DIGITAL_LENDING_V2_EN',`n        'Digital Lending System V2 (EN)',`n        'Full-featured digital loan application and approval system with credit checks, risk assessment, collateral management, multi-level approval, and automated disbursement',`n        'DRAFT',`n        '1.0.2'"

# 保存临时文件
$tempFile = "deploy/init-scripts/08-digital-lending-v2-en/01-create-digital-lending-v1.0.2.sql"
$sqlContent | Out-File -FilePath $tempFile -Encoding UTF8

# 步骤 3: 执行部署脚本
Write-Host "`n步骤 3: 创建功能单元（版本 1.0.2）..." -ForegroundColor Green
docker cp $tempFile platform-postgres-dev:/tmp/create-fu.sql
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/create-fu.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "功能单元创建失败" -ForegroundColor Red
    Remove-Item $tempFile -ErrorAction SilentlyContinue
    exit 1
}

# 清理临时文件
Remove-Item $tempFile -ErrorAction SilentlyContinue

# 步骤 4: 插入 BPMN 流程
Write-Host "`n步骤 4: 插入 BPMN 流程..." -ForegroundColor Green
& deploy/init-scripts/08-digital-lending-v2-en/02-insert-bpmn-process.ps1

if ($LASTEXITCODE -ne 0) {
    Write-Host "BPMN 流程插入失败" -ForegroundColor Red
    exit 1
}

# 步骤 5: 绑定动作
Write-Host "`n步骤 5: 绑定动作..." -ForegroundColor Green
Get-Content deploy/init-scripts/08-digital-lending-v2-en/03-bind-actions.sql -Raw | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

if ($LASTEXITCODE -ne 0) {
    Write-Host "动作绑定失败" -ForegroundColor Red
    exit 1
}

# 步骤 6: 修复表单格式（转换为 form-create 格式）
Write-Host "`n步骤 6: 修复表单格式..." -ForegroundColor Green
& deploy/init-scripts/08-digital-lending-v2-en/update-all-forms.ps1

if ($LASTEXITCODE -ne 0) {
    Write-Host "表单格式修复失败" -ForegroundColor Red
    exit 1
}

# 步骤 7: 同步表单到 Admin Center
Write-Host "`n步骤 7: 同步表单到 Admin Center..." -ForegroundColor Green

# 获取新版本的功能单元 ID
$functionUnitId = docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -c "SELECT id FROM sys_function_units WHERE code = 'DIGITAL_LENDING_V2_EN' AND version = '1.0.2' LIMIT 1;"
$functionUnitId = $functionUnitId.Trim()

if ([string]::IsNullOrWhiteSpace($functionUnitId)) {
    Write-Host "错误: 无法找到版本 1.0.2 的功能单元" -ForegroundColor Red
    exit 1
}

Write-Host "功能单元 ID (v1.0.2): $functionUnitId" -ForegroundColor Yellow

# 更新 sync-forms.ps1 中的功能单元 ID
$syncScript = Get-Content deploy/init-scripts/08-digital-lending-v2-en/sync-forms.ps1 -Raw
$syncScript = $syncScript -replace '\$functionUnitId = "[^"]*"', "`$functionUnitId = `"$functionUnitId`""
$syncScript | Out-File -FilePath "deploy/init-scripts/08-digital-lending-v2-en/sync-forms-temp.ps1" -Encoding UTF8

# 执行同步
& deploy/init-scripts/08-digital-lending-v2-en/sync-forms-temp.ps1

if ($LASTEXITCODE -ne 0) {
    Write-Host "表单同步失败" -ForegroundColor Red
    Remove-Item "deploy/init-scripts/08-digital-lending-v2-en/sync-forms-temp.ps1" -ErrorAction SilentlyContinue
    exit 1
}

# 清理临时文件
Remove-Item "deploy/init-scripts/08-digital-lending-v2-en/sync-forms-temp.ps1" -ErrorAction SilentlyContinue

# 步骤 8: 验证部署
Write-Host "`n步骤 8: 验证部署..." -ForegroundColor Green

$verifyQuery = @"
SELECT 
    fu.id,
    fu.code,
    fu.version,
    fu.enabled,
    COUNT(DISTINCT fc.id) as form_count,
    COUNT(DISTINCT pd.id) as process_count
FROM sys_function_units fu
LEFT JOIN sys_function_unit_contents fc ON fc.function_unit_id = fu.id AND fc.content_type = 'FORM'
LEFT JOIN dw_function_units dfu ON dfu.code = fu.code AND dfu.version = fu.version
LEFT JOIN dw_process_definitions pd ON pd.function_unit_id = dfu.id
WHERE fu.code = 'DIGITAL_LENDING_V2_EN' AND fu.version = '1.0.2'
GROUP BY fu.id, fu.code, fu.version, fu.enabled;
"@

docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c $verifyQuery

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "部署完成！版本 1.0.2" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`n下一步:" -ForegroundColor Yellow
Write-Host "1. 访问 User Portal: http://localhost:3001" -ForegroundColor White
Write-Host "2. 按 Ctrl+F5 强制刷新页面" -ForegroundColor White
Write-Host "3. 进入流程中心 → Digital Lending System V2 (EN)" -ForegroundColor White
Write-Host "4. 点击'发起流程'测试表单显示" -ForegroundColor White
