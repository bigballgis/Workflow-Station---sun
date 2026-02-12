# Simple Approval Workflow - Export Summary

## Export Details

**Date**: 2026-02-12  
**Source**: workflow_platform_dev database  
**Function Unit**: SIMPLE_APPROVAL  
**Version**: 1.0.5  
**Status**: PUBLISHED

## Exported Data

### Function Unit
```
ID: 2
Code: SIMPLE_APPROVAL
Name: Simple Approval Workflow
Description: Simple approval workflow with manager approval
Status: PUBLISHED
Current Version: 1.0.5
Version: 1.0.0
Is Active: true
Enabled: true
```

### Forms

#### 1. Request Form (ID: 6)
- **Type**: MAIN
- **Description**: Request submission form
- **Fields**:
  - id (inputNumber) - Primary key
  - request_number (input, 50 chars) - Request number (unique)
  - request_date (datePicker, datetime) - Request date
  - title (input, 200 chars) - Request title
  - description (textarea) - Request description
  - status (input, 30 chars) - Request status

#### 2. Approval Form (ID: 7)
- **Type**: MAIN
- **Description**: Manager approval form
- **Fields**:
  - approval_comments (textarea) - Approval comments
  - status (input, 30 chars) - Request status

### Actions

#### 1. Submit Request (ID: 16)
- **Type**: PROCESS_SUBMIT
- **Icon**: Upload
- **Button Color**: primary
- **Config**:
  - confirmMessage: "Confirm submitting this request?"
  - requireComment: false
  - successMessage: "Request submitted successfully"

#### 2. Approve (ID: 17)
- **Type**: APPROVE
- **Icon**: Check
- **Button Color**: success
- **Config**:
  - targetStatus: "APPROVED"
  - confirmMessage: "Confirm approving this request?"
  - requireComment: true
  - successMessage: "Request approved"

#### 3. Reject (ID: 18)
- **Type**: REJECT
- **Icon**: Close
- **Button Color**: danger
- **Config**:
  - targetStatus: "REJECTED"
  - requireReason: true
  - confirmMessage: "Confirm rejecting this request?"
  - requireComment: true
  - successMessage: "Request rejected"

### BPMN Process Definition

**Process ID**: SimpleApprovalProcess  
**Process Name**: Simple Approval Process  
**Executable**: true

**Nodes**:
1. StartEvent_1 (Start Event) - "Start"
2. Task_SubmitRequest (User Task) - "Submit Request"
   - Form: Request Form (ID: 6)
   - Actions: [Submit Request (ID: 16)]
3. Task_ManagerApproval (User Task) - "Manager Approval"
   - Form: Request Form (ID: 6, read-only)
   - Actions: [Approve (ID: 17), Reject (ID: 18)]
4. Gateway_ManagerDecision (Exclusive Gateway) - "Approved?"
5. EndEvent_Approved (End Event) - "Approved"
6. EndEvent_Rejected (End Event) - "Rejected"

**Flows**:
- Flow_1: Start → Submit Request
- Flow_2: Submit Request → Manager Approval
- Flow_3: Manager Approval → Approved?
- Flow_Approved: Approved? → Approved (condition: `${decision == 'yes'}`)
- Flow_Rejected: Approved? → Rejected (condition: `${decision == 'no'}`)

## Key Features

1. **Dynamic ID Replacement**: The SQL scripts use placeholders (e.g., `{{REQUEST_FORM_ID}}`) that are replaced with actual database IDs at runtime.

2. **Idempotent Scripts**: All scripts use `ON CONFLICT` clauses to allow safe re-execution without errors.

3. **Read-Only Approval**: The Manager Approval task displays the Request Form in read-only mode, allowing reviewers to see the original request data without modification.

4. **Decision Variable**: The workflow uses a `decision` variable to control the flow:
   - Approve action sets `decision = 'yes'`
   - Reject action sets `decision = 'no'`

5. **Custom Extension Elements**: The BPMN uses custom extension elements (`custom_1:properties`) to bind forms and actions to tasks.

## Export Method

The data was exported using the following commands:

```bash
# Function Unit
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  -c "SELECT * FROM dw_function_units WHERE code = 'SIMPLE_APPROVAL';"

# Forms
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  -c "SELECT * FROM dw_form_definitions WHERE function_unit_id = 2 ORDER BY id;"

# Actions
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  -c "SELECT * FROM dw_action_definitions WHERE function_unit_id = 2 ORDER BY id;"

# BPMN Process (Base64 encoded)
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  -t -c "SELECT bpmn_xml FROM dw_process_definitions WHERE function_unit_id = 2;"
```

## Usage

To import this function unit into a new database:

```bash
# Run in order
psql -h localhost -p 5432 -U platform_dev -d workflow_platform_dev \
  -f deploy/init-scripts/10-simple-approval/00-create-simple-approval.sql

psql -h localhost -p 5432 -U platform_dev -d workflow_platform_dev \
  -f deploy/init-scripts/10-simple-approval/01-insert-bpmn-process.sql
```

Or using Docker:

```bash
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  < deploy/init-scripts/10-simple-approval/00-create-simple-approval.sql

docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  < deploy/init-scripts/10-simple-approval/01-insert-bpmn-process.sql
```

## Verification

After import, verify the data:

```sql
-- Check function unit
SELECT id, code, name, status, current_version 
FROM dw_function_units 
WHERE code = 'SIMPLE_APPROVAL';

-- Check forms
SELECT id, form_name, form_type 
FROM dw_form_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'SIMPLE_APPROVAL');

-- Check actions
SELECT id, action_name, action_type, button_color 
FROM dw_action_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'SIMPLE_APPROVAL');

-- Check process definition
SELECT id, function_unit_id, LEFT(bpmn_xml, 100) as bpmn_preview 
FROM dw_process_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'SIMPLE_APPROVAL');
```

## Notes

- The exported BPMN XML was stored as Base64 in the database but has been decoded for the SQL scripts
- Form configurations are stored as JSON and contain complete form-create configuration
- Action configurations are also JSON and include validation rules and UI settings
- The scripts preserve all metadata including timestamps and version information
