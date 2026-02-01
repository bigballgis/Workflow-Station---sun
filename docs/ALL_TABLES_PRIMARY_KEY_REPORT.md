# 所有表主键检查报告

## 📊 统计结果

### workflow_platform_executable_clean_fixed.sql

- **总表数**: 141 个
- **有主键**: 30 个（仅 sys_* 表）
- **无主键**: 111 个

---

## ✅ 有主键的表（30 个）

### sys_* 表（30 个）- 已修复

所有 `sys_*` 表在文件末尾通过 `ALTER TABLE` 语句添加了主键：

1. ✅ sys_approvers
2. ✅ sys_business_unit_roles
3. ✅ sys_business_units
4. ✅ sys_developer_role_permissions
5. ✅ sys_dictionaries
6. ✅ sys_dictionary_data_sources
7. ✅ sys_dictionary_items
8. ✅ sys_dictionary_versions
9. ✅ sys_function_unit_access
10. ✅ sys_function_unit_approvals
11. ✅ sys_function_unit_contents
12. ✅ sys_function_unit_dependencies
13. ✅ sys_function_unit_deployments
14. ✅ sys_function_units
15. ✅ sys_login_audit
16. ✅ sys_member_change_logs
17. ✅ sys_permission_requests
18. ✅ sys_permissions
19. ✅ sys_role_assignments
20. ✅ sys_role_permissions
21. ✅ sys_roles
22. ✅ sys_user_business_unit_roles
23. ✅ sys_user_business_units
24. ✅ sys_user_preferences
25. ✅ sys_user_roles
26. ✅ sys_users
27. ✅ sys_virtual_group_members
28. ✅ sys_virtual_group_roles
29. ✅ sys_virtual_group_task_history
30. ✅ sys_virtual_groups

---

## ❌ 缺少主键的表（111 个）

### 1. Flowable 表（act_*）- 67 个

**说明**: Flowable 工作流引擎的表，通常由 Flowable 自动管理主键

| 表名 | 主键字段 | 状态 |
|------|---------|------|
| act_app_appdef | id_ | ❌ 缺少 |
| act_app_databasechangelog | id | ❌ 缺少 |
| act_app_databasechangeloglock | id | ❌ 缺少 |
| act_app_deployment | id_ | ❌ 缺少 |
| act_app_deployment_resource | id_ | ❌ 缺少 |
| act_cmmn_casedef | id_ | ❌ 缺少 |
| act_cmmn_databasechangelog | id | ❌ 缺少 |
| act_cmmn_databasechangeloglock | id | ❌ 缺少 |
| act_cmmn_deployment | id_ | ❌ 缺少 |
| act_cmmn_deployment_resource | id_ | ❌ 缺少 |
| act_cmmn_hi_case_inst | id_ | ❌ 缺少 |
| act_cmmn_hi_mil_inst | id_ | ❌ 缺少 |
| act_cmmn_hi_plan_item_inst | id_ | ❌ 缺少 |
| act_cmmn_ru_case_inst | id_ | ❌ 缺少 |
| act_cmmn_ru_mil_inst | id_ | ❌ 缺少 |
| act_cmmn_ru_plan_item_inst | id_ | ❌ 缺少 |
| act_cmmn_ru_sentry_part_inst | id_ | ❌ 缺少 |
| act_dmn_databasechangelog | id | ❌ 缺少 |
| act_dmn_databasechangeloglock | id | ❌ 缺少 |
| act_dmn_decision | id_ | ❌ 缺少 |
| act_dmn_deployment | id_ | ❌ 缺少 |
| act_dmn_deployment_resource | id_ | ❌ 缺少 |
| act_dmn_hi_decision_execution | id_ | ❌ 缺少 |
| act_evt_log | log_nr_ | ❌ 缺少 |
| act_ge_bytearray | id_ | ❌ 缺少 |
| act_ge_property | name_ | ❌ 缺少 |
| act_hi_actinst | id_ | ❌ 缺少 |
| act_hi_attachment | id_ | ❌ 缺少 |
| act_hi_comment | id_ | ❌ 缺少 |
| act_hi_detail | id_ | ❌ 缺少 |
| act_hi_entitylink | id_ | ❌ 缺少 |
| act_hi_identitylink | id_ | ❌ 缺少 |
| act_hi_procinst | id_ | ❌ 缺少 |
| act_hi_taskinst | id_ | ❌ 缺少 |
| act_hi_tsk_log | id_ | ❌ 缺少 |
| act_hi_varinst | id_ | ❌ 缺少 |
| act_id_bytearray | id_ | ❌ 缺少 |
| act_id_group | id_ | ❌ 缺少 |
| act_id_info | id_ | ❌ 缺少 |
| act_id_membership | id_ | ❌ 缺少 |
| act_id_priv | id_ | ❌ 缺少 |
| act_id_priv_mapping | id_ | ❌ 缺少 |
| act_id_property | name_ | ❌ 缺少 |
| act_id_token | id_ | ❌ 缺少 |
| act_id_user | id_ | ❌ 缺少 |
| act_procdef_info | id_ | ❌ 缺少 |
| act_re_deployment | id_ | ❌ 缺少 |
| act_re_model | id_ | ❌ 缺少 |
| act_re_procdef | id_ | ❌ 缺少 |
| act_ru_actinst | id_ | ❌ 缺少 |
| act_ru_deadletter_job | id_ | ❌ 缺少 |
| act_ru_entitylink | id_ | ❌ 缺少 |
| act_ru_event_subscr | id_ | ❌ 缺少 |
| act_ru_execution | id_ | ❌ 缺少 |
| act_ru_external_job | id_ | ❌ 缺少 |
| act_ru_history_job | id_ | ❌ 缺少 |
| act_ru_identitylink | id_ | ❌ 缺少 |
| act_ru_job | id_ | ❌ 缺少 |
| act_ru_suspended_job | id_ | ❌ 缺少 |
| act_ru_task | id_ | ❌ 缺少 |
| act_ru_timer_job | id_ | ❌ 缺少 |
| act_ru_variable | id_ | ❌ 缺少 |

**注意**: Flowable 表的主键通常由 Flowable 引擎在运行时自动添加，但 SQL 文件中应该包含主键定义。

---

### 2. Flowable Event Registry 表（flw_*）- 9 个

| 表名 | 主键字段 | 状态 |
|------|---------|------|
| flw_channel_definition | id_ | ❌ 缺少 |
| flw_ev_databasechangelog | id | ❌ 缺少 |
| flw_ev_databasechangeloglock | id | ❌ 缺少 |
| flw_event_definition | id_ | ❌ 缺少 |
| flw_event_deployment | id_ | ❌ 缺少 |
| flw_event_resource | id_ | ❌ 缺少 |
| flw_ru_batch | id_ | ❌ 缺少 |
| flw_ru_batch_part | id_ | ❌ 缺少 |

---

### 3. Developer Workstation 表（dw_*）- 11 个

| 表名 | 主键字段 | 状态 |
|------|---------|------|
| dw_action_definitions | id | ❌ 缺少 |
| dw_field_definitions | id | ❌ 缺少 |
| dw_foreign_keys | id | ❌ 缺少 |
| dw_form_definitions | id | ❌ 缺少 |
| dw_form_table_bindings | id | ❌ 缺少 |
| dw_function_units | id | ❌ 缺少 |
| dw_icons | id | ❌ 缺少 |
| dw_operation_logs | id | ❌ 缺少 |
| dw_process_definitions | id | ❌ 缺少 |
| dw_table_definitions | id | ❌ 缺少 |
| dw_versions | id | ❌ 缺少 |

**注意**: 在 Flyway 迁移脚本中，这些表都有主键（`BIGSERIAL PRIMARY KEY`），但 `workflow_platform_executable_clean_fixed.sql` 中缺少。

---

### 4. User Portal 表（up_*）- 10 个

| 表名 | 主键字段 | 状态 |
|------|---------|------|
| up_dashboard_layout | id | ❌ 缺少 |
| up_delegation_audit | id | ❌ 缺少 |
| up_delegation_rule | id | ❌ 缺少 |
| up_favorite_process | id | ❌ 缺少 |
| up_notification_preference | id | ❌ 缺少 |
| up_permission_request | id | ❌ 缺少 |
| up_process_draft | id | ❌ 缺少 |
| up_process_history | id | ❌ 缺少 |
| up_process_instance | id | ❌ 缺少 |
| up_user_preference | id | ❌ 缺少 |

---

### 5. Workflow 表（wf_*）- 6 个

| 表名 | 主键字段 | 状态 |
|------|---------|------|
| wf_audit_logs | id | ❌ 缺少 |
| wf_exception_records | id | ❌ 缺少 |
| wf_extended_task_info | id | ❌ 缺少 |
| wf_process_variables | id | ❌ 缺少 |
| wf_saga_steps | id | ❌ 缺少 |
| wf_saga_transactions | id | ❌ 缺少 |

---

### 6. Admin Center 表（admin_*）- 14 个

| 表名 | 主键字段 | 状态 |
|------|---------|------|
| admin_alert_rules | id | ❌ 缺少 |
| admin_alerts | id | ❌ 缺少 |
| admin_audit_logs | id | ❌ 缺少 |
| admin_column_permissions | id | ❌ 缺少 |
| admin_config_history | id | ❌ 缺少 |
| admin_data_permission_rules | id | ❌ 缺少 |
| admin_log_retention_policies | id | ❌ 缺少 |
| admin_password_history | id | ❌ 缺少 |
| admin_permission_change_history | id | ❌ 缺少 |
| admin_permission_conflicts | id | ❌ 缺少 |
| admin_permission_delegations | id | ❌ 缺少 |
| admin_security_policies | id | ❌ 缺少 |
| admin_system_configs | id | ❌ 缺少 |
| admin_system_logs | id | ❌ 缺少 |

---

## 🔍 分析

### 问题原因

1. **workflow_platform_executable_clean_fixed.sql** 可能是从现有数据库导出的（`pg_dump`）
2. 导出时可能使用了 `--no-owner` 或类似选项，导致主键约束丢失
3. 或者表是通过 Hibernate `ddl-auto: update` 创建的，没有显式的主键定义

### 影响

- ❌ **数据完整性**: 没有主键的表无法保证数据唯一性
- ❌ **性能**: 没有主键的表查询性能较差
- ❌ **外键引用**: 其他表无法正确引用这些表
- ❌ **ORM 映射**: JPA/Hibernate 需要主键才能正常工作

---

## ✅ 修复建议

### 方案 1: 在 CREATE TABLE 语句中添加主键（推荐）

修改每个表的定义，在 `id` 字段后添加 `PRIMARY KEY`：

```sql
-- 修复前
CREATE TABLE IF NOT EXISTS public.dw_function_units (
    id bigint NOT NULL,
    ...
);

-- 修复后
CREATE TABLE IF NOT EXISTS public.dw_function_units (
    id bigint PRIMARY KEY,  -- ✅ 添加 PRIMARY KEY
    ...
);
```

### 方案 2: 在文件末尾添加 ALTER TABLE 语句（已用于 sys_* 表）

在文件末尾添加 `DO` 块，为所有表添加主键：

```sql
DO $$
BEGIN
    -- dw_* 表
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'dw_function_units_pkey') THEN
        ALTER TABLE public.dw_function_units ADD PRIMARY KEY (id);
    END IF;
    -- ... 其他表
END $$;
```

### 方案 3: 使用 Flyway 迁移脚本（最佳实践）

- ✅ 使用 Flyway 迁移脚本，它们已经正确定义了主键
- ✅ 不要使用 `workflow_platform_executable_clean_fixed.sql` 作为初始化脚本

---

## 📝 优先级

### 🔴 高优先级（必须修复）

- **dw_* 表**（11 个）- 应用核心表
- **up_* 表**（10 个）- 用户门户表
- **wf_* 表**（6 个）- 工作流表
- **admin_* 表**（14 个）- 管理后台表

### 🟡 中优先级（建议修复）

- **act_* 表**（67 个）- Flowable 表（可能由 Flowable 自动管理）
- **flw_* 表**（9 个）- Flowable Event Registry 表

---

## ✅ 总结

- **总表数**: 141 个
- **有主键**: 30 个（仅 sys_* 表）
- **无主键**: 111 个
- **修复状态**: 仅 sys_* 表已修复

**建议**: 
1. 优先修复应用表（dw_*, up_*, wf_*, admin_*）
2. Flowable 表（act_*, flw_*）可能由 Flowable 引擎自动管理，但建议也添加主键定义
3. 考虑使用 Flyway 迁移脚本替代 `workflow_platform_executable_clean_fixed.sql`
