# Design Document: Task Assignment Redesign

## Overview

本设计文档描述了工作流任务分配机制的重新设计。新的分配机制将从现有的7种类型更新为9种标准类型，以更好地支持基于业务单元（Business Unit）和角色（Role）的任务分配。

核心设计原则：
1. **直接分配**：只有能确定唯一处理人的类型（职能经理、实体经理、发起人）才直接分配
2. **认领机制**：所有基于角色的分配都采用认领机制，将任务分配给候选人列表
3. **角色类型区分**：BU_BOUNDED 角色需要配合业务单元使用，BU_UNBOUNDED 角色直接生效

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Developer Workstation (Frontend)                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    UserTaskProperties.vue                            │   │
│  │  - 9种分配类型下拉选择                                                │   │
│  │  - 角色选择器（按类型过滤）                                           │   │
│  │  - 业务单元选择器（FIXED_BU_ROLE时显示）                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ BPMN XML (assigneeType, roleId, businessUnitId)
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Workflow Engine Core (Backend)                        │
│  ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────┐ │
│  │ TaskAssignmentListener│───▶│ TaskAssigneeResolver │───▶│AdminCenterClient│ │
│  │ (Flowable Event)     │    │ (9种类型解析)        │    │ (API调用)       │ │
│  └─────────────────────┘    └─────────────────────┘    └─────────────────┘ │
│                                      │                          │           │
│                                      │                          │           │
│  ┌─────────────────────┐            │                          │           │
│  │   AssigneeType      │◀───────────┘                          │           │
│  │   (9种枚举值)        │                                       │           │
│  └─────────────────────┘                                       │           │
└─────────────────────────────────────────────────────────────────│───────────┘
                                                                  │
                                                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Admin Center (Backend)                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         New API Endpoints                            │   │
│  │  GET /roles?type=BU_BOUNDED          - 获取所有BU绑定型角色          │   │
│  │  GET /roles?type=BU_UNBOUNDED        - 获取所有BU无关型角色          │   │
│  │  GET /business-units/{id}/eligible-roles - 获取业务单元准入角色      │   │
│  │  GET /business-units/{id}/role/{roleId}/users - 获取BU角色用户列表   │   │
│  │  GET /roles/{roleId}/users           - 获取角色用户列表（通过虚拟组） │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. AssigneeType 枚举（更新）

```java
package com.workflow.enums;

public enum AssigneeType {
    // 直接分配类型（不需要认领）
    FUNCTION_MANAGER("FUNCTION_MANAGER", "职能经理", false, false, false),
    ENTITY_MANAGER("ENTITY_MANAGER", "实体经理", false, false, false),
    INITIATOR("INITIATOR", "流程发起人", false, false, false),
    
    // 基于当前人业务单元的角色分配（需要认领）
    CURRENT_BU_ROLE("CURRENT_BU_ROLE", "当前人业务单元角色", true, true, false),
    CURRENT_PARENT_BU_ROLE("CURRENT_PARENT_BU_ROLE", "当前人上级业务单元角色", true, true, false),
    
    // 基于发起人业务单元的角色分配（需要认领）
    INITIATOR_BU_ROLE("INITIATOR_BU_ROLE", "发起人业务单元角色", true, true, false),
    INITIATOR_PARENT_BU_ROLE("INITIATOR_PARENT_BU_ROLE", "发起人上级业务单元角色", true, true, false),
    
    // 指定业务单元角色分配（需要认领）
    FIXED_BU_ROLE("FIXED_BU_ROLE", "指定业务单元角色", true, true, true),
    
    // BU无关型角色分配（需要认领）
    BU_UNBOUNDED_ROLE("BU_UNBOUNDED_ROLE", "BU无关型角色", true, true, false);
    
    private final String code;
    private final String name;
    private final boolean requiresClaim;
    private final boolean requiresRoleId;
    private final boolean requiresBusinessUnitId;
    
    // Constructor and getters...
}
```

### 2. TaskAssigneeResolver 服务（更新）

```java
public interface TaskAssigneeResolver {
    
    @Data
    @Builder
    class ResolveResult {
        private String assignee;              // 直接分配的处理人ID
        private List<String> candidateUsers;  // 候选人ID列表
        private boolean requiresClaim;        // 是否需要认领
        private AssigneeType assigneeType;    // 分配类型
        private String errorMessage;          // 错误信息
    }
    
    /**
     * 解析任务处理人
     * @param assigneeTypeCode 分配类型代码
     * @param roleId 角色ID（部分类型需要）
     * @param businessUnitId 业务单元ID（FIXED_BU_ROLE需要）
     * @param initiatorId 流程发起人ID
     * @param currentUserId 当前处理人ID（用于基于当前人的分配）
     */
    ResolveResult resolve(String assigneeTypeCode, String roleId, 
                          String businessUnitId, String initiatorId, 
                          String currentUserId);
}
```

### 3. AdminCenterClient 新增方法

```java
public interface AdminCenterClient {
    
    // 现有方法保留...
    
    /**
     * 获取用户的业务单元ID
     */
    String getUserBusinessUnitId(String userId);
    
    /**
     * 获取业务单元的父业务单元ID
     */
    String getParentBusinessUnitId(String businessUnitId);
    
    /**
     * 获取业务单元中拥有指定角色的用户列表
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID（BU_BOUNDED类型）
     * @return 用户ID列表
     */
    List<String> getUsersByBusinessUnitAndRole(String businessUnitId, String roleId);
    
    /**
     * 获取拥有指定BU无关型角色的用户列表
     * @param roleId 角色ID（BU_UNBOUNDED类型）
     * @return 用户ID列表
     */
    List<String> getUsersByUnboundedRole(String roleId);
    
    /**
     * 获取业务单元的准入角色列表
     * @param businessUnitId 业务单元ID
     * @return 角色ID列表
     */
    List<String> getEligibleRoleIds(String businessUnitId);
    
    /**
     * 获取所有BU绑定型角色
     */
    List<Map<String, Object>> getBuBoundedRoles();
    
    /**
     * 获取所有BU无关型角色
     */
    List<Map<String, Object>> getBuUnboundedRoles();
}
```

### 4. 前端 UserTaskProperties 组件更新

```typescript
// 9种分配类型
type AssigneeTypeEnum = 
  | 'FUNCTION_MANAGER'      // 职能经理
  | 'ENTITY_MANAGER'        // 实体经理
  | 'INITIATOR'             // 流程发起人
  | 'CURRENT_BU_ROLE'       // 当前人业务单元角色
  | 'CURRENT_PARENT_BU_ROLE'// 当前人上级业务单元角色
  | 'INITIATOR_BU_ROLE'     // 发起人业务单元角色
  | 'INITIATOR_PARENT_BU_ROLE' // 发起人上级业务单元角色
  | 'FIXED_BU_ROLE'         // 指定业务单元角色
  | 'BU_UNBOUNDED_ROLE';    // BU无关型角色

interface AssigneeConfig {
  assigneeType: AssigneeTypeEnum;
  roleId?: string;           // 角色ID（6种角色类型需要）
  businessUnitId?: string;   // 业务单元ID（FIXED_BU_ROLE需要）
  assigneeLabel?: string;    // 显示标签
}
```

### 5. Admin Center 新增 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/roles?type=BU_BOUNDED` | 获取所有BU绑定型角色 |
| GET | `/roles?type=BU_UNBOUNDED` | 获取所有BU无关型角色 |
| GET | `/business-units/{id}/eligible-roles` | 获取业务单元准入角色 |
| GET | `/business-units/{id}/roles/{roleId}/users` | 获取业务单元中拥有指定角色的用户 |
| GET | `/roles/{roleId}/users` | 获取拥有指定角色的用户（通过虚拟组） |

## Data Models

### 数据库表关系

```
sys_users
├── id (PK)
├── function_manager_id (FK -> sys_users.id)
├── entity_manager_id (FK -> sys_users.id)
└── ...

sys_user_business_units
├── id (PK)
├── user_id (FK -> sys_users.id)
├── business_unit_id (FK -> sys_business_units.id)
└── ...

sys_business_units
├── id (PK)
├── parent_id (FK -> sys_business_units.id)
└── ...

sys_roles
├── id (PK)
├── type (BU_BOUNDED | BU_UNBOUNDED | ADMIN | DEVELOPER)
└── ...

sys_business_unit_roles (准入角色)
├── id (PK)
├── business_unit_id (FK -> sys_business_units.id)
├── role_id (FK -> sys_roles.id, type=BU_BOUNDED)
└── ...

sys_user_business_unit_roles (用户在业务单元的角色)
├── id (PK)
├── user_id (FK -> sys_users.id)
├── business_unit_id (FK -> sys_business_units.id)
├── role_id (FK -> sys_roles.id, type=BU_BOUNDED)
└── ...

sys_virtual_groups
├── id (PK)
└── ...

sys_virtual_group_members
├── id (PK)
├── group_id (FK -> sys_virtual_groups.id)
├── user_id (FK -> sys_users.id)
└── ...

sys_virtual_group_roles (虚拟组绑定的角色)
├── id (PK)
├── group_id (FK -> sys_virtual_groups.id)
├── role_id (FK -> sys_roles.id)
└── ...
```

### 分配类型解析逻辑

| 类型 | 解析逻辑 | 数据来源 |
|------|---------|---------|
| FUNCTION_MANAGER | 查询 initiator 的 function_manager_id | sys_users |
| ENTITY_MANAGER | 查询 initiator 的 entity_manager_id | sys_users |
| INITIATOR | 直接使用 initiator | 流程变量 |
| CURRENT_BU_ROLE | 查询 currentUser 的 BU，再查该 BU 中有 roleId 的用户 | sys_user_business_units + sys_user_business_unit_roles |
| CURRENT_PARENT_BU_ROLE | 查询 currentUser 的 BU 的 parent，再查该 BU 中有 roleId 的用户 | sys_business_units + sys_user_business_unit_roles |
| INITIATOR_BU_ROLE | 查询 initiator 的 BU，再查该 BU 中有 roleId 的用户 | sys_user_business_units + sys_user_business_unit_roles |
| INITIATOR_PARENT_BU_ROLE | 查询 initiator 的 BU 的 parent，再查该 BU 中有 roleId 的用户 | sys_business_units + sys_user_business_unit_roles |
| FIXED_BU_ROLE | 直接查询指定 BU 中有 roleId 的用户 | sys_user_business_unit_roles |
| BU_UNBOUNDED_ROLE | 查询绑定了 roleId 的虚拟组的所有成员 | sys_virtual_group_roles + sys_virtual_group_members |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Direct Assignment Resolution

*For any* user with a valid function_manager_id, when assigneeType is FUNCTION_MANAGER, the resolved assignee should equal the user's function_manager_id.

*For any* user with a valid entity_manager_id, when assigneeType is ENTITY_MANAGER, the resolved assignee should equal the user's entity_manager_id.

*For any* process instance with a valid initiator, when assigneeType is INITIATOR, the resolved assignee should equal the initiator.

**Validates: Requirements 1.3, 1.4, 1.5, 2.1, 2.2, 2.3**

### Property 2: BU Role Candidate Resolution

*For any* business unit and BU_BOUNDED role combination, when resolving candidates for CURRENT_BU_ROLE, INITIATOR_BU_ROLE, or FIXED_BU_ROLE, the returned candidate list should contain exactly the users who have that role in that business unit (from sys_user_business_unit_roles).

**Validates: Requirements 3.2, 5.2, 7.3**

### Property 3: Parent BU Role Candidate Resolution

*For any* business unit with a parent and BU_BOUNDED role combination, when resolving candidates for CURRENT_PARENT_BU_ROLE or INITIATOR_PARENT_BU_ROLE, the returned candidate list should contain exactly the users who have that role in the parent business unit.

**Validates: Requirements 4.2, 4.3, 6.2, 6.3**

### Property 4: BU Unbounded Role Candidate Resolution

*For any* BU_UNBOUNDED role, when resolving candidates for BU_UNBOUNDED_ROLE, the returned candidate list should contain exactly the users who are members of virtual groups that have this role bound.

**Validates: Requirements 8.2, 8.3**

### Property 5: Claim Mode Consistency

*For any* assignee type that is not FUNCTION_MANAGER, ENTITY_MANAGER, or INITIATOR, the resolve result should have requiresClaim=true and assignee=null.

**Validates: Requirements 3.3, 4.4, 5.3, 7.4, 8.4**

### Property 6: Eligible Role Validation

*For any* FIXED_BU_ROLE assignment, if the roleId is not in the business unit's eligible roles (sys_business_unit_roles), the resolution should return an error.

**Validates: Requirements 7.2, 7.5**

### Property 7: BPMN Extension Round-Trip

*For any* valid assignee configuration (assigneeType, roleId, businessUnitId, assigneeLabel), saving to BPMN XML and then loading should produce an equivalent configuration.

**Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5**

### Property 8: Role Type Filtering

*For any* role dropdown in the frontend, when assigneeType requires BU_BOUNDED roles, only roles with type='BU_BOUNDED' should be displayed. When assigneeType is BU_UNBOUNDED_ROLE, only roles with type='BU_UNBOUNDED' should be displayed.

**Validates: Requirements 9.4, 9.5, 9.6**

## Error Handling

### 解析错误处理

| 错误场景 | 处理方式 |
|---------|---------|
| 用户无职能经理 | 返回错误信息，任务不分配 |
| 用户无实体经理 | 返回错误信息，任务不分配 |
| 用户无业务单元 | 返回错误信息，任务不分配 |
| 业务单元无父级 | 返回错误信息，任务不分配 |
| 角色不是准入角色 | 返回错误信息，任务不分配 |
| 无候选人 | 返回警告，任务设置空候选人列表 |
| Admin Center 不可用 | 返回错误信息，任务不分配 |

### 日志记录

```java
// 成功分配
log.info("Task {} assigned to user: {}", taskId, assignee);
log.info("Task {} set candidate users: {}", taskId, candidateUsers);

// 解析失败
log.warn("Failed to resolve assignee for task {}: {}", taskId, errorMessage);

// 服务不可用
log.error("Admin Center is not available, cannot resolve assignee");
```

## Testing Strategy

### 单元测试

1. **AssigneeType 枚举测试**
   - 验证枚举包含9种类型
   - 验证每种类型的属性（requiresClaim, requiresRoleId, requiresBusinessUnitId）

2. **TaskAssigneeResolver 测试**
   - 测试每种分配类型的解析逻辑
   - 测试错误场景（无经理、无业务单元等）
   - 使用 Mock 的 AdminCenterClient

3. **AdminCenterClient 测试**
   - 测试新增的 API 调用方法
   - 测试错误处理

### 属性测试（Property-Based Testing）

使用 jqwik 框架进行属性测试：

1. **Property 1**: 直接分配解析
   - 生成随机用户数据（有/无经理）
   - 验证解析结果正确性

2. **Property 2-4**: 候选人解析
   - 生成随机业务单元、角色、用户数据
   - 验证候选人列表正确性

3. **Property 5**: 认领模式一致性
   - 对所有非直接分配类型验证 requiresClaim=true

4. **Property 7**: BPMN 扩展属性往返
   - 生成随机配置
   - 保存到 BPMN XML 再加载
   - 验证数据一致性

### 集成测试

1. **端到端流程测试**
   - 部署包含各种分配类型的流程
   - 启动流程实例
   - 验证任务分配正确

2. **API 测试**
   - 测试 Admin Center 新增的 API 端点
   - 验证返回数据正确性

### 测试框架配置

```xml
<!-- pom.xml -->
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.2</version>
    <scope>test</scope>
</dependency>
```

```java
// 属性测试示例
@Property(tries = 100)
void directAssignmentResolvesCorrectly(
    @ForAll @From("usersWithManagers") User user
) {
    // Feature: task-assignment-redesign, Property 1: Direct Assignment Resolution
    ResolveResult result = resolver.resolve("FUNCTION_MANAGER", null, null, user.getId(), user.getId());
    assertThat(result.getAssignee()).isEqualTo(user.getFunctionManagerId());
    assertThat(result.isRequiresClaim()).isFalse();
}
```
