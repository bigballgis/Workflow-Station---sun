# 枚举值一致性检查报告

生成时间: 2026-01-18

## 检查目的

检查数据库中所有使用枚举类型的字段，确保数据库中的实际值与 Java 枚举定义一致，避免出现 `IllegalArgumentException: No enum constant` 错误。

## 已修复的问题

### 1. sys_virtual_groups.type

**问题**: 数据库中存在 `STATIC` 类型的虚拟组，但枚举 `VirtualGroupType` 只支持 `SYSTEM` 和 `CUSTOM`

**修复**: 
- 已将 5 条 `STATIC` 类型的记录更新为 `SYSTEM`
- 已修复默认值从 `STATIC` 改为 `CUSTOM`
- 已添加 CHECK 约束：`CHECK (type IN ('SYSTEM', 'CUSTOM'))`

**状态**: ✅ 已修复

### 2. sys_roles.type

**问题**: 数据库中存在 `BUSINESS` 类型的角色，但枚举 `RoleType` 只支持 `BU_BOUNDED`、`BU_UNBOUNDED`、`ADMIN`、`DEVELOPER`

**修复**: 
- 已将 3 条 `BUSINESS` 类型的记录更新为 `BU_UNBOUNDED`
- 已修复 CHECK 约束，从允许 `BUSINESS` 改为允许 `BU_BOUNDED` 和 `BU_UNBOUNDED`

**状态**: ✅ 已修复

## 检查结果

### ✅ 正常（数据与枚举匹配）

#### 1. sys_roles.type (RoleType)
- **枚举值**: `ADMIN`, `DEVELOPER`, `BU_BOUNDED`, `BU_UNBOUNDED`
- **数据库值**: `ADMIN` (2条), `BU_UNBOUNDED` (3条), `DEVELOPER` (3条)
- **状态**: ✅ 一致

#### 2. sys_virtual_groups.type (VirtualGroupType)
- **枚举值**: `SYSTEM`, `CUSTOM`
- **数据库值**: `SYSTEM` (5条)
- **状态**: ✅ 一致（已修复）

#### 3. sys_role_assignments.target_type (AssignmentTargetType)
- **枚举值**: `USER`, `VIRTUAL_GROUP`
- **数据库值**: `USER` (18条), `VIRTUAL_GROUP` (3条)
- **状态**: ✅ 一致

#### 4. sys_function_units.status (FunctionUnitStatus)
- **枚举值**: `DRAFT`, `VALIDATED`, `DEPLOYED`, `DEPRECATED`
- **数据库值**: `DEPLOYED` (2条)
- **状态**: ✅ 一致

#### 5. sys_dictionaries.type (DictionaryType)
- **枚举值**: `SYSTEM`, `BUSINESS`, `CUSTOM`
- **数据库值**: `SYSTEM` (5条)
- **状态**: ✅ 一致

#### 6. sys_dictionaries.status (DictionaryStatus)
- **枚举值**: `ACTIVE`, `INACTIVE`, `DRAFT`
- **数据库值**: `ACTIVE` (5条)
- **状态**: ✅ 一致

#### 7-10. Developer Workstation 表枚举字段

##### 7. dw_table_definitions.table_type (TableType)
- **枚举值**: `MAIN`, `SUB`, `ACTION`, `RELATION`
- **数据库值**: `ACTION`, `SUB`, `MAIN`, `RELATION`
- **状态**: ✅ 一致

##### 8. dw_form_definitions.form_type (FormType)
- **枚举值**: `MAIN`, `SUB`, `ACTION`, `POPUP`
- **数据库值**: `POPUP`, `ACTION`, `SUB`, `MAIN`
- **状态**: ✅ 一致

##### 9. dw_action_definitions.action_type (ActionType)
- **枚举值**: `APPROVE`, `REJECT`, `TRANSFER`, `DELEGATE`, `ROLLBACK`, `WITHDRAW`, `API_CALL`, `FORM_POPUP`, `SCRIPT`, `CUSTOM_SCRIPT`, `PROCESS_SUBMIT`, `PROCESS_REJECT`, `COMPOSITE`
- **数据库值**: `CUSTOM_SCRIPT`, `FORM_POPUP`, `PROCESS_SUBMIT`, `REJECT`, `APPROVE`, `WITHDRAW`, `ROLLBACK`, `TRANSFER`, `DELEGATE`, `API_CALL` (10个值)
- **状态**: ✅ 一致（所有数据库值都在枚举中）

##### 10. dw_field_definitions.data_type (DataType)
- **枚举值**: `VARCHAR`, `TEXT`, `INTEGER`, `BIGINT`, `DECIMAL`, `BOOLEAN`, `DATE`, `TIME`, `TIMESTAMP`, `JSON`, `BYTEA`
- **数据库值**: `TEXT`, `INTEGER`, `DATE`, `TIMESTAMP`, `DECIMAL`, `VARCHAR` (6个值)
- **状态**: ✅ 一致（所有数据库值都在枚举中）

### ⚠️ 无数据（无法验证）

以下表没有数据，无法检查一致性，但枚举定义正确：

- `sys_permission_requests.request_type` (PermissionRequestType)
- `sys_approvers.target_type` (ApproverTargetType)
- `sys_virtual_group_task_history.action_type` (TaskActionType)
- `sys_member_change_logs.change_type` (MemberChangeType)
- `sys_member_change_logs.target_type`
- `sys_dictionaries.data_source_type` (DataSourceType)

## 枚举类型列表

### Admin Center

| 枚举类 | 枚举值 | 使用表/字段 |
|--------|--------|------------|
| `RoleType` | `BU_BOUNDED`, `BU_UNBOUNDED`, `ADMIN`, `DEVELOPER` | `sys_roles.type` |
| `VirtualGroupType` | `SYSTEM`, `CUSTOM` | `sys_virtual_groups.type` |
| `BusinessUnitStatus` | `ACTIVE`, `DISABLED` | `sys_business_units.status` |
| `ApproverTargetType` | `VIRTUAL_GROUP`, `BUSINESS_UNIT` | `sys_approvers.target_type` |
| `TaskActionType` | `CREATED`, `ASSIGNED`, `CLAIMED`, `DELEGATED`, `COMPLETED`, `CANCELLED`, `RETURNED` | `sys_virtual_group_task_history.action_type` |
| `PermissionRequestType` | `VIRTUAL_GROUP`, `BUSINESS_UNIT`, `BUSINESS_UNIT_ROLE` (deprecated) | `sys_permission_requests.request_type` |
| `MemberChangeType` | `JOIN`, `EXIT`, `REMOVED` | `sys_member_change_logs.change_type` |
| `FunctionUnitStatus` | `DRAFT`, `VALIDATED`, `DEPLOYED`, `DEPRECATED` | `sys_function_units.status` |
| `DictionaryType` | `SYSTEM`, `BUSINESS`, `CUSTOM` | `sys_dictionaries.type` |
| `DictionaryStatus` | `ACTIVE`, `INACTIVE`, `DRAFT` | `sys_dictionaries.status` |
| `DataSourceType` | `DATABASE`, `API`, `FILE`, `STATIC` | `sys_dictionaries.data_source_type` |

### Platform Security

| 枚举类 | 枚举值 | 使用表/字段 |
|--------|--------|------------|
| `AssignmentTargetType` | `USER`, `VIRTUAL_GROUP` | `sys_role_assignments.target_type` |

### Developer Workstation

| 枚举类 | 枚举值 | 使用表/字段 | 数据库值 | 状态 |
|--------|--------|------------|---------|------|
| `TableType` | `MAIN`, `SUB`, `ACTION`, `RELATION` | `dw_table_definitions.table_type` | `ACTION`, `SUB`, `MAIN`, `RELATION` | ✅ 一致 |
| `FormType` | `MAIN`, `SUB`, `ACTION`, `POPUP` | `dw_form_definitions.form_type` | `POPUP`, `ACTION`, `SUB`, `MAIN` | ✅ 一致 |
| `ActionType` | `APPROVE`, `REJECT`, `TRANSFER`, `DELEGATE`, `ROLLBACK`, `WITHDRAW`, `API_CALL`, `FORM_POPUP`, `SCRIPT`, `CUSTOM_SCRIPT`, `PROCESS_SUBMIT`, `PROCESS_REJECT`, `COMPOSITE` | `dw_action_definitions.action_type` | `CUSTOM_SCRIPT`, `FORM_POPUP`, `PROCESS_SUBMIT`, `REJECT`, `APPROVE`, `WITHDRAW`, `ROLLBACK`, `TRANSFER`, `DELEGATE`, `API_CALL` | ✅ 一致 |
| `DataType` | `VARCHAR`, `TEXT`, `INTEGER`, `BIGINT`, `DECIMAL`, `BOOLEAN`, `DATE`, `TIME`, `TIMESTAMP`, `JSON`, `BYTEA` | `dw_field_definitions.data_type` | `TEXT`, `INTEGER`, `DATE`, `TIMESTAMP`, `DECIMAL`, `VARCHAR` | ✅ 一致 |

## 建议

1. **数据库约束**: 所有使用枚举的字段都应该添加 CHECK 约束，确保数据一致性
2. **定期检查**: 在数据迁移或导入时，确保新的枚举值已在 Java 代码中定义
3. **测试覆盖**: 添加单元测试验证枚举值与数据库约束的一致性

## 修复脚本

- `deploy/scripts/fix-virtual-group-static-type.sql` - 修复虚拟组类型
- `deploy/scripts/fix-virtual-group-type-default.sql` - 修复虚拟组类型默认值
- `deploy/scripts/add-virtual-group-type-constraint.sql` - 添加虚拟组类型约束
- `deploy/scripts/fix-role-business-type.sql` - 修复角色类型
- `deploy/scripts/check-all-enum-consistency.sql` - 检查所有枚举一致性

## 总结

✅ **所有检查的枚举字段都与 Java 代码一致，没有发现其他不一致的问题。**

已知问题已全部修复，系统可以正常运行。
