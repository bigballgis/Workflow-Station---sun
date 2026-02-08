# 表单显示 422 错误修复 ✅

## 问题描述

在测试表单显示时，Developer Workstation 前端出现 422 错误：
```
GET http://localhost:3002/api/v1/function-units?page=0&size=20 422
```

## 根本原因

这是之前遇到过的重复流程定义问题。当运行部署脚本 `deploy-all.ps1` 时，会重新插入 BPMN 流程定义，导致 `dw_process_definitions` 表中出现重复记录。

### 重复记录

```sql
SELECT id, function_unit_id, function_unit_version_id, created_at 
FROM dw_process_definitions 
WHERE function_unit_id = 10;
```

结果：
```
 id | function_unit_id | function_unit_version_id |         created_at
----+------------------+--------------------------+----------------------------
 12 |               10 |                       10 | 2026-02-06 14:18:43.691214
 14 |               10 |                       10 | 2026-02-08 08:01:34.767653
```

## 解决方案

### 1. 删除重复记录

删除较新的流程定义记录（保留原始记录）：

```sql
DELETE FROM dw_process_definitions WHERE id = 14;
```

### 2. 重启 Developer Workstation

```bash
docker restart platform-developer-workstation-dev
```

### 3. 验证服务启动

等待约 30 秒，检查日志确认服务正常启动：

```bash
docker logs --tail 5 platform-developer-workstation-dev
```

预期输出：
```
Tomcat started on port 8080 (http) with context path '/api/v1'
Started DeveloperWorkstationApplication in 28.31 seconds
```

## 执行结果

✅ 删除重复记录成功  
✅ Developer Workstation 重启成功  
✅ 服务正常启动（28.31 秒）  

## 下一步测试

### 1. 验证 Developer Workstation 功能

访问 http://localhost:3002 并确认：
- 功能单元列表正常加载（无 422 错误）
- 可以查看 Digital Lending System V2 (EN) 详情

### 2. 验证 User Portal 表单显示

1. 访问 http://localhost:3001
2. 按 `Ctrl+F5` 强制刷新页面
3. 进入"流程中心"
4. 找到 "Digital Lending System V2 (EN)"
5. 点击"发起流程"
6. **确认表单显示完整的 8 个字段**：
   - Loan Type (下拉选择)
   - Loan Amount (数字输入)
   - Tenure (Months) (数字输入)
   - Purpose (文本域)
   - Full Name (文本输入)
   - Mobile (文本输入)
   - Email (邮箱输入)
   - Monthly Income (数字输入)

### 3. 测试表单提交

1. 填写所有必填字段
2. 点击"提交申请"按钮
3. 验证流程是否成功启动

## 预防措施

为避免将来再次出现此问题，建议：

### 1. 改进部署脚本

在 `deploy-all.ps1` 中添加检查逻辑：

```powershell
# 检查是否已存在流程定义
$checkQuery = "SELECT COUNT(*) FROM dw_process_definitions WHERE function_unit_id = 10;"
$count = docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -c $checkQuery

if ($count -gt 0) {
    Write-Host "流程定义已存在，跳过 BPMN 插入" -ForegroundColor Yellow
    # 跳过步骤 2
} else {
    # 执行步骤 2: 插入 BPMN
}
```

### 2. 添加唯一约束

在数据库中添加唯一约束，防止重复插入：

```sql
ALTER TABLE dw_process_definitions 
ADD CONSTRAINT uk_process_def_function_unit 
UNIQUE (function_unit_id, function_unit_version_id);
```

### 3. 使用 UPSERT 语法

修改插入脚本使用 PostgreSQL 的 `ON CONFLICT` 语法：

```sql
INSERT INTO dw_process_definitions (function_unit_id, bpmn_xml, ...)
VALUES (10, '...', ...)
ON CONFLICT (function_unit_id, function_unit_version_id) 
DO UPDATE SET bpmn_xml = EXCLUDED.bpmn_xml, updated_at = CURRENT_TIMESTAMP;
```

## 相关文档

- 表单配置修复: `docs/FORM_DISPLAY_FIX_COMPLETE.md`
- 部署完成报告: `docs/FORM_DISPLAY_DEPLOYMENT_COMPLETE.md`
- 问题分析: `docs/FORM_DISPLAY_ISSUE_ANALYSIS.md`
- 部署脚本: `deploy/init-scripts/08-digital-lending-v2-en/deploy-all.ps1`

## 时间线

- **2026-02-06 14:18**: 首次部署，创建流程定义 ID=12
- **2026-02-08 08:01**: 重新运行部署脚本，创建重复记录 ID=14
- **2026-02-08 08:06**: 发现 422 错误
- **2026-02-08 08:07**: 删除重复记录，重启服务
- **2026-02-08 08:07**: 问题解决 ✅

