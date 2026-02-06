# Custom Action Buttons - Solution Summary

## Date: 2026-02-06

---

## Problem Analysis

### Issue 1: Frontend Not Showing Custom Buttons
**Symptom**: "Verify Documents" task showed custom buttons, but "Perform Credit Check" and other tasks did not.

**Root Cause**: The BPMN deployed in Flowable (version 7) had:
- ✅ Task_DocumentVerification: String actionIds `[action-dl-verify-docs,action-dl-approve-loan,action-dl-reject-loan,action-dl-request-info]`
- ❌ Task_CreditCheck: Old numeric actionIds `[13,14,15,16,18]`
- ❌ Other tasks: Old numeric actionIds

**Why**: The BPMN was updated in `dw_process_definitions` table but NOT redeployed to Flowable. Existing process instances continue using the old Flowable version 7.

---

## Solution

### What Was Done

#### 1. Created Action Definitions (✅ Complete)
- File: `09-add-more-action-definitions.sql`
- Added 15 new action definitions with String IDs
- Total: 19 action definitions in `sys_action_definitions` table

#### 2. Updated BPMN File (✅ Complete)
- File: `digital-lending-process.bpmn`
- Updated all 7 tasks with String actionIds
- Replaced numeric IDs like `[13,14,15,16,18]` with String IDs like `[action-dl-credit-check,action-dl-approve-loan,action-dl-reject-loan,action-dl-request-info]`

#### 3. Updated Database (✅ Complete)
- File: `10-update-all-bpmn-action-ids.sql`
- Updated `dw_process_definitions` table with new BPMN
- Workflow Engine will use this BPMN for NEW process instances

#### 4. Backend Implementation (✅ Complete)
- Workflow Engine extracts actionIds from BPMN
- User Portal fetches action definitions from database
- API returns `actions` array in task details

#### 5. Frontend Implementation (✅ Complete)
- Dynamic button rendering based on `actions` array
- Button type mapping (primary, success, danger, warning)
- Icon mapping (check, close, files, warning, etc.)
- Frontend deployed to Docker container

---

## How to See Custom Buttons

### Option 1: Start a New Process (RECOMMENDED)

1. **Open User Portal**: http://localhost:3001
2. **Login** as a user with permission to start processes
3. **Navigate to**: Start Process → Digital Lending Process
4. **Fill in** the loan application form
5. **Submit** the application

**Result**: The new process will automatically use the updated BPMN (Flowable version 8) with all custom action buttons.

### Option 2: Wait for Existing Process to Reach "Verify Documents"

If you have an existing process (version 7), only the "Verify Documents" task will show custom buttons. Other tasks will show default buttons because they have old numeric actionIds.

---

## Verification

### Check Flowable Version
```bash
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT id_, key_, version_ FROM act_re_procdef WHERE key_ = 'DigitalLendingProcess' ORDER BY version_ DESC LIMIT 3;"
```

Expected output:
- Version 7: Old BPMN (existing processes)
- Version 8: New BPMN (new processes) - will appear after starting a new process

### Check Task API Response
```bash
curl -X GET "http://localhost:8082/api/portal/tasks/{task_id}"
```

Expected: `actions` array with action objects:
```json
{
  "taskId": "...",
  "taskName": "Perform Credit Check",
  "actions": [
    {
      "actionId": "action-dl-credit-check",
      "actionName": "Perform Credit Check",
      "actionType": "APPROVE",
      "buttonColor": "primary",
      "icon": "check"
    },
    {
      "actionId": "action-dl-approve-loan",
      "actionName": "Approve Loan",
      "actionType": "APPROVE",
      "buttonColor": "success",
      "icon": "check-circle"
    },
    {
      "actionId": "action-dl-reject-loan",
      "actionName": "Reject Loan",
      "actionType": "REJECT",
      "buttonColor": "danger",
      "icon": "times-circle"
    },
    {
      "actionId": "action-dl-request-info",
      "actionName": "Request Additional Info",
      "actionType": "FORM_POPUP",
      "buttonColor": "warning",
      "icon": "warning"
    }
  ]
}
```

### Check Frontend Display
1. Open task detail page
2. Scroll to bottom (Action Buttons section)
3. Verify buttons appear with correct colors and icons

---

## Architecture Flow

```
User clicks task
    ↓
Frontend: GET /api/portal/tasks/{taskId}
    ↓
User Portal Backend: TaskController.getTaskDetail()
    ↓
Workflow Engine API: GET /api/workflow/tasks/{taskId}
    ↓
TaskManagerComponent.getTaskDetail()
    ├─ Extract actionIds from BPMN (e.g., ["action-dl-credit-check", "action-dl-approve-loan", ...])
    └─ Return TaskInfo with actionIds
    ↓
User Portal: TaskActionService.enrichTaskWithActions()
    ├─ Query sys_action_definitions table
    └─ Match actionIds to action definitions
    ↓
Frontend: Render dynamic buttons
    ├─ Map buttonColor to Element Plus type
    ├─ Map icon to Vue component
    └─ Display buttons
```

---

## Key Files

### Database
- `sys_action_definitions` - Action definitions (19 rows)
- `dw_process_definitions` - BPMN source (updated)
- `act_re_procdef` - Flowable process definitions (version 7 = old, version 8 = new)
- `act_ge_bytearray` - Flowable BPMN bytes

### Backend
- `backend/workflow-engine-core/src/main/java/com/workflow/component/TaskManagerComponent.java` - Extracts actionIds from BPMN
- `backend/user-portal/src/main/java/com/portal/service/TaskActionService.java` - Fetches action definitions
- `backend/user-portal/src/main/java/com/portal/dto/TaskActionInfo.java` - Action DTO

### Frontend
- `frontend/user-portal/src/api/task.ts` - TaskActionInfo interface
- `frontend/user-portal/src/views/tasks/detail.vue` - Dynamic button rendering

### Scripts
- `deploy/init-scripts/06-digital-lending/09-add-more-action-definitions.sql` - Insert action definitions
- `deploy/init-scripts/06-digital-lending/10-update-all-bpmn-action-ids.sql` - Update BPMN in database
- `deploy/init-scripts/06-digital-lending/digital-lending-process.bpmn` - Updated BPMN file

---

## Summary

✅ **Backend**: Complete - extracts actionIds and fetches action definitions
✅ **Frontend**: Complete - renders dynamic buttons
✅ **Database**: Complete - action definitions and updated BPMN
⏳ **Deployment**: Requires starting a NEW process to use updated BPMN

**Action Required**: Start a new Digital Lending process to see custom action buttons on all tasks.

**Why**: Existing processes use Flowable version 7 (old BPMN). New processes will use version 8 (updated BPMN).

