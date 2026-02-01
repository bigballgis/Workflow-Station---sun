# 数据库与 Flyway 脚本对比报告

生成时间：2026-01-31

## 执行摘要

本报告对比了当前数据库中的实际表结构与 Flyway 迁移脚本中定义的表结构。

### 总体统计

| 类别 | 数据库实际表数 | Flyway 脚本表数 | 差异 | 状态 |
|------|---------------|----------------|------|------|
| Platform Security (sys_*) | 30 | 30 | 0 | ✅ 完全匹配 |
| Developer Workstation (dw_*) | 11 | 11 | 0 | ✅ 完全匹配 |
| Admin Center (admin_*) | 14 | 14 | 0 | ✅ 完全匹配 |
| User Portal (up_*) | 10 | 10 | 0 | ✅ 完全匹配 |
| Workflow Engine (wf_*) | 4 | 4 | 0 | ✅ 完全匹配 |
| Flowable (act_*) | 62 | N/A | - | ✅ 自动管理 |
| Flowable (flw_*) | 8 | N/A | - | ✅ 自动管理 |
| Other (flyway_schema_history) | 1 | N/A | - | ✅ Flyway 元数据 |

**总计：140 张表**

## 详细分析

### 1. Platform Security 模块 (sys_*)

**数据库实际表（30张）：**
```sql
sys_approvers
sys_business_unit_roles
sys_business_units
sys_developer_role_permissions
sys_dictionaries
sys_dictionary_data_sources
sys_dictionary_items
sys_dictionary_versions
sys_function_unit_access
sys_function_unit_approvals
sys_function_unit_contents
sys_function_unit_dependencies
sys_function_unit_deployments
sys_function_units
sys_login_audit
sys_member_change_logs
sys_permission_requests
sys_permissions
sys_role_assignments
sys_role_permissions
sys_roles
sys_user_business_unit_roles
sys_user_business_units
sys_user_preferences
sys_user_roles
sys_users
sys_virtual_group_members
sys_virtual_group_roles
sys_virtual_group_task_history
sys_virtual_groups
```

**Flyway 脚本：**
- 位置：`backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`
- 定义表数：30

**差异分析：**
- ✅ 完全匹配！数据库表与 Flyway V1 脚本一致

**后续迁移脚本：**
- V2__fix_user_status_constraint.sql
- V2__init_data.sql
- V3__ensure_sys_login_audit.sql
- V4__add_developer_function_unit_create.sql
- V5__assign_developer_roles_to_dev_users.sql
- V6__assign_developer_role_to_adam.sql
- V7__add_developer_function_unit_delete.sql
- V8__sync_developers_vg_to_sys_user_roles.sql
- V9__add_developer_function_unit_publish.sql

### 2. Developer Workstation 模块 (dw_*)

**数据库实际表（11张）：**
```sql
dw_action_definitions
dw_field_definitions
dw_foreign_keys
dw_form_definitions
dw_form_table_bindings
dw_function_units
dw_icons
dw_operation_logs
dw_process_definitions
dw_table_definitions
dw_versions
```

**Flyway 脚本：**
- 位置：`backend/developer-workstation/src/main/resources/db/migration/V1__init_schema.sql`
- 定义表数：11

**差异分析：**
- ✅ 完全匹配！数据库表与 Flyway V1 脚本一致

**后续迁移脚本：**
- V2__fix_form_table_bindings_constraint.sql
- V2__init_data.sql
- V3__init_process.sql
- V4__assign_adam_developer_role.sql
- V5__sync_developers_vg_to_sys_user_roles.sql

### 3. Admin Center 模块 (admin_*)

**数据库实际表（14张）：**
```sql
admin_alert_rules
admin_alerts
admin_audit_logs
admin_column_permissions
admin_config_history
admin_data_permission_rules
admin_log_retention_policies
admin_password_history
admin_permission_change_history
admin_permission_conflicts
admin_permission_delegations
admin_security_policies
admin_system_configs
admin_system_logs
```

**Flyway 脚本：**
- 位置：`backend/admin-center/src/main/resources/db/migration/V1__init_schema.sql`
- 定义表数：14

**差异分析：**
- ✅ 完全匹配！数据库表与 Flyway V1 脚本一致

### 4. User Portal 模块 (up_*)

**数据库实际表（10张）：**
```sql
up_dashboard_layout
up_delegation_audit
up_delegation_rule
up_favorite_process
up_notification_preference
up_permission_request
up_process_draft
up_process_history
up_process_instance
up_user_preference
```

**Flyway 脚本：**
- 位置：`backend/user-portal/src/main/resources/db/migration/V1__init_schema.sql`
- 定义表数：10

**差异分析：**
- ✅ 完全匹配！数据库表与 Flyway V1 脚本一致

### 5. Workflow Engine 模块 (wf_*)

**数据库实际表（4张）：**
```sql
wf_audit_logs
wf_exception_records
wf_extended_task_info
wf_process_variables
```

**Flyway 脚本：**
- 位置：`backend/workflow-engine-core/src/main/resources/db/migration/V1__init_schema.sql`
- 定义表数：4

**差异分析：**
- ✅ 完全匹配！数据库表与 Flyway V1 脚本一致

**后续迁移脚本：**
- V2__fix_binary_value_type.sql

### 6. Flowable 引擎表

**Flowable 表（70张）：**
- act_* 表：62 张（流程引擎核心表）
- flw_* 表：8 张（事件引擎表）

**说明：**
- ✅ 这些表由 Flowable 引擎自动管理
- ✅ 使用 Liquibase 进行版本控制
- ✅ 不需要在项目 Flyway 脚本中定义

### 7. Flyway 元数据表

- `flyway_schema_history` - Flyway 版本控制表

## Flyway 执行历史

| Rank | Version | Description | Script | Installed On | Success |
|------|---------|-------------|--------|--------------|---------|
| 1 | 0 | Initial baseline | Initial baseline | 2026-01-26 00:53:24 | ✅ |
| 2 | 1 | init schema | V1__init_schema.sql | 2026-01-26 00:53:27 | ✅ |
| 3 | 2 | fix binary value type | V2__fix_binary_value_type.sql | 2026-01-26 00:53:27 | ✅ |

**注意：** 只有 workflow-engine-core 模块的 Flyway 被执行了！

## 问题与建议

### ✅ 好消息

**所有模块的数据库表与 Flyway V1 脚本完全匹配！**

- Platform Security: 30/30 ✅
- Developer Workstation: 11/11 ✅
- Admin Center: 14/14 ✅
- User Portal: 10/10 ✅
- Workflow Engine: 4/4 ✅

### 🔴 严重问题

1. **Flyway 未在所有模块启用**
   - 当前只有 `workflow-engine-core` 模块的 Flyway 被执行
   - 其他模块（platform-security, developer-workstation, admin-center, user-portal）的 Flyway 脚本未执行
   - 这导致数据库结构完全依赖 JPA 的 `ddl-auto=update`

2. **缺少版本控制**
   - 虽然表结构与 V1 脚本匹配，但这些表是通过 JPA 自动创建的
   - 没有 Flyway 执行记录，无法追踪变更历史
   - 生产环境部署时可能出现问题

### ⚠️ 警告

1. **JPA ddl-auto=update 的风险**
   - 当前配置依赖 JPA 自动创建表
   - 生产环境不推荐使用 `ddl-auto=update`
   - 可能导致数据丢失或结构不一致

2. **后续迁移脚本未执行**
   - Platform Security 有 V2-V9 迁移脚本未执行
   - Developer Workstation 有 V2-V5 迁移脚本未执行
   - 这些脚本中的数据初始化和结构调整未应用

### ✅ 建议方案

#### 方案 1：启用所有模块的 Flyway（推荐）

1. **修改各模块配置**
   ```yaml
   spring:
     flyway:
       enabled: true
       baseline-on-migrate: true
       baseline-version: 0
   ```

2. **同步现有表结构到 Flyway 脚本**
   - 导出当前数据库结构
   - 更新各模块的 V1__init_schema.sql
   - 确保脚本与实际表结构一致

3. **禁用 JPA 自动 DDL**
   ```yaml
   spring:
     jpa:
       hibernate:
         ddl-auto: validate  # 或 none
   ```

#### 方案 2：保持现状但记录差异

1. 创建 V2 迁移脚本记录额外的表
2. 继续使用 JPA `ddl-auto=update`
3. 定期同步 Flyway 脚本

## 检查命令

### 查找数据库中额外的表

```bash
# Platform Security
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'public' AND tablename LIKE 'sys_%' 
ORDER BY tablename;
"

# Developer Workstation
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'public' AND tablename LIKE 'dw_%' 
ORDER BY tablename;
"

# Admin Center
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'public' AND tablename LIKE 'admin_%' 
ORDER BY tablename;
"

# User Portal
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'public' AND tablename LIKE 'up_%' 
ORDER BY tablename;
"

# Workflow Engine
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'public' AND tablename LIKE 'wf_%' 
ORDER BY tablename;
"
```

### 导出表结构

```bash
# 导出所有表的 DDL
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform --schema-only > current_schema.sql

# 导出特定模块的表
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform --schema-only -t 'sys_*' > sys_tables.sql
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform --schema-only -t 'dw_*' > dw_tables.sql
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform --schema-only -t 'admin_*' > admin_tables.sql
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform --schema-only -t 'up_*' > up_tables.sql
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform --schema-only -t 'wf_*' > wf_tables.sql
```

## 下一步行动

1. ✅ **立即行动**：确定是否启用所有模块的 Flyway
2. ⚠️ **短期**：导出当前数据库结构，更新 Flyway 脚本
3. 📋 **中期**：禁用 JPA `ddl-auto=update`，完全使用 Flyway 管理
4. 🎯 **长期**：建立数据库变更审查流程

## 参考文档

- Flyway 迁移规则：`docs/development-guidelines.md` 第 1 节
- Schema 切换指南：`docs/SCHEMA_MIGRATION_GUIDE.md`
