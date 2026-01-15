# Requirements Document

## Introduction

本需求文档定义了工作流任务分配机制的重新设计。新的分配机制将从现有的7种类型更新为9种标准类型，以更好地支持基于业务单元（Business Unit）和角色（Role）的任务分配。核心原则是：所有非具体到人的分配都采用认领机制。

## Glossary

- **Task_Assignment_System**: 任务分配系统，负责在流程运行时将任务分配给正确的处理人
- **AssigneeType**: 分配类型枚举，定义了9种标准的任务分配方式
- **Business_Unit**: 业务单元，组织架构中的基本单位（存储在 sys_business_units 表）
- **BU_Bounded_Role**: 业务单元绑定型角色（type='BU_BOUNDED'），需要配合业务单元使用才能生效
- **BU_Unbounded_Role**: 业务单元无关型角色（type='BU_UNBOUNDED'），用户获得后直接拥有权限
- **Eligible_Role**: 准入角色，业务单元允许的 BU_Bounded_Role 列表（存储在 sys_business_unit_roles 表）
- **Claim_Mode**: 认领模式，任务分配给候选人列表，需要用户主动认领
- **Direct_Assignment**: 直接分配，任务直接分配给特定用户
- **Initiator**: 流程发起人，启动流程实例的用户
- **Current_User**: 当前人，在任务分配上下文中指流程发起人或上一节点处理人
- **Function_Manager**: 职能经理，用户的职能线上级
- **Entity_Manager**: 实体经理，用户的实体线上级
- **BPMN_Extension**: BPMN 扩展属性，用于在流程定义中存储分配配置

## Requirements

### Requirement 1: 分配类型枚举定义

**User Story:** As a 系统开发者, I want 定义9种标准的任务分配类型, so that 流程设计者可以灵活配置任务处理人。

#### Acceptance Criteria

1. THE Task_Assignment_System SHALL support exactly 9 assignee types
2. THE AssigneeType enum SHALL include: FUNCTION_MANAGER, ENTITY_MANAGER, INITIATOR, CURRENT_BU_ROLE, CURRENT_PARENT_BU_ROLE, INITIATOR_BU_ROLE, INITIATOR_PARENT_BU_ROLE, FIXED_BU_ROLE, BU_UNBOUNDED_ROLE
3. WHEN assigneeType is FUNCTION_MANAGER, THE Task_Assignment_System SHALL directly assign to the current user's function manager
4. WHEN assigneeType is ENTITY_MANAGER, THE Task_Assignment_System SHALL directly assign to the current user's entity manager
5. WHEN assigneeType is INITIATOR, THE Task_Assignment_System SHALL directly assign to the process initiator

### Requirement 2: 直接分配类型（不需要认领）

**User Story:** As a 流程设计者, I want 配置直接分配给特定人的任务, so that 任务可以自动分配给明确的处理人。

#### Acceptance Criteria

1. WHEN assigneeType is FUNCTION_MANAGER, THE Task_Assignment_System SHALL resolve the current user's function_manager_id and directly assign the task
2. WHEN assigneeType is ENTITY_MANAGER, THE Task_Assignment_System SHALL resolve the current user's entity_manager_id and directly assign the task
3. WHEN assigneeType is INITIATOR, THE Task_Assignment_System SHALL directly assign to the initiator variable stored in process context
4. IF the resolved assignee is null or empty, THEN THE Task_Assignment_System SHALL log an error and leave the task unassigned

### Requirement 3: 当前人业务单元角色分配（认领模式）

**User Story:** As a 流程设计者, I want 将任务分配给当前人所在业务单元的某个角色, so that 同业务单元的相关角色人员可以处理任务。

#### Acceptance Criteria

1. WHEN assigneeType is CURRENT_BU_ROLE, THE Task_Assignment_System SHALL require a roleId parameter
2. THE Task_Assignment_System SHALL find all users who have the specified BU_Bounded_Role in the current user's business unit
3. THE Task_Assignment_System SHALL set these users as candidate users (claim mode)
4. IF no users are found, THEN THE Task_Assignment_System SHALL log a warning and leave the task with empty candidates

### Requirement 4: 当前人上级业务单元角色分配（认领模式）

**User Story:** As a 流程设计者, I want 将任务分配给当前人上级业务单元的某个角色, so that 上级业务单元的相关角色人员可以审批任务。

#### Acceptance Criteria

1. WHEN assigneeType is CURRENT_PARENT_BU_ROLE, THE Task_Assignment_System SHALL require a roleId parameter
2. THE Task_Assignment_System SHALL find the current user's business unit's parent business unit
3. THE Task_Assignment_System SHALL find all users who have the specified BU_Bounded_Role in the parent business unit
4. THE Task_Assignment_System SHALL set these users as candidate users (claim mode)
5. IF the current user's business unit has no parent, THEN THE Task_Assignment_System SHALL log a warning

### Requirement 5: 流程发起人业务单元角色分配（认领模式）

**User Story:** As a 流程设计者, I want 将任务分配给流程发起人所在业务单元的某个角色, so that 发起人所在业务单元的相关角色人员可以处理任务。

#### Acceptance Criteria

1. WHEN assigneeType is INITIATOR_BU_ROLE, THE Task_Assignment_System SHALL require a roleId parameter
2. THE Task_Assignment_System SHALL find all users who have the specified BU_Bounded_Role in the initiator's business unit
3. THE Task_Assignment_System SHALL set these users as candidate users (claim mode)
4. IF no users are found, THEN THE Task_Assignment_System SHALL log a warning

### Requirement 6: 流程发起人上级业务单元角色分配（认领模式）

**User Story:** As a 流程设计者, I want 将任务分配给流程发起人上级业务单元的某个角色, so that 发起人上级业务单元的相关角色人员可以审批任务。

#### Acceptance Criteria

1. WHEN assigneeType is INITIATOR_PARENT_BU_ROLE, THE Task_Assignment_System SHALL require a roleId parameter
2. THE Task_Assignment_System SHALL find the initiator's business unit's parent business unit
3. THE Task_Assignment_System SHALL find all users who have the specified BU_Bounded_Role in the parent business unit
4. THE Task_Assignment_System SHALL set these users as candidate users (claim mode)

### Requirement 7: 指定业务单元角色分配（认领模式）

**User Story:** As a 流程设计者, I want 将任务分配给指定业务单元的指定角色, so that 特定业务单元的特定角色人员可以处理任务。

#### Acceptance Criteria

1. WHEN assigneeType is FIXED_BU_ROLE, THE Task_Assignment_System SHALL require both businessUnitId and roleId parameters
2. THE roleId SHALL be one of the eligible roles (准入角色) of the specified business unit
3. THE Task_Assignment_System SHALL find all users who have the specified BU_Bounded_Role in the specified business unit
4. THE Task_Assignment_System SHALL set these users as candidate users (claim mode)
5. IF the role is not an eligible role of the business unit, THEN THE Task_Assignment_System SHALL log an error

### Requirement 8: 业务单元无关型角色分配（认领模式）

**User Story:** As a 流程设计者, I want 将任务分配给某个业务单元无关型角色的所有成员, so that 拥有该角色的所有用户都可以处理任务。

#### Acceptance Criteria

1. WHEN assigneeType is BU_UNBOUNDED_ROLE, THE Task_Assignment_System SHALL require a roleId parameter
2. THE roleId SHALL reference a role with type='BU_UNBOUNDED'
3. THE Task_Assignment_System SHALL find all users who have this BU_Unbounded_Role (through virtual group membership)
4. THE Task_Assignment_System SHALL set these users as candidate users (claim mode)

### Requirement 9: 前端流程设计器配置

**User Story:** As a 流程设计者, I want 在流程设计器中配置任务分配方式, so that 我可以可视化地设置每个任务节点的处理人。

#### Acceptance Criteria

1. THE UserTaskProperties component SHALL display a dropdown with all 9 assignee types
2. WHEN assigneeType requires a role selection, THE component SHALL display a role dropdown filtered by role type
3. WHEN assigneeType is FIXED_BU_ROLE, THE component SHALL display both business unit tree selector and role dropdown
4. THE role dropdown for BU_Bounded types SHALL only show roles with type='BU_BOUNDED'
5. THE role dropdown for BU_UNBOUNDED_ROLE SHALL only show roles with type='BU_UNBOUNDED'
6. WHEN assigneeType is FIXED_BU_ROLE, THE role dropdown SHALL only show eligible roles of the selected business unit

### Requirement 10: BPMN 扩展属性存储

**User Story:** As a 系统开发者, I want 在 BPMN XML 中存储分配配置, so that 流程定义可以持久化分配设置。

#### Acceptance Criteria

1. THE BPMN extension SHALL store assigneeType as a custom property
2. THE BPMN extension SHALL store roleId when required by the assignee type
3. THE BPMN extension SHALL store businessUnitId when assigneeType is FIXED_BU_ROLE
4. THE BPMN extension SHALL store assigneeLabel for display purposes
5. WHEN loading a process definition, THE system SHALL correctly parse all assignment properties

### Requirement 11: 删除废弃的分配类型

**User Story:** As a 系统开发者, I want 删除不再使用的分配类型代码, so that 代码库保持整洁。

#### Acceptance Criteria

1. THE Task_Assignment_System SHALL remove DEPT_OTHERS assignee type
2. THE Task_Assignment_System SHALL remove PARENT_DEPT assignee type
3. THE Task_Assignment_System SHALL remove FIXED_DEPT assignee type
4. THE Task_Assignment_System SHALL remove VIRTUAL_GROUP assignee type
5. THE steering files SHALL be updated to reflect the new 9 assignee types

### Requirement 12: 角色查询 API

**User Story:** As a 前端开发者, I want 调用 API 获取角色列表, so that 我可以在流程设计器中显示角色选项。

#### Acceptance Criteria

1. THE admin-center API SHALL provide an endpoint to list all BU_BOUNDED roles
2. THE admin-center API SHALL provide an endpoint to list all BU_UNBOUNDED roles
3. THE admin-center API SHALL provide an endpoint to list eligible roles for a specific business unit
4. WHEN querying eligible roles, THE API SHALL return roles from sys_business_unit_roles table
