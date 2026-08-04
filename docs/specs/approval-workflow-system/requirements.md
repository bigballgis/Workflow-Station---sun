# 需求文档：审批流程系统

## 简介

审批流程系统是一个基于 Spring Boot 微服务架构的通用审批流程功能单元，集成在 Developer Workstation 服务中。该系统使用 BPMN 2.0 标准定义流程，提供后端 API 支持申请提交、主管审批、状态流转和审批历史记录等核心功能。用户交互界面（包括申请发起、数据查看、审批操作）在 User Portal 中实现，通过调用审批流程系统的 REST API 完成业务操作。

## 术语表

- **Approval_System**: 审批流程系统，负责管理审批流程的后端服务系统
- **User_Portal**: 用户门户，提供用户交互界面的前端应用
- **Function_Unit**: 功能单元，包含 process、table、form 和 action 的完整业务功能模块
- **Process_Engine**: 流程引擎，执行 BPMN 流程定义的运行时组件
- **Request**: 申请单，用户提交的待审批事项
- **Initiator**: 申请人，提交申请的用户
- **Manager**: 直属主管，负责审批申请的用户
- **Approval_Record**: 审批记录，记录审批操作的历史数据
- **Request_Status**: 申请单状态，包括 DRAFT（草稿）、PENDING（待审批）、APPROVED（已批准）、REJECTED（已拒绝）
- **BPMN_Definition**: BPMN 流程定义，使用 BPMN 2.0 XML 格式描述的流程
- **Form_Definition**: 表单定义，描述表单字段和验证规则的元数据
- **Table_Definition**: 数据表定义，描述数据库表结构的元数据
- **Action_Definition**: 操作定义，描述用户可执行操作的元数据

## 需求

### 需求 1：流程定义管理

**用户故事：** 作为系统管理员，我希望能够定义和管理审批流程，以便支持标准化的审批业务流程。

#### 验收标准

1. THE Approval_System SHALL store BPMN_Definition in XML format conforming to BPMN 2.0 specification
2. WHEN a BPMN_Definition is created, THE Approval_System SHALL validate it contains at least one start event, one end event, and one user task
3. THE BPMN_Definition SHALL include process variables for request_id, initiator_id, manager_id, and approval_status
4. THE Approval_System SHALL support exclusive gateways with conditional expressions based on approval_status
5. WHEN a Process_Engine loads a BPMN_Definition, THE Approval_System SHALL parse and validate the XML structure before execution

### 需求 2：申请单数据管理

**用户故事：** 作为申请人，我希望系统能够存储我的申请信息，以便追踪申请状态和历史记录。

#### 验收标准

1. THE Approval_System SHALL create a Table_Definition for Request with fields: id, initiator_id, title, content, status, created_at, updated_at
2. WHEN a Request is created, THE Approval_System SHALL set status to DRAFT and record created_at timestamp
3. THE Approval_System SHALL enforce that initiator_id references a valid user in the system
4. WHEN Request status changes, THE Approval_System SHALL update the updated_at timestamp
5. THE Approval_System SHALL persist Request data to PostgreSQL database using JPA

### 需求 3：审批记录管理

**用户故事：** 作为系统用户，我希望查看完整的审批历史，以便了解申请的处理过程。

#### 验收标准

1. THE Approval_System SHALL create a Table_Definition for Approval_Record with fields: id, request_id, approver_id, action, comment, created_at
2. WHEN an approval action occurs, THE Approval_System SHALL create an Approval_Record with the approver_id, action type, and timestamp
3. THE Approval_System SHALL support action types: SUBMIT, APPROVE, REJECT
4. THE Approval_System SHALL allow approvers to provide optional comment text up to 1000 characters
5. THE Approval_System SHALL maintain referential integrity between Approval_Record and Request via request_id foreign key

### 需求 4：申请表单定义

**用户故事：** 作为申请人，我希望通过 User Portal 的表单提交申请，以便提供结构化的申请信息。

#### 验收标准

1. THE Approval_System SHALL create a Form_Definition for request submission with fields: title, content
2. THE Form_Definition SHALL mark title field as required with maximum length of 200 characters
3. THE Form_Definition SHALL mark content field as required with maximum length of 2000 characters
4. WHEN User_Portal submits form data via API with invalid data, THE Approval_System SHALL return validation errors indicating which fields failed validation
5. THE Form_Definition SHALL be serializable to JSON format for User_Portal consumption

### 需求 5：审批表单定义

**用户故事：** 作为主管，我希望通过 User Portal 的审批表单处理申请，以便做出批准或拒绝的决定。

#### 验收标准

1. THE Approval_System SHALL create a Form_Definition for approval with fields: decision, comment
2. THE Form_Definition SHALL restrict decision field to values: APPROVE, REJECT
3. THE Form_Definition SHALL mark decision field as required
4. THE Form_Definition SHALL mark comment field as optional with maximum length of 1000 characters
5. WHEN User_Portal submits approval form data via API, THE Approval_System SHALL validate the decision value is one of the allowed values

### 需求 6：申请提交操作

**用户故事：** 作为申请人，我希望在 User Portal 上提交申请，以便启动审批流程。

#### 验收标准

1. THE Approval_System SHALL create an Action_Definition for submit operation that transitions Request status from DRAFT to PENDING
2. WHEN User_Portal calls submit API for a Request, THE Approval_System SHALL create an Approval_Record with action type SUBMIT
3. WHEN a Request is submitted, THE Process_Engine SHALL start a new process instance with the Request data
4. THE Approval_System SHALL assign the user task to the Manager specified in the Request
5. WHEN a Request with status other than DRAFT is submitted, THE Approval_System SHALL reject the operation and return an error

### 需求 7：审批操作

**用户故事：** 作为主管，我希望在 User Portal 上批准或拒绝申请，以便完成审批流程。

#### 验收标准

1. THE Approval_System SHALL create Action_Definition for approve operation that transitions Request status to APPROVED
2. THE Approval_System SHALL create Action_Definition for reject operation that transitions Request status to REJECTED
3. WHEN User_Portal calls approve API for a Request, THE Approval_System SHALL create an Approval_Record with action type APPROVE
4. WHEN User_Portal calls reject API for a Request, THE Approval_System SHALL create an Approval_Record with action type REJECT
5. WHEN an approval action completes, THE Process_Engine SHALL complete the user task and continue process execution
6. THE Approval_System SHALL enforce that only the assigned Manager can perform approval actions on a Request

### 需求 8：查看操作

**用户故事：** 作为系统用户，我希望在 User Portal 上查看申请详情和审批历史，以便了解申请的完整信息。

#### 验收标准

1. THE Approval_System SHALL create an Action_Definition for view operation that retrieves Request details
2. WHEN User_Portal calls view API, THE Approval_System SHALL return Request data including id, title, content, status, initiator_id, created_at, updated_at
3. WHEN User_Portal calls view API, THE Approval_System SHALL return all associated Approval_Record entries ordered by created_at descending
4. THE Approval_System SHALL allow Initiator to view their own Request
5. THE Approval_System SHALL allow Manager to view Request assigned to them

### 需求 9：权限控制

**用户故事：** 作为系统管理员，我希望系统强制执行权限控制，以便确保用户只能执行授权的操作。

#### 验收标准

1. WHEN an Initiator attempts to perform an approval action, THE Approval_System SHALL reject the operation and return an authorization error
2. WHEN a Manager attempts to submit a Request they did not create, THE Approval_System SHALL reject the operation and return an authorization error
3. WHEN a user attempts to view a Request they are not authorized to access, THE Approval_System SHALL reject the operation and return an authorization error
4. THE Approval_System SHALL verify user identity before executing any Action_Definition
5. THE Approval_System SHALL log all authorization failures with user_id, action, and timestamp

### 需求 10：状态流转管理

**用户故事：** 作为系统，我需要管理申请单的状态流转，以便确保业务流程的正确性。

#### 验收标准

1. THE Approval_System SHALL enforce state transitions: DRAFT → PENDING → APPROVED
2. THE Approval_System SHALL enforce state transitions: DRAFT → PENDING → REJECTED
3. WHEN a Request is in APPROVED status, THE Approval_System SHALL prevent any further status changes
4. WHEN a Request is in REJECTED status, THE Approval_System SHALL prevent any further status changes
5. WHEN an invalid state transition is attempted, THE Approval_System SHALL reject the operation and return a state transition error

### 需求 11：功能单元集成

**用户故事：** 作为开发者，我希望审批流程作为功能单元集成到 Developer Workstation 服务中，以便复用和扩展。

#### 验收标准

1. THE Approval_System SHALL create a Function_Unit entity containing references to Process_Definition, Table_Definition, Form_Definition, and Action_Definition
2. THE Function_Unit SHALL have a unique identifier and name "approval-workflow"
3. WHEN the Function_Unit is deployed, THE Approval_System SHALL register all definitions with the Developer Workstation service
4. THE Approval_System SHALL expose REST API endpoints for all Action_Definition operations
5. THE Approval_System SHALL integrate with the existing authentication and authorization mechanisms of Developer Workstation service

### 需求 16：User Portal 集成

**用户故事：** 作为用户，我希望在 User Portal 上完成所有审批相关的交互操作，以便获得统一的用户体验。

#### 验收标准

1. THE User_Portal SHALL provide user interface for Initiator to create and submit Request
2. THE User_Portal SHALL provide user interface for Manager to view pending Request and perform approval actions
3. THE User_Portal SHALL provide user interface for users to view Request details and Approval_Record history
4. THE User_Portal SHALL call Approval_System REST API endpoints to perform all backend operations
5. THE User_Portal SHALL display validation errors and authorization errors returned by Approval_System to users
6. THE User_Portal SHALL handle HTTP response codes from Approval_System and provide appropriate user feedback

### 需求 12：流程实例管理

**用户故事：** 作为系统，我需要管理流程实例的生命周期，以便跟踪每个申请的流程执行状态。

#### 验收标准

1. WHEN a Request is submitted, THE Process_Engine SHALL create a process instance with a unique process_instance_id
2. THE Approval_System SHALL store the association between Request and process_instance_id
3. WHEN a process instance completes, THE Process_Engine SHALL update the Request status to the final state
4. THE Approval_System SHALL support querying active process instances by Request id
5. WHEN a process instance encounters an error, THE Process_Engine SHALL log the error and maintain the Request in its current state

### 需求 13：数据验证

**用户故事：** 作为系统，我需要验证所有输入数据，以便确保数据完整性和安全性。

#### 验收标准

1. WHEN Request data is received, THE Approval_System SHALL validate all required fields are present and non-empty
2. WHEN string fields exceed maximum length, THE Approval_System SHALL reject the data and return a validation error
3. WHEN foreign key references are invalid, THE Approval_System SHALL reject the data and return a referential integrity error
4. THE Approval_System SHALL sanitize all text input to prevent SQL injection and XSS attacks
5. WHEN validation fails, THE Approval_System SHALL return error messages indicating which fields failed and why

### 需求 14：API 接口

**用户故事：** 作为 User Portal 开发者，我希望通过 REST API 与审批系统交互，以便在用户界面上实现申请发起、查看和审批功能。

#### 验收标准

1. THE Approval_System SHALL expose POST /api/requests endpoint for User_Portal to create new Request
2. THE Approval_System SHALL expose POST /api/requests/{id}/submit endpoint for User_Portal to submit Request
3. THE Approval_System SHALL expose POST /api/requests/{id}/approve endpoint for User_Portal to approve Request
4. THE Approval_System SHALL expose POST /api/requests/{id}/reject endpoint for User_Portal to reject Request
5. THE Approval_System SHALL expose GET /api/requests/{id} endpoint for User_Portal to retrieve Request details
6. THE Approval_System SHALL return HTTP 200 for successful operations with response body in JSON format
7. THE Approval_System SHALL return HTTP 400 for validation errors with error details in JSON format
8. THE Approval_System SHALL return HTTP 403 for authorization errors with error message in JSON format
9. THE Approval_System SHALL return HTTP 404 when Request is not found

### 需求 15：事务管理

**用户故事：** 作为系统，我需要确保数据操作的原子性，以便维护数据一致性。

#### 验收标准

1. WHEN a Request is submitted, THE Approval_System SHALL execute Request update, Approval_Record creation, and process instance creation within a single database transaction
2. WHEN an approval action is performed, THE Approval_System SHALL execute Request status update, Approval_Record creation, and process task completion within a single database transaction
3. IF any operation within a transaction fails, THE Approval_System SHALL rollback all changes
4. THE Approval_System SHALL use Spring @Transactional annotation for transaction management
5. THE Approval_System SHALL configure transaction isolation level to READ_COMMITTED
