# Requirements Document

## Introduction

用户管理功能是管理员中心的核心模块，提供完整的用户 CRUD（创建、读取、更新、删除）操作，支持用户列表查询、用户创建、用户编辑、用户禁用/启用以及批量导入功能。

## Glossary

- **User_Management_System**: 用户管理系统，负责用户数据的增删改查操作
- **Admin_Center**: 管理员中心前端应用
- **User_Service**: 用户服务后端 API
- **User_Status**: 用户状态，包括 ACTIVE（活跃）、INACTIVE（停用）、LOCKED（锁定）
- **Pagination**: 分页查询，支持页码和每页数量参数
- **User_Import**: 用户批量导入功能，支持 Excel 文件格式

## Requirements

### Requirement 1: 用户列表查询

**User Story:** As an administrator, I want to query and view the user list with filtering and pagination, so that I can efficiently manage system users.

#### Acceptance Criteria

1. WHEN an administrator requests the user list, THE User_Service SHALL return a paginated list of users
2. WHEN an administrator provides search criteria (username, email, status, department), THE User_Service SHALL filter users matching all provided criteria
3. WHEN an administrator specifies pagination parameters, THE User_Service SHALL return the requested page with correct total count
4. WHEN displaying user list, THE Admin_Center SHALL show username, display name, email, department, status, and creation time
5. THE User_Service SHALL support sorting by any displayed field in ascending or descending order

### Requirement 2: 用户创建

**User Story:** As an administrator, I want to create new users, so that new employees can access the system.

#### Acceptance Criteria

1. WHEN an administrator submits valid user data, THE User_Service SHALL create a new user and return the created user information
2. WHEN creating a user, THE User_Service SHALL validate that username is unique
3. WHEN creating a user, THE User_Service SHALL validate that email format is correct and unique
4. WHEN creating a user, THE User_Service SHALL hash the initial password before storage
5. WHEN creating a user with roles, THE User_Service SHALL assign the specified roles to the user
6. IF username already exists, THEN THE User_Service SHALL return error code USER_001 with message "用户名已存在"
7. IF email already exists, THEN THE User_Service SHALL return error code USER_002 with message "邮箱已被使用"

### Requirement 3: 用户更新

**User Story:** As an administrator, I want to update user information, so that I can keep user data accurate and current.

#### Acceptance Criteria

1. WHEN an administrator submits updated user data, THE User_Service SHALL update the user and return the updated information
2. WHEN updating a user, THE User_Service SHALL validate that the new email is unique (excluding current user)
3. WHEN updating a user, THE User_Service SHALL preserve the password if not provided in the update request
4. WHEN updating user roles, THE User_Service SHALL replace existing roles with the new role set
5. IF user does not exist, THEN THE User_Service SHALL return error code USER_003 with message "用户不存在"
6. THE User_Service SHALL update the updatedAt timestamp on every modification

### Requirement 4: 用户状态管理

**User Story:** As an administrator, I want to enable, disable, or lock users, so that I can control user access to the system.

#### Acceptance Criteria

1. WHEN an administrator disables a user, THE User_Service SHALL set user status to INACTIVE
2. WHEN an administrator enables a user, THE User_Service SHALL set user status to ACTIVE
3. WHEN an administrator locks a user, THE User_Service SHALL set user status to LOCKED
4. WHEN a user status changes, THE User_Service SHALL record the change in audit log
5. IF attempting to disable the last active administrator, THEN THE User_Service SHALL return error code USER_004 with message "不能禁用最后一个管理员"

### Requirement 5: 用户删除

**User Story:** As an administrator, I want to delete users, so that I can remove users who no longer need system access.

#### Acceptance Criteria

1. WHEN an administrator deletes a user, THE User_Service SHALL perform soft delete by setting a deleted flag
2. WHEN a user is deleted, THE User_Service SHALL prevent the user from logging in
3. WHEN querying users, THE User_Service SHALL exclude soft-deleted users by default
4. IF attempting to delete the last active administrator, THEN THE User_Service SHALL return error code USER_005 with message "不能删除最后一个管理员"
5. THE User_Service SHALL record deletion in audit log with operator information

### Requirement 6: 用户批量导入

**User Story:** As an administrator, I want to import multiple users from an Excel file, so that I can efficiently onboard many users at once.

#### Acceptance Criteria

1. WHEN an administrator uploads an Excel file, THE User_Service SHALL parse and validate all user records
2. WHEN importing users, THE User_Service SHALL validate each record against the same rules as single user creation
3. WHEN validation fails for any record, THE User_Service SHALL return detailed error information for each failed record
4. WHEN all records are valid, THE User_Service SHALL create all users in a single transaction
5. THE User_Service SHALL provide a template Excel file for download
6. THE Admin_Center SHALL display import progress and results summary

### Requirement 7: 用户详情查询

**User Story:** As an administrator, I want to view detailed user information, so that I can understand a user's complete profile.

#### Acceptance Criteria

1. WHEN an administrator requests user details, THE User_Service SHALL return complete user information including roles and permissions
2. THE User_Service SHALL return user's login history (last 10 logins)
3. THE User_Service SHALL return user's role assignments with role names
4. IF user does not exist, THEN THE User_Service SHALL return error code USER_003 with message "用户不存在"

### Requirement 8: 密码重置

**User Story:** As an administrator, I want to reset a user's password, so that I can help users who have forgotten their passwords.

#### Acceptance Criteria

1. WHEN an administrator resets a user's password, THE User_Service SHALL generate a new temporary password
2. WHEN password is reset, THE User_Service SHALL mark the password as requiring change on next login
3. THE User_Service SHALL return the temporary password to the administrator for communication to the user
4. THE User_Service SHALL record password reset in audit log
