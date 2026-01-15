# Implementation Plan: Permission Request and Approval System (Redesign)

## Overview

本实现计划将重新设计权限申请和审批系统，主要变更包括：
1. 虚拟组单角色绑定（每个虚拟组只能绑定一个角色）
2. 角色类型拆分（BUSINESS -> BU_BOUNDED + BU_UNBOUNDED）
3. 移除"业务单元+角色"直接申请，改为先加入虚拟组获取角色，再申请加入业务单元
4. 虚拟组添加 AD Group 字段

## Tasks

### Phase 1: 数据模型变更

- [x] 1. 更新角色类型枚举
  - [x] 1.1 修改 RoleType 枚举
    - 修改 `backend/admin-center/src/main/java/com/admin/enums/RoleType.java`
    - 将 `BUSINESS` 拆分为 `BU_BOUNDED` 和 `BU_UNBOUNDED`
    - _Requirements: 3.1, 3.2_
  - [x] 1.2 创建数据库迁移脚本
    - 创建 `V5__role_type_split.sql`
    - 更新现有 BUSINESS 角色为 BU_BOUNDED（默认）
    - _Requirements: 3.1_

- [x] 2. 更新虚拟组实体
  - [x] 2.1 添加 adGroup 字段
    - 修改 `backend/admin-center/src/main/java/com/admin/entity/VirtualGroup.java`
    - 添加 `adGroup` 字段
    - _Requirements: 6.1, 6.2_
  - [x] 2.2 创建数据库迁移脚本
    - 创建 `V6__virtual_group_ad_group.sql`
    - 添加 `ad_group` 列到 `sys_virtual_groups` 表
    - _Requirements: 6.1_

- [x] 3. 更新虚拟组角色绑定为单角色
  - [x] 3.1 修改 VirtualGroupRole 实体
    - 修改 `backend/admin-center/src/main/java/com/admin/entity/VirtualGroupRole.java`
    - 添加 `virtual_group_id` 唯一约束
    - _Requirements: 2.1, 2.7_
  - [x] 3.2 创建数据库迁移脚本
    - 创建 `V7__virtual_group_single_role.sql`
    - 添加唯一约束，清理重复绑定
    - _Requirements: 2.7_
  - [x] 3.3 编写单角色约束属性测试
    - **Property 2: Single Role Per Virtual Group**
    - **Validates: Requirements 2.1, 2.7**

- [x] 4. 创建用户业务单元成员关系表
  - [x] 4.1 创建 UserBusinessUnit 实体
    - 创建 `backend/admin-center/src/main/java/com/admin/entity/UserBusinessUnit.java`
    - _Requirements: 8.1, 11.1_
  - [x] 4.2 创建 UserBusinessUnitRepository
    - 创建 `backend/admin-center/src/main/java/com/admin/repository/UserBusinessUnitRepository.java`
    - _Requirements: 8.1, 11.1_
  - [x] 4.3 创建数据库迁移脚本
    - 创建 `V8__user_business_units.sql`
    - 创建 `sys_user_business_units` 表
    - _Requirements: 8.1_

- [x] 5. 更新权限申请实体
  - [x] 5.1 修改 PermissionRequest 实体
    - 修改 `backend/admin-center/src/main/java/com/admin/entity/PermissionRequest.java`
    - 移除 `roleIds` 字段
    - _Requirements: 7.3, 8.2_
  - [x] 5.2 修改 PermissionRequestType 枚举
    - 修改 `backend/admin-center/src/main/java/com/admin/enums/PermissionRequestType.java`
    - 将 `BUSINESS_UNIT_ROLE` 改为 `BUSINESS_UNIT`
    - _Requirements: 7.3, 8.2_
  - [x] 5.3 创建数据库迁移脚本
    - 创建 `V9__permission_request_update.sql`
    - 移除 `role_ids` 列，更新 `request_type` 值
    - _Requirements: 7.3, 8.2_

### Phase 2: 服务层变更

- [x] 6. 更新虚拟组角色绑定服务
  - [x] 6.1 修改 VirtualGroupRoleService
    - 修改 `backend/admin-center/src/main/java/com/admin/service/VirtualGroupRoleService.java`
    - 更新 `bindRole` 方法为替换现有绑定
    - 添加 `getBoundRole` 方法（返回单个角色）
    - 移除 `getBoundRoles` 方法（返回列表）
    - _Requirements: 2.1, 2.3, 2.4_
  - [x] 6.2 更新角色类型验证
    - 验证只接受 BU_BOUNDED 或 BU_UNBOUNDED 类型
    - _Requirements: 2.6_
  - [x] 6.3 编写角色类型验证属性测试
    - **Property 1: Role Type Validation for Virtual Group Binding**
    - **Property 2: Single Role Per Virtual Group**
    - **Validates: Requirements 2.1, 2.6, 2.7**

- [x] 7. 创建用户业务单元服务
  - [x] 7.1 创建 UserBusinessUnitService
    - 创建 `backend/admin-center/src/main/java/com/admin/service/UserBusinessUnitService.java`
    - 实现 `addUserToBusinessUnit`, `removeUserFromBusinessUnit`, `getUserBusinessUnits` 方法
    - _Requirements: 11.1, 15.4, 16.3_

- [x] 8. 更新权限申请服务
  - [x] 8.1 修改 PermissionRequestService
    - 修改 `backend/admin-center/src/main/java/com/admin/service/PermissionRequestService.java`
    - 更新 `createBusinessUnitRoleRequest` 为 `createBusinessUnitRequest`（移除角色选择）
    - 添加 `getApplicableBusinessUnits` 方法（获取用户可申请的业务单元）
    - 添加业务单元申请验证（检查用户是否有关联的 BU_BOUNDED 角色）
    - _Requirements: 8.1, 8.2, 8.10_
  - [x] 8.2 编写申请服务属性测试
    - **Property 5: Duplicate Request Prevention**
    - **Property 6: Request Status Transition**
    - **Property 17: Business Unit Application Restricted to BU-Bounded Role Associations**
    - **Validates: Requirements 7.5, 8.1, 8.4, 8.10, 9.4, 9.5, 13.2, 13.3**

- [x] 9. 更新成员管理服务
  - [x] 9.1 修改 MemberManagementService
    - 修改 `backend/admin-center/src/main/java/com/admin/service/MemberManagementService.java`
    - 更新 `removeBusinessUnitRole` 为 `removeBusinessUnitMember`
    - 更新 `exitBusinessUnitRoles` 为 `exitBusinessUnit`
    - _Requirements: 15.4, 16.3_
  - [x] 9.2 编写成员管理属性测试
    - **Property 11: Member Removal Immediate Effect**
    - **Property 15: Virtual Group Exit Revokes Role**
    - **Property 16: Business Unit Exit Deactivates BU-Bounded Roles**
    - **Validates: Requirements 15.3, 15.4, 16.2, 16.3**

- [x] 10. 创建用户权限查询服务
  - [x] 10.1 创建 UserPermissionService
    - 创建 `backend/admin-center/src/main/java/com/admin/service/UserPermissionService.java`
    - 实现 `getUserRoles`, `getUserBuBoundedRoles`, `getUserBuUnboundedRoles`, `hasRoleInBusinessUnit` 方法
    - 实现 `getUnactivatedBuBoundedRoles` 方法（获取未激活的 BU-Bounded 角色）
    - 实现 `shouldShowBuApplicationReminder` 方法（检查是否需要显示提醒）
    - 实现 `setDontRemindPreference` 方法（设置不再提醒偏好）
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 18.1, 18.2, 18.6, 18.7_
  - [x] 10.2 创建 UserPreference 实体和 Repository
    - 创建 `backend/admin-center/src/main/java/com/admin/entity/UserPreference.java`
    - 创建 `backend/admin-center/src/main/java/com/admin/repository/UserPreferenceRepository.java`
    - _Requirements: 18.7_
  - [x] 10.3 创建数据库迁移脚本
    - 创建 `V10__user_preferences.sql`
    - 创建 `sys_user_preferences` 表
    - _Requirements: 18.7_
  - [x] 10.4 编写用户权限属性测试
    - **Property 12: BU-Bounded Role Activation**
    - **Property 13: BU-Unbounded Role Immediate Effect**
    - **Property 18: Unactivated BU-Bounded Role Reminder**
    - **Validates: Requirements 3.4, 3.5, 10.3, 10.4, 11.2, 18.1, 18.2, 18.6, 18.7, 18.9**

### Phase 3: 控制器层变更

- [x] 11. 更新 Admin Center 控制器
  - [x] 11.1 更新 VirtualGroupController
    - 添加 `adGroup` 字段到创建/更新 API
    - 更新 `VirtualGroupCreateRequest` DTO 添加 `adGroup` 字段
    - 更新 `VirtualGroupInfo` DTO 添加 `adGroup` 字段
    - 更新 `VirtualGroupManagerComponent` 在创建和更新时处理 `adGroup` 字段
    - _Requirements: 6.2, 6.3_
  - [x] 11.2 更新 VirtualGroupRoleController
    - 更新 API 为单角色绑定
    - `GET /virtual-groups/{id}/role` 返回单个角色
    - _Requirements: 2.3_
    - _Note: Already implemented - controller returns single role via getBoundRole()_
  - [x] 11.3 更新 RoleController
    - 添加角色类型（BU_BOUNDED/BU_UNBOUNDED）到创建/更新 API
    - _Requirements: 3.2, 3.3_
    - _Note: Already implemented - CreateRoleRequest includes RoleType field_

- [x] 12. 更新 User Portal 控制器
  - [x] 12.1 更新 PermissionRequestController
    - 更新业务单元申请 API（移除角色选择）
    - `POST /permission-requests/business-unit` 只需要 businessUnitId 和 reason
    - `GET /permission-requests/applicable-business-units` 获取用户可申请的业务单元
    - `GET /permission-requests/business-units/{businessUnitId}/activatable-roles` 获取可激活的角色
    - _Requirements: 8.1, 8.2, 8.3, 8.10_
  - [x] 12.2 更新 ExitController
    - 更新退出业务单元 API
    - `POST /exit/business-unit/{id}` 退出整个业务单元
    - _Requirements: 16.3_
  - [x] 12.3 创建 UserPermissionController
    - `GET /my-permissions` 获取当前用户的权限视图
    - `GET /my-permissions/unactivated-roles` 获取未激活的 BU-Bounded 角色
    - `GET /my-permissions/should-show-reminder` 检查是否需要显示提醒
    - `POST /my-permissions/dont-remind` 设置不再提醒偏好
    - `GET /my-permissions/roles/{roleId}/status` 获取角色状态
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 18.1, 18.2, 18.6, 18.7_
  - [x] 12.4 编写审批控制器属性测试
    - **Property 3: Approver Scope Filtering**
    - **Property 4: Self-Approval Prevention**
    - **Property 9: Approver Menu Visibility**
    - **Property 10: Approver-Only Application Requirement**
    - **Property 14: Rejection Requires Comment**
    - **Validates: Requirements 4.6, 5.6, 7.7, 8.6, 9.2, 9.6, 9.8, 9.9, 12.1, 12.2, 12.3**

### Phase 4: 前端变更

- [x] 13. 更新 Admin Center 前端
  - [x] 13.1 更新虚拟组管理页面
    - 添加 AD Group 字段
    - 更新角色绑定为单选（而非多选）
    - _Requirements: 2.3, 6.2, 6.3_
  - [x] 13.2 更新角色管理页面
    - 添加角色类型选择（BU-Bounded/BU-Unbounded）
    - _Requirements: 3.2, 3.3_
  - [x] 13.3 更新用户管理页面
    - 显示用户的业务单元成员身份
    - 移除业务单元角色分配（改为通过虚拟组获取）
    - _Requirements: 17.1, 17.2_

- [x] 14. 更新 User Portal 前端
  - [x] 14.1 更新权限申请页面
    - 虚拟组申请：显示绑定的角色及类型
    - 业务单元申请：只显示与用户 BU-Bounded 角色关联的业务单元
    - 业务单元申请：显示将激活的 BU-Bounded 角色
    - _Requirements: 7.2, 8.1, 8.9, 8.10_
  - [x] 14.2 更新退出角色页面
    - 更新为退出虚拟组或业务单元
    - _Requirements: 16.1, 16.2, 16.3_
  - [x] 14.3 创建权限视图页面
    - 显示用户的所有角色和业务单元
    - 区分 BU-Bounded 和 BU-Unbounded 角色
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_
  - [x] 14.4 创建 BU-Bounded 角色未激活提醒弹框
    - 在用户登录后检查是否有未激活的 BU-Bounded 角色
    - 显示提醒弹框，列出未激活的角色
    - 提供"立即申请"按钮跳转到业务单元申请页面
    - 提供"稍后提醒"和"不再提醒"选项
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8_

- [x] 15. 更新前端 i18n
  - [x] 15.1 更新 Admin Center i18n
    - 添加 AD Group、BU-Bounded、BU-Unbounded 相关翻译
    - _Requirements: 1.5_
  - [x] 15.2 更新 User Portal i18n
    - 添加权限视图、角色类型相关翻译
    - 添加 BU-Bounded 角色未激活提醒相关翻译
    - _Requirements: 1.5, 18.3, 18.4, 18.5, 18.6_

### Phase 5: 测试和验证

- [x] 16. 编写集成测试
  - [x] 16.1 虚拟组申请-审批-生效流程测试
    - **Property 7: Virtual Group Approval Immediate Effect**
    - **Validates: Requirements 10.1, 10.2**
  - [x] 16.2 业务单元申请-审批-生效流程测试
    - **Property 8: Business Unit Approval Immediate Effect**
    - **Validates: Requirements 11.1, 11.2**
  - [x] 16.3 BU-Bounded 角色两阶段激活流程测试
    - 测试用户加入虚拟组获取 BU-Bounded 角色后，再加入业务单元激活角色
    - _Requirements: 3.4, 10.4, 11.2_
  - [x] 16.4 BU-Unbounded 角色即时生效流程测试
    - 测试用户加入虚拟组获取 BU-Unbounded 角色后立即生效
    - _Requirements: 3.5, 10.3_

- [x] 17. Checkpoint - 确保所有代码编译通过
  - 确保 admin-center 编译通过
  - 确保 user-portal 编译通过
  - 确保 frontend/admin-center 编译通过
  - 确保 frontend/user-portal 编译通过
  - 运行所有属性测试确保通过

## Notes

- 本次重构是对现有系统的重大变更，需要谨慎处理数据迁移
- 数据库迁移脚本需要处理现有数据的转换
- 前端需要同步更新以适应新的 API 结构
- 所有属性测试都是必须完成的，确保系统正确性

## Property Tests Summary

| Property | Description | Validates |
|----------|-------------|-----------|
| 1 | Role Type Validation for Virtual Group Binding | Req 2.6 |
| 2 | Single Role Per Virtual Group | Req 2.1, 2.7 |
| 3 | Approver Scope Filtering | Req 9.2, 9.9 |
| 4 | Self-Approval Prevention | Req 9.8, 9.9 |
| 5 | Duplicate Request Prevention | Req 7.5, 8.5 |
| 6 | Request Status Transition | Req 9.4, 9.5, 13.2, 13.3 |
| 7 | Virtual Group Approval Immediate Effect | Req 10.1, 10.2 |
| 8 | Business Unit Approval Immediate Effect | Req 11.1, 11.2 |
| 9 | Approver Menu Visibility | Req 12.1, 12.2, 12.3 |
| 10 | Approver-Only Application Requirement | Req 4.6, 5.6, 7.7, 8.7 |
| 11 | Member Removal Immediate Effect | Req 15.3, 15.4, 16.2, 16.3 |
| 12 | BU-Bounded Role Activation | Req 3.4, 10.4, 11.2 |
| 13 | BU-Unbounded Role Immediate Effect | Req 3.5, 10.3 |
| 14 | Rejection Requires Comment | Req 9.6 |
| 15 | Virtual Group Exit Revokes Role | Req 15.3, 16.2 |
| 16 | Business Unit Exit Deactivates BU-Bounded Roles | Req 15.4, 16.3 |
| 17 | Business Unit Application Restricted to BU-Bounded Role Associations | Req 8.1, 8.10 |
| 18 | Unactivated BU-Bounded Role Reminder | Req 18.1, 18.2, 18.6, 18.7, 18.9 |

