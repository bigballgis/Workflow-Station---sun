# Action Buttons Deployment Status

## Date: 2026-02-06

## Current Situation

### ✅ What's Working
1. **Database**: All 19 action definitions are in `sys_action_definitions` table
2. **BPMN File**: `digital-lending-process.bpmn` has been updated with String actionIds for all 7 tasks
3. **Developer Workstation**: `dw_process_definitions` table contains the updated BPMN
4. **Backend**: Workflow Engine can extract actionIds from BPMN and User Portal can fetch action definitions
5. **Frontend**: Dynamic button rendering is implemented and deployed

### ⚠️ The Problem
- **Existing process instances** (like the test process `0e7902a5-02ba-11f1-9c21-5aaa8f1520e4`) use **Flowable version 7**
- Flowable version 7 has the **OLD numeric actionIds** (e.g., `[13,14,15,16,18]`)
- Only "Verify Documents" task in version 7 has the new String IDs
- The updated BPMN is in `dw_process_definitions` but NOT yet deployed to Flowable

### 🔍 Root Cause
When a process is started, Flowable creates a snapshot of the BPMN definition. Existing processes continue using their original version even if the BPMN is updated. To use the new actionIds, you need to:
1. Start a NEW process instance, OR
2. Deploy a new version to Flowable (version 8)

---

## Solution Options

### Option 1: Start a New Process Instance (RECOMMENDED)
**Steps**:
1. Go to User Portal → Start Process
2. Select "Digital Lending Process"
3. Fill in the loan application form
4. Submit

**Result**: The new process will automatically use the latest BPMN from `dw_process_definitions`, which will be deployed as Flowable version 8 with all the updated String actionIds.

**Verification**:
```bash
# Check that a new version was created
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT id_, key_, version_ FROM act_re_procdef WHERE key_ = 'DigitalLendingProcess' ORDER BY version_ DESC LIMIT 3;"
```

### Option 2: Manually Deploy to Flowable (ADVANCED)
If you need to update existing processes (not recommended as it may cause issues), you can:

1. **Delete old deployments** (DANGEROUS - will break existing processes):
```sql
DELETE FROM act_re_procdef WHERE key_ = 'DigitalLendingProcess' AND version_ < 8;
DELETE FROM act_re_deployment WHERE id_ IN (SELECT deployment_id_ FROM act_re_procdef WHERE key_ = 'DigitalLendingProcess' AND version_ < 8);
```

2. **Restart Workflow Engine** to force redeployment from `dw_process_definitions`

**⚠️ WARNING**: This will break all existing process instances!

---

## Verification Steps

### 1. Check Action Definitions
```sql
SELECT id, action_name, action_type, button_color 
FROM sys_action_definitions 
WHERE function_unit_id = 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89'
ORDER BY action_name;
```

Expected: 19 rows with String IDs like `action-dl-credit-check`, `action-dl-approve-loan`, etc.

### 2. Check BPMN in dw_process_definitions
```sql
SELECT bpmn_xml FROM dw_process_definitions WHERE function_unit_id = 4;
```

Search for: `actionIds" value="[action-dl-credit-check,action-dl-approve-loan,action-dl-reject-loan,action-dl-request-info]"`

### 3. Check Flowable Version
```sql
SELECT id_, key_, version_, deployment_id_ 
FROM act_re_procdef 
WHERE key_ = 'DigitalLendingProcess' 
ORDER BY version_ DESC 
LIMIT 3;
```

Expected: Version 7 (old), Version 8 (new - after starting a new process)

### 4. Check BPMN in Flowable
```sql
SELECT convert_from(bytes_, 'UTF8') 
FROM act_ge_bytearray 
WHERE deployment_id_ = (
    SELECT deployment_id_ 
    FROM act_re_procdef 
    WHERE key_ = 'DigitalLendingProcess' 
    AND version_ = 8
);
```

Search for: `actionIds" value="[action-dl-credit-check,action-dl-approve-loan,action-dl-reject-loan,action-dl-request-info]"`

### 5. Test API Response
```bash
# Get a task from the NEW process
curl -X GET "http://localhost:8082/api/portal/tasks/{new_task_id}"
```

Expected: `actions` array with 4 action objects for Credit Check task

### 6. Test Frontend
1. Open User Portal: http://localhost:3001
2. Navigate to the new task (e.g., "Perform Credit Check")
3. Verify custom buttons appear:
   - "Perform Credit Check" (blue/primary)
   - "Approve Loan" (green/success)
   - "Reject Loan" (red/danger)
   - "Request Additional Info" (yellow/warning)

---

## Task-to-ActionIds Mapping

| Task Name | ActionIds |
|-----------|-----------|
| Submit Loan Application | `[12,22]` (old numeric - not updated) |
| Verify Documents | `[action-dl-verify-docs, action-dl-approve-loan, action-dl-reject-loan, action-dl-request-info]` |
| Perform Credit Check | `[action-dl-credit-check, action-dl-approve-loan, action-dl-reject-loan, action-dl-request-info]` |
| Assess Risk | `[action-dl-assess-risk, action-dl-mark-low-risk, action-dl-mark-high-risk]` |
| Manager Approval | `[action-dl-manager-approve, action-dl-manager-reject, action-dl-request-revision]` |
| Senior Manager Approval | `[action-dl-senior-approve, action-dl-senior-reject, action-dl-escalate]` |
| Process Disbursement | `[action-dl-disburse, action-dl-hold-disbursement, action-dl-verify-account]` |

---

## Summary

**To see custom action buttons on ALL tasks**:
1. Start a NEW Digital Lending process instance
2. The new process will use Flowable version 8 with updated actionIds
3. All tasks in the new process will display custom buttons

**Existing processes** (version 7) will continue showing:
- Custom buttons on "Verify Documents" task only
- Default buttons on other tasks (because they have old numeric IDs that don't match any action definitions)

---

## Files Modified

1. `deploy/init-scripts/06-digital-lending/09-add-more-action-definitions.sql` - Added 15 new action definitions
2. `deploy/init-scripts/06-digital-lending/10-update-all-bpmn-action-ids.sql` - Updated BPMN in dw_process_definitions
3. `deploy/init-scripts/06-digital-lending/digital-lending-process.bpmn` - Updated BPMN file with String actionIds
4. `deploy/init-scripts/06-digital-lending/11-redeploy-bpmn-to-flowable.ps1` - Script to manually deploy to Flowable (not used)
5. `deploy/init-scripts/06-digital-lending/12-deploy-via-flowable-api.ps1` - Script to deploy via REST API (failed)
6. `deploy/init-scripts/06-digital-lending/ACTION_BUTTONS_DEPLOYMENT_STATUS.md` - This document

---

## Next Steps

1. **User Action Required**: Start a new Digital Lending process to test the updated actionIds
2. **Verify**: Check that all 7 tasks display custom action buttons
3. **Optional**: Delete old test processes if no longer needed
4. **Documentation**: Update user guide with screenshots of custom buttons

