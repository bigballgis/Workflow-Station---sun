# Implementation Plan: Role System Redesign

## Overview

本实现计划将角色体系重构为三大类（业务角色、管理角色、开发角色），并简化功能单元访问配置为仅支持业务角色分配。

## Tasks

- [x] 1. 更新角色类型枚举和实体
  - [x] 1.1 更新 RoleType 枚举为 BUSINESS, ADMIN, DEVELOPER
    - 修改 `backend/admin-center/src/main/java/com/admin/enums/RoleType.java`
    - _Requirements: 1.1, 8.1_
  - [x] 1.2 更新 Role 实体添加 is_system 字段
    - 修改 `backend/admin-center/src/main/java/com/admin/entity/Role.java`
    - 添加 `isSystem` 布尔字段标识系统角色
    - _Requirements: 3.3, 6.3_

- [x] 2. 创建开发者权限体系
  - [x] 2.1 创建 DeveloperPermission 枚举
    - 创建 `backend/admin-center/src/main/java/com/admin/enums/DeveloperPermission.java`
    - 定义功能单元、表单、流程、数据表的权限代码
    - _Requirements: 5.1_
  - [x] 2.2 创建 DeveloperRolePermission 实体
    - 创建 `backend/admin-center/src/main/java/com/admin/entity/DeveloperRolePermission.java`
    - 存储开发角色与权限的映射关系
    - _Requirements: 5.2, 5.3, 5.4_
  - [x] 2.3 创建 DeveloperRolePermissionRepository
    - 创建 `backend/admin-center/src/main/java/com/admin/repository/DeveloperRolePermissionRepository.java`
    - _Requirements: 5.5_
  - [x] 2.4 创建 DeveloperPermissionService
    - 创建 `backend/admin-center/src/main/java/com/admin/service/DeveloperPermissionService.java`
    - 实现权限检查和获取方法
    - _Requirements: 5.5_

- [x] 3. 简化功能单元访问配置
  - [x] 3.1 简化 FunctionUnitAccessType 枚举
    - 修改 `backend/admin-center/src/main/java/com/admin/enums/FunctionUnitAccessType.java`
    - 只保留 ROLE 类型
    - _Requirements: 9.1, 9.4_
  - [x] 3.2 更新 FunctionUnitAccess 实体
    - 修改 `backend/admin-center/src/main/java/com/admin/entity/FunctionUnitAccess.java`
    - 移除 accessType 字段，改为直接存储 roleId
    - _Requirements: 7.1_
  - [x] 3.3 更新 FunctionUnitAccessRepository
    - 修改查询方法适配新的数据结构
    - _Requirements: 7.1_
  - [x] 3.4 更新 FunctionUnitAccessService
    - 添加业务角色验证逻辑
    - 只允许业务角色被分配功能单元访问权限
    - _Requirements: 7.1, 7.2, 9.3_

- [x] 4. 更新 RoleService 和 Controller
  - [x] 4.1 更新 RoleService 添加按类型查询方法
    - 添加 `getRolesByType(RoleType type)` 方法
    - 添加 `getBusinessRoles()` 方法
    - _Requirements: 9.3_
  - [x] 4.2 更新 RoleController 添加按类型查询端点
    - 添加 `GET /roles?type=BUSINESS` 端点
    - 添加 `GET /roles/business` 端点
    - 添加 `GET /roles/developer` 端点
    - _Requirements: 9.3_
  - [x] 4.3 添加系统角色删除保护
    - 在删除角色时检查 isSystem 字段
    - 系统角色不可删除（已在 RolePermissionManagerComponent.deleteRole 中实现）
    - _Requirements: 3.3, 6.3_

- [x] 5. Checkpoint - 确保后端编译通过
  - 后端代码编译通过 ✓

- [x] 6. 创建数据库迁移脚本
  - [x] 6.1 创建角色表结构更新脚本
    - 添加 is_system 字段
    - 更新现有角色的 type 字段
    - _Requirements: 8.3_
  - [x] 6.2 创建默认角色初始化脚本
    - 插入系统管理员角色 (ADMIN)
    - 插入技术主管、技术组长、开发工程师角色 (DEVELOPER)
    - _Requirements: 6.1, 6.2_
  - [x] 6.3 创建开发角色权限初始化脚本
    - 为技术主管分配所有权限
    - 为技术组长分配创建/管理权限
    - 为开发工程师分配查看/开发权限
    - _Requirements: 6.4_
  - [x] 6.4 创建功能单元访问表结构更新脚本
    - 移除 access_type 字段
    - 重命名 target_id 为 role_id
    - _Requirements: 9.4_
  - 脚本文件: `deploy/init-scripts/14-role-system-redesign.sql`

- [x] 7. 更新前端 Admin Center
  - [x] 7.1 更新角色管理页面
    - 更新 Role 类型定义为 BUSINESS, ADMIN, DEVELOPER
    - 添加 getBusinessRoles, getDeveloperRoles API 方法
    - _Requirements: 1.1_
  - [x] 7.2 更新功能单元访问配置对话框
    - 移除部门、虚拟组选项
    - 只显示业务角色列表
    - 添加说明提示
    - _Requirements: 9.2, 9.3_
  - [x] 7.3 更新角色 API 调用
    - 添加按类型查询参数
    - 简化 FunctionUnitAccessRequest 为只需 roleId
    - _Requirements: 9.3_

- [x] 8. Checkpoint - 确保前端编译通过
  - 前端代码编译通过 ✓

- [x] 9. 更新 Developer Workstation 权限控制
  - [x] 9.1 创建权限检查中间件/拦截器
    - 创建 `RequireDeveloperPermission` 注解
    - 创建 `DeveloperPermissionInterceptor` 拦截器
    - 创建 `DeveloperPermissionChecker` 权限检查器
    - 配置 `WebMvcConfig` 注册拦截器
    - _Requirements: 5.5_
  - [x] 9.2 为功能单元相关 API 添加权限注解
    - 创建操作需要 FUNCTION_UNIT_CREATE 权限
    - 删除操作需要 FUNCTION_UNIT_DELETE 权限
    - 更新操作需要 FUNCTION_UNIT_UPDATE 权限
    - 查看操作需要 FUNCTION_UNIT_VIEW 权限
    - 发布操作需要 FUNCTION_UNIT_PUBLISH 权限
    - _Requirements: 4.3, 4.4, 4.5_
  - [x] 9.3 在 admin-center 添加开发者权限 API
    - 创建 `DeveloperPermissionController`
    - 添加 `getUserPermissionCodes` 方法
    - _Requirements: 5.5_

- [x] 10. 更新 User Portal 功能单元可见性
  - [x] 10.1 创建功能单元可见性服务
    - 创建 `FunctionUnitAccessComponent`
    - 根据用户的业务角色过滤可见的功能单元
    - _Requirements: 7.3, 7.4, 7.5_
  - [x] 10.2 更新功能单元列表 API
    - 更新 `ProcessComponent.getAvailableProcessDefinitions`
    - 只返回用户有权限访问的功能单元
    - _Requirements: 7.3, 7.5_

- [x] 11. Final Checkpoint - 确保所有代码编译通过
  - admin-center 编译通过 ✓
  - developer-workstation 编译通过 ✓
  - user-portal 编译通过 ✓
  - frontend/admin-center 编译通过 ✓

## Notes

- 任务按照依赖关系排序，后续任务依赖前面任务的完成
- 数据库迁移脚本需要在后端代码更新后执行
- 前端更新依赖后端 API 的完成
- 每个 Checkpoint 用于验证阶段性成果
