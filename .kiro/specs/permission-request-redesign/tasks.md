# Implementation Tasks

## Overview
重新设计 User Portal 的权限申请功能，支持申请组织单元角色和加入虚拟组。

## Tasks

- [x] 1. 更新后端数据模型
  - 更新 PermissionRequestType 枚举，添加 ROLE_ASSIGNMENT 和 VIRTUAL_GROUP_JOIN 类型
  - 在 PermissionRequest 实体中添加 roleId, roleName, organizationUnitId, organizationUnitName, virtualGroupId, virtualGroupName 字段
  - 保留旧字段以兼容现有数据
  - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [x] 2. 创建 Admin Center API 调用组件
  - [x] 2.1 创建 RoleAccessComponent
    - getAvailableBusinessRoles() - 获取可申请的业务角色
    - getUserRoles(userId) - 获取用户当前角色
    - assignRoleToUser(userId, roleId, reason) - 分配角色给用户
    - _Requirements: 5.1, 5.5, 5.8_
  - [x] 2.2 创建 VirtualGroupAccessComponent
    - getAvailableVirtualGroups() - 获取可加入的虚拟组
    - getUserVirtualGroups(userId) - 获取用户当前虚拟组
    - addUserToVirtualGroup(userId, groupId, reason) - 添加用户到虚拟组
    - _Requirements: 5.2, 5.6, 5.8_

- [x] 3. 重写 PermissionComponent
  - 新增 getAvailableRoles(userId) - 获取用户可申请的角色
  - 新增 getAvailableVirtualGroups(userId) - 获取用户可加入的虚拟组
  - 新增 requestRoleAssignment(userId, roleId, orgUnitId, reason) - 申请角色（自动批准）
  - 新增 requestVirtualGroupJoin(userId, groupId, reason) - 申请加入虚拟组（自动批准）
  - 新增 getUserCurrentRoles(userId) - 获取用户当前角色
  - 新增 getUserCurrentVirtualGroups(userId) - 获取用户当前虚拟组
  - _Requirements: 1.1-1.6, 2.1-2.6, 3.1-3.4_

- [x] 4. 更新 PermissionController
  - GET /permissions/available-roles - 获取可申请的角色
  - GET /permissions/available-virtual-groups - 获取可加入的虚拟组
  - POST /permissions/request-role - 申请角色
  - POST /permissions/request-virtual-group - 申请加入虚拟组
  - GET /permissions/my-roles - 获取当前角色
  - GET /permissions/my-virtual-groups - 获取当前虚拟组
  - _Requirements: 5.1-5.7_

- [x] 5. 添加 Admin Center 用户角色/虚拟组查询 API
  - GET /users/{userId}/roles - 获取用户的角色列表
  - GET /users/{userId}/virtual-groups - 获取用户的虚拟组列表
  - _Requirements: 5.5, 5.6_

- [x] 6. 更新前端 API 定义
  - 新增接口类型: RoleInfo, VirtualGroupInfo, UserRoleAssignment, UserVirtualGroupMembership
  - 新增 API 方法: getAvailableRoles, getAvailableVirtualGroups, requestRole, requestVirtualGroup, getMyRoles, getMyVirtualGroups
  - _Requirements: 5.1-5.7_

- [x] 7. 重写前端权限页面
  - "我的权限" Tab: 显示当前角色和虚拟组成员身份
  - "申请权限" 对话框: 支持申请角色和加入虚拟组两种模式
  - "申请历史" Tab: 显示新类型的申请记录
  - _Requirements: 7.1-7.6_

- [x] 8. 更新国际化文案
  - 添加新的翻译 key 支持新界面
  - _Requirements: 7.1-7.6_

- [x] 9. Checkpoint - 确保所有功能正常工作
  - 确保所有测试通过，如有问题请询问用户

