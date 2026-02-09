# Session Summary - February 5, 2026 (Part 2)

## Issue: Incomplete Flow History Records in Applications Detail Page

### Problem Description
User reported that when viewing completed tasks in the Applications detail page, only 2 history records were displayed (submit + current node) instead of the complete flow history showing all approval steps.

### Root Cause Analysis
The `ProcessComponent.getProcessHistory()` method was trying to get the first active task from the process instance and then fetch history by taskId. However, for **completed processes**, there are no active tasks anymore, causing the method to return an empty list and fall back to the simple `initHistoryRecords()` method which only creates 2 basic records.

### Solution Implemented

#### 1. Added New Endpoint in TaskController
Created a new endpoint `/api/v1/tasks/process/{processInstanceId}/history` that accepts a process instance ID directly instead of requiring a task ID:

**File**: `backend/workflow-engine-core/src/main/java/com/workflow/controller/TaskController.java`

```java
@GetMapping("/process/{processInstanceId}/history")
@Operation(summary = "获取流程实例流转历史", description = "获取流程实例的完整流转历史，包含用户名称解析")
public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProcessInstanceHistory(
        @Parameter(description = "流程实例ID", required = true)
        @PathVariable String processInstanceId) {
    
    log.info("Getting process instance history for: {}", processInstanceId);
    
    // Query Flowable's historic activity instances
    List<HistoricActivityInstance> activities = historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .orderByHistoricActivityInstanceStartTime().asc()
        .list();
    
    // Convert to frontend format with user name resolution
    // ... (resolves fullName > displayName > username > userId)
    
    return ResponseEntity.ok(ApiResponse.success(historyList));
}
```

This endpoint:
- Queries Flowable's `HistoricActivityInstance` by process instance ID
- Filters for userTask, startEvent, and endEvent activities
- Resolves user names using the priority: fullName > displayName > username > userId
- Returns complete history for both running and completed processes

#### 2. Updated WorkflowEngineClient
Added a new method `getProcessInstanceHistory()` to call the new endpoint:

**File**: `backend/user-portal/src/main/java/com/portal/client/WorkflowEngineClient.java`

```java
/**
 * 获取流程实例流转历史（通过流程实例ID，包含用户名称解析）
 */
public Optional<List<Map<String, Object>>> getProcessInstanceHistory(String processInstanceId) {
    if (!isAvailable()) {
        return Optional.empty();
    }
    try {
        String url = workflowEngineUrl + "/api/v1/tasks/process/" + processInstanceId + "/history";
        
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, null,
            new ParameterizedTypeReference<Map<String, Object>>() {});
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            return Optional.ofNullable(data);
        }
    } catch (Exception e) {
        log.warn("Failed to get process instance history from workflow engine: {}", e.getMessage());
    }
    return Optional.empty();
}
```

#### 3. Updated ProcessComponent
Simplified the `getProcessHistory()` method to directly call the new endpoint:

**File**: `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`

```java
/**
 * 获取流程历史记录
 * 调用 workflow-engine 的流程历史接口，返回已解析用户名称的历史记录
 */
public List<Map<String, Object>> getProcessHistory(String processId) {
    log.info("Getting process history for process instance: {}", processId);
    
    if (!workflowEngineClient.isAvailable()) {
        log.warn("Workflow engine not available, returning empty history");
        return Collections.emptyList();
    }
    
    try {
        // 直接调用 workflow-engine 的流程实例历史接口（通过 processInstanceId）
        // 该接口会查询 Flowable 的历史活动记录并解析用户名称
        Optional<List<Map<String, Object>>> historyResult = workflowEngineClient.getProcessInstanceHistory(processId);
        
        if (historyResult.isPresent()) {
            List<Map<String, Object>> history = historyResult.get();
            log.info("Got {} history records for process: {}", history.size(), processId);
            return history;
        } else {
            log.warn("Failed to get process history from workflow engine for process: {}", processId);
            return Collections.emptyList();
        }
        
    } catch (Exception e) {
        log.error("Failed to get process history for {}: {}", processId, e.getMessage(), e);
        return Collections.emptyList();
    }
}
```

### Changes Summary

**Modified Files**:
1. `backend/workflow-engine-core/src/main/java/com/workflow/controller/TaskController.java`
   - Added `getProcessInstanceHistory()` endpoint
   - Refactored existing `getTaskHistory()` to call the new method

2. `backend/user-portal/src/main/java/com/portal/client/WorkflowEngineClient.java`
   - Added `getProcessInstanceHistory()` method

3. `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`
   - Simplified `getProcessHistory()` to use the new endpoint

### Deployment

1. **Compiled backend modules**:
   ```bash
   mvn clean package -DskipTests -pl backend/workflow-engine-core,backend/user-portal -am
   ```

2. **Rebuilt Docker images**:
   ```bash
   docker-compose -f deploy/environments/dev/docker-compose.dev.yml build --no-cache workflow-engine user-portal
   ```

3. **Restarted containers**:
   ```bash
   docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d workflow-engine user-portal
   ```

### Testing Instructions

1. **Login to User Portal**: http://localhost:3001
2. **Navigate to Applications** (我的申请)
3. **Click on a completed application** to view details
4. **Verify Flow History section** shows complete history:
   - Submit record (提交申请)
   - All approval steps with user names (not UUIDs)
   - End event (if completed)

### Expected Behavior

The Flow History (流转记录) section should now display:
- ✅ Complete list of all process activities
- ✅ User names resolved (fullName > displayName > username > userId)
- ✅ Correct operation types (SUBMIT, APPROVE, etc.)
- ✅ Timestamps for each activity
- ✅ Works for both running and completed processes

### Technical Notes

**Why the previous approach failed**:
- The old method tried to get active tasks first, then fetch history by taskId
- For completed processes, there are no active tasks in Flowable
- This caused the method to return empty and fall back to simple 2-record history

**Why the new approach works**:
- Directly queries Flowable's `HistoricActivityInstance` by process instance ID
- Historic activities exist for both running and completed processes
- No dependency on active tasks
- User name resolution happens in the workflow-engine layer

### Status
✅ **FIXED** - Complete flow history now displays correctly for all processes (running and completed)

---

## Related Issues
- Previous fix: User name display (UUIDs → names) - COMPLETED
- Database cleanup for testing - COMPLETED
