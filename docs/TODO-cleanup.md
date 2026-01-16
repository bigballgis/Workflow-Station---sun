# 代码清理任务表

> 本文档记录开发后期需要清理的无用代码和待优化项。
> 
> 状态说明：
> - [ ] 待处理
> - [x] 已完成
> - [~] 保留/跳过（有说明原因）

## 1. 前端代码清理

### 1.1 admin-center

#### 废弃的 API 文件
- [x] `frontend/admin-center/src/api/roleAssignment.ts` - 角色分配 API（角色只能分配给虚拟组，不再需要直接分配功能）- 已删除

#### 废弃的组件/功能
- [x] `RolePermissionDialog.vue` - 已删除
- [x] 虚拟组有效期相关代码已移除，确认无残留

#### Department 相关代码（需重构为 BusinessUnit）
- [x] `frontend/admin-center/src/views/organization/components/DepartmentFormDialog.vue` - 已重命名为 BusinessUnitFormDialog.vue
- [x] `frontend/admin-center/src/views/organization/DepartmentTree.vue` - 已重命名为 BusinessUnitTree.vue，变量名已改为 businessUnit
- [x] `frontend/admin-center/src/stores/organization.ts` - 已添加 businessUnitTree 变量名（保留 departmentTree 别名向后兼容）
- [x] `frontend/admin-center/src/api/organization.ts` - 已添加 BusinessUnit 类型定义（保留 Department 别名向后兼容）
- [x] `frontend/admin-center/src/views/user/UserList.vue` - 已移除 departmentId 查询参数和 flattenDepartments 函数
- [x] `frontend/admin-center/src/views/user/UserImport.vue` - 已移除 departmentCode 字段

#### i18n 清理
- [x] 检查并移除未使用的翻译 key - 已移除 `role.assignmentType`, `role.user`, `role.department`, `role.virtualGroup`, `role.selectDepartment`, `role.selectVirtualGroup`（保留 `role.addMember` 和 `role.selectUser`，仍在虚拟组/业务单元成员管理中使用）
- [x] i18n 中 department 相关 key 已重命名为 businessUnit（organization.departmentName → organization.businessUnitName 等）

#### 向后兼容别名（可选清理 - 低优先级）
> 注意：这些别名已清理完成

- [x] `frontend/admin-center/src/api/department.ts` - 已删除（只是重新导出 businessUnit.ts）
- [x] `frontend/admin-center/src/api/organization.ts` - Department 类型别名已移除
- [x] `frontend/admin-center/src/api/businessUnit.ts` - departmentApi 别名已移除，manager 相关字段已移除
- [x] `frontend/admin-center/src/stores/organization.ts` - departmentTree 等别名已移除
- [x] `frontend/admin-center/src/api/user.ts` - departmentId/departmentName 字段已移除
- [x] `frontend/admin-center/src/api/auth.ts` - AssignmentTargetType 中的 DEPARTMENT/DEPARTMENT_HIERARCHY（应移除）- 已移除
- [x] `frontend/admin-center/src/api/auth.ts` - UserInfo 中的 departmentId 字段（应移除）- 已移除
- [x] `frontend/admin-center/src/api/dashboard.ts` - DashboardStats 中的 totalDepartments 字段（应改为 totalBusinessUnits）- 已更新

#### developer-workstation 前端
- [x] `frontend/developer-workstation/src/api/adminCenter.ts` - DepartmentTree 接口和 getDepartmentTree/searchDepartments 方法（调用已删除的 API）- 已删除

### 1.2 user-portal

- [x] `frontend/user-portal/src/stores/user.ts` - UserInfo 接口中的 department 字段 - 已移除
- [x] `frontend/user-portal/src/api/permission.ts` - getDepartments() 方法 - 已移除
- [x] `frontend/user-portal/src/api/auth.ts` - AssignmentTargetType 中的 DEPARTMENT/DEPARTMENT_HIERARCHY 类型，departmentId 字段 - 已移除
- [x] `frontend/user-portal/src/components/FormRenderer.vue` - department 类型字段渲染 - 已改为 businessUnit 类型

### 1.3 developer-workstation

- [x] `frontend/developer-workstation/src/api/auth.ts` - AssignmentTargetType 中的 DEPARTMENT/DEPARTMENT_HIERARCHY 类型，departmentId 字段 - 已移除
- [x] `frontend/developer-workstation/src/i18n/locales/*.ts` - selectDepartment 翻译 key - 已移除

---

## 2. 后端代码清理

### 2.1 admin-center

#### 废弃的角色分配功能
- [x] `RoleAssignmentController.java` - 已删除
- [x] `RoleAssignmentComponent.java` - 已删除
- [x] `RoleAssignmentComponentProperties.java` - 角色分配属性测试 - 已删除
- [~] `platform-security` 模块中的 `RoleAssignment` 实体和 `RoleAssignmentRepository` - 保留（用于虚拟组角色分配）
- [x] 相关的 DTO - 已删除：`CreateAssignmentRequest`, `RoleAssignmentResponse`, `EffectiveUserResponse`
- [x] `backend/admin-center/src/main/java/com/admin/dto/response/DepartmentResult.java` - 未使用的 DTO 类 - 已删除

#### Department 相关代码（需清理或重构）
- [x] `backend/admin-center/src/main/java/com/admin/entity/Department.java` - 已删除
- [x] `backend/admin-center/src/main/java/com/admin/repository/DepartmentRepository.java` - 已删除
- [x] `backend/admin-center/src/main/java/com/admin/controller/DepartmentController.java` - 已删除

#### 虚拟组有效期相关
- [x] `VirtualGroup` 实体中的 `validFrom`/`validTo` 字段已移除

### 2.2 platform-security

#### Department 相关代码
- [x] `backend/platform-security/src/main/java/com/platform/security/resolver/DepartmentTargetResolver.java` - 已删除
- [x] `backend/platform-security/src/main/java/com/platform/security/resolver/DepartmentHierarchyTargetResolver.java` - 已删除
- [x] `backend/platform-security/src/main/java/com/platform/security/resolver/UserTargetResolver.java` - 已移除 sys_departments 查询
- [x] `backend/platform-security/src/main/java/com/platform/security/resolver/VirtualGroupTargetResolver.java` - 已移除 sys_departments 查询
- [x] `backend/platform-security/src/main/java/com/platform/security/service/impl/UserRoleServiceImpl.java` - 已移除 sys_departments 查询
- [x] `backend/platform-security/src/main/java/com/platform/security/enums/AssignmentTargetType.java` - 已移除 DEPARTMENT 和 DEPARTMENT_HIERARCHY 枚举值
- [x] `backend/platform-security/src/main/java/com/platform/security/repository/RoleAssignmentRepository.java` - 已移除 department 相关查询方法
- [x] `backend/platform-security/src/main/java/com/platform/security/dto/ResolvedUser.java` - 已移除 departmentId/departmentName 字段
- [x] `backend/platform-security/src/test/java/com/platform/security/property/TargetResolverProperties.java` - 已移除 department 相关测试
- [x] `backend/platform-security/src/test/java/com/platform/security/property/UserRoleServiceProperties.java` - 已移除 department 相关测试
- [x] `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` - sys_departments 表定义已移除
- [x] `backend/platform-security/src/main/java/com/platform/security/repository/UserRepository.java` - findByDepartmentId() 方法（未使用）- 已删除
- [x] `backend/platform-security/src/main/java/com/platform/security/model/User.java` - departmentId 字段 - 已更新为 primaryBusinessUnitId
- [x] `backend/platform-security/src/main/java/com/platform/security/dto/UserInfo.java` - departmentId 字段 - 已更新为 primaryBusinessUnitId
- [x] `backend/platform-security/src/main/java/com/platform/security/config/DataInitializer.java` - createUserIfNotExists() 方法中的 departmentId 参数 - 已更新为 primaryBusinessUnitId

#### JWT Token 中的 departmentId（需评估）
- [x] `backend/platform-security/src/test/java/com/platform/security/property/JwtTokenPropertyTest.java` - 测试中使用 departmentId - 已更新为 primaryBusinessUnitId
- [x] `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java` - generateToken 方法包含 departmentId 参数 - 已更新为 primaryBusinessUnitId
- [x] `backend/platform-security/src/main/java/com/platform/security/service/impl/AuthenticationServiceImpl.java` - 认证时设置 departmentId - 已更新为 primaryBusinessUnitId
- [x] `backend/platform-security/src/main/java/com/platform/security/util/SecurityContextUtils.java` - getCurrentDepartmentId() 方法 - 已更新为 getCurrentPrimaryBusinessUnitId()

#### 其他
- [~] 检查 `RoleAssignment` 相关代码 - 保留（用于 USER 和 VIRTUAL_GROUP 类型分配）

### 2.3 platform-common

#### Department 相关字段（需评估 - 影响 JWT Token）
- [x] `backend/platform-common/src/main/java/com/platform/common/dto/UserPrincipal.java` - departmentId, departmentName 字段 - 已更新为 primaryBusinessUnitId, primaryBusinessUnitName
- [x] `backend/platform-common/src/main/java/com/platform/common/constant/PlatformConstants.java` - JWT_CLAIM_DEPARTMENT_ID 常量 - 已更新为 JWT_CLAIM_PRIMARY_BUSINESS_UNIT_ID

### 2.4 workflow-engine-core

#### Department 相关代码
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/dto/response/UserSecurityInfo.java` - 已移除 departmentId, departmentName 字段
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/dto/response/HistoryStatisticsResult.java` - departmentStats 字段已重命名为 businessUnitStats
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/service/UserPermissionService.java` - 已移除 hasUserDepartmentRole, getUserDepartmentRoles, isUserInDepartmentHierarchy 方法
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/enums/AuditResourceType.java` - 已将 DEPARTMENT 改为 BUSINESS_UNIT
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/enums/AssignmentType.java` - 已移除 DEPT_ROLE 枚举值
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/client/AdminCenterClient.java` - 已删除所有 department 相关方法
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/dto/request/TaskAssignmentRequest.java` - 已移除 DEPT_ROLE 相关代码
- [x] `backend/workflow-engine-core/src/main/java/com/workflow/component/TaskManagerComponent.java` - 已移除 DEPT_ROLE 相关代码
- [x] `backend/workflow-engine-core/src/test/java/com/workflow/properties/MultiDimensionalTaskQueryProperties.java` - 已移除 departmentRoleTasksVisibilityFilterAccuracy 测试
- [x] `backend/workflow-engine-core/src/test/java/com/workflow/properties/TaskDelegationPermissionProperties.java` - 已移除 departmentRoleTaskDelegationPermission 测试
- [x] `backend/workflow-engine-core/src/test/java/com/workflow/properties/MultiDimensionalTaskAssignmentProperties.java` - 已移除 DEPT_ROLE 相关测试

### 2.5 user-portal

- [x] `backend/user-portal/src/main/java/com/portal/controller/AuthController.java` - 已移除 departmentId 从登录响应
- [x] `backend/user-portal/src/main/java/com/portal/dto/LoginResponse.java` - 已移除 departmentId 字段
- [x] `backend/user-portal/src/main/java/com/portal/entity/User.java` - departmentId 字段（需评估是否移除）- 已移除
- [x] `backend/user-portal/src/main/java/com/portal/component/RoleAccessComponent.java` - getDepartments() 和 getDepartmentById() 方法 - 已删除
- [x] `backend/user-portal/src/main/java/com/portal/component/PermissionComponent.java` - getDepartments() 方法 - 已删除，getDepartmentById() 改为使用 BusinessUnit API
- [x] `backend/user-portal/src/main/java/com/portal/controller/PermissionController.java` - `/departments` 端点 - 已删除
- [x] `backend/user-portal/src/main/java/com/portal/component/TaskQueryComponent.java` - departmentRoles 相关代码（已迁移到 AssigneeType）- 已删除 getUserDeptRoles() 方法
- [x] `backend/user-portal/src/main/java/com/portal/component/TaskProcessComponent.java` - isUserHasDeptRole() 方法和 departmentRoles 相关代码 - 已删除
- [x] `backend/user-portal/src/test/java/com/portal/properties/TaskQueryProperties.java` - departmentRoles 测试数据 - 已清理
- [x] `backend/user-portal/src/test/java/com/portal/properties/TaskProcessProperties.java` - departmentRoles 测试数据 - 已清理

### 2.6 developer-workstation

- [x] `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java` - 已移除 departmentId 从登录响应
- [x] `backend/developer-workstation/src/main/java/com/developer/dto/LoginResponse.java` - 已移除 departmentId 字段
- [x] `backend/developer-workstation/src/main/java/com/developer/entity/User.java` - departmentId 字段（需评估是否移除）- 已移除

---

## 3. 数据库清理

### 3.1 废弃的表
- [~] `sys_role_assignments` - 角色分配表（保留，用于 USER 和 VIRTUAL_GROUP 类型分配）
- [x] `sys_departments` - 旧的部门表（数据已迁移到 `sys_business_units`，已删除）
  - V5 迁移脚本已创建：`V5__drop_sys_departments_table.sql`
  - V1__init_schema.sql 中的表定义已移除

### 3.2 废弃的字段
- [x] `sys_virtual_groups.valid_from` - 虚拟组有效期开始（已移除）
- [x] `sys_virtual_groups.valid_to` - 虚拟组有效期结束（已移除）
- [x] `sys_users.department_id` - 用户部门ID - 已重命名为 primary_business_unit_id（V3迁移脚本）
- [x] `sys_users.primary_business_unit_id` - 用户主业务单元ID - 已删除（V4迁移脚本，用户与业务单元是多对多关系）
- [x] `idx_sys_users_department` - department_id 索引 - 已重命名为 idx_sys_users_primary_business_unit（V3迁移脚本）
- [x] `idx_sys_users_primary_business_unit` - primary_business_unit_id 索引 - 已删除（V4迁移脚本）
- [x] `fk_user_department` - department_id 外键约束 - 已删除（V3迁移脚本）
- [x] `sys_business_units.manager_id` - 业务单元负责人（已改用 sys_approvers 表管理审批人，待删除）- 已通过 V5 迁移脚本删除
- [x] `sys_business_units.secondary_manager_id` - 业务单元副经理（已改用 sys_approvers 表管理审批人，待删除）- 已通过 V5 迁移脚本删除

### 3.3 Flyway 迁移文件
- [x] `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` - 已更新 sys_users 表移除 primary_business_unit_id，已移除 sys_departments 表定义
- [x] `backend/platform-security/src/main/resources/db/migration/V3__rename_department_to_business_unit.sql` - 迁移脚本，重命名 department_id 为 primary_business_unit_id（历史）
- [x] `backend/platform-security/src/main/resources/db/migration/V4__drop_primary_business_unit_id.sql` - 迁移脚本，删除 primary_business_unit_id 列和索引
- [x] `backend/platform-security/src/main/resources/db/migration/V5__drop_sys_departments_table.sql` - 迁移脚本，删除 sys_departments 表
- [x] `backend/admin-center/src/main/resources/db/migration/V14__drop_business_unit_manager_fields.sql` - 迁移脚本，删除 manager_id 和 secondary_manager_id 列

### 3.4 测试数据 SQL 文件
- [x] `deploy/init-scripts/02-test-data/01-organization.sql` - 已更新为插入 sys_business_units 数据
- [x] `deploy/init-scripts/02-test-data/02-organization-detail.sql` - 已更新为插入 sys_business_units 数据
- [x] `deploy/init-scripts/02-test-data/04-department-managers.sql` - 已删除，替换为 04-business-unit-approvers.sql（使用 sys_approvers 表）
- [x] `deploy/init-scripts/99-utilities/01-cleanup-old-tables.sql` - 已添加 sys_departments 表删除脚本

---

## 4. 配置文件清理

- [x] 检查 `application.yml` / `application-dev.yml` 中是否有废弃的配置项 - 无 department 相关配置
- [~] 检查 `pom.xml` 中是否有未使用的依赖 - 低优先级，暂不处理

---

## 5. 测试代码清理

- [x] `RoleAssignmentComponentProperties.java` - 角色分配组件测试 - 已删除
- [x] `TargetResolverProperties.java` - 已移除 department 相关测试
- [x] `UserRoleServiceProperties.java` - 已移除 department 相关测试

---

## 6. 文档清理

- [x] 更新 API 文档，移除废弃的接口说明 - 已更新 architecture-diagrams.md
- [x] 更新架构文档，反映当前的角色分配机制 - 已更新 table-inconsistency-report.md, database-schema-inconsistency-report.md

---

## 清理记录

### 2026-01-16 (续17)
- 完成：i18n department key 重命名为 businessUnit
  - `zh-CN.ts` - organization.departmentName → organization.businessUnitName 等
  - `zh-TW.ts` - organization.departmentName → organization.businessUnitName 等
  - `en.ts` - organization.departmentName → organization.businessUnitName 等
  - `menu.department` → `menu.businessUnit`
  - `user.department` → `user.businessUnit`
  - `user.selectDepartment` → `user.selectBusinessUnit`
  - `user.departmentPlaceholder` → `user.businessUnitPlaceholder`
  - `user.departmentCode` → `user.businessUnitCode`
  - `organization.createDepartment` → `organization.createBusinessUnit`
  - `organization.editDepartment` → `organization.editBusinessUnit`
  - `organization.searchDepartment` → `organization.searchBusinessUnit`
  - `organization.parentDepartment` → `organization.parentBusinessUnit`
  - `organization.departmentName` → `organization.businessUnitName`
  - `organization.departmentCode` → `organization.businessUnitCode`
- 完成：更新 Vue 组件使用新的 i18n key
  - `BusinessUnitTree.vue` - 更新所有 organization.department* 引用
  - `BusinessUnitFormDialog.vue` - 更新所有 organization.department* 引用
- 验证：所有清理任务已完成，包括可选优化任务

### 2026-01-16 (续16)
- 完成：清理前端向后兼容别名
  - 删除 `frontend/admin-center/src/api/department.ts`（只是重新导出 businessUnit.ts）
  - 移除 `businessUnit.ts` 中的 `departmentApi` 别名和 manager 相关字段
  - 移除 `organization.ts` 中的 `Department` 类型别名
  - 移除 `organization.ts` store 中的 `departmentTree` 等别名
  - 移除 `user.ts` 中的 `departmentId/departmentName` 字段
- 验证：所有清理任务已完成
  - 高优先级任务：数据库表和字段清理完成
  - 低优先级任务：前端向后兼容别名清理完成

### 2026-01-16 (续15)
- 完成：删除 sys_departments 表
  - 创建 `V5__drop_sys_departments_table.sql` 迁移脚本（platform-security）
  - 从 `V1__init_schema.sql` 中移除 sys_departments 表定义和相关索引
  - 移除 `COMMENT ON TABLE sys_departments` 语句
  - 执行 DROP TABLE 删除数据库中的表
- 修复：V14 迁移脚本版本冲突
  - 将 `V5__drop_business_unit_manager_fields.sql` 重命名为 `V14__drop_business_unit_manager_fields.sql`（admin-center 已有 V5）
  - 手动执行 ALTER TABLE 删除 manager_id 和 secondary_manager_id 列
- 验证：所有高优先级清理任务已完成
  - sys_business_units 表已移除 manager_id/secondary_manager_id 列
  - sys_departments 表已删除
  - V1__init_schema.sql 已更新

### 2026-01-16 (续14)
- 完成：移除 BusinessUnit 实体中的 managerId/secondaryManagerId 字段
  - `BusinessUnit.java` - 移除 managerId, secondaryManagerId 字段
  - `BusinessUnitTree.java` - 移除 managerId, managerName, leaderName, secondaryManagerId, secondaryManagerName 字段
  - `DepartmentTree.java` - 移除 managerId, managerName, leaderName, secondaryManagerId, secondaryManagerName 字段
  - `BusinessUnitCreateRequest.java` - 移除 managerId, secondaryManagerId 字段
  - `BusinessUnitUpdateRequest.java` - 移除 managerId, secondaryManagerId 字段
  - `DepartmentCreateRequest.java` - 移除 managerId, secondaryManagerId 字段
  - `DepartmentUpdateRequest.java` - 移除 managerId, secondaryManagerId 字段
  - `OrganizationManagerComponent.java` - 移除所有 manager 相关代码
- 新增：V5 迁移脚本 `V5__drop_business_unit_manager_fields.sql`
  - 删除 sys_business_units 表中的 manager_id 和 secondary_manager_id 列
- 注意：审批人现在通过 sys_approvers 表管理，不再使用 manager_id/secondary_manager_id

### 2026-01-16 (续13)
- 审查：更新 TODO-cleanup.md 文档，整理剩余清理任务
- 确认：sys_departments 表已废弃，清理脚本已准备好
- 确认：BusinessUnit.java 中的 managerId/secondaryManagerId 字段待清理
- 计划：需要创建 V5 迁移脚本删除 sys_business_units 表中的 manager_id/secondary_manager_id 字段

### 2026-01-16 (续12)
- 完成：移除 primaryBusinessUnitId（用户与业务单元是多对多关系，无需主业务单元）
  - `User.java` (model) - 移除 primaryBusinessUnitId 字段
  - `UserPrincipal.java` - 移除 primaryBusinessUnitId, primaryBusinessUnitName 字段
  - `PlatformConstants.java` - 移除 JWT_CLAIM_PRIMARY_BUSINESS_UNIT_ID 常量
  - `JwtTokenService.java` - 移除 primaryBusinessUnitId 参数（5参数 → 4参数）
  - `JwtTokenServiceImpl.java` - 移除 CLAIM_PRIMARY_BUSINESS_UNIT_ID 常量和相关代码
  - `AuthenticationServiceImpl.java` - 移除 getPrimaryBusinessUnitId() 调用
  - `SecurityContextUtils.java` - 移除 getCurrentPrimaryBusinessUnitId() 方法
  - `UserInfo.java` - 移除 primaryBusinessUnitId 字段（7字段 → 6字段）
  - `DataInitializer.java` - 移除 primaryBusinessUnitId 参数
  - `JwtTokenPropertyTest.java` - 移除 primaryBusinessUnitId 测试参数
  - `LogoutBlacklistPropertyTest.java` - 移除 primaryBusinessUnitId 参数
  - `TokenRefreshPropertyTest.java` - 移除 primaryBusinessUnitId 参数
  - `V1__init_schema.sql` - 移除 primary_business_unit_id 列和索引
  - `V4__drop_primary_business_unit_id.sql` - 新增迁移脚本，删除列和索引
- 数据库迁移已执行：DROP INDEX idx_sys_users_primary_business_unit; ALTER TABLE sys_users DROP COLUMN primary_business_unit_id;
- 重新构建 platform-common 和 platform-security 模块
- 重启所有后端服务

### 2026-01-16 (续11)
- 完成：JWT Token department → primaryBusinessUnitId 迁移
  - `DataInitializer.java` - 更新 createUserIfNotExists() 方法参数为 primaryBusinessUnitId，更新测试用户的业务单元ID格式（bu-xxx）
  - `JwtTokenPropertyTest.java` - 更新测试用例使用 primaryBusinessUnitId
  - `V1__init_schema.sql` - 更新 sys_users 表使用 primary_business_unit_id 列，移除 fk_user_department 外键约束
  - `V3__rename_department_to_business_unit.sql` - 新增迁移脚本，用于现有数据库的列重命名
- 注意：platform-common 和 platform-security 模块已在之前的会话中更新完成
  - UserPrincipal.java - primaryBusinessUnitId, primaryBusinessUnitName
  - PlatformConstants.java - JWT_CLAIM_PRIMARY_BUSINESS_UNIT_ID
  - JwtTokenService.java - generateToken 方法签名
  - JwtTokenServiceImpl.java - CLAIM_PRIMARY_BUSINESS_UNIT_ID
  - AuthenticationServiceImpl.java - getPrimaryBusinessUnitId()
  - SecurityContextUtils.java - getCurrentPrimaryBusinessUnitId()
  - User.java (model) - primaryBusinessUnitId
  - UserInfo.java - primaryBusinessUnitId

### 2026-01-16 (续7)
- 完成：清理 `TaskQueryComponent.java` - 删除 `getUserDeptRoles()` 方法，更新 `queryTasks()` 不再传递 deptRoles 参数
- 完成：清理 `TaskProcessComponent.java` - 删除 `isUserHasDeptRole()` 方法，更新 `canClaimTask()` 和 `canProcessTask()` 移除 DEPT_ROLE 分支
- 完成：清理 `TaskQueryProperties.java` - 移除 departmentRoles mock 数据，删除 `deptRoleTasksShouldBeVisibleToQualifiedUsers` 测试
- 完成：清理 `TaskProcessProperties.java` - 移除 departmentRoles mock 数据，删除 `deptRoleTaskCanBeClaimedByQualifiedUser` 测试
- 完成：清理前端 department 相关代码
  - `frontend/admin-center/src/api/auth.ts` - 移除 DEPARTMENT/DEPARTMENT_HIERARCHY 类型和 departmentId 字段
  - `frontend/admin-center/src/api/dashboard.ts` - 将 totalDepartments 改为 totalBusinessUnits
  - `frontend/admin-center/src/views/dashboard/index.vue` - 更新统计卡片显示"业务单元数量"
  - `frontend/developer-workstation/src/api/adminCenter.ts` - 删除 DepartmentTree 接口和相关方法
  - `backend/admin-center/src/main/java/com/admin/dto/response/DashboardStats.java` - 将 totalDepartments 改为 totalBusinessUnits
  - `backend/admin-center/src/main/java/com/admin/controller/DashboardController.java` - 更新使用 totalBusinessUnits

### 2026-01-16 (续6)
- 完成：删除 `DepartmentResult.java` DTO 类（未使用）
- 完成：清理 `RoleAccessComponent.java` - 删除 getDepartments() 和 getDepartmentById() 方法
- 完成：清理 `PermissionComponent.java` - 删除 getDepartments() 方法，将 getDepartmentById() 改为使用 BusinessUnit API
- 完成：清理 `PermissionController.java` - 删除 `/departments` 端点
- 完成：清理 `UserRepository.java` - 删除未使用的 findByDepartmentId() 方法

### 2026-01-16 (续5)
- 更新：发现更多需要清理的 department 相关代码
  - 后端：
    - `backend/admin-center/src/main/java/com/admin/dto/response/DepartmentResult.java` - 未使用的 DTO 类
    - `backend/user-portal/src/main/java/com/portal/entity/User.java` - departmentId 字段
    - `backend/user-portal/src/main/java/com/portal/component/RoleAccessComponent.java` - getDepartments() 和 getDepartmentById() 方法
    - `backend/user-portal/src/main/java/com/portal/component/PermissionComponent.java` - getDepartments() 方法
    - `backend/user-portal/src/main/java/com/portal/controller/PermissionController.java` - `/departments` 端点
    - `backend/user-portal/src/main/java/com/portal/component/TaskQueryComponent.java` - departmentRoles 相关代码
    - `backend/user-portal/src/main/java/com/portal/component/TaskProcessComponent.java` - isUserHasDeptRole() 方法
    - `backend/user-portal/src/test/java/com/portal/properties/TaskQueryProperties.java` - departmentRoles 测试数据
    - `backend/user-portal/src/test/java/com/portal/properties/TaskProcessProperties.java` - departmentRoles 测试数据
    - `backend/developer-workstation/src/main/java/com/developer/entity/User.java` - departmentId 字段
    - `backend/platform-security/src/main/java/com/platform/security/repository/UserRepository.java` - findByDepartmentId() 方法
    - `backend/platform-security/src/main/java/com/platform/security/model/User.java` - departmentId 字段
    - `backend/platform-security/src/main/java/com/platform/security/dto/UserInfo.java` - departmentId 字段
    - `backend/platform-security/src/main/java/com/platform/security/config/DataInitializer.java` - departmentId 参数
  - 前端：
    - `frontend/admin-center/src/api/auth.ts` - AssignmentTargetType 中的 DEPARTMENT/DEPARTMENT_HIERARCHY
    - `frontend/admin-center/src/api/auth.ts` - UserInfo 中的 departmentId 字段
    - `frontend/admin-center/src/api/dashboard.ts` - totalDepartments 字段
    - `frontend/developer-workstation/src/api/adminCenter.ts` - DepartmentTree 接口和相关方法
- 注意：这些代码调用了已删除的 DepartmentController API，需要评估是否改为调用 BusinessUnit API 或直接删除
- 注意：向后兼容别名（如 departmentApi、Department 类型）可以保留，但应在未来版本中移除

### 2026-01-16 (续4)
- 完成：workflow-engine-core 中的 DEPT_ROLE 相关代码清理
  - 移除 `AssignmentType.DEPT_ROLE` 枚举值
  - 更新 `UserPermissionService.java` - 移除 department 相关方法
  - 更新 `AdminCenterClient.java` - 移除所有 department 相关方法
  - 更新 `TaskAssignmentRequest.java` - 移除 DEPT_ROLE 相关代码
  - 更新 `TaskManagerComponent.java` - 移除 DEPT_ROLE 相关代码
  - 更新 `TaskController.java` - 移除 departmentRoles 返回字段
  - 更新测试文件：
    - `MultiDimensionalTaskQueryProperties.java` - 移除 departmentRoleTasksVisibilityFilterAccuracy 测试
    - `TaskDelegationPermissionProperties.java` - 移除 departmentRoleTaskDelegationPermission 测试
    - `MultiDimensionalTaskAssignmentProperties.java` - 移除 DEPT_ROLE 相关测试
- 注意：新的任务分配机制使用 `AssigneeType` 枚举（9种标准类型）和 `TaskAssigneeResolver` 服务

### 2026-01-16 (续3)
- 完成：i18n 清理
  - 移除 admin-center 三个语言文件中未使用的翻译 key：`role.assignmentType`, `role.user`, `role.department`, `role.virtualGroup`, `role.selectDepartment`, `role.selectVirtualGroup`
  - 保留 `role.addMember` 和 `role.selectUser`（仍在虚拟组/业务单元成员管理中使用）
- 完成：废弃的角色分配相关代码清理
  - 删除 `RoleAssignmentComponentProperties.java` 测试文件
  - 删除 `CreateAssignmentRequest.java` DTO
  - 删除 `RoleAssignmentResponse.java` DTO
  - 删除 `EffectiveUserResponse.java` DTO
- 完成：workflow-engine-core 中的 HistoryStatisticsResult.java
  - 将 `departmentStats` 字段重命名为 `businessUnitStats`
- 待处理：AssignmentType 枚举迁移到 AssigneeType（较大重构任务）
  - 旧的 `AssignmentType` 枚举（USER, VIRTUAL_GROUP, DEPT_ROLE）仍在多处使用
  - 新的 `AssigneeType` 枚举已定义9种标准类型
  - 需要逐步迁移测试代码和业务代码

### 2026-01-16 (续2)
- 完成：前端 Department 相关代码清理
  - 更新 `frontend/user-portal/src/api/auth.ts` - 移除 DEPARTMENT/DEPARTMENT_HIERARCHY 类型和 departmentId 字段
  - 更新 `frontend/developer-workstation/src/api/auth.ts` - 移除 DEPARTMENT/DEPARTMENT_HIERARCHY 类型和 departmentId 字段
  - 更新 `frontend/user-portal/src/stores/user.ts` - 移除 UserInfo 接口中的 department 字段
  - 更新 `frontend/user-portal/src/api/permission.ts` - 移除 getDepartments() 方法
  - 更新 `frontend/admin-center/src/views/user/UserList.vue` - 移除 departmentId 查询参数和 flattenDepartments 函数
  - 更新 `frontend/admin-center/src/views/user/UserImport.vue` - 移除 departmentCode 字段
  - 更新 `frontend/user-portal/src/components/FormRenderer.vue` - 将 department 类型改为 businessUnit 类型
  - 更新 `frontend/developer-workstation/src/i18n/locales/*.ts` - 移除 selectDepartment 翻译 key
- 完成：后端 Department 相关代码清理
  - 更新 `backend/workflow-engine-core/.../UserSecurityInfo.java` - 移除 departmentId/departmentName 字段
  - 更新 `backend/workflow-engine-core/.../AuditResourceType.java` - 将 DEPARTMENT 改为 BUSINESS_UNIT
  - 更新 `backend/user-portal/.../LoginResponse.java` - 移除 departmentId 字段
  - 更新 `backend/user-portal/.../AuthController.java` - 移除 departmentId 从登录响应
  - 更新 `backend/developer-workstation/.../LoginResponse.java` - 移除 departmentId 字段
  - 更新 `backend/developer-workstation/.../AuthController.java` - 移除 departmentId 从登录响应

### 2026-01-16 (续)
- 完成：admin-center 模块编译错误修复
  - 更新 `DashboardController.java` - 使用 BusinessUnitRepository 替代 DepartmentRepository
  - 更新 `DepartmentTree.java` - 使用 BusinessUnit 替代 Department
  - 删除 `DepartmentStatus.java` - 使用 BusinessUnitStatus 替代
- 完成：platform-security 模块 Department 相关代码清理
  - 删除 `DepartmentTargetResolver.java`（之前已删除）
  - 删除 `DepartmentHierarchyTargetResolver.java`（之前已删除）
  - 更新 `AssignmentTargetType.java` - 移除 DEPARTMENT 和 DEPARTMENT_HIERARCHY 枚举值
  - 更新 `RoleAssignmentRepository.java` - 移除 department 相关查询方法
  - 更新 `UserRoleServiceImpl.java` - 移除 department 相关逻辑
  - 更新 `UserTargetResolver.java` - 移除 sys_departments 表查询
  - 更新 `VirtualGroupTargetResolver.java` - 移除 sys_departments 表查询
  - 更新 `ResolvedUser.java` - 移除 departmentId/departmentName 字段
  - 更新 `TargetResolverProperties.java` - 移除 department 相关测试
  - 更新 `UserRoleServiceProperties.java` - 移除 department 相关测试
- 完成：admin-center 模块废弃代码清理
  - 删除 `frontend/admin-center/src/api/roleAssignment.ts`
  - 删除 `frontend/admin-center/src/views/role/components/RolePermissionDialog.vue`
  - 删除 `backend/admin-center/src/main/java/com/admin/repository/DepartmentRepository.java`
  - 删除 `backend/admin-center/src/main/java/com/admin/component/RoleAssignmentComponent.java`
  - 删除 `backend/admin-center/src/main/java/com/admin/controller/RoleAssignmentController.java`
  - 删除 `backend/admin-center/src/main/java/com/admin/controller/DepartmentController.java`
  - 删除 `backend/admin-center/src/main/java/com/admin/entity/Department.java`
- 注意：JWT Token 中的 departmentId 字段暂时保留，需要评估是否影响现有功能

### 2026-01-16 (续10)
- 完成：文档清理
  - `docs/table-inconsistency-report.md` - 将 `sys_departments` 改为 `sys_business_units`
  - `docs/database-schema-inconsistency-report.md` - 更新 `sys_departments` 相关内容，标记为已废弃
  - `docs/architecture-diagrams.md` - 将 `admin_departments` 改为 `sys_business_units`，更新 ER 图
- 完成：配置文件检查 - 无 department 相关配置项

### 2026-01-16 (续9)
- 完成：测试数据 SQL 文件清理
  - `deploy/init-scripts/02-test-data/01-organization.sql` - 更新为插入 sys_business_units 数据
  - `deploy/init-scripts/02-test-data/02-organization-detail.sql` - 更新为插入 sys_business_units 数据
  - `deploy/init-scripts/02-test-data/04-department-managers.sql` - 删除，替换为 `04-business-unit-approvers.sql`
  - `deploy/init-scripts/99-utilities/01-cleanup-old-tables.sql` - 添加 sys_departments 表删除脚本
- 注意：DataPermissionProperties.java 中的 `departmentScopeFiltersByDeptId` 测试保留
  - 原因：这是数据权限功能的测试，`DEPARTMENT` 和 `DEPARTMENT_AND_CHILDREN` 是数据范围类型（DataScopeType）
  - 这些类型用于数据过滤（如 `department_id = 'xxx'`），与 sys_departments 表无关
  - 可以在未来版本中将 DataScopeType.DEPARTMENT 重命名为 BUSINESS_UNIT（可选）

### 2026-01-16 (续8)
- 完成：清理 User 实体中的 departmentId 字段
  - `backend/user-portal/src/main/java/com/portal/entity/User.java` - 移除 departmentId 字段
  - `backend/developer-workstation/src/main/java/com/developer/entity/User.java` - 移除 departmentId 字段
- 决定：JWT Token 中的 departmentId 字段暂时保留
  - 原因：该字段深度集成在认证系统中，涉及 JwtTokenService、AuthenticationServiceImpl、UserPrincipal 等多个核心组件
  - 影响范围：platform-security、platform-common、所有使用 JWT 认证的模块
  - 建议：在未来版本中逐步移除，或改为存储用户的主业务单元ID（primaryBusinessUnitId）
  - 相关文件：
    - `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`
    - `backend/platform-common/src/main/java/com/platform/common/dto/UserPrincipal.java`
    - `backend/platform-security/src/main/java/com/platform/security/model/User.java`
    - `backend/platform-security/src/main/java/com/platform/security/dto/UserInfo.java`
- 重启服务：user-portal (8082) 和 developer-workstation (8083) - 启动成功

### 2026-01-16
- 完成：移除 Admin Center 系统监控功能（后续将使用 Splunk/AppDynamics 等专业工具）
  - 删除路由：`/monitor` (SystemMonitor)
  - 删除视图：`frontend/admin-center/src/views/monitor/index.vue`
  - i18n 中的 `menu.monitor` 翻译 key 保留（不影响功能）

### 2026-01-15
- 完成：任务分配机制重构 - 从7种类型更新为9种标准类型
  - 删除旧类型：DEPT_OTHERS, PARENT_DEPT, FIXED_DEPT, VIRTUAL_GROUP
  - 新增类型：CURRENT_BU_ROLE, CURRENT_PARENT_BU_ROLE, INITIATOR_BU_ROLE, INITIATOR_PARENT_BU_ROLE, FIXED_BU_ROLE, BU_UNBOUNDED_ROLE
  - 更新 `AssigneeType.java` 枚举
  - 更新 `TaskAssigneeResolver.java` 解析服务
  - 更新 `TaskAssignmentListener.java` 监听器
  - 新增 `TaskAssignmentController.java` API 控制器
  - 新增 `TaskAssignmentQueryService.java` 查询服务
  - 更新 `AdminCenterClient.java` 客户端
  - 更新 `UserTaskProperties.vue` 前端组件
  - 更新 `adminCenter.ts` 前端 API
  - 更新 i18n 翻译文件（zh-CN, zh-TW, en）
  - 更新 steering 文件（workflow-engine-architecture.md, function-unit-generation.md）
- 待清理：`AdminCenterClient.java` 中的废弃方法
  - `getDepartmentMembers()` - 部门成员查询（已被业务单元角色查询替代）
  - `getVirtualGroupMembers()` - 虚拟组成员查询（已被 BU_UNBOUNDED_ROLE 查询替代）
  - `getDepartmentInfo()` - 部门信息查询
  - `isUserInDepartmentHierarchy()` - 部门层级检查
  - `isDepartmentDescendant()` - 部门后代检查
  - `hasUserDepartmentRole()` - 部门角色检查
  - `getUserDepartmentRoles()` - 用户部门角色查询
- 待清理：`TaskAssigneeResolver.java` 中的废弃方法（已删除）
  - `resolveDeptOthers()` - 本部门其他人解析
  - `resolveParentDept()` - 上级部门解析
  - `resolveFixedDept()` - 指定部门解析
  - `resolveVirtualGroup()` - 虚拟组解析
- 完成：重构 Department 相关前端代码为 BusinessUnit
  - `DepartmentTree.vue` → `BusinessUnitTree.vue`
  - `DepartmentFormDialog.vue` → `BusinessUnitFormDialog.vue`
  - `organization.ts` API 添加 BusinessUnit 类型
  - `organization.ts` store 添加 businessUnitTree 变量
- 完成：将"绑定角色"改为"准入角色"（Eligible Roles），更清晰表达业务含义
  - 更新 i18n：zh-CN, zh-TW, en
  - 更新 BusinessUnitRolesDialog.vue 标题和说明文字
- 完成：组织架构页面布局优化
  - 成员列表和审批人列表两列并排显示
  - 表格简化为只显示用户名（可点击）和姓名
  - 点击用户名可查看用户详情弹窗
- 记录：角色分配功能简化，角色只能分配给虚拟组
- 记录：虚拟组有效期功能移除
- 记录：`roleAssignment.ts` API 文件不再使用
- 记录：`RoleMembersDialog.vue` 已简化，移除分配功能
- 记录：用户与业务单元是多对多关系，移除用户表单/列表中的 department 字段显示
  - `UserList.vue` - 移除 departmentName 列
  - `UserFormDialog.vue` - 移除 department 选择字段
  - `UserDetailDialog.vue` - 移除 department 显示
  - `VirtualGroupMembersDialog.vue` - 移除 departmentName 列
  - `profile/index.vue` - 移除 department 显示
- 待清理：后端 User 实体和 DTO 中的 departmentId/departmentName 字段（如果不再需要）
- 修复：组织架构页面无数据问题 - 数据从 `sys_departments` 复制到 `sys_business_units` 表
- 待清理：`sys_departments` 表和 `Department` 实体（数据已迁移到 `sys_business_units`）
- 完成：将测试用户添加到 sys_user_business_units 关联表（43条记录）
- 完成：为8个用户配置多业务单元关系（admin, tech.director, core.lead, channel.lead, risk.lead, dev.john, corp.director, hr.manager）
- 待清理：`sys_users.department_id` 和 `sys_users.business_unit_id` 字段（应使用 sys_user_business_units 多对多关联表）
- 完成：移除业务单元负责人和副经理字段（前端），改用 approvers 列表管理审批人
  - `DepartmentTree.vue` - 移除 leader/secondaryManager 显示
  - `DepartmentFormDialog.vue` - 移除 managerId/secondaryManagerId 表单字段
  - `organization.ts` - 移除 Department 接口中的 manager 相关字段
- 待清理：后端 BusinessUnit 实体和 DTO 中的 managerId/secondaryManagerId 字段
- 待清理：数据库 sys_business_units 表中的 manager_id/secondary_manager_id 字段

---

## 注意事项

1. **清理前备份**：在删除任何代码前，确保已提交到版本控制
2. **逐步清理**：建议分批次清理，每次清理后进行测试
3. **保持向后兼容**：如果有外部系统依赖某些 API，需要先确认再删除
4. **数据库迁移**：删除表或字段需要通过 Flyway 迁移脚本进行
5. **测试覆盖**：清理后确保所有测试通过
6. **JWT Token 中的 departmentId**：此字段涉及认证系统，需要谨慎评估是否移除，可能影响多个模块

---

## 清理进度总结

### 已完成的主要清理工作

1. **Department 相关代码清理** - 已完成
   - 前端：移除了 DEPARTMENT/DEPARTMENT_HIERARCHY 类型、departmentId 字段、相关 API 方法
   - 后端：移除了 DepartmentController、DepartmentRepository、Department 实体、相关 Resolver
   - 测试：移除了 department 相关测试用例
   - 文档：更新了架构图和数据库文档

2. **角色分配机制重构** - 完成
   - 从旧的 AssignmentType (USER, VIRTUAL_GROUP, DEPT_ROLE) 迁移到新的 AssigneeType (9种标准类型)
   - 删除了 RoleAssignmentController、RoleAssignmentComponent 等废弃代码

3. **User 实体 departmentId 字段** - 完成
   - ✅ user-portal/entity/User.java - 已移除
   - ✅ developer-workstation/entity/User.java - 已移除
   - ✅ platform-security/model/User.java - 已更新为 primaryBusinessUnitId
   - ✅ platform-security/dto/UserInfo.java - 已更新为 primaryBusinessUnitId
   - ✅ platform-security/config/DataInitializer.java - 已更新为 primaryBusinessUnitId

4. **JWT Token department → primaryBusinessUnitId 迁移** - 完成
   - ✅ UserPrincipal.java - primaryBusinessUnitId, primaryBusinessUnitName
   - ✅ PlatformConstants.java - JWT_CLAIM_PRIMARY_BUSINESS_UNIT_ID
   - ✅ JwtTokenService.java - generateToken 方法签名
   - ✅ JwtTokenServiceImpl.java - CLAIM_PRIMARY_BUSINESS_UNIT_ID
   - ✅ AuthenticationServiceImpl.java - getPrimaryBusinessUnitId()
   - ✅ SecurityContextUtils.java - getCurrentPrimaryBusinessUnitId()
   - ✅ JwtTokenPropertyTest.java - 测试用例已更新

5. **primaryBusinessUnitId 完全移除** - 完成（用户与业务单元是多对多关系，无需主业务单元）
   - ✅ User.java (model) - 移除 primaryBusinessUnitId 字段
   - ✅ UserPrincipal.java - 移除 primaryBusinessUnitId, primaryBusinessUnitName 字段
   - ✅ PlatformConstants.java - 移除 JWT_CLAIM_PRIMARY_BUSINESS_UNIT_ID 常量
   - ✅ JwtTokenService.java - 移除 primaryBusinessUnitId 参数
   - ✅ JwtTokenServiceImpl.java - 移除 CLAIM_PRIMARY_BUSINESS_UNIT_ID 常量和相关代码
   - ✅ AuthenticationServiceImpl.java - 移除 getPrimaryBusinessUnitId() 调用
   - ✅ SecurityContextUtils.java - 移除 getCurrentPrimaryBusinessUnitId() 方法
   - ✅ UserInfo.java - 移除 primaryBusinessUnitId 字段
   - ✅ DataInitializer.java - 移除 primaryBusinessUnitId 参数
   - ✅ JwtTokenPropertyTest.java - 移除 primaryBusinessUnitId 测试
   - ✅ LogoutBlacklistPropertyTest.java - 移除 primaryBusinessUnitId 参数
   - ✅ TokenRefreshPropertyTest.java - 移除 primaryBusinessUnitId 参数
   - ✅ V1__init_schema.sql - 移除 primary_business_unit_id 列和索引
   - ✅ V4__drop_primary_business_unit_id.sql - 新增迁移脚本，删除列和索引

6. **数据库迁移** - 完成
   - ✅ V1__init_schema.sql - 已更新 sys_users 表移除 primary_business_unit_id
   - ✅ V3__rename_department_to_business_unit.sql - 迁移脚本（历史）
   - ✅ V4__drop_primary_business_unit_id.sql - 新增迁移脚本，删除 primary_business_unit_id 列

6. **测试数据 SQL 文件** - 已完成
   - ✅ 01-organization.sql - 已更新为 sys_business_units
   - ✅ 02-organization-detail.sql - 已更新为 sys_business_units
   - ✅ 04-business-unit-approvers.sql - 新文件替代旧的 department-managers.sql
   - ✅ 01-cleanup-old-tables.sql - 已添加 sys_departments 删除脚本

5. **文档清理** - 已完成
   - ✅ table-inconsistency-report.md - 已更新
   - ✅ database-schema-inconsistency-report.md - 已更新
   - ✅ architecture-diagrams.md - 已更新

6. **配置文件检查** - 已完成
   - ✅ application.yml 无 department 相关配置
   - ✅ pom.xml 无 department 相关依赖

### 待处理的清理工作

所有高优先级和低优先级清理任务已完成。

**可选的未来优化：**
- ✅ i18n 中的 department key 已重命名为 businessUnit（已完成）

所有清理任务已完成！

---

## 下一步清理计划

### 已完成
1. ✅ 创建 V14 迁移脚本删除 `sys_business_units.manager_id/secondary_manager_id` 字段
2. ✅ 更新 `BusinessUnit.java` 实体移除 managerId/secondaryManagerId 字段
3. ✅ 创建 V5 迁移脚本删除 `sys_departments` 表
4. ✅ 从 V1__init_schema.sql 中移除 sys_departments 表定义
5. ✅ 清理前端向后兼容别名（departmentApi、Department 类型、departmentTree 等）
6. ✅ 清理前端 user.ts 中的 departmentId/departmentName 字段
7. ✅ 清理前端 businessUnit.ts 中的 manager 相关字段

### 可选优化（不影响功能）
1. ✅ 重命名 i18n 中的 department key 为 businessUnit - 已完成
