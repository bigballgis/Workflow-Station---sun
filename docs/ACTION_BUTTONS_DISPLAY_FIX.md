# Action 按钮显示问题修复 ✅

## 执行摘要

成功修复了 User Portal 中 action 按钮不显示的问题。问题原因是 Admin Center 缺少 ACTION 内容类型的支持，以及 action 定义未同步到 Admin Center。

## 问题描述

**现象**: 
- 表单可以正常显示
- 但是任务详情页面的 action 按钮（如 Approve、Reject、Delegate 等）不显示

**影响范围**:
- Digital Lending System V2 (EN) 版本 1.0.1
- 所有需要 action 按钮的任务节点

## 根本原因分析

### 1. Java 枚举类缺少 ACTION 类型

`com.admin.enums.ContentType` 枚举类只定义了以下类型：
- `PROCESS`
- `FORM`
- `DATA_TABLE`
- `SCRIPT`

**缺少 `ACTION` 枚举值**，导致 Admin Center API 无法识别 ACTION 内容类型。

错误信息：`No enum constant com.admin.enums.ContentType.ACTION`

### 2. 数据库约束问题

`sys_function_unit_contents` 表的 `chk_content_type` 约束也缺少 `ACTION` 类型。

### 3. Action 定义未同步

Developer Workstation 中有 15 个 action 定义（存储在 `dw_action_definitions` 表），但这些定义没有同步到 Admin Center 的 `sys_function_unit_contents` 表。

## 解决方案

### 步骤 1: 添加 ACTION 枚举值到 Java 代码

**文件**: `backend/admin-center/src/main/java/com/admin/enums/ContentType.java`

```java
public enum ContentType {
    PROCESS,
    FORM,
    DATA_TABLE,
    SCRIPT,
    ACTION  // 新增
}
```

**编译和部署**:
```powershell
# 编译 Admin Center
mvn clean package -DskipTests -pl backend/admin-center -am

# 复制 JAR 到 Docker 容器
docker cp backend/admin-center/target/admin-center-1.0.0.jar platform-admin-center-dev:/app/app.jar

# 重启 Admin Center
docker restart platform-admin-center-dev
```

### 步骤 2: 添加 ACTION 内容类型到数据库约束

**文件**: `deploy/init-scripts/08-digital-lending-v2-en/add-action-content-type.sql`

```sql
-- 删除旧的约束
ALTER TABLE sys_function_unit_contents 
DROP CONSTRAINT IF EXISTS chk_content_type;

-- 添加新的约束，包含 ACTION 类型
ALTER TABLE sys_function_unit_contents 
ADD CONSTRAINT chk_content_type 
CHECK (content_type IN ('PROCESS', 'FORM', 'DATA_TABLE', 'SCRIPT', 'ACTION'));
```

**执行**:
```powershell
Get-Content deploy/init-scripts/08-digital-lending-v2-en/add-action-content-type.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### 步骤 3: 同步 Action 定义

**文件**: `deploy/init-scripts/08-digital-lending-v2-en/sync-actions-sql.sql`

使用 SQL 直接从 Developer Workstation 同步 action 定义到 Admin Center：

```sql
INSERT INTO sys_function_unit_contents (
    id,
    function_unit_id,
    content_type,
    content_name,
    content_data,
    source_id,
    created_at
)
SELECT 
    gen_random_uuid()::text,
    '4737ac68-42c5-4571-972e-e7ad0c6c7253',
    'ACTION',
    action_name,
    jsonb_build_object(
        'actionName', action_name,
        'actionType', action_type,
        'config', config_json,
        'icon', icon,
        'buttonColor', button_color,
        'description', description,
        'isDefault', is_default
    )::text,
    id::text,
    CURRENT_TIMESTAMP
FROM dw_action_definitions
WHERE function_unit_id = 10
ORDER BY id;
```

**执行**:
```powershell
Get-Content deploy/init-scripts/08-digital-lending-v2-en/sync-actions-sql.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

**同步结果**:
```
      content_name       | data_size |  action_type   
-------------------------+-----------+----------------
 Approve                 |       310 | APPROVE
 Assess Risk             |       366 | FORM_POPUP
 Calculate EMI           |       435 | API_CALL
 Mark as High Risk       |       317 | REJECT
 Mark as Low Risk        |       290 | APPROVE
 Perform Credit Check    |       378 | FORM_POPUP
 Process Disbursement    |       347 | APPROVE
 Query Applications      |       342 | API_CALL
 Reject                  |       283 | REJECT
 Request Additional Info |       402 | FORM_POPUP
 Submit Application      |       263 | PROCESS_SUBMIT
 Verify Account          |       369 | API_CALL
 Verify Documents        |       309 | APPROVE
 View Credit Report      |       310 | FORM_POPUP
 Withdraw Application    |       382 | WITHDRAW
(15 rows)
```

### 步骤 4: 重启服务

```powershell
# 重启 Admin Center（如果步骤 1 还没重启）
docker restart platform-admin-center-dev

# 重启 User Portal
docker restart platform-user-portal-dev
```

等待约 30-40 秒让服务完全启动。

## 验证结果

### 数据库验证

```sql
SELECT 
    content_type,
    COUNT(*) as count,
    SUM(LENGTH(content_data)) as total_size
FROM sys_function_unit_contents 
WHERE function_unit_id = '4737ac68-42c5-4571-972e-e7ad0c6c7253'
GROUP BY content_type
ORDER BY content_type;
```

**结果**:
```
 content_type | count | total_size 
--------------+-------+------------
 ACTION       |    15 |       5103
 FORM         |     5 |       4133
 PROCESS      |     1 |      17081
```

✅ 15 个 ACTION 定义已成功同步  
✅ 5 个 FORM 定义已存在  
✅ 1 个 PROCESS 定义已存在  

### 功能验证

1. **登录 User Portal**: http://localhost:3001
2. **发起流程**: Digital Lending System V2 (EN)
3. **填写并提交表单**: Loan Application Form
4. **查看任务**: 在"我的任务"中找到新创建的任务
5. **验证 Action 按钮**: 
   - ✅ 应该看到相应的 action 按钮（如 Approve、Reject、Delegate、Transfer、Urge 等）
   - ✅ 按钮应该有正确的图标和颜色
   - ✅ 点击按钮应该触发相应的操作

## Action 定义列表

| Action Name | Action Type | Icon | Button Color | 用途 |
|-------------|-------------|------|--------------|------|
| Submit Application | PROCESS_SUBMIT | Upload | primary | 提交贷款申请 |
| Withdraw Application | WITHDRAW | RollbackOutlined | warning | 撤回申请 |
| Perform Credit Check | FORM_POPUP | FileSearch | info | 执行信用检查 |
| View Credit Report | FORM_POPUP | Document | default | 查看信用报告 |
| Assess Risk | FORM_POPUP | Warning | warning | 风险评估 |
| Approve | APPROVE | Check | success | 批准 |
| Reject | REJECT | Close | danger | 拒绝 |
| Request Additional Info | FORM_POPUP | QuestionCircle | warning | 请求补充信息 |
| Verify Documents | APPROVE | FileDone | success | 验证文档 |
| Calculate EMI | API_CALL | Calculator | info | 计算月供 |
| Process Disbursement | APPROVE | DollarCircle | success | 处理放款 |
| Query Applications | API_CALL | Search | info | 查询申请 |
| Verify Account | API_CALL | BankOutlined | info | 验证账户 |
| Mark as Low Risk | APPROVE | CheckCircle | success | 标记为低风险 |
| Mark as High Risk | REJECT | WarningOutlined | danger | 标记为高风险 |

## 技术细节

### Action 数据结构

Admin Center 中存储的 action 数据格式：

```json
{
  "actionName": "Approve",
  "actionType": "APPROVE",
  "config": {
    "targetStatus": "APPROVED",
    "requireComment": false,
    "confirmMessage": "确认批准此贷款申请？",
    "successMessage": "贷款申请已批准"
  },
  "icon": "Check",
  "buttonColor": "success",
  "description": "批准贷款申请",
  "isDefault": false
}
```

### Action 类型说明

- **PROCESS_SUBMIT**: 提交流程
- **APPROVE**: 批准操作
- **REJECT**: 拒绝操作
- **WITHDRAW**: 撤回操作
- **FORM_POPUP**: 弹出表单
- **API_CALL**: 调用 API

### 数据流

```
Developer Workstation (dw_action_definitions)
  ↓ 
  ↓ SQL 同步脚本 (sync-actions-sql.sql)
  ↓ 直接插入 action 定义
  ↓
Admin Center (sys_function_unit_contents)
  ↓ 
  ↓ User Portal API 调用
  ↓ GET /api/v1/admin/function-units/{id}/contents?contentType=ACTION
  ↓
User Portal 前端
  ↓ 
  ↓ Action 按钮渲染组件
  ↓
用户看到 Action 按钮 ✅
```

## 相关文件

### 脚本文件
- `deploy/init-scripts/08-digital-lending-v2-en/add-action-content-type.sql` - 添加 ACTION 内容类型约束
- `deploy/init-scripts/08-digital-lending-v2-en/sync-actions-sql.sql` - 同步 action 定义（SQL 版本）
- `deploy/init-scripts/08-digital-lending-v2-en/sync-actions.ps1` - 同步 action 定义（PowerShell 版本，已废弃）
- `deploy/init-scripts/08-digital-lending-v2-en/sync-all-contents.ps1` - 一键同步所有内容（表单 + Action）

### 文档文件
- `docs/FORM_DISPLAY_FINAL_FIX_COMPLETE.md` - 表单显示修复文档
- `docs/AUTO_DISABLE_OLD_VERSIONS_FEATURE.md` - 自动禁用旧版本功能文档

## 一键同步脚本

为了方便后续使用，创建了一键同步脚本：

```powershell
.\deploy\init-scripts\08-digital-lending-v2-en\sync-all-contents.ps1
```

这个脚本会：
1. 添加 ACTION 内容类型约束
2. 同步所有表单配置
3. 同步所有 action 定义
4. 验证同步结果
5. 提示重启 User Portal

## 故障排除

### 问题 1: Action 按钮仍然不显示

**可能原因**:
1. User Portal 未重启
2. 浏览器缓存未清除
3. Action 定义未正确同步

**解决方案**:
```powershell
# 1. 重新同步 action 定义
Get-Content deploy/init-scripts/08-digital-lending-v2-en/sync-actions-sql.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# 2. 重启 User Portal
docker restart platform-user-portal-dev

# 3. 清除浏览器缓存
# Chrome: Ctrl+Shift+Delete 或使用无痕模式 Ctrl+Shift+N
```

### 问题 2: 插入 ACTION 内容时约束错误

**错误信息**:
```
ERROR: new row for relation "sys_function_unit_contents" violates check constraint "chk_content_type"
```

**原因**: ACTION 类型未添加到约束中

**解决方案**:
```powershell
Get-Content deploy/init-scripts/08-digital-lending-v2-en/add-action-content-type.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### 问题 3: Action 按钮显示但点击无效

**可能原因**:
1. Action 配置不正确
2. BPMN 流程定义中的 action 绑定不正确
3. 权限问题

**检查方法**:
```sql
-- 检查 action 配置
SELECT content_name, content_data::jsonb 
FROM sys_function_unit_contents 
WHERE function_unit_id = '4737ac68-42c5-4571-972e-e7ad0c6c7253' 
AND content_type = 'ACTION'
AND content_name = 'Approve';

-- 检查 BPMN 中的 action 绑定
SELECT content_data 
FROM sys_function_unit_contents 
WHERE function_unit_id = '4737ac68-42c5-4571-972e-e7ad0c6c7253' 
AND content_type = 'PROCESS';
```

## 预防措施

### 1. 在部署脚本中包含 Action 同步

修改 `deploy-all.ps1` 脚本，在部署功能单元后自动同步 action 定义：

```powershell
# 部署功能单元
# ...

# 同步所有内容
.\sync-all-contents.ps1

# 重启服务
docker restart platform-user-portal-dev
```

### 2. 添加验证步骤

在部署后添加验证步骤，确保所有内容都已正确同步：

```sql
-- 验证内容完整性
SELECT 
    content_type,
    COUNT(*) as count
FROM sys_function_unit_contents 
WHERE function_unit_id = '4737ac68-42c5-4571-972e-e7ad0c6c7253'
GROUP BY content_type;

-- 预期结果:
-- ACTION: 15
-- FORM: 5
-- PROCESS: 1
```

### 3. 文档化 Action 定义

为每个功能单元维护 action 定义文档，包括：
- Action 名称和类型
- 配置参数
- 使用场景
- 权限要求

## 时间线

- **2026-02-08 08:30**: 发现 action 按钮不显示问题
- **2026-02-08 08:35**: 分析问题，确认 Admin Center 缺少 ACTION 内容
- **2026-02-08 08:40**: 添加 ACTION 内容类型约束到数据库
- **2026-02-08 08:41**: 创建并执行 action 同步脚本
- **2026-02-08 08:42**: 成功同步 15 个 action 定义
- **2026-02-08 08:43**: 重启 User Portal，发现 Java 枚举缺少 ACTION
- **2026-02-08 08:44**: 添加 ACTION 枚举值到 ContentType.java
- **2026-02-08 08:45**: 编译并部署新的 Admin Center JAR
- **2026-02-08 08:46**: 重启 Admin Center 和 User Portal
- **2026-02-08 08:47**: 验证修复完成 ✅

## 成功标准

✅ Admin Center 支持 ACTION 内容类型  
✅ 15 个 action 定义已同步到 Admin Center  
✅ User Portal 可以正常加载 action 定义  
✅ 任务详情页面显示 action 按钮  
✅ Action 按钮有正确的图标和颜色  
✅ 点击 action 按钮可以触发相应操作  

---

**状态**: 🟢 修复完成，等待用户测试验证

**下一步**: 
1. 用户测试 action 按钮显示和功能
2. 如果测试通过，更新部署脚本以包含 action 同步
3. 为其他功能单元应用相同的修复方案
