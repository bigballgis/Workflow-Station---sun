# 数据库同步指南 - DBeaver 连接后

生成时间: 2026-01-18

本指南将帮助您在迁移到新电脑后，使用 DBeaver 连接到 PostgreSQL 数据库，并让数据库结构与代码保持一致。

---

## 📋 目录

1. [数据库连接信息](#数据库连接信息)
2. [方法一：使用 Flyway 自动迁移（可选）](#方法一使用-flyway-自动迁移可选)
3. [方法二：手动执行 SQL 脚本（推荐，不依赖 Flyway）](#方法二手动执行-sql-脚本推荐不依赖-flyway)
4. [方法三：从备份恢复（如果有备份）](#方法三从备份恢复如果有备份)
5. [验证数据库结构](#验证数据库结构)
6. [常见问题](#常见问题)

> **💡 快速选择**：
> - **不想用 Flyway？** → 直接使用 [方法二](#方法二手动执行-sql-脚本推荐不依赖-flyway)，在 DBeaver 中手动执行 SQL 脚本即可
> - **想自动化？** → 使用 [方法一](#方法一使用-flyway-自动迁移可选)，让应用启动时自动执行

---

## 数据库连接信息

根据项目配置，数据库连接信息如下：

- **数据库名**: `workflow_platform`
- **用户名**: `platform`
- **密码**: `platform123`
- **主机**: `localhost`
- **端口**: `5432`
- **JDBC URL**: `jdbc:postgresql://localhost:5432/workflow_platform`
- **Schema**: `public` (默认 schema，无需创建)

### 关于 Schema

**重要说明**：
- ✅ 本项目使用 PostgreSQL 的默认 `public` schema
- ✅ **不需要**创建额外的 schema
- ✅ 所有表都创建在 `public` schema 中
- ✅ PostgreSQL 默认会自动创建 `public` schema

### 检查当前 Schema

在 DBeaver 中执行以下 SQL 检查当前 schema：

```sql
-- 1. 查看当前数据库的所有 schema
SELECT schema_name 
FROM information_schema.schemata 
WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
ORDER BY schema_name;

-- 应该看到至少有一个 'public' schema

-- 2. 查看当前连接的默认 schema
SHOW search_path;

-- 应该显示: "$user", public

-- 3. 查看 public schema 中的所有表
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
```

### 在 DBeaver 中创建连接

1. 打开 DBeaver
2. 点击 "新建数据库连接" 或 `Ctrl+Shift+N`
3. 选择 **PostgreSQL**
4. 填写连接信息：
   - **主机**: `localhost`
   - **端口**: `5432`
   - **数据库**: `workflow_platform`
   - **用户名**: `platform`
   - **密码**: `platform123`
   - **默认 Schema**: `public` (可选，DBeaver 会自动识别)
5. 点击 "测试连接" 确认连接成功
6. 点击 "完成" 保存连接

### 在 DBeaver 中查看 Schema

连接成功后，在 DBeaver 的数据库导航树中：
- 展开 `workflow_platform` 数据库
- 展开 `Schemas` 节点
- 您应该能看到 `public` schema（如果看不到，刷新连接）
- 展开 `public` → `Tables` 可以看到所有表

---

## 方法一：使用 Flyway 自动迁移（可选）

> **注意**: 如果您不想使用 Flyway，可以直接跳到 [方法二：手动执行 SQL 脚本](#方法二手动执行-sql-脚本)，该方法完全不依赖 Flyway。

这是最简单的方法，让 Spring Boot 应用启动时自动执行 Flyway 迁移。

### 步骤 1：检查 Flyway 配置

查看各模块的 `application.yml` 文件，确认 Flyway 是否启用：

| 模块 | Flyway 状态 | 迁移脚本位置 |
|------|-----------|-------------|
| `platform-security` | 需要启用 | `backend/platform-security/src/main/resources/db/migration/` |
| `workflow-engine-core` | ✅ 已启用 | `backend/workflow-engine-core/src/main/resources/db/migration/` |
| `admin-center` | 需要启用 | `backend/admin-center/src/main/resources/db/migration/` |
| `developer-workstation` | 需要启用 | `backend/developer-workstation/src/main/resources/db/migration/` |
| `user-portal` | 需要启用 | `backend/user-portal/src/main/resources/db/migration/` |

### 步骤 2：启用 Flyway

编辑各模块的 `application.yml`，将 `flyway.enabled` 设置为 `true`：

**示例：`backend/platform-security/src/main/resources/application.yml`**

```yaml
spring:
  flyway:
    enabled: true  # 改为 true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

### 步骤 3：启动应用

按以下顺序启动后端服务：

1. **platform-security** (基础模块，必须先启动)
2. **workflow-engine-core**
3. **admin-center**
4. **developer-workstation**
5. **user-portal**

每个服务启动时，Flyway 会自动执行迁移脚本，创建或更新数据库结构。

### 步骤 4：验证

在 DBeaver 中检查表是否已创建：

```sql
-- 查看所有表
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- 检查 Flyway 迁移历史（如果使用 Flyway）
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

---

## 方法二：手动执行 SQL 脚本（推荐，不依赖 Flyway）

**✅ 这是最简单直接的方法，完全不依赖 Flyway，适合快速同步数据库结构。**

### 优点

- ✅ **不需要**启动应用
- ✅ **不需要**配置 Flyway
- ✅ **不需要**创建 Flyway 历史表
- ✅ 直接在 DBeaver 中执行，直观可控
- ✅ 可以随时查看和修改 SQL 脚本

### 步骤 1：找到所有 SQL 迁移脚本

迁移脚本位置（这些是 Flyway 脚本，但我们可以直接手动执行）：

```
backend/
├── platform-security/src/main/resources/db/migration/
│   ├── V1__init_schema.sql  ← 核心系统表（必须最先执行）
│   └── V2__init_data.sql    ← 初始数据（可选）
├── workflow-engine-core/src/main/resources/db/migration/
│   └── V1__init_schema.sql  ← 工作流引擎表
├── admin-center/src/main/resources/db/migration/
│   └── V1__init_schema.sql  ← 管理后台表
├── developer-workstation/src/main/resources/db/migration/
│   ├── V1__init_schema.sql  ← 开发者工作站表
│   ├── V2__init_data.sql    ← 初始数据（可选）
│   └── V3__init_process.sql ← 流程相关（可选）
└── user-portal/src/main/resources/db/migration/
    └── V1__init_schema.sql  ← 用户门户表
```

### 步骤 2：在 DBeaver 中执行脚本

**⚠️ 重要：必须按以下顺序执行！**

#### 2.1 执行 platform-security 模块（第一步，必须最先执行）

1. 在 DBeaver 中，打开 SQL 编辑器（`Ctrl+\` 或点击工具栏的 SQL 编辑器图标）
2. 打开文件：`backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`
3. 全选内容（`Ctrl+A`）
4. 执行（`F5` 或 `Ctrl+Enter`）
5. 检查执行结果，确保没有错误

**执行内容**：
- 创建所有 `sys_*` 表（sys_users, sys_roles, sys_permissions 等）
- 创建索引和约束

**可选**：如果需要初始数据，执行 `V2__init_data.sql`

#### 2.2 执行 workflow-engine-core 模块

1. 打开文件：`backend/workflow-engine-core/src/main/resources/db/migration/V1__init_schema.sql`
2. 全选并执行
3. 检查执行结果

**执行内容**：
- 创建工作流相关表（wf_*, act_*）

#### 2.3 执行 admin-center 模块

1. 打开文件：`backend/admin-center/src/main/resources/db/migration/V1__init_schema.sql`
2. 全选并执行
3. 检查执行结果

**执行内容**：
- 创建管理后台相关表（admin_*）

#### 2.4 执行 developer-workstation 模块

1. 打开文件：`backend/developer-workstation/src/main/resources/db/migration/V1__init_schema.sql`
2. 全选并执行
3. 检查执行结果

**执行内容**：
- 创建开发者工作站表（dw_*）

**可选**：
- 如果需要初始数据，执行 `V2__init_data.sql`
- 如果需要流程数据，执行 `V3__init_process.sql`

#### 2.5 执行 user-portal 模块

1. 打开文件：`backend/user-portal/src/main/resources/db/migration/V1__init_schema.sql`
2. 全选并执行
3. 检查执行结果

**执行内容**：
- 创建用户门户相关表

### 步骤 3：验证执行结果

在 DBeaver 中执行以下 SQL 验证：

```sql
-- 1. 查看所有已创建的表
SELECT table_schema, table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_schema, table_name;

-- 2. 检查核心表是否存在
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name IN ('sys_users', 'sys_roles', 'sys_permissions')
ORDER BY table_name;

-- 3. 检查表数量（应该有几十个表）
SELECT COUNT(*) as table_count
FROM information_schema.tables 
WHERE table_schema = 'public';
```

### 步骤 4：禁用 Flyway（可选）

如果您确定不使用 Flyway，可以在各模块的 `application.yml` 中禁用：

```yaml
spring:
  flyway:
    enabled: false  # 禁用 Flyway
```

这样应用启动时就不会尝试执行 Flyway 迁移了。

### 常见问题

**Q: 执行脚本时出现 "relation already exists" 错误？**

A: 表已经存在，可以：
- 如果表结构正确，忽略此错误
- 如果需要重新创建，先删除：`DROP TABLE IF EXISTS table_name CASCADE;`

**Q: 执行脚本时出现 "constraint already exists" 错误？**

A: 约束已存在，可以忽略或删除后重新执行：
```sql
ALTER TABLE table_name DROP CONSTRAINT IF EXISTS constraint_name;
```

**Q: 如何知道哪些脚本已经执行过？**

A: 检查表是否存在：
```sql
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
```

---

## 方法三：从备份恢复（如果有备份）

如果您有数据库备份文件（`.sql` 或 `.dump`），可以直接恢复。

### 使用 DBeaver 恢复

1. 在 DBeaver 中，右键点击数据库连接
2. 选择 **工具** → **执行脚本**
3. 选择备份文件（`.sql`）
4. 点击 "开始" 执行

### 使用命令行恢复

**Windows PowerShell**:

```powershell
# 使用 psql 恢复
psql -h localhost -U platform -d workflow_platform -f workflow_platform_backup.sql

# 或者使用 pg_restore（如果是 .dump 文件）
pg_restore -h localhost -U platform -d workflow_platform workflow_platform_backup.dump
```

---

## 验证数据库结构

### 1. 检查核心表是否存在

```sql
-- 检查平台安全相关表
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name LIKE 'sys_%'
ORDER BY table_name;

-- 应该看到以下表：
-- sys_users, sys_roles, sys_permissions, sys_virtual_groups, 
-- sys_function_units, sys_function_unit_contents, 等
```

### 2. 检查工作流引擎表

```sql
-- 检查工作流引擎表
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND (table_name LIKE 'wf_%' OR table_name LIKE 'act_%')
ORDER BY table_name;

-- 应该看到：
-- wf_extended_task_info, act_ru_execution, act_ru_task, 等
```

### 3. 检查开发者工作站表

```sql
-- 检查开发者工作站表
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name LIKE 'dw_%'
ORDER BY table_name;

-- 应该看到：
-- dw_function_units, dw_table_definitions, dw_form_definitions, 等
```

### 4. 检查表结构

```sql
-- 检查某个表的结构（例如 sys_users）
SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'sys_users'
ORDER BY ordinal_position;
```

### 5. 检查约束

```sql
-- 检查表的约束
SELECT 
    tc.constraint_name,
    tc.table_name,
    tc.constraint_type,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'public'
  AND tc.table_name = 'sys_users'
ORDER BY tc.constraint_type, tc.constraint_name;
```

---

## 常见问题

### Q1: 执行 SQL 脚本时出现 "relation already exists" 错误

**原因**: 表已经存在

**解决方案**:
- 如果表结构正确，可以忽略此错误
- 如果需要重新创建，先删除表：
  ```sql
  DROP TABLE IF EXISTS table_name CASCADE;
  ```

### Q2: 执行 SQL 脚本时出现 "constraint already exists" 错误

**原因**: 约束已经存在

**解决方案**:
```sql
-- 删除约束后重新执行
ALTER TABLE table_name DROP CONSTRAINT IF EXISTS constraint_name;
```

### Q3: 如何知道哪些脚本已经执行过？

**解决方案**:
```sql
-- 查看 Flyway 历史
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- 或者检查表是否存在
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
```

### Q4: 数据库结构与代码不一致怎么办？

**解决方案**:
1. 检查 Flyway 迁移脚本是否是最新的
2. 比较数据库实际结构与 Flyway 脚本
3. 创建新的迁移脚本（V2, V3...）来修复差异
4. 或者手动执行 ALTER TABLE 语句修复

### Q5: 如何重置数据库？

**⚠️ 警告：这会删除所有数据！**

```sql
-- 1. 断开所有连接
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'workflow_platform' AND pid <> pg_backend_pid();

-- 2. 删除所有表
DO $$ 
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END $$;

-- 3. 删除 Flyway 历史
DROP TABLE IF EXISTS flyway_schema_history;

-- 4. 重新执行迁移脚本（方法二）
```

---

## 快速检查清单

### 如果使用 Flyway（方法一）
- [ ] DBeaver 已连接到 `workflow_platform` 数据库
- [ ] 已启用各模块的 Flyway 配置
- [ ] 已按顺序启动后端服务
- [ ] 核心表（sys_users, sys_roles 等）已创建
- [ ] 工作流表（wf_*, act_*）已创建
- [ ] 开发者工作站表（dw_*）已创建
- [ ] 可以正常启动后端服务

### 如果手动执行（方法二，推荐）
- [ ] DBeaver 已连接到 `workflow_platform` 数据库
- [ ] 已执行 `platform-security/V1__init_schema.sql`（第一步，必须最先执行）
- [ ] 已执行 `workflow-engine-core/V1__init_schema.sql`
- [ ] 已执行 `admin-center/V1__init_schema.sql`
- [ ] 已执行 `developer-workstation/V1__init_schema.sql`
- [ ] 已执行 `user-portal/V1__init_schema.sql`
- [ ] 已验证所有表已创建（使用验证 SQL）
- [ ] 核心表（sys_users, sys_roles 等）已创建
- [ ] 工作流表（wf_*, act_*）已创建
- [ ] 开发者工作站表（dw_*）已创建
- [ ] 已禁用 Flyway（可选，如果确定不使用）
- [ ] 可以正常启动后端服务

---

## 下一步

数据库结构同步完成后：

1. **启动后端服务**，验证连接是否正常
2. **启动前端服务**，测试功能是否正常
3. **导入测试数据**（如果需要）
4. **运行测试**，确保一切正常

---

## 相关文档

- [系统迁移指南 (Windows)](./system-migration-guide-windows.md)
- [Flyway 代码一致性报告](./flyway-code-consistency-report.md)
- [开发细则指南](../.kiro/steering/development-guidelines.md)

---

**最后更新**: 2026-01-18
