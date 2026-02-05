# Task Form Data Binding Fix

## Date
2026-02-05

## Issues

### Issue 1: Form Data Not Displaying
When viewing a task in User Portal, the approval form shows empty fields instead of displaying the data submitted by the applicant (employee name, leave type, dates, etc.).

**Root Cause**: The `TaskManagerComponent.getTaskInfo()` method in workflow-engine-core does not include process variables in the returned `TaskInfo` object.

### Issue 2: Approval Status Should Not Be Manually Selected
The "Approval Status" field in the approval form is currently a select dropdown where users manually choose "Approved", "Rejected", or "Pending". This is incorrect - the approval status should be automatically set based on which action button the user clicks (Approve/Reject).

**Root Cause**: The form configuration includes an `approvalStatus` select field that should not exist.

## Solutions

### Solution 1: Add Process Variables to TaskInfo ✅

**File**: `backend/workflow-engine-core/src/main/java/com/workflow/component/TaskManagerComponent.java`

**Method**: `buildTaskInfoFromFlowableTask(Task task)`

**Change Required**:
Add code to fetch process variables and include them in the TaskInfo object:

```java
// Get process variables
Map<String, Object> variables = null;
if (task.getProcessInstanceId() != null) {
    variables = runtimeService.getVariables(task.getProcessInstanceId());
}

return TaskListResult.TaskInfo.builder()
    .taskId(task.getId())
    .taskName(task.getName())
    // ... other fields ...
    .variables(variables)  // ADD THIS LINE
    .build();
```

**TaskInfo DTO Update Required**:
The `TaskListResult.TaskInfo` class needs to have a `variables` field:

```java
@Data
@Builder
public static class TaskInfo {
    private String taskId;
    private String taskName;
    // ... other fields ...
    private Map<String, Object> variables;  // ADD THIS FIELD
}
```

### Solution 2: Remove Approval Status Field from Form ✅ COMPLETED

**File**: `deploy/init-scripts/05-demo-leave-management/05-fix-approval-form.sql`

**Status**: ✅ Already executed

The approval form has been updated to remove the `approvalStatus` select field. Now it only has:
- 7 read-only fields (employee info from application)
- 1 editable field (approver comments)

**Verification**:
```sql
SELECT id, form_name, jsonb_array_length(config_json->'rule') as field_count
FROM dw_form_definitions
WHERE function_unit_id = 3 AND form_name = 'Leave Approval Form';
```

Result: 8 fields (down from 9)

## How Approval Status Should Work

### Current Incorrect Flow:
1. User opens approval task
2. User manually selects "Approved" or "Rejected" from dropdown
3. User enters comments
4. User clicks generic "Submit" button

### Correct Flow:
1. User opens approval task
2. User enters comments (required)
3. User clicks specific action button:
   - **Approve** button → Sets `approvalStatus = "APPROVED"` automatically
   - **Reject** button → Sets `approvalStatus = "REJECTED"` automatically
4. Backend receives action type and sets the variable accordingly

### Implementation in User Portal

**Frontend** (`frontend/user-portal/src/views/tasks/detail.vue`):

The page already has separate action buttons:
```vue
<el-button type="success" @click="handleApprove">
  <el-icon><Check /></el-icon> {{ t('task.approve') }}
</el-button>
<el-button type="danger" @click="handleReject">
  <el-icon><Close /></el-icon> {{ t('task.reject') }}
</el-button>
```

When user clicks Approve or Reject:
```typescript
const submitApprove = async () => {
  submitting.value = true
  try {
    await completeTask(taskId, {
      taskId: taskId,
      action: currentApproveAction.value,  // "APPROVE" or "REJECT"
      comment: approveForm.comment
    })
    ElMessage.success('操作成功')
    approveDialogVisible.value = false
    router.push('/tasks')
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}
```

**Backend** (`backend/user-portal/src/main/java/com/portal/component/TaskProcessComponent.java`):

The `completeTask` method should:
1. Receive the action type ("APPROVE" or "REJECT")
2. Set the `approvalStatus` variable based on the action
3. Include form data from `formData` in variables
4. Complete the task with all variables

```java
public void completeTask(TaskCompleteRequest request, String userId) {
    Map<String, Object> variables = new HashMap<>();
    
    // Add form data
    if (request.getFormData() != null) {
        variables.putAll(request.getFormData());
    }
    
    // Set approval status based on action
    if ("APPROVE".equals(request.getAction())) {
        variables.put("approvalStatus", "APPROVED");
    } else if ("REJECT".equals(request.getAction())) {
        variables.put("approvalStatus", "REJECTED");
    }
    
    // Add comment
    if (request.getComment() != null) {
        variables.put("approverComments", request.getComment());
    }
    
    // Complete task
    workflowEngineClient.completeTask(request.getTaskId(), userId, variables);
}
```

## Testing Steps

### Test 1: Verify Form Data Display
1. Start a new leave request process
2. Fill in all fields (employee name, leave type, dates, etc.)
3. Submit the request
4. Login as a manager/approver
5. Open the approval task
6. **Expected**: All fields from the application should be displayed (read-only)
7. **Current**: Fields are empty ❌

### Test 2: Verify Approval Status Auto-Set
1. Open an approval task
2. Enter approver comments
3. Click "Approve" button
4. **Expected**: Task completes with `approvalStatus = "APPROVED"`
5. **Current**: Need to manually select status ❌

### Test 3: Verify Reject Status Auto-Set
1. Open an approval task
2. Enter approver comments
3. Click "Reject" button
4. **Expected**: Task completes with `approvalStatus = "REJECTED"`
5. **Current**: Need to manually select status ❌

## Implementation Priority

### High Priority (Blocking User Testing):
1. ✅ Remove Approval Status field from form (COMPLETED)
2. ⚠️ Add variables to TaskInfo (REQUIRED - blocks form data display)
3. ⚠️ Update backend to set approvalStatus based on action (REQUIRED - blocks correct workflow)

### Medium Priority:
4. Update frontend to pass form data when completing task
5. Add validation to ensure comments are provided

### Low Priority:
6. Add audit trail for approval actions
7. Add notification when approval status changes

## Related Files

### Backend Files to Modify:
- `backend/workflow-engine-core/src/main/java/com/workflow/component/TaskManagerComponent.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/dto/response/TaskListResult.java`
- `backend/user-portal/src/main/java/com/portal/component/TaskProcessComponent.java`
- `backend/user-portal/src/main/java/com/portal/dto/TaskCompleteRequest.java`

### Frontend Files (Already Correct):
- `frontend/user-portal/src/views/tasks/detail.vue` (has separate Approve/Reject buttons)
- `frontend/user-portal/src/api/task.ts` (API calls)

### Database Files:
- `deploy/init-scripts/05-demo-leave-management/05-fix-approval-form.sql` ✅ (COMPLETED)

## Summary

✅ **Form configuration fixed** - Approval Status field removed
⚠️ **Backend changes required** - Add variables to TaskInfo
⚠️ **Backend changes required** - Auto-set approval status based on action

The form configuration is correct, but the backend needs to be updated to:
1. Return process variables with task details
2. Automatically set approval status based on which action button was clicked

