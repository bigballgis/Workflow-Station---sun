# Requirements Document

## Introduction

本文档定义了工作流平台三个前端（管理员中心、开发者工作站、用户门户）的统一认证功能需求。当前三个前端使用 mock 登录，需要实现真实的后端 JWT 认证 API，包括登录、登出、Token 刷新等功能。

## Glossary

- **Authentication_Service**: 认证服务，负责处理用户登录、登出、Token 管理的后端服务
- **JWT_Token**: JSON Web Token，用于用户身份验证的令牌
- **Access_Token**: 访问令牌，用于 API 请求认证，有效期较短（1小时）
- **Refresh_Token**: 刷新令牌，用于获取新的访问令牌，有效期较长（7天）
- **User_Entity**: 用户实体，存储用户基本信息的数据库记录
- **Frontend_Client**: 前端客户端，包括管理员中心、开发者工作站、用户门户
- **API_Gateway**: API 网关，统一处理所有前端请求的入口服务

## Requirements

### Requirement 1: 用户实体和数据库表

**User Story:** As a 系统管理员, I want 用户信息存储在数据库中, so that 可以持久化管理用户账户。

#### Acceptance Criteria

1. THE User_Entity SHALL include fields: id, username, password_hash, email, display_name, status, created_at, updated_at
2. THE User_Entity SHALL use UUID as primary key
3. WHEN a user is created, THE Authentication_Service SHALL hash the password using BCrypt
4. THE User_Entity SHALL support status values: ACTIVE, INACTIVE, LOCKED
5. THE User_Entity SHALL include a roles field for RBAC support

### Requirement 2: 登录认证

**User Story:** As a 用户, I want 使用用户名和密码登录系统, so that 可以访问平台功能。

#### Acceptance Criteria

1. WHEN a user submits valid credentials to POST /api/v1/auth/login, THE Authentication_Service SHALL return access_token and refresh_token
2. WHEN a user submits invalid credentials, THE Authentication_Service SHALL return 401 Unauthorized with error message
3. WHEN a user account is LOCKED, THE Authentication_Service SHALL return 403 Forbidden with account locked message
4. WHEN a user account is INACTIVE, THE Authentication_Service SHALL return 403 Forbidden with account inactive message
5. THE Authentication_Service SHALL record login timestamp and IP address for audit
6. THE Access_Token SHALL contain user_id, username, roles, permissions, department_id, language claims
7. THE Access_Token SHALL expire after 1 hour by default
8. THE Refresh_Token SHALL expire after 7 days by default

### Requirement 3: 登出功能

**User Story:** As a 用户, I want 安全登出系统, so that 保护我的账户安全。

#### Acceptance Criteria

1. WHEN a user calls POST /api/v1/auth/logout with valid token, THE Authentication_Service SHALL invalidate the token
2. WHEN a user logs out, THE Authentication_Service SHALL add the token to blacklist in Redis
3. WHEN a blacklisted token is used, THE Authentication_Service SHALL return 401 Unauthorized
4. THE Authentication_Service SHALL record logout timestamp for audit

### Requirement 4: Token 刷新

**User Story:** As a 用户, I want 在 Token 过期前自动刷新, so that 不会因为 Token 过期而中断操作。

#### Acceptance Criteria

1. WHEN a user calls POST /api/v1/auth/refresh with valid refresh_token, THE Authentication_Service SHALL return new access_token
2. WHEN refresh_token is expired, THE Authentication_Service SHALL return 401 Unauthorized
3. WHEN refresh_token is invalid, THE Authentication_Service SHALL return 401 Unauthorized
4. THE Authentication_Service SHALL optionally rotate refresh_token on each refresh
5. FOR ALL valid refresh operations, THE new access_token SHALL contain the same user claims as the original

### Requirement 5: 前端登录集成

**User Story:** As a 前端开发者, I want 统一的登录 API 调用方式, so that 三个前端可以复用相同的认证逻辑。

#### Acceptance Criteria

1. WHEN Frontend_Client calls login API, THE Frontend_Client SHALL store access_token and refresh_token in localStorage
2. WHEN Frontend_Client makes API requests, THE Frontend_Client SHALL include access_token in Authorization header
3. WHEN access_token expires, THE Frontend_Client SHALL automatically call refresh API
4. WHEN refresh fails, THE Frontend_Client SHALL redirect to login page
5. WHEN user clicks logout, THE Frontend_Client SHALL call logout API and clear stored tokens
6. THE Frontend_Client SHALL display appropriate error messages for authentication failures

### Requirement 6: 测试用户初始化

**User Story:** As a 开发者, I want 预置测试用户数据, so that 可以快速测试登录功能。

#### Acceptance Criteria

1. THE Authentication_Service SHALL provide database migration script for test users
2. THE test users SHALL include: super_admin, system_admin, tenant_admin, auditor (for admin-center)
3. THE test users SHALL include: dev_lead, senior_dev, developer, designer, tester (for developer-workstation)
4. THE test users SHALL include: manager, team_lead, employee_a, employee_b, hr_staff, finance (for user-portal)
5. WHEN application starts in dev profile, THE Authentication_Service SHALL auto-initialize test users if not exist

### Requirement 7: API 网关路由

**User Story:** As a 系统架构师, I want 认证 API 通过网关统一路由, so that 前端只需要访问一个入口。

#### Acceptance Criteria

1. THE API_Gateway SHALL route /api/v1/auth/** requests to Authentication_Service
2. THE API_Gateway SHALL allow unauthenticated access to /api/v1/auth/login and /api/v1/auth/refresh
3. THE API_Gateway SHALL validate JWT token for all other protected routes
4. WHEN JWT validation fails, THE API_Gateway SHALL return 401 Unauthorized

