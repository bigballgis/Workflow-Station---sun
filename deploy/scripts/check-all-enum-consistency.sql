-- =====================================================
-- 检查数据库中所有枚举字段的一致性
-- =====================================================
-- 生成时间: 2026-01-18
-- 目的: 检查数据库中实际的值是否与 Java 枚举定义一致
-- =====================================================

-- 1. sys_roles.type (RoleType: ADMIN, DEVELOPER, BU_BOUNDED, BU_UNBOUNDED)
SELECT 'sys_roles.type' as table_column, type as value, COUNT(*) as count
FROM sys_roles
GROUP BY type
ORDER BY type;

-- 2. sys_virtual_groups.type (VirtualGroupType: SYSTEM, CUSTOM)
SELECT 'sys_virtual_groups.type' as table_column, type as value, COUNT(*) as count
FROM sys_virtual_groups
GROUP BY type
ORDER BY type;

-- 3. sys_role_assignments.target_type (AssignmentTargetType: USER, VIRTUAL_GROUP, ...)
SELECT 'sys_role_assignments.target_type' as table_column, target_type as value, COUNT(*) as count
FROM sys_role_assignments
WHERE target_type IS NOT NULL
GROUP BY target_type
ORDER BY target_type;

-- 4. sys_function_units.status (FunctionUnitStatus: DRAFT, VALIDATED, DEPLOYED, DEPRECATED)
SELECT 'sys_function_units.status' as table_column, status as value, COUNT(*) as count
FROM sys_function_units
WHERE status IS NOT NULL
GROUP BY status
ORDER BY status;

-- 5. sys_dictionaries.type (DictionaryType: SYSTEM, BUSINESS, CUSTOM)
SELECT 'sys_dictionaries.type' as table_column, type as value, COUNT(*) as count
FROM sys_dictionaries
WHERE type IS NOT NULL
GROUP BY type
ORDER BY type;

-- 6. sys_dictionaries.status (DictionaryStatus: ACTIVE, INACTIVE, DRAFT)
SELECT 'sys_dictionaries.status' as table_column, status as value, COUNT(*) as count
FROM sys_dictionaries
WHERE status IS NOT NULL
GROUP BY status
ORDER BY status;

-- 7. sys_dictionaries.data_source_type (DataSourceType: DATABASE, API, FILE, STATIC)
SELECT 'sys_dictionaries.data_source_type' as table_column, data_source_type as value, COUNT(*) as count
FROM sys_dictionaries
WHERE data_source_type IS NOT NULL
GROUP BY data_source_type
ORDER BY data_source_type;

-- 8. sys_permission_requests.request_type (PermissionRequestType: VIRTUAL_GROUP, BUSINESS_UNIT, BUSINESS_UNIT_ROLE)
SELECT 'sys_permission_requests.request_type' as table_column, request_type as value, COUNT(*) as count
FROM sys_permission_requests
WHERE request_type IS NOT NULL
GROUP BY request_type
ORDER BY request_type;

-- 9. sys_approvers.target_type (ApproverTargetType: VIRTUAL_GROUP, BUSINESS_UNIT)
SELECT 'sys_approvers.target_type' as table_column, target_type as value, COUNT(*) as count
FROM sys_approvers
WHERE target_type IS NOT NULL
GROUP BY target_type
ORDER BY target_type;

-- 10. sys_virtual_group_task_history.action_type (TaskActionType: CREATED, ASSIGNED, CLAIMED, DELEGATED, COMPLETED, CANCELLED, RETURNED)
SELECT 'sys_virtual_group_task_history.action_type' as table_column, action_type as value, COUNT(*) as count
FROM sys_virtual_group_task_history
WHERE action_type IS NOT NULL
GROUP BY action_type
ORDER BY action_type;

-- 11. sys_member_change_logs.change_type (MemberChangeType: JOIN, EXIT, REMOVED)
SELECT 'sys_member_change_logs.change_type' as table_column, change_type as value, COUNT(*) as count
FROM sys_member_change_logs
WHERE change_type IS NOT NULL
GROUP BY change_type
ORDER BY change_type;

-- 12. sys_member_change_logs.target_type (可能是 AssignmentTargetType 或其他)
SELECT 'sys_member_change_logs.target_type' as table_column, target_type as value, COUNT(*) as count
FROM sys_member_change_logs
WHERE target_type IS NOT NULL
GROUP BY target_type
ORDER BY target_type;

-- 13. dt_table_definitions.table_type (TableType: MAIN, SUB, TEMP)
SELECT 'dt_table_definitions.table_type' as table_column, table_type as value, COUNT(*) as count
FROM dt_table_definitions
WHERE table_type IS NOT NULL
GROUP BY table_type
ORDER BY table_type;

-- 14. dt_form_definitions.form_type (FormType: CREATE, EDIT, VIEW, LIST)
SELECT 'dt_form_definitions.form_type' as table_column, form_type as value, COUNT(*) as count
FROM dt_form_definitions
WHERE form_type IS NOT NULL
GROUP BY form_type
ORDER BY form_type;

-- 15. dt_action_definitions.action_type (ActionType: CREATE, UPDATE, DELETE, QUERY, EXPORT, IMPORT)
SELECT 'dt_action_definitions.action_type' as table_column, action_type as value, COUNT(*) as count
FROM dt_action_definitions
WHERE action_type IS NOT NULL
GROUP BY action_type
ORDER BY action_type;

-- 16. dt_field_definitions.data_type (DataType: VARCHAR, TEXT, INTEGER, BIGINT, DECIMAL, BOOLEAN, DATE, TIME, TIMESTAMP, JSON, BYTEA)
SELECT 'dt_field_definitions.data_type' as table_column, data_type as value, COUNT(*) as count
FROM dt_field_definitions
WHERE data_type IS NOT NULL
GROUP BY data_type
ORDER BY data_type;
