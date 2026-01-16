# Requirements Document

## Introduction

本功能实现用户权限申请和审批机制，基于以下核心设计原则：

1. **虚拟组单角色绑定**：每个虚拟组只能绑定一个用户角色
2. **角色类型区分**：业务角色分为两种类型：
   - **BU-Bounded（业务单元绑定型）**：必须配合业务单元使用，代表用户在特定业务单元内的权限
   - **BU-Unbounded（业务单元无关型）**：独立于业务单元，用户获得后直接拥有该角色权限
3. **两阶段权限获取**：
   - 用户首先申请加入虚拟组，获得虚拟组绑定的角色
   - 对于 BU-Bounded 角色，用户还需要申请加入业务单元才能获得该业务单元内的具体权限
4. **分离的审批流程**：虚拟组申请和业务单元申请分别由各自的审批人审批

**重要变更**：
- 本需求将系统中原有的"部门"（Department）概念统一更名为"业务单元"（Business Unit）
- 移除原有的"业务单元+角色"直接申请功能，改为先加入虚拟组获取角色，再申请加入业务单元

## Glossary

- **Business_Unit**: 业务单元，组织架构的每个层级（原"部门"），可以有层级关系
- **Permission_Request**: 权限申请，用户发起的加入虚拟组或业务单元的申请
- **Virtual_Group_Request**: 虚拟组申请，用户申请加入某个虚拟组以获得绑定的角色
- **Business_Unit_Request**: 业务单元申请，用户申请加入某个业务单元（仅对 BU-Bounded 角色有意义）
- **Approver**: 审批人，有权审批权限申请的用户
- **Virtual_Group_Approver**: 虚拟组审批人，有权审批虚拟组申请的用户
- **Business_Unit_Approver**: 业务单元审批人，有权审批业务单元申请的用户
- **Request_Status**: 申请状态，包括 PENDING（待审批）、APPROVED（已批准）、REJECTED（已拒绝）、CANCELLED（已取消）
- **Admin_Center**: 管理中心，管理员配置审批人的系统
- **User_Portal**: 用户门户，用户发起申请和审批人审批申请的系统
- **Business_Role**: 业务角色，用于 User Portal 中的用户权限管理
- **BU_Bounded_Role**: 业务单元绑定型角色，必须配合业务单元使用才能生效
- **BU_Unbounded_Role**: 业务单元无关型角色，用户获得后直接拥有权限，无需关联业务单元
- **Virtual_Group**: 虚拟组，只能绑定一个业务角色
- **AD_Group**: Active Directory 组，用于与企业 AD 系统集成

## Requirements

### Requirement 1: 部门重命名为业务单元

**User Story:** As a 系统架构师, I want 将系统中的"部门"概念统一更名为"业务单元", so that 术语更准确地反映组织架构的层级结构。

#### Acceptance Criteria

1. THE System SHALL rename all "Department" references to "Business Unit" in the codebase
2. THE System SHALL update database table name from sys_departments to sys_business_units
3. THE System SHALL update all API endpoints from /departments to /business-units
4. THE System SHALL update all frontend UI labels from "部门" to "业务单元"
5. THE System SHALL update all i18n translations for zh-CN, zh-TW, and en locales
6. THE System SHALL maintain backward compatibility during the migration

### Requirement 2: 虚拟组单角色绑定

**User Story:** As a 系统管理员, I want 为虚拟组绑定一个业务角色, so that 加入虚拟组的用户可以自动获得该角色。

#### Acceptance Criteria

1. THE Admin_Center SHALL allow administrators to bind exactly ONE Business_Role to a Virtual_Group
2. WHEN a Business_Role is bound to a Virtual_Group, THE System SHALL store the binding relationship
3. THE Admin_Center SHALL display the Business_Role bound to each Virtual_Group
4. THE Admin_Center SHALL allow administrators to change the bound Business_Role of a Virtual_Group
5. THE Admin_Center SHALL allow administrators to remove the Business_Role binding from a Virtual_Group
6. THE Business_Role binding to Virtual_Group SHALL only accept roles with type BUSINESS (either BU_Bounded or BU_Unbounded)
7. THE System SHALL prevent binding more than one role to a Virtual_Group

### Requirement 3: 角色类型拆分

**User Story:** As a 系统架构师, I want 将业务角色分为 BU-Bounded 和 BU-Unbounded 两种类型, so that 系统可以区分需要业务单元上下文的角色和独立角色。

#### Acceptance Criteria

1. THE System SHALL support two subtypes of BUSINESS role: BU_BOUNDED and BU_UNBOUNDED
2. THE Admin_Center SHALL allow administrators to specify the role subtype when creating or editing a Business_Role
3. THE System SHALL display the role subtype in role lists and detail views
4. THE BU_BOUNDED role SHALL require association with a Business_Unit to be effective
5. THE BU_UNBOUNDED role SHALL be effective immediately upon assignment without Business_Unit association
6. THE System SHALL validate role subtype when processing permission requests

### Requirement 4: 虚拟组审批人配置

**User Story:** As a 系统管理员, I want 为虚拟组配置审批人, so that 用户的虚拟组申请可以被正确的人审批。

#### Acceptance Criteria

1. THE Admin_Center SHALL allow administrators to configure one or more Approvers for each Virtual_Group
2. WHEN an Approver is configured for a Virtual_Group, THE System SHALL store the approver relationship
3. THE Admin_Center SHALL display the list of Approvers for each Virtual_Group
4. THE Admin_Center SHALL allow administrators to remove Approvers from a Virtual_Group
5. THE Virtual_Group_Approver configuration SHALL accept any active user as an approver
6. IF no Approver is configured for a Virtual_Group, THEN THE System SHALL not allow users to apply for that Virtual_Group

### Requirement 5: 业务单元审批人配置

**User Story:** As a 系统管理员, I want 为业务单元配置审批人, so that 用户的业务单元申请可以被正确的人审批。

#### Acceptance Criteria

1. THE Admin_Center SHALL allow administrators to configure one or more Approvers for each Business_Unit
2. WHEN an Approver is configured for a Business_Unit, THE System SHALL store the approver relationship
3. THE Admin_Center SHALL display the list of Approvers for each Business_Unit
4. THE Admin_Center SHALL allow administrators to remove Approvers from a Business_Unit
5. THE Business_Unit_Approver configuration SHALL accept any active user as an approver
6. IF no Approver is configured for a Business_Unit, THEN THE System SHALL not allow users to apply for that Business_Unit

### Requirement 6: 虚拟组 AD Group 字段

**User Story:** As a 系统管理员, I want 为虚拟组配置 AD Group 字段, so that 未来可以与企业 Active Directory 系统集成。

#### Acceptance Criteria

1. THE VirtualGroup entity SHALL have an optional adGroup field
2. THE Admin_Center SHALL allow administrators to set the AD Group name for a Virtual_Group
3. THE Admin_Center SHALL display the AD Group in the Virtual_Group detail view
4. THE adGroup field SHALL be optional and can be left empty
5. THE adGroup field SHALL accept alphanumeric characters, hyphens, and underscores

### Requirement 7: 用户申请加入虚拟组

**User Story:** As a User Portal 用户, I want 申请加入虚拟组, so that 我可以获得虚拟组绑定的业务角色权限。

#### Acceptance Criteria

1. THE User_Portal SHALL display a list of Virtual_Groups that the user can apply to join
2. THE User_Portal SHALL display the bound role and its type (BU_Bounded/BU_Unbounded) for each Virtual_Group
3. WHEN a user submits a Virtual_Group application, THE System SHALL create a Permission_Request with status PENDING
4. THE User_Portal SHALL allow users to provide a reason for their application
5. THE User_Portal SHALL prevent users from submitting duplicate applications for the same Virtual_Group while a PENDING request exists
6. THE User_Portal SHALL display the user's Virtual_Group application history
7. THE User_Portal SHALL only show Virtual_Groups that have at least one Approver configured
8. THE User_Portal SHALL indicate which Virtual_Groups the user has already joined

### Requirement 8: 用户申请加入业务单元

**User Story:** As a User Portal 用户, I want 申请加入业务单元, so that 我可以激活我拥有的 BU-Bounded 角色在该业务单元的权限。

#### Acceptance Criteria

1. THE User_Portal SHALL only display Business_Units that are associated with the user's BU_BOUNDED roles
2. IF a user has no BU_BOUNDED roles, THE User_Portal SHALL not display the Business_Unit application option
3. WHEN a user submits a Business_Unit application, THE System SHALL create a Permission_Request with status PENDING
4. THE User_Portal SHALL allow users to provide a reason for their application
5. THE User_Portal SHALL prevent users from submitting duplicate applications for the same Business_Unit while a PENDING request exists
6. THE User_Portal SHALL display the user's Business_Unit application history
7. THE User_Portal SHALL only show Business_Units that have at least one Approver configured
8. THE User_Portal SHALL indicate which Business_Units the user has already joined
9. THE User_Portal SHALL display the user's BU-Bounded roles that will be activated upon joining the Business_Unit
10. THE System SHALL validate that the requested Business_Unit is associated with at least one of the user's BU_BOUNDED roles

### Requirement 9: 审批人审批申请

**User Story:** As a 审批人, I want 在 User Portal 中审批权限申请, so that 我可以控制用户的权限获取。

#### Acceptance Criteria

1. THE User_Portal SHALL display a pending approval list for Approvers
2. WHEN an Approver views the approval list, THE System SHALL only show requests for Virtual_Groups or Business_Units where the user is configured as an Approver
3. THE User_Portal SHALL allow Approvers to approve or reject each request
4. WHEN an Approver approves a request, THE System SHALL update the request status to APPROVED
5. WHEN an Approver rejects a request, THE System SHALL update the request status to REJECTED
6. THE User_Portal SHALL require Approvers to provide a comment when rejecting a request
7. THE User_Portal SHALL allow Approvers to provide an optional comment when approving a request
8. THE System SHALL NOT allow an Approver to approve or reject their own requests
9. WHEN an Approver submits a request, THE System SHALL exclude that request from the Approver's approval list

### Requirement 10: 虚拟组申请审批通过后即时生效

**User Story:** As a 系统, I want 虚拟组申请审批通过后立即生效, so that 用户可以立即获得相应的权限。

#### Acceptance Criteria

1. WHEN a Virtual_Group request is approved, THE System SHALL immediately add the user to the Virtual_Group
2. WHEN a Virtual_Group request is approved, THE System SHALL immediately grant the user the Business_Role bound to that Virtual_Group
3. IF the bound role is BU_UNBOUNDED, THE user SHALL immediately have the role's permissions
4. IF the bound role is BU_BOUNDED, THE user SHALL have the role but it will only be effective in Business_Units the user has joined
5. THE permission changes SHALL take effect without requiring the user to log out and log in again
6. THE System SHALL send a notification to the user when their request is approved or rejected

### Requirement 11: 业务单元申请审批通过后即时生效

**User Story:** As a 系统, I want 业务单元申请审批通过后立即生效, so that 用户的 BU-Bounded 角色可以在该业务单元内生效。

#### Acceptance Criteria

1. WHEN a Business_Unit request is approved, THE System SHALL immediately add the user to the Business_Unit
2. WHEN a Business_Unit request is approved, THE System SHALL activate all BU_BOUNDED roles the user has for that Business_Unit
3. THE permission changes SHALL take effect without requiring the user to log out and log in again
4. THE System SHALL send a notification to the user when their request is approved or rejected

### Requirement 12: 审批人菜单和权限控制

**User Story:** As a 系统, I want 只有审批人可以看到审批相关的菜单和功能, so that 普通用户不会看到无关的功能。

#### Acceptance Criteria

1. THE User_Portal SHALL only display the approval menu to users who are configured as Approvers for at least one Virtual_Group or Business_Unit
2. THE User_Portal SHALL only display approval detail actions to Approvers
3. WHEN a user is not an Approver, THE User_Portal SHALL hide all approval-related menus and actions
4. THE approval permission check SHALL be performed on both frontend and backend

### Requirement 13: 用户取消申请

**User Story:** As a User Portal 用户, I want 取消我的待审批申请, so that 我可以撤回错误提交的申请。

#### Acceptance Criteria

1. THE User_Portal SHALL allow users to cancel their own PENDING requests
2. WHEN a user cancels a request, THE System SHALL update the request status to CANCELLED
3. THE User_Portal SHALL not allow users to cancel requests that are already APPROVED, REJECTED, or CANCELLED
4. THE cancelled request SHALL not affect the user's ability to submit a new request for the same target

### Requirement 14: 申请记录查询

**User Story:** As a 系统管理员, I want 查看所有权限申请记录, so that 我可以审计权限变更历史。

#### Acceptance Criteria

1. THE Admin_Center SHALL display a list of all Permission_Requests
2. THE Admin_Center SHALL allow filtering requests by status, type, applicant, and date range
3. THE Admin_Center SHALL display request details including applicant, target, status, approver, and timestamps
4. THE Admin_Center SHALL not allow administrators to modify request status directly (only through the approval workflow)

### Requirement 15: 审批人清退成员

**User Story:** As a 审批人, I want 清退我权限范围内的成员, so that 我可以管理业务单元或虚拟组的成员。

#### Acceptance Criteria

1. THE User_Portal SHALL allow Approvers to view the member list of Virtual_Groups or Business_Units where they are configured as Approvers
2. THE User_Portal SHALL allow Approvers to remove members from Virtual_Groups or Business_Units within their approval scope
3. WHEN an Approver removes a member from a Virtual_Group, THE System SHALL immediately remove the user from the Virtual_Group and revoke the inherited Business_Role
4. WHEN an Approver removes a member from a Business_Unit, THE System SHALL immediately remove the user from the Business_Unit and deactivate BU_BOUNDED roles for that Business_Unit
5. THE System SHALL record the removal action including the Approver, target user, and timestamp
6. THE System SHALL send a notification to the user when they are removed from a Virtual_Group or Business_Unit

### Requirement 16: 用户申请退出

**User Story:** As a User Portal 用户, I want 申请退出虚拟组或业务单元, so that 我可以主动放弃不再需要的权限。

#### Acceptance Criteria

1. THE User_Portal SHALL display the user's current Virtual_Groups and Business_Units
2. THE User_Portal SHALL allow users to exit from a Virtual_Group, which will revoke the inherited Business_Role
3. THE User_Portal SHALL allow users to exit from a Business_Unit, which will deactivate BU_BOUNDED roles for that Business_Unit
4. THE exit action SHALL NOT require approval (immediate effect)
5. THE User_Portal SHALL display the user's exit history
6. THE System SHALL record the exit action including the user and timestamp

### Requirement 17: 用户权限视图

**User Story:** As a User Portal 用户, I want 查看我当前拥有的所有权限, so that 我可以了解我的权限状态。

#### Acceptance Criteria

1. THE User_Portal SHALL display all Virtual_Groups the user has joined and the associated roles
2. THE User_Portal SHALL display all Business_Units the user has joined
3. THE User_Portal SHALL clearly indicate which roles are BU_BOUNDED and which are BU_UNBOUNDED
4. FOR BU_BOUNDED roles, THE User_Portal SHALL show which Business_Units the role is active in
5. THE User_Portal SHALL provide a clear summary of the user's effective permissions

### Requirement 18: BU-Bounded 角色未激活提醒

**User Story:** As a User Portal 用户, I want 在登录时被提醒我有未激活的 BU-Bounded 角色, so that 我可以及时申请加入业务单元来激活这些角色。

#### Acceptance Criteria

1. WHEN a user logs in to User_Portal, THE System SHALL check if the user has any BU_BOUNDED roles that are not associated with any Business_Unit
2. IF the user has one or more BU_BOUNDED roles that are not activated in any Business_Unit, THE User_Portal SHALL display a modal dialog reminding the user
3. THE reminder dialog SHALL display the list of BU_BOUNDED roles that are not yet activated in any Business_Unit
4. THE reminder dialog SHALL provide a direct link to the Business_Unit application page
5. THE reminder dialog SHALL allow the user to dismiss it temporarily (for the current session)
6. THE reminder dialog SHALL allow the user to choose "Don't remind me again" option
7. THE System SHALL store the user's "Don't remind me again" preference
8. THE reminder dialog SHALL only appear once per login session (not on every page navigation)
9. IF a BU_BOUNDED role is activated in at least one Business_Unit, THE System SHALL NOT include it in the reminder list

