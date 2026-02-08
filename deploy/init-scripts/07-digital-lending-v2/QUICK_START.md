# 数字贷款系统 V2 - 快速开始

## 方式一：一键部署（推荐）

### 步骤 1：准备环境

确保以下服务正在运行：

```powershell
# 检查 Docker 容器状态
docker ps | Select-String "postgres|developer-workstation"
```

应该看到：
- `platform-postgres-dev` - 数据库
- `platform-developer-workstation-dev` - 开发者工作台后端
- `platform-developer-workstation-frontend-dev` - 开发者工作台前端

### 步骤 2：运行一键部署脚本

```powershell
# 进入脚本目录
cd deploy/init-scripts/07-digital-lending-v2

# 运行一键部署脚本
.\deploy-all.ps1

# 如果虚拟组已存在，可以跳过虚拟组创建
.\deploy-all.ps1 -SkipVirtualGroups
```

脚本会自动完成：
1. 创建虚拟组
2. 创建功能单元（表、表单、动作）
3. 插入 BPMN 流程
4. 验证动作绑定

### 步骤 3：验证安装

```powershell
# 运行验证脚本
Get-Content verify-installation.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### 步骤 4：访问开发者工作台

打开浏览器访问：http://localhost:3002

登录后应该能看到"数字贷款系统 V2"功能单元。

---

## 方式二：分步部署

### 步骤 1：准备环境

（同上）

### 步骤 2：创建虚拟组

```powershell
# 进入脚本目录
cd deploy/init-scripts/07-digital-lending-v2

# 创建虚拟组
Get-Content 00-create-virtual-groups.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### 步骤 3：创建功能单元

```powershell
# 创建功能单元（表、表单、动作）
Get-Content 01-create-digital-lending-complete.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### 步骤 4：插入 BPMN 流程

```powershell
# 运行 PowerShell 脚本插入流程
.\02-insert-bpmn-process.ps1
```

### 步骤 5：验证动作绑定

```powershell
# 验证动作绑定
Get-Content 03-bind-actions.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### 步骤 6：访问开发者工作台

打开浏览器访问：http://localhost:3002

登录后应该能看到"数字贷款系统 V2"功能单元。


## 快速测试

### 测试场景 1：正常审批流程

1. **提交申请**
   - 登录用户门户：http://localhost:3001
   - 用户：test_user / password123
   - 填写贷款申请表单
   - 点击"Submit Application"

2. **文档验证**
   - 登录为文档验证员
   - 查看待办任务
   - 点击"Verify Documents" → "Approve"

3. **信用检查**
   - 登录为信用审查员
   - 点击"Perform Credit Check"
   - 填写信用评分（如：750）
   - 保存并批准

4. **风险评估**
   - 登录为风险评估员
   - 点击"Assess Risk"
   - 设置风险等级为"Low"
   - 批准继续

5. **经理审批**
   - 登录为部门经理
   - 查看完整信息
   - 点击"Approve"

6. **高级经理审批**
   - 登录为高级经理
   - 最终审批
   - 点击"Final Approve"

7. **财务放款**
   - 登录为财务人员
   - 验证账户
   - 点击"Process Disbursement"

### 测试场景 2：拒绝流程

在任何审批节点点击"Reject"，流程将直接结束。

### 测试场景 3：补充信息

在风险评估阶段，选择"Need More Info"，流程将返回到提交申请节点。


## 验证安装

### 检查功能单元

```sql
-- 查询功能单元
SELECT id, code, name, status, version 
FROM dw_function_units 
WHERE code = 'DIGITAL_LENDING_V2';
```

### 检查表定义

```sql
-- 查询表定义
SELECT t.id, t.table_name, t.table_type, COUNT(f.id) as field_count
FROM dw_table_definitions t
LEFT JOIN dw_field_definitions f ON f.table_id = t.id
WHERE t.function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
)
GROUP BY t.id, t.table_name, t.table_type
ORDER BY t.table_type, t.id;
```

预期结果：7个表，每个表都有字段定义

### 检查表单定义

```sql
-- 查询表单定义
SELECT f.id, f.form_name, f.form_type, COUNT(b.id) as binding_count
FROM dw_form_definitions f
LEFT JOIN dw_form_table_bindings b ON b.form_id = f.id
WHERE f.function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
)
GROUP BY f.id, f.form_name, f.form_type
ORDER BY f.id;
```

预期结果：5个表单，每个表单都有表绑定

### 检查动作定义

```sql
-- 查询动作定义
SELECT id, action_name, action_type, button_color
FROM dw_action_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
)
ORDER BY id;
```

预期结果：15个动作

### 检查流程定义

```sql
-- 查询流程定义
SELECT id, function_unit_id, LENGTH(bpmn_xml) as xml_length
FROM dw_process_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
);
```

预期结果：1个流程定义，XML 长度 > 10000


## 故障排除

### 问题 1：虚拟组不存在

**错误信息：** Virtual group not found

**解决方案：**
```powershell
# 重新创建虚拟组
Get-Content 00-create-virtual-groups.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### 问题 2：流程插入失败

**错误信息：** BPMN XML insert failed

**解决方案：**
```powershell
# 检查 BPMN 文件是否存在
Test-Path digital-lending-process-v2.bpmn

# 重新运行插入脚本
.\02-insert-bpmn-process.ps1
```

### 问题 3：动作绑定失败

**错误信息：** Action ID not found

**解决方案：**
```sql
-- 查询实际的动作 ID
SELECT id, action_name FROM dw_action_definitions 
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
);

-- 根据实际 ID 更新 03-bind-actions.sql
```

### 问题 4：功能单元已存在

**错误信息：** Duplicate key value violates unique constraint

**解决方案：**
```sql
-- 删除现有功能单元
DELETE FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2';

-- 重新运行创建脚本
```

## 下一步

1. **学习 AI 生成框架**
   - 阅读：`docs/AI_FUNCTION_UNIT_GENERATION_FRAMEWORK.md`
   - 理解七个阶段的设计流程

2. **创建自己的功能单元**
   - 使用框架指导与 AI 对话
   - 生成自定义的业务流程

3. **探索高级功能**
   - 版本管理和回滚
   - 权限系统集成
   - API 集成和扩展

4. **部署到生产环境**
   - 参考：`deploy/k8s/README.md`
   - 配置生产环境变量

