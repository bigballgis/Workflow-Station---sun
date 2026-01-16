# 数据库与 Flyway 脚本差异报告

**生成时间**: 2026-01-12  
**更新时间**: 2026-01-12  
**数据库**: workflow_platform (PostgreSQL)  
**比较范围**: platform-security 和 admin-center 相关的 sys_* 和 admin_* 表

---

## 差异汇总

| 表名 | 差异类型 | 严重程度 | 状态 |
|------|----------|----------|------|
| sys_departments | 已废弃 | - | ✅ 已迁移到 sys_business_units |
| sys_permissions | 数据库多出字段 | 中 | ✅ 已修复 - 更新 Flyway |
| sys_role_permissions | 数据库多出字段 | 中 | ✅ 已修复 - 更新 Flyway |
| sys_role_assignments | 字段长度不一致 | 低 | ✅ 已修复 - 改为 VARCHAR(20) |
| sys_virtual_group_members | 数据库多出字段 | 低 | ✅ 已修复 - 删除 added_at |
| sys_virtual_group_task_history | 数据库多出字段 | 高 | ✅ 已修复 - 更新 Flyway |
| admin_permission_delegations | 数据库多出字段 | 高 | ✅ 已修复 - 删除旧版字段 |
| admin_password_history | 数据库缺少约束 | 低 | ✅ 已修复 - 添加约束和索引 |

---

## 修复详情

### 1. sys_departments - ✅ 已废弃

**决定**: 该表已废弃，数据已迁移到 `sys_business_units` 表

**说明**: 
- 原 `sys_departments` 表已被 `sys_business_units` 表替代
- 数据已通过 Flyway V3 迁移脚本迁移
- 可通过 `deploy/init-scripts/99-utilities/01-cleanup-old-tables.sql` 删除旧表

---

### 2. sys_permissions - ✅ 已修复

**决定**: 保留 `parent_id` 和 `sort_order` 字段（用于权限树结构）

**修复**: 更新 `platform-security/V1__create_auth_tables.sql`，添加字段定义

---

### 3. sys_role_permissions - ✅ 已修复

**决定**: 保留条件权限字段（`condition_type`, `condition_value`, `granted_at`, `granted_by`）

**修复**: 更新 `platform-security/V1__create_auth_tables.sql`，添加字段定义

---

### 4. sys_role_assignments - ✅ 已修复

**决定**: 以数据库为准，使用 VARCHAR(20)

**修复**: 更新 `platform-security/V3__create_role_assignments_table.sql`

---

### 5. sys_virtual_group_members - ✅ 已修复

**决定**: 删除 `added_at`，保留 `joined_at`（代码只使用 joined_at）

**修复脚本**: `platform-security/V4__cleanup_schema_inconsistencies.sql`
```sql
ALTER TABLE sys_virtual_group_members DROP COLUMN IF EXISTS added_at;
```

---

### 6. sys_virtual_group_task_history - ✅ 已修复

**决定**: 保留所有额外字段（代码中都有使用）

字段用途：
- `action_type`: 操作类型（CREATED/ASSIGNED/CLAIMED/DELEGATED/COMPLETED/CANCELLED/RETURNED）
- `from_user_id`: 操作发起者
- `to_user_id`: 操作接收者
- `reason`: 操作原因
- `comment`: 备注信息
- `created_at`: 记录创建时间

**修复**: 更新 `platform-security/V1__create_auth_tables.sql`，添加字段定义和约束

---

### 7. admin_permission_delegations - ✅ 已修复

**决定**: 删除旧版字段（`permission_type`, `resource_type`, `resource_id`, `start_time`, `end_time`）

**修复脚本**: `admin-center/V13__cleanup_schema_inconsistencies.sql`
```sql
ALTER TABLE admin_permission_delegations DROP COLUMN IF EXISTS permission_type;
ALTER TABLE admin_permission_delegations DROP COLUMN IF EXISTS resource_type;
ALTER TABLE admin_permission_delegations DROP COLUMN IF EXISTS resource_id;
ALTER TABLE admin_permission_delegations DROP COLUMN IF EXISTS start_time;
ALTER TABLE admin_permission_delegations DROP COLUMN IF EXISTS end_time;
```

---

### 8. admin_password_history - ✅ 已修复

**决定**: 添加外键约束和索引（代码中按 user_id 查询）

**修复脚本**: `admin-center/V13__cleanup_schema_inconsistencies.sql`
```sql
ALTER TABLE admin_password_history 
ADD CONSTRAINT fk_password_history_user 
FOREIGN KEY (user_id) REFERENCES sys_users(id);

CREATE INDEX IF NOT EXISTS idx_password_history_user ON admin_password_history(user_id);
```

---

## 新增迁移脚本

### platform-security/V4__cleanup_schema_inconsistencies.sql
- 删除 sys_virtual_group_members.added_at
- 添加索引和注释

### admin-center/V13__cleanup_schema_inconsistencies.sql
- 删除 admin_permission_delegations 旧版字段
- 添加 admin_password_history 外键和索引
- 添加 CHECK 约束

---

## 执行说明

这些修复脚本会在下次服务启动时由 Flyway 自动执行。

如需手动执行，可以运行：
```bash
# 重启服务让 Flyway 执行迁移
docker-compose restart admin-center
docker-compose restart platform-security
```

或直接在数据库中执行 SQL 脚本。

---

## 相关文件

**已更新的 Flyway 脚本**:
- `backend/platform-security/src/main/resources/db/migration/V1__create_auth_tables.sql`
- `backend/platform-security/src/main/resources/db/migration/V3__create_role_assignments_table.sql`

**新增的清理脚本**:
- `backend/platform-security/src/main/resources/db/migration/V4__cleanup_schema_inconsistencies.sql`
- `backend/admin-center/src/main/resources/db/migration/V13__cleanup_schema_inconsistencies.sql`
