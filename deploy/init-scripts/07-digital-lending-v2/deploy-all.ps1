# =============================================================================
# 数字贷款系统 V2 - 一键部署脚本
# 使用文件复制方式避免管道传输问题
# =============================================================================

param(
    [switch]$SkipVirtualGroups = $false
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "数字贷款系统 V2 - 一键部署" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Docker 容器
Write-Host "检查 Docker 容器状态..." -ForegroundColor Yellow
$containers = docker ps --format "{{.Names}}" | Select-String "platform-postgres-dev"
if (-not $containers) {
    Write-Host "错误：platform-postgres-dev 容器未运行" -ForegroundColor Red
    Write-Host "请先启动 Docker 容器" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Docker 容器正常运行" -ForegroundColor Green
Write-Host ""

# 步骤 1：创建虚拟组
if (-not $SkipVirtualGroups) {
    Write-Host "步骤 1/4: 创建虚拟组..." -ForegroundColor Cyan
    
    # 复制文件到容器
    docker cp 00-create-virtual-groups.sql platform-postgres-dev:/tmp/00-create-virtual-groups.sql
    
    # 在容器内执行
    docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/00-create-virtual-groups.sql
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "错误：虚拟组创建失败" -ForegroundColor Red
        exit 1
    }
    
    # 清理临时文件
    docker exec platform-postgres-dev rm /tmp/00-create-virtual-groups.sql
    
    Write-Host "  ✓ 虚拟组创建成功" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "步骤 1/4: 跳过虚拟组创建（使用 -SkipVirtualGroups 参数）" -ForegroundColor Yellow
    Write-Host ""
}

# 步骤 2：创建功能单元
Write-Host "步骤 2/4: 创建功能单元（表、表单、动作）..." -ForegroundColor Cyan

# 复制文件到容器
docker cp 01-create-digital-lending-complete.sql platform-postgres-dev:/tmp/01-create-digital-lending-complete.sql

# 在容器内执行
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/01-create-digital-lending-complete.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：功能单元创建失败" -ForegroundColor Red
    exit 1
}

# 清理临时文件
docker exec platform-postgres-dev rm /tmp/01-create-digital-lending-complete.sql

Write-Host "  ✓ 功能单元创建成功" -ForegroundColor Green
Write-Host ""

# 步骤 3：插入 BPMN 流程
Write-Host "步骤 3/4: 插入 BPMN 流程..." -ForegroundColor Cyan
.\02-insert-bpmn-process.ps1
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：BPMN 流程插入失败" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ BPMN 流程插入成功" -ForegroundColor Green
Write-Host ""

# 步骤 4：验证动作绑定
Write-Host "步骤 4/4: 验证动作绑定..." -ForegroundColor Cyan

# 复制文件到容器
docker cp 03-bind-actions.sql platform-postgres-dev:/tmp/03-bind-actions.sql

# 在容器内执行
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/03-bind-actions.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "错误：动作绑定验证失败" -ForegroundColor Red
    exit 1
}

# 清理临时文件
docker exec platform-postgres-dev rm /tmp/03-bind-actions.sql

Write-Host "  ✓ 动作绑定验证成功" -ForegroundColor Green
Write-Host ""

# 完成
Write-Host "========================================" -ForegroundColor Green
Write-Host "部署完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "数字贷款系统 V2 已成功部署到数据库" -ForegroundColor White
Write-Host ""
Write-Host "下一步操作：" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. 访问开发者工作台" -ForegroundColor White
Write-Host "   URL: http://localhost:3002" -ForegroundColor Gray
Write-Host "   找到'数字贷款系统 V2'并点击'部署'按钮" -ForegroundColor Gray
Write-Host ""
Write-Host "2. 访问用户门户测试" -ForegroundColor White
Write-Host "   URL: http://localhost:3001" -ForegroundColor Gray
Write-Host "   提交贷款申请并测试完整流程" -ForegroundColor Gray
Write-Host ""
Write-Host "3. 查看文档" -ForegroundColor White
Write-Host "   README.md - 完整系统文档" -ForegroundColor Gray
Write-Host "   QUICK_START.md - 快速开始指南" -ForegroundColor Gray
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
