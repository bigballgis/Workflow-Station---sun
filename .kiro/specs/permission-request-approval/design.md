# Design Document: Permission Request and Approval System

## Overview

本设计文档描述了权限申请和审批系统的架构设计，包括：
1. 将"部门"重命名为"业务单元"
2. 虚拟组单角色绑定（每个虚拟组只能绑定一个角色）
3. 角色类型拆分（BU-Bounded 和 BU-Unbounded）
4. 虚拟组 AD Group 字段
5. 审批人配置
6. 用户申请加入虚拟组（获取角色）
7. 用户申请加入业务单元（激活 BU-Bounded 角色）
8. 审批人审批申请
9. 审批人清退成员
10. 用户主动退出

## Architecture

用户权限获取流程：
1. 用户申请加入虚拟组 -> 审批通过 -> 获得虚拟组绑定的角色
2. 如果角色是 BU-Unbounded：立即拥有权限
3. 如果角色是 BU-Bounded：需要再申请加入业务单元 -> 审批通过 -> 角色在该业务单元内激活

## Components and Interfaces

### 1. 业务单元（原部门）

```java
@Entity
@Table(name = "sys_business_units")
public class BusinessUnit {
    @Id
    private String id;
    private String name;
    private String code;
    private String parentId;
    private Integer level;
    private String path;
    private String managerId;
    private String secondaryManagerId;
}
```

### 2. 角色类型枚举（更新）

```java
public enum RoleType {
    /** 业务角色 - 业务单元绑定型，需要配合业务单元使用 */
    BU_BOUNDED,
    /** 业务角色 - 业务单元无关型，独立生效 */
    BU_UNBOUNDED,
    /** 管理角色 - 用于 Admin Center 管理功能 */
    ADMIN,
    /** 开发角色 - 用于 Developer Workstation 功能权限控制 */
    DEVELOPER
}
```

### 3. 虚拟组实体（更新）

```java
@Entity
@Table(name = "sys_virtual_groups")
public class VirtualGroup {
    @Id
    private String id;
    private String name;
    
    @Enumerated(EnumType.STRING)
    private VirtualGroupType type;
    
    private String description;
    private Instant validFrom;
    private Instant validTo;
    private String status;
    
    // 新增：AD Group 字段
    @Column(name = "ad_group", length = 100)
    private String adGroup;
}
```

### 4. 虚拟组角色绑定（单角色）

```java
@Entity
@Table(name = "sys_virtual_group_roles")
public class VirtualGroupRole {
    @Id
    private String id;
    
    @Column(name = "virtual_group_id", unique = true)
    private String virtualGroupId;
    
    @Column(name = "role_id")
    private String roleId;
    
    private Instant createdAt;
    private String createdBy;
}
```

### 5. 用户业务单元成员关系

```java
@Entity
@Table(name = "sys_user_business_units")
public class UserBusinessUnit {
    @Id
    private String id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "business_unit_id")
    private String businessUnitId;
    
    private Instant createdAt;
    private String createdBy;
}
```

### 6. 审批人配置

```java
@Entity
@Table(name = "sys_approvers")
public class Approver {
    @Id
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private ApproverTargetType targetType;
    
    @Column(name = "target_id")
    private String targetId;
    
    @Column(name = "user_id")
    private String userId;
    
    private Instant createdAt;
    private String createdBy;
}

public enum ApproverTargetType {
    VIRTUAL_GROUP,
    BUSINESS_UNIT
}
```

### 7. 权限申请

```java
@Entity
@Table(name = "sys_permission_requests")
public class PermissionRequest {
    @Id
    private String id;
    
    @Column(name = "applicant_id")
    private String applicantId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type")
    private PermissionRequestType requestType;
    
    @Column(name = "target_id")
    private String targetId;
    
    private String reason;
    
    @Enumerated(EnumType.STRING)
    private PermissionRequestStatus status;
    
    @Column(name = "approver_id")
    private String approverId;
    
    @Column(name = "approver_comment")
    private String approverComment;
    
    private Instant createdAt;
    private Instant updatedAt;
    private Instant approvedAt;
}

public enum PermissionRequestType {
    VIRTUAL_GROUP,
    BUSINESS_UNIT
}

public enum PermissionRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
```

### 8. 服务接口

```java
// 虚拟组角色绑定服务（单角色）
public interface VirtualGroupRoleService {
    void bindRole(String virtualGroupId, String roleId);
    void unbindRole(String virtualGroupId);
    Optional<Role> getBoundRole(String virtualGroupId);
    boolean hasRole(String virtualGroupId);
}

// 审批人配置服务
public interface ApproverService {
    void addApprover(ApproverTargetType targetType, String targetId, String userId);
    void removeApprover(String approverId);
    List<User> getApprovers(ApproverTargetType targetType, String targetId);
    boolean isApprover(String userId, ApproverTargetType targetType, String targetId);
    boolean isAnyApprover(String userId);
}

// 权限申请服务
public interface PermissionRequestService {
    PermissionRequest createVirtualGroupRequest(String applicantId, String virtualGroupId, String reason);
    PermissionRequest createBusinessUnitRequest(String applicantId, String businessUnitId, String reason);
    void approve(String requestId, String approverId, String comment);
    void reject(String requestId, String approverId, String comment);
    void cancel(String requestId, String userId);
    List<PermissionRequest> getPendingRequestsForApprover(String approverId);
    List<PermissionRequest> getRequestsByApplicant(String applicantId);
    // 获取用户可申请的业务单元（基于用户的 BU_BOUNDED 角色）
    List<BusinessUnit> getApplicableBusinessUnits(String userId);
}

// 成员管理服务
public interface MemberManagementService {
    void removeVirtualGroupMember(String virtualGroupId, String userId, String approverId);
    void removeBusinessUnitMember(String businessUnitId, String userId, String approverId);
    void exitVirtualGroup(String virtualGroupId, String userId);
    void exitBusinessUnit(String businessUnitId, String userId);
    List<User> getVirtualGroupMembers(String virtualGroupId);
    List<User> getBusinessUnitMembers(String businessUnitId);
}

// 用户权限查询服务
public interface UserPermissionService {
    List<Role> getUserRoles(String userId);
    Map<Role, List<BusinessUnit>> getUserBuBoundedRoles(String userId);
    List<Role> getUserBuUnboundedRoles(String userId);
    boolean hasRoleInBusinessUnit(String userId, String roleId, String businessUnitId);
    // 获取未在任何业务单元激活的 BU-Bounded 角色
    List<Role> getUnactivatedBuBoundedRoles(String userId);
    // 检查用户是否需要显示 BU 申请提醒（有未激活的 BU-Bounded 角色且未设置不再提醒）
    boolean shouldShowBuApplicationReminder(String userId);
    // 设置用户的"不再提醒"偏好
    void setDontRemindPreference(String userId, boolean dontRemind);
}
```

## Data Models

### 数据库表结构

```sql
-- 虚拟组（更新：添加 ad_group 字段）
ALTER TABLE sys_virtual_groups ADD COLUMN ad_group VARCHAR(100);

-- 虚拟组角色绑定（唯一约束确保单角色）
CREATE TABLE sys_virtual_group_roles (
    id VARCHAR(64) PRIMARY KEY,
    virtual_group_id VARCHAR(64) NOT NULL UNIQUE,
    role_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    FOREIGN KEY (virtual_group_id) REFERENCES sys_virtual_groups(id),
    FOREIGN KEY (role_id) REFERENCES sys_roles(id)
);

-- 用户业务单元成员关系
CREATE TABLE sys_user_business_units (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    business_unit_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    UNIQUE (user_id, business_unit_id),
    FOREIGN KEY (user_id) REFERENCES sys_users(id),
    FOREIGN KEY (business_unit_id) REFERENCES sys_business_units(id)
);

-- 审批人配置
CREATE TABLE sys_approvers (
    id VARCHAR(64) PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    UNIQUE (target_type, target_id, user_id),
    FOREIGN KEY (user_id) REFERENCES sys_users(id)
);

-- 权限申请
CREATE TABLE sys_permission_requests (
    id VARCHAR(64) PRIMARY KEY,
    applicant_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approver_id VARCHAR(64),
    approver_comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    approved_at TIMESTAMP,
    FOREIGN KEY (applicant_id) REFERENCES sys_users(id)
);

-- 成员变更记录
CREATE TABLE sys_member_change_logs (
    id VARCHAR(64) PRIMARY KEY,
    change_type VARCHAR(20) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    operator_id VARCHAR(64),
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户偏好设置（用于存储"不再提醒"等偏好）
CREATE TABLE sys_user_preferences (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    preference_key VARCHAR(100) NOT NULL,
    preference_value VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (user_id, preference_key),
    FOREIGN KEY (user_id) REFERENCES sys_users(id)
);
```

## Correctness Properties

### Property 1: Role Type Validation for Virtual Group Binding
*For any* role binding operation to a Virtual_Group, the system shall accept only roles with type BU_BOUNDED or BU_UNBOUNDED, and reject any role with type ADMIN or DEVELOPER.
**Validates: Requirements 2.6**

### Property 2: Single Role Per Virtual Group
*For any* Virtual_Group, the system shall ensure that at most one role is bound at any time.
**Validates: Requirements 2.1, 2.7**

### Property 3: Approver Scope Filtering
*For any* approver viewing the pending approval list, the system shall only return requests for Virtual_Groups or Business_Units where the user is configured as an approver.
**Validates: Requirements 9.2, 9.9**

### Property 4: Self-Approval Prevention
*For any* permission request, if the applicant is also an approver for the target, the system shall exclude that request from the approver's approval list.
**Validates: Requirements 9.8, 9.9**

### Property 5: Duplicate Request Prevention
*For any* user with a PENDING request for a specific target, the system shall reject any new request for the same target.
**Validates: Requirements 7.5, 8.4**

### Property 6: Request Status Transition
*For any* permission request, the status shall only transition in valid paths: PENDING -> APPROVED, PENDING -> REJECTED, PENDING -> CANCELLED.
**Validates: Requirements 9.4, 9.5, 13.2, 13.3**

### Property 7: Virtual Group Approval Immediate Effect
*For any* approved Virtual_Group request, the user shall be immediately added to the Virtual_Group and granted the bound Business_Role.
**Validates: Requirements 10.1, 10.2**

### Property 8: Business Unit Approval Immediate Effect
*For any* approved Business_Unit request, the user shall be immediately added to the Business_Unit.
**Validates: Requirements 11.1, 11.2**

### Property 9: Approver Menu Visibility
*For any* user, the approval menu shall be visible if and only if the user is configured as an approver for at least one Virtual_Group or Business_Unit.
**Validates: Requirements 12.1, 12.2, 12.3**

### Property 10: Approver-Only Application Requirement
*For any* Virtual_Group or Business_Unit without any configured approvers, the system shall not allow users to submit applications.
**Validates: Requirements 4.6, 5.6, 7.7, 8.6**

### Property 11: Member Removal Immediate Effect
*For any* member removal operation, the system shall immediately revoke the membership.
**Validates: Requirements 15.3, 15.4, 16.2, 16.3**

### Property 12: BU-Bounded Role Activation
*For any* user with a BU_BOUNDED role, the role shall only be effective in Business_Units where the user is a member.
**Validates: Requirements 3.4, 10.4, 11.2**

### Property 13: BU-Unbounded Role Immediate Effect
*For any* user who receives a BU_UNBOUNDED role, the role shall be immediately effective.
**Validates: Requirements 3.5, 10.3**

### Property 14: Rejection Requires Comment
*For any* request rejection, the system shall require a non-empty comment from the approver.
**Validates: Requirements 9.6**

### Property 15: Virtual Group Exit Revokes Role
*For any* user who exits a Virtual_Group, the system shall immediately revoke the inherited Business_Role.
**Validates: Requirements 15.3, 16.2**

### Property 16: Business Unit Exit Deactivates BU-Bounded Roles
*For any* user who exits a Business_Unit, the system shall immediately deactivate all BU_BOUNDED roles for that Business_Unit.
**Validates: Requirements 15.4, 16.3**

### Property 17: Business Unit Application Restricted to BU-Bounded Role Associations
*For any* Business_Unit application, the system shall only allow the user to apply for Business_Units that are associated with at least one of the user's BU_BOUNDED roles.
**Validates: Requirements 8.1, 8.10**

### Property 18: Unactivated BU-Bounded Role Reminder
*For any* user with one or more BU_BOUNDED roles that are not activated in any Business_Unit, the system shall display a reminder dialog upon login (unless the user has set "Don't remind me again"). A BU_BOUNDED role is considered "not activated" if the user has not joined any Business_Unit where that role would be effective.
**Validates: Requirements 18.1, 18.2, 18.6, 18.7, 18.9**

## Error Handling

1. **角色类型验证失败**: 返回 400 错误，提示"只能绑定业务角色"
2. **重复申请**: 返回 400 错误，提示"已存在待审批的申请"
3. **自我审批**: 返回 403 错误，提示"不能审批自己的申请"
4. **无审批人**: 返回 400 错误，提示"该目标未配置审批人"
5. **无效状态转换**: 返回 400 错误，提示"只能取消待审批的申请"
6. **权限不足**: 返回 403 错误，提示"您不是该目标的审批人"
7. **拒绝无意见**: 返回 400 错误，提示"拒绝时必须提供审批意见"
8. **业务单元申请无关联角色**: 返回 400 错误，提示"您没有与该业务单元关联的 BU-Bounded 角色"

## Testing Strategy

### Property-Based Tests
- Property 1-2: 角色绑定验证测试
- Property 3-4: 审批人权限测试
- Property 5-6: 申请状态测试
- Property 7-8: 审批生效测试
- Property 9-10: 菜单可见性测试
- Property 11-16: 成员管理测试
- Property 17: 业务单元申请限制测试
- Property 18: 未激活角色提醒测试
