# 代码清理任务表

> 本文档记录开发后期需要清理的无用代码和待优化项。
> 
> 状态说明：
> - [ ] 待处理
> - [x] 已完成

## 1. 前端代码清理

### 1.1 admin-center

#### 废弃的 API 文件
- [ ] `frontend/admin-center/src/api/roleAssignment.ts` - 角色分配 API（角色只能分配给虚拟组，不再需要直接分配功能）

#### 废弃的组件/功能
- [ ] 检查 `RolePermissionDialog.vue` 是否还在使用
- [ ] 虚拟组有效期相关代码已移除，确认无残留

#### Department 相关代码（需重构为 BusinessUnit）
- [x] `frontend/admin-center/src/views/organization/components/DepartmentFormDialog.vue` - 已重命名为 BusinessUnitFormDialog.vue
- [x] `frontend/admin-center/src/views/organization/DepartmentTree.vue` - 已重命名为 BusinessUnitTree.vue，变量名已改为 businessUnit
- [x] `frontend/admin-center/src/stores/organization.ts` - 已添加 businessUnitTree 变量名（保留 departmentTree 别名向后兼容）
- [x] `frontend/admin-center/src/api/organization.ts` - 已添加 BusinessUnit 类型定义（保留 Department 别名向后兼容）
- [ ] `frontend/admin-center/src/views/user/UserList.vue` - 使用 departmentId 查询参数和 flattenDepartments 函数
- [ ] `frontend/admin-center/src/views/user/UserImport.vue` - 使用 departmentCode 字段

#### i18n 清理
- [ ] 检查并移除未使用的翻译 key（如 `role.addMember`, `role.assignmentType` 等分配相关的翻译）
- [ ] i18n 中 department 相关 key 已改为显示 "Business Unit"，但 key 名仍为 department（可选择性重命名）

### 1.2 user-portal

- [ ] `frontend/user-portal/src/stores/user.ts` - UserInfo 接口中的 department 字段
- [ ] `frontend/user-portal/src/api/permission.ts` - getDepartments() 方法
- [ ] `frontend/user-portal/src/api/auth.ts` - AssignmentTargetType 中的 DEPARTMENT/DEPARTMENT_HIERARCHY 类型，departmentId 字段
- [ ] `frontend/user-portal/src/components/FormRenderer.vue` - department 类型字段渲染

### 1.3 developer-workstation

- [ ] `frontend/developer-workstation/src/api/auth.ts` - AssignmentTargetType 中的 DEPARTMENT/DEPARTMENT_HIERARCHY 类型，departmentId 字段
- [ ] `frontend/developer-workstation/src/i18n/locales/*.ts` - selectDepartment 翻译 key

---

## 2. 后端代码清理

### 2.1 admin-center

#### 废弃的角色分配功能
- [ ] `RoleAssignmentController.java` - 角色分配控制器（角色只能通过虚拟组分配）
- [ ] `RoleAssignmentComponent.java` - 角色分配组件
- [ ] `RoleAssignmentComponentProperties.java` - 角色分配属性测试
- [ ] `platform-security` 模块中的 `RoleAssignment` 实体和 `RoleAssignmentRepository`
- [ ] 相关的 DTO：`CreateAssignmentRequest`, `RoleAssignmentResponse`, `EffectiveUserResponse`

#### Department 相关代码（需清理或重构）
- [ ] `backend/admin-center/src/main/java/com/admin/entity/Department.java` - 废弃的 Department 实体（数据已迁移到 BusinessUnit）
- [ ] `backend/admin-center/src/main/java/com/admin/repository/DepartmentRepository.java` - 废弃的 Department 仓库
- [ ] `backend/admin-center/src/main/java/com/admin/controller/DepartmentController.java` - 废弃的 Department 控制器（已标记 @Deprecated）

#### 虚拟组有效期相关
- [ ] 检查 `VirtualGroup` 实体是否还有 `validFrom`/`validTo` 字段
- [ ] 检查数据库迁移文件是否需要清理这些字段

### 2.2 platform-security

#### Department 相关代码
- [ ] `backend/platform-security/src/main/java/com/platform/security/resolver/DepartmentTargetResolver.java` - Department 目标解析器
- [ ] `backend/platform-security/src/main/java/com/platform/security/resolver/DepartmentHierarchyTargetResolver.java` - Department 层级解析器（如存在）
- [ ] `backend/platform-security/src/main/java/com/platform/security/resolver/UserTargetResolver.java` - 查询 sys_departments 表
- [ ] `backend/platform-security/src/main/java/com/platform/security/resolver/VirtualGroupTargetResolver.java` - 查询 sys_departments 表
- [ ] `backend/platform-security/src/main/java/com/platform/security/service/impl/UserRoleServiceImpl.java` - 查询 sys_departments 表
- [ ] `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` - sys_departments 表定义和外键约束

#### 其他
- [ ] 检查 `RoleAssignment` 相关代码
- [ ] 检查 `TargetResolver` 相关代码（用于解析分配目标）

### 2.3 platform-common

#### Department 相关字段
- [ ] `backend/platform-common/src/main/java/com/platform/common/dto/UserPrincipal.java` - departmentId, departmentName 字段
- [ ] `backend/platform-common/src/main/java/com/platform/common/constant/PlatformConstants.java` - JWT_CLAIM_DEPARTMENT_ID 常量

### 2.4 workflow-engine-core

#### Department 相关代码
- [ ] `backend/workflow-engine-core/src/main/java/com/workflow/dto/response/UserSecurityInfo.java` - departmentId, departmentName 字段
- [ ] `backend/workflow-engine-core/src/main/java/com/workflow/dto/response/HistoryStatisticsResult.java` - departmentStats 字段
- [ ] `backend/workflow-engine-core/src/main/java/com/workflow/service/UserPermissionService.java` - hasUserDepartmentRole, getUserDepartmentRoles, isUserInDepartmentHierarchy 方法
- [ ] `backend/workflow-engine-core/src/main/java/com/workflow/service/TaskAssigneeResolver.java` - 使用 departmentId 解析任务分配
- [ ] `backend/workflow-engine-core/src/main/java/com/workflow/enums/AuditResourceType.java` - DEPARTMENT 枚举值
- [ ] `backend/workflow-engine-core/src/test/java/com/workflow/properties/MultiDimensionalTaskQueryProperties.java` - departmentRoleTasksVisibilityFilterAccuracy 测试
- [ ] `backend/workflow-engine-core/src/test/java/com/workflow/properties/TaskDelegationPermissionProperties.java` - departmentRoleTaskDelegationPermission 测试

### 2.5 user-portal

- [ ] 待检查

---

## 3. 数据库清理

### 3.1 废弃的表
- [ ] `sys_role_assignments` - 角色分配表（如果确认不再使用）
- [ ] `sys_departments` - 旧的部门表（数据已迁移到 `sys_business_units`，需要确认后删除）

### 3.2 废弃的字段
- [ ] `sys_virtual_groups.valid_from` - 虚拟组有效期开始（已移除）
- [ ] `sys_virtual_groups.valid_to` - 虚拟组有效期结束（已移除）
- [ ] `sys_users.department_id` - 用户部门ID（用户与业务单元是多对多关系，应使用 sys_user_business_units 关联表）
- [ ] `sys_users.business_unit_id` - 用户业务单元ID（同上，单一字段不适合多对多关系）
- [ ] `idx_sys_users_department` - department_id 索引（随字段一起删除）
- [ ] `fk_user_department` - department_id 外键约束（随字段一起删除）
- [ ] `sys_business_units.manager_id` - 业务单元负责人（已改用 sys_approvers 表管理审批人）
- [ ] `sys_business_units.secondary_manager_id` - 业务单元副经理（已改用 sys_approvers 表管理审批人）

### 3.3 Flyway 迁移文件
- [ ] `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` - 包含 sys_departments 表定义，需要评估是否移除或保留
- [ ] 检查是否需要添加清理迁移脚本

### 3.4 测试数据 SQL 文件
- [ ] `deploy/init-scripts/02-test-data/01-organization.sql` - 插入 sys_departments 数据
- [ ] `deploy/init-scripts/02-test-data/02-organization-detail.sql` - 插入 sys_departments 数据
- [ ] `deploy/init-scripts/02-test-data/04-department-managers.sql` - 更新 sys_departments 数据
- [ ] `deploy/init-scripts/99-utilities/01-cleanup-old-tables.sql` - 引用 sys_departments 表

---

## 4. 配置文件清理

- [ ] 检查 `application.yml` / `application-dev.yml` 中是否有废弃的配置项
- [ ] 检查 `pom.xml` 中是否有未使用的依赖

---

## 5. 测试代码清理

- [ ] `RoleAssignmentComponentProperties.java` - 角色分配组件测试
- [ ] 检查其他测试文件中是否有废弃的测试用例

---

## 6. 文档清理

- [ ] 更新 API 文档，移除废弃的接口说明
- [ ] 更新架构文档，反映当前的角色分配机制

---

## 清理记录

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
