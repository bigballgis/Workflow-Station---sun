# Flyway 脚本分析：V5 和 V6

生成时间：2026-01-31

## 脚本概述

### V5__assign_developer_roles_to_dev_users.sql

**目的：** 为开发用户分配 Developer 角色，修复 403 错误

**操作：**
1. 在 `sys_role_assignments` 表中插入角色分配记录
2. 同步到 `sys_user_roles` 表

**影响用户：**
- `developer` (635281da-5dbb-4118-9610-dd4d6318dcd6) → DEVELOPER_ROLE
- `dev_lead` (b4fe69e8-7313-48c5-865b-878231c24b9f) → TEAM_LEADER_ROLE
- `senior_dev` (7e468949-05ea-4c41-8ab5-484fb0626185) → DEVELOPER_ROLE

### V6__assign_developer_role_to_adam.sql

**目的：** 为用户 Adam 分配 Developer 角色，使其能够创建功能单元

**操作：**
1. 在 `sys_role_assignments` 表中插入角色分配记录
2. 同步到 `sys_user_roles` 表

**影响用户：**
- `Adam` (bfe0805e-adcc-43cd-9c07-c368f3b947fb) → DEVELOPER_ROLE

## 当前数据库状态

### ✅ V5 脚本的效果已存在

| 用户 | User ID | 当前角色 | 状态 |
|------|---------|---------|------|
| developer | 635281da-5dbb-4118-9610-dd4d6318dcd6 | DEVELOPER_ROLE | ✅ 已存在 |
| dev_lead | b4fe69e8-7313-48c5-865b-878231c24b9f | TEAM_LEADER_ROLE | ✅ 已存在 |
| senior_dev | 7e468949-05ea-4c41-8ab5-484fb0626185 | DEVELOPER_ROLE | ✅ 已存在 |

### ✅ V6 脚本的效果已存在

| 用户 | User ID | 当前角色 | 状态 |
|------|---------|---------|------|
| Adam | bfe0805e-adcc-43cd-9c07-c368f3b947fb | DEVELOPER_ROLE | ✅ 已存在 |

### sys_role_assignments 表状态

```sql
ra-developer-developer  → DEVELOPER_ROLE   → USER → 635281da... ✅
ra-devlead-teamleader   → TEAM_LEADER_ROLE → USER → b4fe69e8... ✅
ra-seniordev-developer  → DEVELOPER_ROLE   → USER → 7e468949... ✅
ra-adam-developer       → DEVELOPER_ROLE   → USER → bfe0805e... ✅
```

## 分析结论

### 🎯 是否需要执行这两个脚本？

**答案：不需要执行，但建议保留**

### 理由

1. **数据已存在**
   - 所有用户的角色分配已经在数据库中
   - `sys_role_assignments` 和 `sys_user_roles` 表都有对应记录
   - 脚本使用了 `ON CONFLICT DO NOTHING`，重复执行不会出错

2. **脚本是幂等的**
   - V5 和 V6 都使用了 `ON CONFLICT` 子句
   - 即使执行也不会产生副作用
   - 不会创建重复记录

3. **数据来源**
   - 这些数据可能是通过以下方式创建的：
     - JPA 自动创建（通过应用代码）
     - 手动 SQL 插入
     - 其他初始化脚本（如 V2__init_data.sql）

### 📋 建议

#### 选项 A：保留脚本但不执行（推荐）

**优点：**
- 保持 Flyway 脚本的完整性
- 记录了角色分配的历史
- 新环境部署时会自动执行

**操作：**
- 不需要任何操作
- 数据已存在，脚本幂等

#### 选项 B：删除脚本

**不推荐，原因：**
- 丢失了角色分配的历史记录
- 新环境部署时需要手动分配角色
- 破坏了 Flyway 版本的连续性

#### 选项 C：启用 Flyway 并执行所有脚本（最佳实践）

**推荐用于生产环境：**

1. 启用 platform-security 模块的 Flyway
2. 设置 `baseline-on-migrate: true`
3. 让 Flyway 执行所有迁移脚本
4. 脚本会自动跳过已存在的数据（因为 `ON CONFLICT DO NOTHING`）

## 脚本安全性分析

### ✅ V5 脚本安全性

```sql
-- 使用 ON CONFLICT DO NOTHING，不会创建重复记录
INSERT INTO sys_role_assignments (...)
VALUES (...)
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- 使用 NOT EXISTS 检查，不会创建重复记录
INSERT INTO sys_user_roles (...)
WHERE NOT EXISTS (...);
```

**结论：** 安全，可以重复执行

### ✅ V6 脚本安全性

```sql
-- 使用 ON CONFLICT DO NOTHING
INSERT INTO sys_role_assignments (...)
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- 使用 WHERE NOT EXISTS
INSERT INTO sys_user_roles (...)
WHERE NOT EXISTS (...);
```

**结论：** 安全，可以重复执行

## 测试验证

### 验证脚本可以安全执行

```bash
# 1. 备份当前数据
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform -t sys_role_assignments -t sys_user_roles > backup_roles.sql

# 2. 执行 V5 脚本（测试）
docker exec -i platform-postgres psql -U platform -d workflow_platform << 'EOF'
BEGIN;

-- V5 脚本内容
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at, assigned_by)
VALUES
    ('ra-developer-developer', 'DEVELOPER_ROLE', 'USER', '635281da-5dbb-4118-9610-dd4d6318dcd6', NOW(), 'system'),
    ('ra-devlead-teamleader', 'TEAM_LEADER_ROLE', 'USER', 'b4fe69e8-7313-48c5-865b-878231c24b9f', NOW(), 'system'),
    ('ra-seniordev-developer', 'DEVELOPER_ROLE', 'USER', '7e468949-05ea-4c41-8ab5-484fb0626185', NOW(), 'system')
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- 检查结果
SELECT 'V5 executed successfully' as result;

ROLLBACK;  -- 回滚测试
EOF

# 3. 执行 V6 脚本（测试）
docker exec -i platform-postgres psql -U platform -d workflow_platform << 'EOF'
BEGIN;

-- V6 脚本内容
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at, assigned_by)
VALUES ('ra-adam-developer', 'DEVELOPER_ROLE', 'USER', 'bfe0805e-adcc-43cd-9c07-c368f3b947fb', NOW(), 'system')
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- 检查结果
SELECT 'V6 executed successfully' as result;

ROLLBACK;  -- 回滚测试
EOF
```

## 相关脚本

### 其他未执行的 platform-security 迁移脚本

| 脚本 | 状态 | 说明 |
|------|------|------|
| V2__fix_user_status_constraint.sql | ❌ 未执行 | 修复用户状态约束 |
| V2__init_data.sql | ❌ 未执行 | 初始化数据 |
| V3__ensure_sys_login_audit.sql | ❌ 未执行 | 确保登录审计表 |
| V4__add_developer_function_unit_create.sql | ❌ 未执行 | 添加功能单元创建权限 |
| V5__assign_developer_roles_to_dev_users.sql | ❌ 未执行 | **本脚本** |
| V6__assign_developer_role_to_adam.sql | ❌ 未执行 | **本脚本** |
| V7__add_developer_function_unit_delete.sql | ❌ 未执行 | 添加功能单元删除权限 |
| V8__sync_developers_vg_to_sys_user_roles.sql | ❌ 未执行 | 同步开发者虚拟组 |
| V9__add_developer_function_unit_publish.sql | ❌ 未执行 | 添加功能单元发布权限 |

## 最终建议

### 🎯 推荐方案

**保留 V5 和 V6 脚本，启用 Flyway 并执行所有迁移脚本**

### 执行步骤

1. **备份数据库**
   ```bash
   docker exec -i platform-postgres pg_dump -U platform -d workflow_platform > backup_before_flyway.sql
   ```

2. **启用 platform-security 的 Flyway**
   
   修改 `backend/platform-security/src/main/resources/application.yml`：
   ```yaml
   spring:
     flyway:
       enabled: true
       baseline-on-migrate: true
       baseline-version: 0
   ```

3. **重启服务**
   ```bash
   # 停止 platform-security 相关服务
   # 启动服务，Flyway 会自动执行所有迁移脚本
   ```

4. **验证执行结果**
   ```bash
   docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
   SELECT installed_rank, version, description, success 
   FROM flyway_schema_history 
   WHERE script LIKE '%platform-security%'
   ORDER BY installed_rank;
   "
   ```

### ✅ 预期结果

- V5 和 V6 会被执行
- 由于 `ON CONFLICT DO NOTHING`，不会创建重复记录
- 所有用户的角色分配保持不变
- Flyway 历史记录完整

## 总结

| 问题 | 答案 |
|------|------|
| V5 和 V6 是否必要执行？ | 数据已存在，技术上不必要 |
| 执行会有问题吗？ | 不会，脚本是幂等的 |
| 应该删除这些脚本吗？ | 不应该，保留以维护历史 |
| 最佳实践是什么？ | 启用 Flyway，让它管理所有迁移 |

**结论：保留脚本，启用 Flyway，让系统自动管理数据库版本。**
