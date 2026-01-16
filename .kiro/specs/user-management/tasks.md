# Implementation Plan: User Management

## Overview

本实现计划将用户管理功能分解为可执行的编码任务，按照后端 → 前端 → 集成的顺序实现。使用 Java 17 + Spring Boot 3.2 实现后端，Vue 3 + TypeScript + Element Plus 实现前端。

## Tasks

- [x] 1. 扩展用户实体和创建 DTO
  - [x] 1.1 扩展 User 实体添加新字段
    - 在 `backend/admin-center/src/main/java/com/admin/entity/User.java` 添加 deleted, deletedAt, deletedBy 字段
    - _Requirements: 1.1, 5.1_
  - [x] 1.2 创建用户相关 DTO
    - 创建 UserDetailInfo, UserInfo, UserCreateRequest, UserUpdateRequest, UserQueryRequest
    - 在 `backend/admin-center/src/main/java/com/admin/dto/`
    - _Requirements: 1.1, 2.1, 3.1_
  - [x] 1.3 创建 ImportResult 和 ImportError DTO
    - 创建 UserImportResult.java 用于批量导入结果返回
    - _Requirements: 6.3_

- [x] 2. 实现用户数据访问层
  - [x] 2.1 创建 UserRepository 接口
    - 在 `backend/admin-center/src/main/java/com/admin/repository/UserRepository.java`
    - 实现 findByConditions 自定义查询方法和 countActiveAdmins
    - _Requirements: 1.1, 1.2_
  - [x] 2.2 创建数据库迁移脚本
    - 创建 `V10__add_user_soft_delete_fields.sql`
    - 添加 deleted, deleted_at, deleted_by 字段
    - _Requirements: 1.1, 5.1_

- [x] 3. 实现用户服务层
  - [x] 3.1 创建 UserManagerComponent
    - 在 `backend/admin-center/src/main/java/com/admin/component/UserManagerComponent.java`
    - 定义所有用户管理方法
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1_
  - [x] 3.2 实现查询功能
    - 实现 listUsers (分页、筛选、排序)
    - 实现 getUserDetail (用户详情)
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 7.1_
  - [x] 3.3 实现创建功能
    - 实现 createUser 方法
    - 包含用户名/邮箱唯一性验证
    - 包含密码哈希
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  - [x] 3.4 实现更新功能
    - 实现 updateUser 方法
    - 实现 updateUserStatus 方法
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6, 4.1, 4.2, 4.3_
  - [x] 3.5 实现删除功能
    - 实现软删除逻辑
    - 实现最后管理员保护
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [x] 3.6 实现密码重置
    - 实现 resetPassword 方法
    - 生成临时密码并标记需要修改
    - _Requirements: 8.1, 8.2, 8.3_
  - [x] 3.7 编写用户 CRUD 属性测试
    - **Property 4: User Creation Round-Trip**
    - **Property 8: Update Persistence**
    - **Validates: Requirements 2.1, 2.5, 3.1, 3.4**
  - [x] 3.8 编写唯一性约束属性测试
    - **Property 5: Username Uniqueness**
    - **Property 6: Email Uniqueness**
    - **Validates: Requirements 2.2, 2.3**

- [x] 4. 实现批量导入功能
  - [x] 4.1 创建 Excel 解析服务
    - 使用 Apache POI 解析 Excel 文件
    - 实现模板生成功能
    - _Requirements: 6.1, 6.5_
  - [x] 4.2 实现 importUsers 方法
    - 解析、验证、批量创建
    - 事务管理确保原子性
    - _Requirements: 6.2, 6.3, 6.4_
  - [x] 4.3 编写导入属性测试
    - **Property 12: Import Validation Consistency**
    - **Property 13: Import Atomicity**
    - **Validates: Requirements 6.2, 6.3, 6.4**

- [x] 5. 创建用户管理 API 端点
  - [x] 5.1 创建 UserController
    - 在 `backend/admin-center/src/main/java/com/admin/controller/UserController.java`
    - 实现所有 REST 端点
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1_
  - [x] 5.2 创建用户错误码和异常处理
    - 创建 UserErrorCode 枚举
    - _Requirements: 2.6, 2.7, 3.5, 4.5, 5.4_
  - [x] 5.3 编写分页和筛选属性测试
    - **Property 1: Pagination Consistency**
    - **Property 2: Filter Correctness**
    - **Property 3: Sort Order Correctness**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.5**

- [x] 6. 实现审计日志功能
  - [x] 6.1 创建用户操作审计服务
    - AuditService 已实现记录状态变更、删除、密码重置操作
    - _Requirements: 4.4, 5.5, 8.4_
  - [x] 6.2 编写审计日志属性测试 ✅
    - **Property 15: Audit Trail Completeness**
    - **Validates: Requirements 4.4, 5.5, 8.4**
    - 已创建 `UserAuditProperties.java`

- [x] 7. Checkpoint - 后端完成
  - 后端功能已全部实现，包括用户 CRUD、批量导入、状态管理、密码重置等

- [x] 8. 配置 API Gateway 路由
  - [x] 8.1 添加用户管理路由
    - 更新 `backend/api-gateway/src/main/resources/application.yml`
    - 添加 /api/v1/users/** 路由到 admin-center 服务
    - _Requirements: 1.1_

- [x] 9. 实现前端用户 API 服务
  - [x] 9.1 创建用户 API 模块
    - 在 `frontend/admin-center/src/api/user.ts`
    - 实现所有用户管理 API 调用方法
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1_
  - [x] 9.2 创建用户相关类型定义
    - 在 `frontend/admin-center/src/api/user.ts` 中定义 TypeScript 接口
    - 定义 User, UserDetail, CreateUserRequest 等接口
    - _Requirements: 1.1_

- [x] 10. 实现用户列表页面
  - [x] 10.1 创建用户列表视图
    - 在 `frontend/admin-center/src/views/user/UserList.vue`
    - 实现表格展示、分页、筛选、排序
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_
  - [x] 10.2 实现用户搜索和筛选组件
    - 用户名、邮箱、状态、部门筛选
    - _Requirements: 1.2_
  - [x] 10.3 实现用户状态操作
    - 启用/禁用/锁定按钮和确认对话框
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 11. 实现用户表单对话框
  - [x] 11.1 创建用户创建/编辑对话框
    - 在 `frontend/admin-center/src/views/user/components/UserFormDialog.vue`
    - 支持创建和编辑两种模式
    - _Requirements: 2.1, 3.1_
  - [x] 11.2 实现表单验证
    - 用户名、邮箱格式验证
    - 必填字段验证
    - _Requirements: 2.2, 2.3_
  - [x] 11.3 实现角色选择组件
    - 多选角色分配
    - _Requirements: 2.5, 3.4_

- [x] 12. 实现用户详情页面
  - [x] 12.1 创建用户详情对话框
    - 在 `frontend/admin-center/src/views/user/components/UserDetailDialog.vue`
    - 展示完整用户信息、角色、登录历史
    - _Requirements: 7.1, 7.2, 7.3_
  - [x] 12.2 实现密码重置功能
    - 重置按钮和结果展示
    - _Requirements: 8.1, 8.3_

- [x] 13. 实现用户批量导入功能
  - [x] 13.1 创建导入对话框组件
    - 在 `frontend/admin-center/src/views/user/components/UserImportDialog.vue`
    - 文件上传、进度展示、结果汇总
    - _Requirements: 6.1, 6.6_
  - [x] 13.2 实现模板下载功能
    - 下载 Excel 模板按钮
    - _Requirements: 6.5_
  - [x] 13.3 实现导入错误展示
    - 显示每行的验证错误
    - _Requirements: 6.3_

- [x] 14. 配置路由和菜单
  - [x] 14.1 添加用户管理路由
    - 路由已在 `frontend/admin-center/src/router/index.ts` 配置
    - 包含 /user/list, /user/import 路由
    - _Requirements: 1.1, 7.1_
  - [x] 14.2 更新侧边栏菜单
    - 用户管理菜单项已正确配置
    - _Requirements: 1.4_

- [x] 15. Final Checkpoint - 全部完成 ✅
  - 用户管理功能已全部实现
  - 用户列表、创建、编辑、删除、导入功能正常
  - 属性测试已全部完成并通过：
    - `UserManagementProperties.java` - 用户CRUD、唯一性约束、分页筛选测试
    - `UserImportProperties.java` - 导入验证和原子性测试
    - `UserAuditProperties.java` - 审计日志完整性测试

## Notes

- All tasks are required for comprehensive testing
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
