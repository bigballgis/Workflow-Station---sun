# Action 按钮显示完整修复指南 ✅

## 问题描述

User Portal 中任务详情页面的 action 按钮（如 Approve、Reject、Delegate 等）不显示。

错误信息：
```
Function unit content error: 500 : "No enum constant com.admin.enums.ContentType.ACTION"
```

## 完整解决方案

### 第一步：修改 Java 枚举类

**文件**: `backend/admin-center/src/main/java/com/admin/enums/ContentType.java`

添加 `ACTION` 枚举值：

```java
package com.admin.enums;

/**
 * 功能单元内容类型枚举
 */
public enum ContentType {
    /**
     * 流程定义 (BPMN)
     */
    PROCESS,
    
    /**
     * 表单定义
     */
    FORM,
    
    /**
     * 数据表结构
     */
    DATA_TABLE,
    
    /**
     * 脚本
     */
    SCRIPT,
    
    /**
     * 操作定义 (Action)
     */
    ACTION
}
```

### 第二步：编译和部署 Admin Center

```powershell
# 1. 编译项目
mvn clean package -DskipTests -pl backend/admin-center -am

# 2. 复制新的 JAR 到 Docker 容器
docker cp backend/admin-center/target/admin-center-1.0.0.jar platform-admin-center-dev:/app/app.jar

# 3. 重启 Admin Center
docker restart platform-admin-center-dev

# 4. 等待启动完成（约 40 秒）
Start-Sleep -Seconds 40

# 5. 验证启动成功
docker logs platform-admin-center-dev --tail 5
```

### 第三步：添加数据库约束

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

### 第四步：同步 Action 定义

**文件**: `deploy/init-scripts/08-digital-lending-v2-en/sync-actions-sql.sql`

```sql
-- 功能单元 ID
\set function_unit_id '4737ac68-42c5-4571-972e-e7ad0c6c7253'

-- 清理已存在的 ACTION 内容
DELETE FROM sys_function_unit_contents 
WHERE function_unit_id = :'function_unit_id' 
AND content_type = 'ACTION';

-- 同步所有 action 定义
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
    :'function_unit_id',
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

**预期输出**:
```
DELETE 0
INSERT 0 15
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

### 第五步：重启 User Portal

```powershell
# 重启 User Portal
docker restart platform-user-portal-dev

# 等待启动完成（约 35 秒）
Start-Sleep -Seconds 35

# 验证启动成功
docker logs platform-user-portal-dev --tail 5
```

## 一键执行脚本

为了方便，可以使用一键脚本：

```powershell
.\deploy\init-scripts\08-digital-lending-v2-en\sync-all-contents.ps1
```

**注意**: 这个脚本只同步数据，不包括 Java 代码修改和编译。

## 验证步骤

### 1. 验证数据库内容

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

**预期结果**:
```
 content_type | count | total_size 
--------------+-------+------------
 ACTION       |    15 |       5103  ✅
 FORM         |     5 |       4133  ✅
 PROCESS      |     1 |      17081  ✅
```

### 2. 验证 User Portal

1. **访问**: http://localhost:3001
2. **清除缓存**: 按 `Ctrl+F5` 强制刷新
3. **登录**: 使用测试账号登录
4. **发起流程**: Digital Lending System V2 (EN)
5. **填写表单**: 填写 Loan Application Form
6. **提交**: 点击提交按钮
7. **查看任务**: 在"我的任务"中找到新创建的任务
8. **验证 Action 按钮**: 
   - ✅ 应该看到 action 按钮（Approve、Reject 等）
   - ✅ 按钮有正确的图标和颜色
   - ✅ 点击按钮可以触发操作

### 3. 检查浏览器控制台

按 `F12` 打开开发者工具，检查：
- **Console 标签**: 不应该有 500 错误
- **Network 标签**: 
  - 查找 `/api/v1/admin/function-units/{id}/contents?contentType=ACTION` 请求
  - 应该返回 200 状态码
  - 响应应该包含 15 个 action 定义

## 故障排除

### 问题 1: 仍然显示 "No enum constant" 错误

**原因**: Admin Center 未使用新的 JAR 文件

**解决方案**:
```powershell
# 1. 确认 JAR 文件已复制
docker exec platform-admin-center-dev ls -lh /app/app.jar

# 2. 重新复制并重启
docker cp backend/admin-center/target/admin-center-1.0.0.jar platform-admin-center-dev:/app/app.jar
docker restart platform-admin-center-dev

# 3. 等待启动
Start-Sleep -Seconds 40

# 4. 检查日志
docker logs platform-admin-center-dev --tail 20
```

### 问题 2: Action 定义未同步

**原因**: SQL 脚本执行失败或功能单元 ID 不正确

**解决方案**:
```sql
-- 1. 检查功能单元 ID
SELECT id, code, version, enabled 
FROM sys_function_units 
WHERE code = 'DIGITAL_LENDING_V2_EN' 
AND enabled = true;

-- 2. 更新脚本中的功能单元 ID（如果不同）
-- 编辑 sync-actions-sql.sql，修改 function_unit_id 变量

-- 3. 重新执行同步脚本
```

### 问题 3: 数据库约束错误

**错误信息**:
```
ERROR: new row for relation "sys_function_unit_contents" violates check constraint "chk_content_type"
```

**解决方案**:
```sql
-- 检查当前约束
SELECT conname, pg_get_constraintdef(oid) 
FROM pg_constraint 
WHERE conname = 'chk_content_type';

-- 如果约束不包含 ACTION，重新执行步骤三
```

### 问题 4: 编译失败

**可能原因**: 
- Maven 配置问题
- 依赖下载失败
- Java 版本不匹配

**解决方案**:
```powershell
# 1. 清理 Maven 缓存
mvn clean

# 2. 强制更新依赖
mvn clean package -DskipTests -U -pl backend/admin-center -am

# 3. 检查 Java 版本（需要 Java 17）
java -version
```

## 完整检查清单

在修复完成后，请确认以下所有项目：

- [ ] `ContentType.java` 包含 `ACTION` 枚举值
- [ ] Admin Center 已重新编译
- [ ] 新的 JAR 文件已复制到 Docker 容器
- [ ] Admin Center 已重启并成功启动
- [ ] 数据库约束包含 `ACTION` 类型
- [ ] 15 个 action 定义已同步到 `sys_function_unit_contents`
- [ ] User Portal 已重启并成功启动
- [ ] 浏览器缓存已清除
- [ ] User Portal 可以正常加载 action 定义
- [ ] 任务详情页面显示 action 按钮
- [ ] Action 按钮可以正常点击和执行

## 相关文件

### Java 代码
- `backend/admin-center/src/main/java/com/admin/enums/ContentType.java` - 枚举类定义

### SQL 脚本
- `deploy/init-scripts/08-digital-lending-v2-en/add-action-content-type.sql` - 添加约束
- `deploy/init-scripts/08-digital-lending-v2-en/sync-actions-sql.sql` - 同步 action 定义

### PowerShell 脚本
- `deploy/init-scripts/08-digital-lending-v2-en/sync-all-contents.ps1` - 一键同步脚本

### 文档
- `docs/ACTION_BUTTONS_DISPLAY_FIX.md` - 详细修复文档
- `docs/FORM_DISPLAY_FINAL_FIX_COMPLETE.md` - 表单显示修复文档

## 总结

这个问题需要同时修改：
1. **Java 代码** - 添加 ACTION 枚举值
2. **数据库约束** - 允许 ACTION 内容类型
3. **数据同步** - 同步 action 定义到 Admin Center
4. **服务重启** - 重启 Admin Center 和 User Portal

所有步骤都必须完成，缺一不可。

---

**修复完成时间**: 2026-02-08 08:47  
**状态**: 🟢 完全修复，等待用户验证
