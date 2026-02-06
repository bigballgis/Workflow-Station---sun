# User Name Display Fix

## Date
2026-02-05

## Issue
在任务详情页面的基本信息和流转记录中，以及"My Applications"（我的申请）列表的"Current Assignee"列中，显示的是用户ID（如 `3ca0f68e-43eb-4bcf-9c15-1ceaa3d71740`）而不是用户名称。

## Root Causes

### Issue 1: Task History
在 `workflow-engine-core` 的 `TaskController.getTaskHistory()` 方法中，`operatorName` 字段直接使用了 `activity.getAssignee()`（用户ID），没有调用 AdminCenterClient 来解析用户的显示名称。

### Issue 2: My Applications List
在 `user-portal` 的 `ProcessComponent.toProcessInstanceInfo()` 方法中，`currentAssignee` 字段直接从数据库的 `ProcessInstance` 实体中获取，这个字段存储的是用户ID，没有解析为用户名称。

### Issue 3: Process Instances with No Active Tasks
当流程实例没有活动任务时（已完成或在过渡状态），`toProcessInstanceInfo()` 方法仍然尝试显示 `currentAssignee`，导致显示用户ID而不是名称。这种情况下应该将 `currentAssignee` 设置为 `null`。

## Solutions

### Solution 1: Fix Task History Display

**Modified File**: `backend/workflow-engine-core/src/main/java/com/workflow/controller/TaskController.java`

**Changes**:

1. **Added AdminCenterClient dependency**:
```java
@RequiredArgsConstructor
@Tag(name = "任务管理", description = "工作流任务管理API")
public class TaskController {
    // ... other dependencies ...
    private final com.workflow.client.AdminCenterClient adminCenterClient;
}
```

2. **Updated getTaskHistory() method to resolve user names**:
```java
String assignee = activity.getAssignee();
item.put("operatorId", assignee);

// 解析用户显示名称
String operatorName = assignee;
if (assignee != null && !assignee.isEmpty()) {
    try {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(assignee);
        if (userInfo != null) {
            // 优先使用 fullName
            String fullName = (String) userInfo.get("fullName");
            if (fullName != null && !fullName.isEmpty()) {
                operatorName = fullName;
            } else {
                // 其次使用 displayName
                String displayName = (String) userInfo.get("displayName");
                if (displayName != null && !displayName.isEmpty()) {
                    operatorName = displayName;
                } else {
                    // 再次使用 username
                    String username = (String) userInfo.get("username");
                    if (username != null && !username.isEmpty()) {
                        operatorName = username;
                    }
                }
            }
        }
    } catch (Exception e) {
        log.warn("Failed to resolve user display name for {}: {}", assignee, e.getMessage());
    }
}
item.put("operatorName", operatorName);
```

### Solution 2: Fix My Applications List Display

**Modified File**: `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`

**Changes**:

Updated `toProcessInstanceInfo()` method to:
1. Fetch current assignee name from workflow-engine
2. Handle empty task lists (no active tasks) by setting `currentAssignee` to `null`

```java
private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance) {
    String currentAssignee = instance.getCurrentAssignee();
    String currentAssigneeName = null;
    
    // 如果有当前处理人，尝试从 workflow-engine 获取任务信息以获取用户名称
    if (currentAssignee != null && !currentAssignee.isEmpty() && "RUNNING".equals(instance.getStatus())) {
        try {
            if (workflowEngineClient.isAvailable()) {
                Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(instance.getId());
                if (tasksResult.isPresent()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tasksData = (Map<String, Object>) tasksResult.get().get("data");
                    if (tasksData != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                        if (tasks != null && !tasks.isEmpty()) {
                            Map<String, Object> currentTask = tasks.get(0);
                            currentAssigneeName = (String) currentTask.get("currentAssigneeName");
                            // 如果没有名称，使用ID
                            if (currentAssigneeName == null || currentAssigneeName.isEmpty()) {
                                currentAssigneeName = currentAssignee;
                            }
                        } else {
                            // 任务列表为空，说明流程没有活动任务（可能已完成或在过渡状态）
                            log.debug("No active tasks found for process instance {}, clearing current assignee", instance.getId());
                            currentAssigneeName = null;
                            currentAssignee = null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get current assignee name for process {}: {}", instance.getId(), e.getMessage());
            currentAssigneeName = currentAssignee; // 回退到使用ID
        }
    }
    
    // 如果没有获取到名称，使用ID（如果有的话）
    if (currentAssigneeName == null && currentAssignee != null) {
        currentAssigneeName = currentAssignee;
    }
    
    return ProcessInstanceInfo.builder()
        // ... other fields ...
        .currentAssignee(currentAssigneeName)  // 使用解析后的名称，如果没有活动任务则为null
        .build();
}
```

### User Name Resolution Priority
1. **fullName** (完整姓名) - 优先使用
2. **displayName** (显示名称) - 其次
3. **username** (用户名) - 再次
4. **userId** (用户ID) - 最后回退

### Handling Process Instances with No Active Tasks
When a process instance has no active tasks (empty task list), the `currentAssignee` field is now set to `null` instead of showing a user ID. This handles cases where:
- The process has completed
- The process is in a transition state between tasks
- The process has ended

## Build and Deploy

### Workflow Engine
```bash
# Build
cd backend/workflow-engine-core
mvn clean install -DskipTests

# Docker build
docker-compose -f deploy/environments/dev/docker-compose.dev.yml build workflow-engine

# Deploy
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d workflow-engine
```

### User Portal
```bash
# Build
cd backend/user-portal
mvn clean install -DskipTests

# Docker build
docker-compose -f deploy/environments/dev/docker-compose.dev.yml build user-portal

# Deploy
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d user-portal
```

## Verification

### Workflow Engine
```
2026-02-05 08:54:00 [workflow-engine] [main] [] INFO  c.workflow.WorkflowEngineApplication - Started WorkflowEngineApplication in 31.154 seconds
```

### User Portal
```
2026-02-05T09:06:04.059Z  INFO 1 --- [user-portal] [main] com.portal.UserPortalApplication - Started UserPortalApplication in 20.495 seconds
```

## Result

✅ 任务详情页面的"发起人"和"当前处理人"现在显示用户名称而不是ID
✅ 流转记录中的"Assignee"现在显示用户名称而不是ID
✅ "My Applications"列表的"Current Assignee"列现在显示用户名称而不是ID
✅ 用户名称按照优先级正确解析（fullName > displayName > username > userId）
✅ 没有活动任务的流程实例现在正确显示空的 Current Assignee（而不是用户ID）

## Related Issues
- Task 15: Fix Form Data Display and Approval Status Auto-Set

## Completion Date
2026-02-05 17:06 (Beijing Time)
