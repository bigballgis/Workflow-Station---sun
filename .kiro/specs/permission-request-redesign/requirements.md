# Requirements Document

## Introduction

重新设计 User Portal 的权限申请功能。用户可以通过两种方式获取权限：
1. 申请某个组织单元（部门）的业务角色
2. 申请加入虚拟组（自动获得虚拟组绑定的所有角色）

当前的权限申请实现使用了通用的 FUNCTION/DATA/TEMPORARY 类型，这与新的角色体系不匹配。新设计将直接与业务角色和虚拟组关联，并且暂时不需要审批流程（自动批准）。

## Glossary

- **Business_Role**: 业务角色，用于 User Portal 中的用户权限管理
- **Organization_Unit**: 组织单元/部门，用户所属的组织结构
- **Virtual_Group**: 虚拟组，可以绑定一个或多个业务角色
- **Role_Assignment**: 角色分配，将用户分配到某个组织单元的某个角色
- **Permission_Request**: 权限申请记录，记录用户的申请历史

## Requirements

### Requirement 1: 申请组织单元角色

**User Story:** As a User Portal 用户, I want 申请某个组织单元的业务角色, so that 我可以获得该组织单元下的相应权限。

#### Acceptance Criteria

1. THE User_Portal SHALL display a list of available Business_Roles that the user can request
2. WHEN requesting a role, THE User SHALL select a target Organization_Unit (department)
3. WHEN requesting a role, THE User SHALL provide a reason for the request
4. THE Permission_Request SHALL be automatically approved (no workflow required for now)
5. WHEN approved, THE System SHALL create a UserRole record linking the user to the role and organization unit
6. THE User SHALL NOT be able to request a role they already have in the same organization unit

### Requirement 2: 申请加入虚拟组

**User Story:** As a User Portal 用户, I want 申请加入虚拟组, so that 我可以自动获得虚拟组绑定的所有业务角色。

#### Acceptance Criteria

1. THE User_Portal SHALL display a list of available Virtual_Groups that the user can join
2. WHEN requesting to join a Virtual_Group, THE User SHALL provide a reason for the request
3. THE Permission_Request SHALL be automatically approved (no workflow required for now)
4. WHEN approved, THE System SHALL add the user as a member of the Virtual_Group
5. THE User SHALL automatically receive all Business_Roles bound to the Virtual_Group
6. THE User SHALL NOT be able to request to join a Virtual_Group they are already a member of

### Requirement 3: 查看当前权限

**User Story:** As a User Portal 用户, I want 查看我当前拥有的权限, so that 我可以了解我的访问范围。

#### Acceptance Criteria

1. THE User_Portal SHALL display the user's current role assignments grouped by Organization_Unit
2. THE User_Portal SHALL display the user's current Virtual_Group memberships
3. FOR each Virtual_Group membership, THE User_Portal SHALL show the roles bound to that group
4. THE User_Portal SHALL indicate which roles come from direct assignment vs Virtual_Group membership

### Requirement 4: 申请历史记录

**User Story:** As a User Portal 用户, I want 查看我的权限申请历史, so that 我可以追踪我的申请状态。

#### Acceptance Criteria

1. THE Permission_Request history SHALL include two types: ROLE_ASSIGNMENT and VIRTUAL_GROUP_JOIN
2. FOR ROLE_ASSIGNMENT requests, THE history SHALL show the target role and organization unit
3. FOR VIRTUAL_GROUP_JOIN requests, THE history SHALL show the target Virtual_Group
4. THE history SHALL show request status: APPROVED (auto-approved for now)
5. THE history SHALL show request timestamp and reason

### Requirement 5: 后端 API 设计

**User Story:** As a 开发人员, I want 清晰的 API 接口, so that 前后端可以正确交互。

#### Acceptance Criteria

1. THE Backend SHALL provide `GET /permissions/available-roles` to get roles user can request
2. THE Backend SHALL provide `GET /permissions/available-virtual-groups` to get virtual groups user can join
3. THE Backend SHALL provide `POST /permissions/request-role` to request a role assignment
4. THE Backend SHALL provide `POST /permissions/request-virtual-group` to request joining a virtual group
5. THE Backend SHALL provide `GET /permissions/my-roles` to get user's current role assignments
6. THE Backend SHALL provide `GET /permissions/my-virtual-groups` to get user's current virtual group memberships
7. THE Backend SHALL provide `GET /permissions/requests` to get user's request history
8. THE Backend SHALL call Admin_Center APIs to perform actual role/group assignments

### Requirement 6: 数据模型更新

**User Story:** As a 开发人员, I want 更新权限申请的数据模型, so that 它可以支持新的申请类型。

#### Acceptance Criteria

1. THE PermissionRequest entity SHALL have a new requestType enum: ROLE_ASSIGNMENT, VIRTUAL_GROUP_JOIN
2. FOR ROLE_ASSIGNMENT type, THE entity SHALL store roleId and organizationUnitId
3. FOR VIRTUAL_GROUP_JOIN type, THE entity SHALL store virtualGroupId
4. THE old FUNCTION/DATA/TEMPORARY types SHALL be deprecated
5. THE permissions field (List<String>) SHALL be replaced with specific ID fields

### Requirement 7: 前端界面重新设计

**User Story:** As a User Portal 用户, I want 直观的权限申请界面, so that 我可以轻松管理我的权限。

#### Acceptance Criteria

1. THE "我的权限" tab SHALL show current roles grouped by organization unit
2. THE "我的权限" tab SHALL show current virtual group memberships with bound roles
3. THE "申请权限" dialog SHALL have two modes: "申请角色" and "加入虚拟组"
4. FOR "申请角色" mode, THE dialog SHALL show organization unit selector and role selector
5. FOR "加入虚拟组" mode, THE dialog SHALL show virtual group selector with bound roles preview
6. THE "申请历史" tab SHALL show all requests with new type labels

## Technical Notes

### Admin Center API Dependencies

User Portal backend needs to call these Admin Center APIs:
- `GET /api/v1/admin/roles/business` - Get available business roles
- `GET /api/v1/admin/virtual-groups` - Get available virtual groups
- `GET /api/v1/admin/departments` - Get organization units (departments)
- `POST /api/v1/admin/roles/{roleId}/members/{userId}` - Assign role to user
- `POST /api/v1/admin/virtual-groups/{groupId}/members` - Add user to virtual group
- `GET /api/v1/admin/users/{userId}/roles` - Get user's current roles
- `GET /api/v1/admin/users/{userId}/virtual-groups` - Get user's virtual group memberships

### Database Schema

```sql
-- Updated permission_requests table
ALTER TABLE permission_requests 
  ADD COLUMN role_id VARCHAR(36),
  ADD COLUMN organization_unit_id VARCHAR(36),
  ADD COLUMN virtual_group_id VARCHAR(36);

-- Update request_type enum to include new types
-- ROLE_ASSIGNMENT, VIRTUAL_GROUP_JOIN
```
