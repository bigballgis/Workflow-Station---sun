# Demo Function Unit - Actions Display Fix

## Issue
Actions were not displaying in the Developer Workstation UI for the "Employee Leave Management" demo function unit (ID: 3).

## Root Cause
The SQL script `deploy/init-scripts/05-demo-leave-management/03-create-complete-demo.sql` was inserting actions with invalid `action_type` values that don't exist in the `ActionType` enum:

**Invalid Types Used:**
- `SUBMIT` (should be `PROCESS_SUBMIT`)
- `CANCEL` (should be `WITHDRAW`)
- `QUERY` (should be `API_CALL`)

**Valid ActionType Enum Values:**
- Approval actions: `APPROVE`, `REJECT`, `TRANSFER`, `DELEGATE`, `ROLLBACK`, `WITHDRAW`
- Custom actions: `API_CALL`, `FORM_POPUP`, `SCRIPT`, `CUSTOM_SCRIPT`
- Process actions: `PROCESS_SUBMIT`, `PROCESS_REJECT`
- Composite: `COMPOSITE`

## Error
The backend was crashing with:
```
java.lang.IllegalArgumentException: No enum constant com.developer.enums.ActionType.SUBMIT
```

This prevented the backend from loading any actions for the function unit, causing the UI to show an empty action list.

## Solution

### 1. Fixed SQL Script
Updated `deploy/init-scripts/05-demo-leave-management/03-create-complete-demo.sql` to use correct action types:

```sql
INSERT INTO public.dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color
) VALUES
(v_function_unit_id, 'Submit Leave Request', 'PROCESS_SUBMIT', ...),
(v_function_unit_id, 'Approve Leave Request', 'APPROVE', ...),
(v_function_unit_id, 'Reject Leave Request', 'REJECT', ...),
(v_function_unit_id, 'Withdraw Leave Request', 'WITHDRAW', ...),
(v_function_unit_id, 'Query Leave Requests', 'API_CALL', ...);
```

### 2. Updated config_json Format
Changed the config_json structure to match the expected format for each action type:

**PROCESS_SUBMIT:**
```json
{
  "requireComment": false,
  "confirmMessage": "Submit this leave request?"
}
```

**APPROVE/REJECT:**
```json
{
  "targetStatus": "APPROVED",
  "requireComment": true,
  "confirmMessage": "Approve this leave request?"
}
```

**WITHDRAW:**
```json
{
  "targetStatus": "WITHDRAWN",
  "allowedFromStatus": ["PENDING", "IN_PROGRESS"]
}
```

**API_CALL:**
```json
{
  "url": "/api/leave-management/requests",
  "method": "GET"
}
```

### 3. Database Cleanup
Deleted the invalid actions and re-inserted with correct types:

```sql
DELETE FROM public.dw_action_definitions WHERE function_unit_id = 3;
-- Then re-inserted with correct types
```

### 4. Backend Restart
Restarted the developer-workstation backend to clear the error state:

```bash
docker restart platform-developer-workstation-dev
```

## Verification

### Database Check
```sql
SELECT id, action_name, action_type 
FROM public.dw_action_definitions 
WHERE function_unit_id = 3;
```

Result:
```
 id |      action_name       |  action_type   
----+------------------------+----------------
  7 | Submit Leave Request   | PROCESS_SUBMIT
  8 | Approve Leave Request  | APPROVE
  9 | Reject Leave Request   | REJECT
 10 | Withdraw Leave Request | WITHDRAW
 11 | Query Leave Requests   | API_CALL
```

### Backend Status
- Service started successfully
- No more enum constant errors
- API endpoint `/api/v1/function-units/3/actions` is accessible (requires authentication)

### Frontend Access
- Developer Workstation: http://localhost:3002
- Navigate to Function Unit ID 3 (Employee Leave Management)
- Actions tab should now display all 5 actions

## Files Modified
- `deploy/init-scripts/05-demo-leave-management/03-create-complete-demo.sql` - Fixed action types and config_json format

## Next Steps
1. Open Developer Workstation at http://localhost:3002
2. Login with developer credentials
3. Navigate to the "Employee Leave Management" function unit
4. Click on the "Actions" tab
5. Verify all 5 actions are displayed correctly

## Notes
- The backend requires authentication to access the actions API
- The frontend automatically includes the authentication token in requests
- Future SQL scripts should reference the ActionType enum to ensure valid values are used
- Consider adding database constraints or validation to prevent invalid action types from being inserted

## Date
2026-02-05

## Update: Action Node Bindings Added

After fixing the action types, the actions were displaying but not bound to any process nodes. Added action bindings to the BPMN XML:

### Action Bindings
- **Task_Submit** (Submit Leave Application): 
  - Submit Leave Request (ID: 7)
- **Task_ManagerApproval** (Manager Approval):
  - Approve Leave Request (ID: 8)
  - Reject Leave Request (ID: 9)
- **Task_HRApproval** (HR Approval):
  - Approve Leave Request (ID: 8)
  - Reject Leave Request (ID: 9)
  - Withdraw Leave Request (ID: 10)

### BPMN XML Format
Actions are bound to nodes using custom properties in the extensionElements:
```xml
<bpmn:userTask id="Task_Submit" name="Submit Leave Application">
  <bpmn:extensionElements>
    <custom:properties>
      <custom:property name="formId" value="4" />
      <custom:property name="formName" value="Leave Application Form" />
      <custom:property name="formReadOnly" value="false" />
      <custom:property name="actionIds" value="[7]" />
      <custom:property name="actionNames" value="[&quot;Submit Leave Request&quot;]" />
    </custom:properties>
  </bpmn:extensionElements>
</bpmn:userTask>
```

### Verification
```sql
SELECT bpmn_xml FROM public.dw_process_definitions WHERE function_unit_id = 3;
```

The BPMN XML now contains actionIds and actionNames properties for each userTask node, binding the actions to the appropriate workflow steps.
