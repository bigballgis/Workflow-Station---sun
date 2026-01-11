# Implementation Plan: Manager Assignment

## Overview

本实现计划将管理者分配功能分解为数据库迁移、后端实体更新、前端界面更新和流程引擎扩展四个主要部分，按照依赖顺序逐步实现。

## Tasks

- [x] 1. 数据库迁移脚本
  - [x] 1.1 创建数据库迁移脚本 18-manager-assignment.sql
    - 为 admin_users 表添加 entity_manager_id 和 function_manager_id 列
    - 为 admin_departments 表添加 secondary_manager_id 列
    - 所有新列默认值为 NULL
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 2. Admin Center 后端更新
  - [x] 2.1 更新 User 实体
    - 添加 entityManagerId 和 functionManagerId 字段
    - 添加 JPA 注解
    - _Requirements: 1.1, 1.2_
  - [x] 2.2 更新 Department 实体
    - 添加 secondaryManagerId 字段
    - 添加 JPA 注解
    - _Requirements: 2.1_
  - [x] 2.3 更新 UserDetailInfo DTO
    - 添加 entityManagerId, entityManagerName, functionManagerId, functionManagerName 字段
    - 更新 fromEntity 方法
    - _Requirements: 7.1, 7.2_
  - [x] 2.4 更新 DepartmentInfo DTO
    - 添加 secondaryManagerId, secondaryManagerName 字段
    - _Requirements: 8.1, 8.2_
  - [x] 2.5 更新 UserManagerComponent
    - 在保存用户时验证 entityManagerId 和 functionManagerId 引用的用户存在
    - 在返回用户详情时填充管理者名称
    - _Requirements: 1.3, 1.4, 1.5_
  - [x] 2.6 更新 DepartmentManagerComponent
    - 在保存部门时验证 secondaryManagerId 引用的用户存在
    - 在返回部门详情时填充副经理名称
    - _Requirements: 2.2, 2.3, 2.4_
  - [x] 2.7 编写 User 管理者字段属性测试
    - **Property 1: User Manager Fields Persistence**
    - **Property 2: User Manager Reference Validation**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
  - [x] 2.8 编写 Department 副经理字段属性测试
    - **Property 3: Department Secondary Manager Persistence**
    - **Validates: Requirements 2.1, 2.2, 2.3**

- [x] 3. Checkpoint - 后端基础功能验证
  - 确保所有测试通过，如有问题请询问用户

- [x] 4. Admin Center 前端更新
  - [x] 4.1 更新用户编辑表单
    - 添加实体管理者选择器（可搜索的用户下拉框）
    - 添加职能管理者选择器（可搜索的用户下拉框）
    - 支持清空选择
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  - [x] 4.2 更新用户详情显示
    - 显示实体管理者名称
    - 显示职能管理者名称
    - 未设置时显示"未设置"
    - _Requirements: 7.1, 7.2, 7.3_
  - [x] 4.3 更新部门编辑表单
    - 添加副经理选择器（可搜索的用户下拉框）
    - 支持清空选择
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [x] 4.4 更新部门详情显示
    - 显示副经理名称
    - 未设置时显示"未设置"
    - _Requirements: 8.1, 8.2, 8.3_

- [x] 5. Developer Workstation BPMN 设计器更新
  - [x] 5.1 更新 UserTaskProperties.vue 分配方式选项
    - 添加"实体管理者"选项 (entityManager)
    - 添加"职能管理者"选项 (functionManager)
    - 添加"实体+职能管理者（会签）"选项 (bothManagers)
    - 添加"部门主经理"选项 (departmentManager)
    - 添加"部门副经理"选项 (departmentSecondaryManager)
    - _Requirements: 5.1_
  - [x] 5.2 更新 handleAssigneeTypeChange 方法
    - 为单管理者选项设置 assignee 表达式
    - 为 bothManagers 选项设置 candidateUsers 表达式 ${entityManager},${functionManager}
    - 设置描述性标签
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

- [x] 6. User Portal 流程引擎更新
  - [x] 6.1 扩展 ProcessComponent.resolveProcessVariable 方法
    - 添加 entityManager 变量解析
    - 添加 functionManager 变量解析
    - 添加 departmentSecondaryManager 变量解析
    - 保持 departmentManager/initiatorManager 兼容
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [x] 6.2 实现 getEntityManager 方法
    - 调用 Admin Center API 获取用户的 entityManagerId
    - _Requirements: 6.1_
  - [x] 6.3 实现 getFunctionManager 方法
    - 调用 Admin Center API 获取用户的 functionManagerId
    - _Requirements: 6.2_
  - [x] 6.4 实现 getDepartmentSecondaryManager 方法
    - 获取用户的 departmentId
    - 获取部门的 secondaryManagerId
    - _Requirements: 6.4_
  - [x] 6.5 实现 resolveCandidateUsers 方法
    - 支持解析多个变量表达式（如 ${entityManager},${functionManager}）
    - 过滤掉 null 值
    - _Requirements: 6.5_
  - [x] 6.6 更新 parseFirstUserTask 方法
    - 支持解析 candidateUsers 属性
    - 调用 resolveCandidateUsers 解析候选用户
    - _Requirements: 6.5_
  - [x] 6.7 更新空管理者处理逻辑
    - 当管理者为 null 时，从候选用户中排除或保持未分配状态
    - 记录警告日志
    - _Requirements: 6.6, 6.7_
  - [x] 6.8 编写管理者变量解析属性测试
    - **Property 4: Manager Variable Resolution**
    - **Property 5: Both Managers Candidate Resolution**
    - **Property 6: Null Manager Handling**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 7. Final Checkpoint - 完整功能验证
  - 确保所有测试通过，如有问题请询问用户

## Notes

- All tasks are required for complete implementation
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
