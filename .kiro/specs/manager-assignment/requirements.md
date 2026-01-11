# Requirements Document

## Introduction

本功能为用户添加两种新的管理者类型：实体管理者（Entity Manager）和职能管理者（Function Manager），同时扩展部门支持双经理（主经理和副经理），以支持更灵活的工作流任务分配。用户可以在工作流节点中选择将任务分配给实体管理者、职能管理者、部门主经理或部门副经理。

## Glossary

- **User**: 系统用户实体，存储在 admin_users 表中
- **Department**: 部门实体，存储在 admin_departments 表中
- **Entity_Manager**: 实体管理者，负责用户所在实体/业务单元的管理者，存储在 User.entityManagerId
- **Function_Manager**: 职能管理者，负责用户所属职能线/专业领域的管理者，存储在 User.functionManagerId
- **Primary_Manager**: 部门主经理，部门的主要负责人，存储在 Department.managerId
- **Secondary_Manager**: 部门副经理，部门的副负责人，存储在 Department.secondaryManagerId
- **BPMN_Designer**: 流程设计器，用于设计工作流程
- **Process_Engine**: 流程引擎，负责解析和执行工作流
- **Admin_Center**: 管理员中心，负责用户和组织管理
- **Developer_Workstation**: 开发者工作站，负责流程设计
- **User_Portal**: 用户门户，负责流程发起和任务处理

## Requirements

### Requirement 1: 用户管理者字段扩展

**User Story:** As an administrator, I want to assign entity manager and function manager to users, so that workflow tasks can be routed to the appropriate managers.

#### Acceptance Criteria

1. THE User entity SHALL include an entityManagerId field to store the entity manager reference
2. THE User entity SHALL include a functionManagerId field to store the function manager reference
3. WHEN a user is created or updated, THE Admin_Center SHALL allow setting entityManagerId and functionManagerId
4. WHEN entityManagerId or functionManagerId is set, THE Admin_Center SHALL validate that the referenced user exists
5. THE Admin_Center SHALL allow entityManagerId and functionManagerId to be null (optional fields)

### Requirement 2: 部门双经理支持

**User Story:** As an administrator, I want to assign both a primary manager and a secondary manager to a department, so that departments can have two managers for better management coverage.

#### Acceptance Criteria

1. THE Department entity SHALL include a secondaryManagerId field to store the secondary manager reference
2. WHEN a department is created or updated, THE Admin_Center SHALL allow setting both managerId (primary) and secondaryManagerId
3. WHEN secondaryManagerId is set, THE Admin_Center SHALL validate that the referenced user exists
4. THE Admin_Center SHALL allow secondaryManagerId to be null (optional field)
5. THE Admin_Center_UI SHALL display both primary manager and secondary manager in department details

### Requirement 3: 用户编辑界面更新

**User Story:** As an administrator, I want to select entity manager and function manager from a user list when editing a user, so that I can easily configure the management hierarchy.

#### Acceptance Criteria

1. WHEN editing a user, THE Admin_Center_UI SHALL display entity manager selection field
2. WHEN editing a user, THE Admin_Center_UI SHALL display function manager selection field
3. THE Admin_Center_UI SHALL provide a searchable user selector for entity manager and function manager fields
4. WHEN a manager is selected, THE Admin_Center_UI SHALL display the selected manager's name
5. THE Admin_Center_UI SHALL allow clearing the entity manager and function manager selections

### Requirement 4: 部门编辑界面更新

**User Story:** As an administrator, I want to select both primary and secondary managers when editing a department, so that I can configure dual management for departments.

#### Acceptance Criteria

1. WHEN editing a department, THE Admin_Center_UI SHALL display primary manager selection field (existing)
2. WHEN editing a department, THE Admin_Center_UI SHALL display secondary manager selection field (new)
3. THE Admin_Center_UI SHALL provide a searchable user selector for both manager fields
4. WHEN a manager is selected, THE Admin_Center_UI SHALL display the selected manager's name
5. THE Admin_Center_UI SHALL allow clearing the secondary manager selection

### Requirement 5: 流程设计器任务分配选项

**User Story:** As a process designer, I want to assign user tasks to entity manager, function manager, primary manager, secondary manager, or both entity and function managers, so that I can create flexible approval workflows.

#### Acceptance Criteria

1. WHEN configuring a user task, THE BPMN_Designer SHALL provide manager type selection with options: Entity Manager, Function Manager, Both Managers (Entity + Function), Department Primary Manager, Department Secondary Manager
2. WHEN "Entity Manager" is selected, THE BPMN_Designer SHALL set assignee expression to ${entityManager}
3. WHEN "Function Manager" is selected, THE BPMN_Designer SHALL set assignee expression to ${functionManager}
4. WHEN "Both Managers" is selected, THE BPMN_Designer SHALL set candidateUsers expression to ${entityManager},${functionManager}
5. WHEN "Department Primary Manager" is selected, THE BPMN_Designer SHALL set assignee expression to ${departmentManager}
6. WHEN "Department Secondary Manager" is selected, THE BPMN_Designer SHALL set assignee expression to ${departmentSecondaryManager}
7. THE BPMN_Designer SHALL display a descriptive label for the selected manager type

### Requirement 6: 流程引擎变量解析

**User Story:** As a process engine, I want to resolve manager variables at runtime, so that tasks are assigned to the correct managers.

#### Acceptance Criteria

1. WHEN a process starts with ${entityManager} assignee, THE Process_Engine SHALL resolve it to the initiator's entityManagerId
2. WHEN a process starts with ${functionManager} assignee, THE Process_Engine SHALL resolve it to the initiator's functionManagerId
3. WHEN a process starts with ${departmentManager} assignee, THE Process_Engine SHALL resolve it to the initiator's department primary managerId
4. WHEN a process starts with ${departmentSecondaryManager} assignee, THE Process_Engine SHALL resolve it to the initiator's department secondaryManagerId
5. WHEN a task has candidateUsers with ${entityManager},${functionManager}, THE Process_Engine SHALL resolve both variables and set them as candidate users
6. IF the resolved manager is null, THEN THE Process_Engine SHALL exclude that manager from candidate users or leave the task unassigned
7. THE Process_Engine SHALL log a warning when a manager variable cannot be resolved

### Requirement 7: 用户详情显示管理者信息

**User Story:** As an administrator, I want to see the entity manager and function manager names in user details, so that I can verify the management hierarchy.

#### Acceptance Criteria

1. WHEN displaying user details, THE Admin_Center_UI SHALL show entity manager name if set
2. WHEN displaying user details, THE Admin_Center_UI SHALL show function manager name if set
3. IF entity manager or function manager is not set, THEN THE Admin_Center_UI SHALL display "未设置"

### Requirement 8: 部门详情显示双经理信息

**User Story:** As an administrator, I want to see both primary and secondary manager names in department details, so that I can verify the department management structure.

#### Acceptance Criteria

1. WHEN displaying department details, THE Admin_Center_UI SHALL show primary manager name if set
2. WHEN displaying department details, THE Admin_Center_UI SHALL show secondary manager name if set
3. IF primary manager or secondary manager is not set, THEN THE Admin_Center_UI SHALL display "未设置"

### Requirement 9: 数据库迁移

**User Story:** As a system administrator, I want the database schema to be updated automatically, so that the new manager fields are available.

#### Acceptance Criteria

1. THE Database_Migration SHALL add entity_manager_id column to admin_users table
2. THE Database_Migration SHALL add function_manager_id column to admin_users table
3. THE Database_Migration SHALL add secondary_manager_id column to admin_departments table
4. THE Database_Migration SHALL set default value to NULL for all new columns
5. THE Database_Migration SHALL be backward compatible with existing data
