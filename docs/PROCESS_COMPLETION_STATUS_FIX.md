# Process Completion Status Fix

## Issue Description
After HR approves a request and the process completes in Flowable, the application status in `up_process_instance` table remains as `RUNNING` instead of being updated to `COMPLETED`.

## Root Cause
There was no mechanism to synchronize the process status between Flowable's internal tables (`act_hi_procinst`) and our application's tracking table (`up_process_instance`). When a process completed in Flowable, the `up_process_instance` table was not being updated.

## Solution Implemented

### 1. Created Process Completion Listener
**File**: `backend/workflow-engine-core/src/main/java/com/workflow/listener/ProcessCompletionListener.java`

- Implements `FlowableEventListener` to listen for `PROCESS_COMPLETED` events
- When a process completes, it notifies the user-portal service via REST API
- Added `getLastActivityName()` method to query the last meaningful activity (userTask or serviceTask) from Flowable history
- Passes the last activity name to user-portal for display purposes
- Configured to not fail on exceptions to avoid affecting process execution

### 2. Registered Listener in Flowable Configuration
**File**: `backend/workflow-engine-core/src/main/java/com/workflow/config/FlowableConfig.java`

- Added `ProcessCompletionListener` to the Flowable engine configuration
- Registered for `PROCESS_COMPLETED` event type
- Works alongside the existing `TaskAssignmentListener`

### 3. Added Completion Endpoint in User Portal
**File**: `backend/user-portal/src/main/java/com/portal/controller/ProcessController.java`

- New endpoint: `POST /processes/{processId}/complete`
- Receives notifications from workflow-engine when a process completes
- Extracts `lastActivityName` from the request body
- Calls `ProcessComponent.markProcessAsCompleted(processId, lastActivityName)` to update the database

### 4. Implemented Status Update Logic
**File**: `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`

- Updated method signature: `markProcessAsCompleted(String processId, String lastActivityName)`
- Updates the process instance status to `COMPLETED`
- Sets the `end_time` to current timestamp
- Saves the last activity name to `current_node` field (e.g., "HR Approval", "已批准") instead of clearing it
- Sets `current_assignee` to NULL (displays as "-" in frontend)
- Only updates processes that are currently in `RUNNING` status

### 5. Updated Frontend Display
**File**: `frontend/user-portal/src/views/applications/index.vue`

- Changed display for `currentAssignee` from "Unassigned" to "-" when NULL
- Ensures completed processes show a clean, professional display

## How It Works

1. User completes the final task in a process (e.g., HR Approval)
2. Flowable completes the process and fires `PROCESS_COMPLETED` event
3. `ProcessCompletionListener` catches the event
4. Listener queries Flowable history to get the last meaningful activity name (userTask or serviceTask)
5. Listener calls user-portal's `/processes/{processId}/complete` endpoint with `lastActivityName`
6. User-portal updates `up_process_instance` table:
   - `status` → `COMPLETED`
   - `end_time` → current timestamp
   - `current_node` → last activity name (e.g., "HR Approval", "已批准")
   - `current_assignee` → NULL (displays as "-" in frontend)

## Testing

### Manual Fix for Existing Completed Processes
For processes that completed before the listener was added, run this SQL:

```sql
UPDATE up_process_instance 
SET status = 'COMPLETED', 
    end_time = (SELECT end_time_ FROM act_hi_procinst WHERE proc_inst_id_ = up_process_instance.id),
    current_node = 'HR Approval',  -- Or the appropriate last activity name
    current_assignee = NULL
WHERE id IN (
    SELECT proc_inst_id_ 
    FROM act_hi_procinst 
    WHERE end_time_ IS NOT NULL
)
AND status = 'RUNNING';
```

**Note**: For better display, you can query the actual last activity name from Flowable history:
```sql
-- Get the last activity name for a specific process
SELECT activity_name_ 
FROM act_hi_actinst 
WHERE proc_inst_id_ = '<process_id>' 
  AND end_time_ IS NOT NULL
  AND activity_type_ IN ('userTask', 'serviceTask')
ORDER BY end_time_ DESC 
LIMIT 1;
```

### Testing New Processes
1. Start a new leave request process
2. Complete all approval tasks (Manager → HR)
3. Verify that the process status in "My Applications" shows as `COMPLETED`
4. Check database: `SELECT id, status, end_time FROM up_process_instance WHERE id = '<process_id>';`

## Configuration Requirements

### Workflow Engine
Ensure `user-portal.url` is configured in `application.yml`:

```yaml
user-portal:
  url: http://user-portal:8080
```

### User Portal
No additional configuration required. The endpoint is automatically available.

## Deployment

1. **Compile workflow-engine-core**:
   ```bash
   cd backend/workflow-engine-core
   mvn clean package -DskipTests
   ```

2. **Compile user-portal**:
   ```bash
   cd backend/user-portal
   mvn clean package -DskipTests
   ```

3. **Rebuild Docker images**:
   ```bash
   cd deploy/environments/dev
   docker-compose -f docker-compose.dev.yml build --no-cache workflow-engine user-portal
   ```

4. **Restart containers**:
   ```bash
   docker-compose -f docker-compose.dev.yml up -d --force-recreate workflow-engine user-portal
   ```

## Verification

### Check Listener Registration
```bash
docker logs platform-workflow-engine-dev | grep -i "ProcessCompletionListener\|PROCESS_COMPLETED"
```

### Check Process Status
```sql
-- Check Flowable's process status
SELECT proc_inst_id_, end_time_, delete_reason_ 
FROM act_hi_procinst 
ORDER BY start_time_ DESC 
LIMIT 5;

-- Check our application's process status
SELECT id, status, end_time, current_node, current_assignee 
FROM up_process_instance 
ORDER BY start_time DESC 
LIMIT 5;
```

### Monitor Completion Events
```bash
# Watch workflow-engine logs for completion events
docker logs -f platform-workflow-engine-dev | grep "Process completed event"

# Watch user-portal logs for status updates
docker logs -f platform-user-portal-dev | grep "markProcessAsCompleted"
```

## Files Modified

### Backend - Workflow Engine
- `backend/workflow-engine-core/src/main/java/com/workflow/listener/ProcessCompletionListener.java` (NEW)
- `backend/workflow-engine-core/src/main/java/com/workflow/config/FlowableConfig.java` (MODIFIED)

### Backend - User Portal
- `backend/user-portal/src/main/java/com/portal/controller/ProcessController.java` (MODIFIED)
- `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java` (MODIFIED)

### Frontend - User Portal
- `frontend/user-portal/src/views/applications/index.vue` (MODIFIED)

## Date
2026-02-05

## Status
✅ Implementation complete
✅ Backend compiled successfully (workflow-engine-core and user-portal)
✅ Frontend built successfully (user-portal)
✅ Docker images rebuilt (workflow-engine, user-portal, user-portal-frontend)
✅ Containers restarted and running
✅ Manual fix applied to existing completed process
⏳ Awaiting user testing with new process completion

## Display Requirements for Completed Processes

### Current Node
- Should display the last activity name (e.g., "HR Approval", "已批准")
- NOT empty or NULL

### Current Assignee
- Should display "-" instead of "Unassigned"
- Set to NULL in database

## Next Steps for User

1. **Verify existing completed process**:
   - Navigate to "My Applications" (我的申请)
   - Check if the first process now shows as `COMPLETED` instead of `RUNNING`

2. **Test with new process**:
   - Start a new leave request
   - Complete Manager Approval
   - Complete HR Approval
   - Verify the process status automatically changes to `COMPLETED`

3. **Check logs** (if needed):
   ```bash
   # Workflow engine logs
   docker logs platform-workflow-engine-dev --tail 50 | findstr "Process completed event"
   
   # User portal logs
   docker logs platform-user-portal-dev --tail 50 | findstr "markProcessAsCompleted"
   ```
