# Requirements Document

## Introduction

This feature enables the user-portal to resolve function unit information from Flowable process definition keys. When a user views a task detail page, the system needs to find the corresponding function unit to load forms, actions, and other configurations. Since Flowable stores process definition IDs in the format `{processKey}:{version}:{uuid}`, the system must extract the process key and map it to the correct function unit.

## Glossary

- **Process_Definition_Key**: The unique identifier for a process definition in Flowable, extracted from the full process definition ID (e.g., `Process_PurchaseRequest` from `Process_PurchaseRequest:2:abc123`)
- **Function_Unit**: A business process template designed in the developer workstation, containing forms, actions, and BPMN process definitions
- **BPMN_Process_ID**: The process ID defined in the BPMN XML, stored in `dw_process_definitions.bpmn_xml`
- **Admin_Center**: The backend service that manages function units and provides lookup APIs
- **User_Portal**: The frontend application where users view and process tasks
- **FunctionUnitAccessComponent**: The component in user-portal that retrieves function unit information

## Requirements

### Requirement 1: Extract Process Definition Key from Flowable ID

**User Story:** As a system component, I want to extract the process definition key from Flowable's full process definition ID, so that I can use it to look up the corresponding function unit.

#### Acceptance Criteria

1. WHEN a process definition ID in format `{processKey}:{version}:{uuid}` is received, THE System SHALL extract the `{processKey}` portion before the first colon
2. WHEN a process definition ID contains no colons, THE System SHALL use the entire string as the process key
3. WHEN a process definition ID is null or empty, THE System SHALL return null or throw an appropriate exception
4. THE System SHALL handle process keys with special characters (underscores, hyphens) correctly

### Requirement 2: Function Unit Lookup by Process Key API

**User Story:** As a user-portal developer, I want an API endpoint to find a function unit by its BPMN process key, so that I can load the correct forms and actions for a task.

#### Acceptance Criteria

1. THE Admin_Center SHALL provide a REST endpoint `GET /api/v1/admin/function-units/by-process-key/{processKey}`
2. WHEN a valid process key is provided, THE Admin_Center SHALL search for function units whose BPMN XML contains a matching process ID
3. WHEN a matching function unit is found, THE Admin_Center SHALL return the function unit ID and basic information
4. WHEN no matching function unit is found, THE Admin_Center SHALL return a 404 Not Found response
5. THE Admin_Center SHALL decode Base64-encoded BPMN XML before searching for the process ID
6. THE Admin_Center SHALL support process IDs defined in the BPMN `<bpmn:process id="...">` element

### Requirement 3: FunctionUnitAccessComponent Enhancement

**User Story:** As a user-portal component, I want to resolve function unit information using either function unit ID or process definition key, so that I can support both direct access and task-based access patterns.

#### Acceptance Criteria

1. THE FunctionUnitAccessComponent SHALL support resolving function unit by process definition key
2. WHEN `resolveFunctionUnitId(processDefinitionKey)` is called, THE Component SHALL call the Admin_Center API to find the function unit
3. WHEN the Admin_Center returns a function unit ID, THE Component SHALL cache the mapping for subsequent requests
4. WHEN the Admin_Center returns 404, THE Component SHALL throw a `PortalException` with an appropriate error message
5. THE Component SHALL extract the process key from full Flowable process definition IDs automatically

### Requirement 4: Task Detail Page Integration

**User Story:** As a user, I want to view task details with the correct form and actions loaded, so that I can process the task properly.

#### Acceptance Criteria

1. WHEN a user navigates to a task detail page, THE User_Portal SHALL extract the process definition key from the task's process definition ID
2. THE User_Portal SHALL use the process definition key to resolve the function unit ID
3. WHEN the function unit is resolved, THE User_Portal SHALL load the form configuration bound to the current task node
4. WHEN the function unit is resolved, THE User_Portal SHALL load the actions available for the current task node
5. IF the function unit cannot be resolved, THE User_Portal SHALL display an error message indicating the process configuration is missing

### Requirement 5: BPMN Process ID Consistency

**User Story:** As a developer workstation user, I want the BPMN process ID to be consistent with the function unit code, so that the mapping between Flowable processes and function units is predictable.

#### Acceptance Criteria

1. WHEN a process definition is deployed to Flowable, THE System SHALL use the BPMN process ID from the XML
2. THE BPMN process ID SHOULD follow a consistent naming convention (e.g., `Process_{FunctionUnitCode}`)
3. THE System SHALL support multiple function units with different process IDs
4. THE System SHALL handle cases where the BPMN process ID differs from the function unit code

