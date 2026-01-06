# Implementation Plan: Authentication

## Overview

本实现计划将认证功能分解为可执行的编码任务，按照后端 → 前端 → 集成的顺序实现。使用 Java 17 + Spring Boot 3.2 实现后端，Vue 3 + TypeScript 实现前端。

## Tasks

- [x] 1. 创建用户实体和数据库迁移
  - [x] 1.1 创建 User 实体类和 UserStatus 枚举
    - 在 `backend/platform-security/src/main/java/com/platform/security/model/` 创建 User.java
    - 包含 id, username, passwordHash, email, displayName, status, departmentId, language, roles, createdAt, updatedAt 字段
    - _Requirements: 1.1, 1.2, 1.4, 1.5_
  - [x] 1.2 创建 UserRepository 接口
    - 在 `backend/platform-security/src/main/java/com/platform/security/repository/` 创建 UserRepository.java
    - 包含 findByUsername, existsByUsername 方法
    - _Requirements: 1.1_
  - [x] 1.3 创建数据库迁移脚本
    - 在 `backend/platform-security/src/main/resources/db/migration/` 创建 V1__create_auth_tables.sql
    - 创建 sys_user, sys_role, sys_user_role, sys_login_audit 表
    - _Requirements: 1.1, 1.2_
  - [x] 1.4 编写 User 实体属性测试
    - **Property 1: Password Hashing Security**
    - **Validates: Requirements 1.3**

- [x] 2. 实现 JWT Token 服务
  - [x] 2.1 实现 JwtTokenServiceImpl
    - 在 `backend/platform-security/src/main/java/com/platform/security/service/impl/` 创建 JwtTokenServiceImpl.java
    - 实现 generateToken, validateToken, extractUserPrincipal, blacklistToken, isBlacklisted 方法
    - _Requirements: 2.6, 2.7, 2.8, 3.2, 3.3_
  - [x] 2.2 配置 Redis Token 黑名单
    - 使用 Redis SET 存储黑名单 token hash
    - 设置 TTL 为 token 剩余有效期
    - _Requirements: 3.2_
  - [x] 2.3 编写 Token 属性测试
    - **Property 2: Token Content Completeness**
    - **Validates: Requirements 2.6, 2.7, 2.8**

- [x] 3. 实现认证服务
  - [x] 3.1 创建认证相关 DTO
    - 创建 LoginRequest, LoginResponse, RefreshRequest, TokenResponse, UserInfo 记录类
    - 在 `backend/platform-security/src/main/java/com/platform/security/dto/`
    - _Requirements: 2.1, 4.1_
  - [x] 3.2 实现 AuthenticationService 接口和实现类
    - 创建 AuthenticationService.java 接口
    - 创建 AuthenticationServiceImpl.java 实现类
    - 实现 login, logout, refreshToken, getCurrentUser 方法
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 4.1_
  - [x] 3.3 实现登录审计记录
    - 创建 LoginAudit 实体和 LoginAuditRepository
    - 在登录/登出时记录审计日志
    - _Requirements: 2.5, 3.4_
  - [x] 3.4 编写认证正确性属性测试
    - **Property 3: Authentication Correctness**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
  - [x] 3.5 编写登出黑名单属性测试
    - **Property 4: Logout and Blacklist Enforcement**
    - **Validates: Requirements 3.1, 3.2, 3.3**

- [x] 4. 创建认证 API 端点
  - [x] 4.1 创建 AuthController
    - 在 `backend/platform-security/src/main/java/com/platform/security/controller/` 创建 AuthController.java
    - 实现 POST /api/v1/auth/login, POST /api/v1/auth/logout, POST /api/v1/auth/refresh, GET /api/v1/auth/me
    - _Requirements: 2.1, 3.1, 4.1_
  - [x] 4.2 创建认证错误码枚举
    - 在 `backend/platform-security/src/main/java/com/platform/security/exception/` 创建 AuthErrorCode.java
    - 定义 AUTH_001 到 AUTH_008 错误码
    - _Requirements: 2.2, 2.3, 2.4, 4.2, 4.3_
  - [x] 4.3 创建认证异常处理器
    - 创建 AuthExceptionHandler.java 处理认证相关异常
    - _Requirements: 2.2, 2.3, 2.4_
  - [x] 4.4 编写 Token 刷新属性测试
    - **Property 5: Token Refresh Round-Trip**
    - **Validates: Requirements 4.1, 4.5**
  - [x] 4.5 编写无效 Token 拒绝属性测试
    - **Property 6: Invalid Token Rejection**
    - **Validates: Requirements 4.2, 4.3, 7.3, 7.4**

- [x] 5. Checkpoint - 后端认证服务完成
  - 确保所有测试通过，如有问题请询问用户

- [x] 6. 配置 API Gateway 路由
  - [x] 6.1 添加认证路由配置
    - 更新 `backend/api-gateway/src/main/resources/application.yml`
    - 添加 /api/v1/auth/** 路由到认证服务
    - _Requirements: 7.1_
  - [x] 6.2 配置公开端点白名单
    - 配置 /api/v1/auth/login 和 /api/v1/auth/refresh 为公开端点
    - _Requirements: 7.2_
  - [x] 6.3 更新 JWT 过滤器配置
    - 确保 JwtAuthenticationFilter 正确验证 token
    - _Requirements: 7.3, 7.4_

- [x] 7. 创建测试用户初始化
  - [x] 7.1 创建测试用户数据迁移脚本
    - 在 `backend/platform-security/src/main/resources/db/migration/` 创建 V2__init_test_users.sql
    - 插入管理员中心、开发者工作站、用户门户的测试用户
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [x] 7.2 创建 DataInitializer 组件
    - 在 dev profile 下自动初始化测试用户（如果不存在）
    - _Requirements: 6.5_

- [x] 8. Checkpoint - 后端完成
  - 确保所有测试通过，如有问题请询问用户

- [x] 9. 实现前端认证 API 服务
  - [x] 9.1 创建统一的 API 配置
    - 在各前端项目创建 `src/api/config.ts` 配置 axios 实例
    - 配置 baseURL, 请求拦截器, 响应拦截器
    - _Requirements: 5.2_
  - [x] 9.2 创建认证 API 模块
    - 在各前端项目创建 `src/api/auth.ts`
    - 实现 login, logout, refresh, getCurrentUser 方法
    - _Requirements: 5.1, 5.5_
  - [x] 9.3 实现 Token 自动刷新拦截器
    - 在响应拦截器中检测 401 错误
    - 自动调用 refresh API 获取新 token
    - _Requirements: 5.3, 5.4_

- [x] 10. 更新前端登录组件
  - [x] 10.1 更新管理员中心登录页面
    - 修改 `frontend/admin-center/src/views/login/index.vue`
    - 调用真实登录 API，存储 token
    - _Requirements: 5.1, 5.6_
  - [x] 10.2 更新开发者工作站登录页面
    - 修改 `frontend/developer-workstation/src/views/Login.vue`
    - 调用真实登录 API，存储 token
    - _Requirements: 5.1, 5.6_
  - [x] 10.3 更新用户门户登录页面
    - 修改 `frontend/user-portal/src/views/login/index.vue`
    - 调用真实登录 API，存储 token
    - _Requirements: 5.1, 5.6_

- [x] 11. 实现前端登出功能
  - [x] 11.1 更新各前端的登出逻辑
    - 调用登出 API
    - 清除 localStorage 中的 token
    - 重定向到登录页面
    - _Requirements: 5.5_

- [x] 12. Final Checkpoint - 全部完成
  - 确保所有测试通过
  - 验证三个前端都能正常登录/登出
  - 如有问题请询问用户

## Notes

- All tasks are required for comprehensive testing
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases

