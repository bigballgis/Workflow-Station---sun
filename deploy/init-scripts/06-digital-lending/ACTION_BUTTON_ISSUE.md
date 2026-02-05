# Digital Lending - Action Button Display Issue

## Problem

When viewing a task in the User Portal, the task detail page shows generic action buttons (Approve, Reject, Delegate, Transfer, Urge) instead of the specific action buttons defined in the BPMN process (Verify Documents, Approve Loan, Reject Loan, Request Additional Info).

## Root Cause

The BPMN XML in Flowable contains the correct action bindings:

```xml
<bpmn:userTask id="Task_DocumentVerification" name="Verify Documents">
  <bpmn:extensionElements>
    <custom:properties>
      <custom:property name="actionIds" value="[20,16,17,18]" />
      <custom:property name="actionNames" value="[&quot;Verify Documents&quot;,&quot;Approve Loan&quot;,&quot;Reject Loan&quot;,&quot;Request Additional Info&quot;]" />
    </custom:properties>
  </bpmn:extensionElements>
  ...
</bpmn:userTask>
```

However, the **User Portal frontend is not parsing and displaying these action buttons correctly**.

## Verification

### Check BPMN in Flowable
```sql
-- Verify action bindings exist in Flowable BPMN
SELECT 
    substring(
        convert_from(bytes_, 'UTF8'),
        position('Task_DocumentVerification' in convert_from(bytes_, 'UTF8')),
        500
    ) as task_section
FROM act_ge_bytearray
WHERE name_ = 'DIGITAL_LENDING.bpmn'
ORDER BY id_ DESC
LIMIT 1;
```

Expected: Should show `actionIds` and `actionNames` properties

### Check Action Definitions
```sql
-- Verify actions exist in database
SELECT id, action_name, action_type
FROM dw_action_definitions
WHERE id IN (20, 16, 17, 18)
ORDER BY id;
```

Expected:
- 16: Approve Loan (APPROVE)
- 17: Reject Loan (REJECT)
- 18: Request Additional Info (FORM_POPUP)
- 20: Verify Documents (APPROVE)

## Expected Behavior

When a user opens the "Verify Documents" task, they should see 4 action buttons:
1. **Verify Documents** (APPROVE type)
2. **Approve Loan** (APPROVE type)
3. **Reject Loan** (REJECT type)
4. **Request Additional Info** (FORM_POPUP type)

## Actual Behavior

The task detail page shows generic buttons:
- Approve
- Reject
- Delegate
- Transfer
- Urge

## Frontend Code Location

The issue is likely in the User Portal frontend code that renders task action buttons. The code needs to:

1. **Fetch BPMN definition** from Flowable or backend API
2. **Parse extensionElements** to extract `actionIds` and `actionNames`
3. **Fetch action definitions** from backend API using the action IDs
4. **Render action buttons** based on the fetched action definitions

### Possible File Locations
- `frontend/user-portal/src/views/tasks/*.vue` - Task detail views
- `frontend/user-portal/src/components/ActionButtons.vue` - Action button component
- `frontend/user-portal/src/api/task.ts` - Task API calls

### Required API Endpoints

The frontend needs to call:
1. `GET /api/v1/tasks/{taskId}` - Get task details including BPMN definition
2. `GET /api/v1/actions/{actionId}` - Get action definition by ID
3. Or a combined endpoint: `GET /api/v1/tasks/{taskId}/actions` - Get all actions for a task

## Workaround

Until the frontend code is fixed, users can:
1. Use the generic "Approve" button to complete tasks
2. The workflow will still progress correctly
3. Action-specific logic (like FORM_POPUP) will not work

## Comparison with Working Example

The "Employee Leave Management" function unit works correctly and displays custom action buttons. Compare the implementation:

### Leave Management BPMN
```sql
SELECT 
    substring(bpmn_xml, position('userTask' in bpmn_xml), 400)
FROM dw_process_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'LEAVE_MGMT')
LIMIT 1;
```

The format is identical, so the issue is not with the BPMN format.

## Recommended Fix

### Option 1: Fix Frontend Code
Update the User Portal frontend to properly parse and display action buttons from BPMN.

### Option 2: Backend API Enhancement
Create a backend API endpoint that returns task actions:
```
GET /api/portal/tasks/{taskId}/actions
```

Response:
```json
{
  "actions": [
    {
      "id": 20,
      "name": "Verify Documents",
      "type": "APPROVE",
      "icon": "check",
      "color": "success"
    },
    {
      "id": 16,
      "name": "Approve Loan",
      "type": "APPROVE",
      "icon": "check-circle",
      "color": "success"
    },
    {
      "id": 17,
      "name": "Reject Loan",
      "type": "REJECT",
      "icon": "times-circle",
      "color": "danger"
    },
    {
      "id": 18,
      "name": "Request Additional Info",
      "type": "FORM_POPUP",
      "icon": "file-alt",
      "color": "warning",
      "formId": 7
    }
  ]
}
```

## Status

- ✅ BPMN contains correct action bindings
- ✅ Actions exist in database
- ✅ Virtual groups created
- ✅ Tasks can be assigned
- ❌ **Action buttons not displaying in User Portal** (Frontend issue)

## Next Steps

1. Review User Portal frontend code for task action rendering
2. Implement proper BPMN parsing to extract action bindings
3. Create or use existing API to fetch action definitions
4. Render action buttons dynamically based on BPMN configuration

---

**Date**: 2026-02-05  
**Issue Type**: Frontend Implementation  
**Priority**: High  
**Status**: Identified - Awaiting Frontend Fix
