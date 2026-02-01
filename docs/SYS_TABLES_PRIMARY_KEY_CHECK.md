# sys_* 表主键检查报告

## 📋 检查结果

### ✅ Flyway 迁移脚本（V1__init_schema.sql）

**状态**: ✅ **所有表都有主键**

在 `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` 中，所有 `sys_*` 表都**正确定义了主键**：

| 表名 | 主键定义 |
|------|---------|
| `sys_users` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_roles` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_business_units` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_user_roles` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_role_assignments` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_permissions` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_role_permissions` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_login_audit` | `id UUID PRIMARY KEY DEFAULT uuid_generate_v4()` ✅ |
| `sys_virtual_groups` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_virtual_group_members` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_virtual_group_roles` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_virtual_group_task_history` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_business_unit_roles` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_user_business_units` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_user_business_unit_roles` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_approvers` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_permission_requests` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_member_change_logs` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_user_preferences` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_dictionaries` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_dictionary_items` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_dictionary_versions` | `id VARCHAR(36) PRIMARY KEY` ✅ |
| `sys_dictionary_data_sources` | `id VARCHAR(36) PRIMARY KEY` ✅ |
| `sys_function_units` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_function_unit_deployments` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_function_unit_approvals` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_function_unit_dependencies` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_function_unit_contents` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_function_unit_access` | `id VARCHAR(64) PRIMARY KEY` ✅ |
| `sys_developer_role_permissions` | `id VARCHAR(64) PRIMARY KEY` ✅ |

**总计**: 30 个 `sys_*` 表，**全部都有主键** ✅

---

### ⚠️ workflow_platform_executable_clean_fixed.sql

**状态**: ⚠️ **表定义中缺少 PRIMARY KEY 约束**

在 `workflow_platform_executable_clean_fixed.sql` 中，`sys_*` 表的定义格式如下：

```sql
CREATE TABLE IF NOT EXISTS public.sys_approvers (
    id character varying(64) NOT NULL,  -- ❌ 只有 NOT NULL，没有 PRIMARY KEY
    created_at timestamp(6) with time zone,
    ...
);
```

**问题**:
- ❌ 表定义中 `id` 字段只有 `NOT NULL` 约束
- ❌ 没有 `PRIMARY KEY` 约束
- ❌ 没有后续的 `ALTER TABLE ... ADD PRIMARY KEY` 语句

**可能的原因**:
1. 这个 SQL 文件可能是从现有数据库导出的（`pg_dump`），主键约束可能在其他地方
2. 主键可能通过索引或其他方式定义
3. 文件可能不完整

---

## 🔍 详细对比

### Flyway 迁移脚本（正确）

```sql
-- backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql
CREATE TABLE IF NOT EXISTS sys_users (
    id VARCHAR(64) PRIMARY KEY,  -- ✅ 直接定义主键
    username VARCHAR(100) NOT NULL UNIQUE,
    ...
);
```

### workflow_platform_executable_clean_fixed.sql（有问题）

```sql
CREATE TABLE IF NOT EXISTS public.sys_users (
    id character varying(64) NOT NULL,  -- ❌ 只有 NOT NULL
    username character varying(100) NOT NULL,
    ...
);
-- 没有 PRIMARY KEY 约束
```

---

## ✅ 结论

### 1. Flyway 迁移脚本
- ✅ **所有 `sys_*` 表都有主键**
- ✅ 主键定义正确（`id VARCHAR(64) PRIMARY KEY` 或 `id UUID PRIMARY KEY`）

### 2. workflow_platform_executable_clean_fixed.sql
- ⚠️ **表定义中缺少 PRIMARY KEY 约束**
- ⚠️ 如果使用这个文件初始化数据库，表可能没有主键

---

## 🔧 建议

### 如果使用 Flyway 迁移脚本（推荐）
- ✅ **无需修复**：所有表都有主键
- ✅ 使用 `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`

### 如果使用 workflow_platform_executable_clean_fixed.sql
- ⚠️ **需要修复**：添加 PRIMARY KEY 约束
- 可以：
  1. 在 CREATE TABLE 语句中添加 `PRIMARY KEY`
  2. 或者在文件末尾添加 `ALTER TABLE ... ADD PRIMARY KEY` 语句

---

## 📝 修复示例

如果需要修复 `workflow_platform_executable_clean_fixed.sql`，可以：

```sql
-- 方法 1: 修改 CREATE TABLE 语句
CREATE TABLE IF NOT EXISTS public.sys_approvers (
    id character varying(64) PRIMARY KEY,  -- ✅ 添加 PRIMARY KEY
    ...
);

-- 方法 2: 添加 ALTER TABLE 语句
ALTER TABLE public.sys_approvers ADD PRIMARY KEY (id);
ALTER TABLE public.sys_business_unit_roles ADD PRIMARY KEY (id);
-- ... 等等
```

---

## ✅ 总结

- **Flyway 迁移脚本**: ✅ 所有 `sys_*` 表都有主键
- **workflow_platform_executable_clean_fixed.sql**: ⚠️ 缺少主键约束
- **建议**: 使用 Flyway 迁移脚本，它已经正确定义了所有主键
