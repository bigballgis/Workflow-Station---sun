# Design Document: Task Initiator Display

## Overview

This feature adds the ability to display and filter by process initiator (the user who started the workflow) in the task list. The implementation spans three layers:

1. **Workflow Engine Core** - Retrieve initiator ID from Flowable and resolve display name via admin-center
2. **User Portal Backend** - Pass through initiator information in task queries
3. **User Portal Frontend** - Display initiator column and support filtering

## Architecture

```
┌─────────────────────┐     HTTP      ┌──────────────────────┐     Flowable API    ┌─────────────┐
│   user-portal       │ ────────────> │ workflow-engine-core │ ─────────────────> │   Flowable  │
│   (Frontend)        │               │                      │                     │   Engine    │
│                     │               │                      │                     │             │
│ - Task List View    │               │ - TaskController     │     Admin API       │             │
│ - Initiator Column  │               │ - TaskManagerComponent ───────────────────>│ admin-center│
│ - Filter by Initiator               │                      │                     │             │
└─────────────────────┘               └──────────────────────┘                     └─────────────┘
```

## Components and Interfaces

### 1. Workflow Engine Core Changes

#### TaskListResult.TaskInfo (DTO)
Add two new fields to the existing TaskInfo class:

```java
// backend/workflow-engine-core/src/main/java/com/workflow/dto/response/TaskListResult.java
@Data
@Builder
public static class TaskInfo {
    // ... existing fields ...
    
    /**
     * 流程发起人ID
     */
    private String initiatorId;
    
    /**
     * 流程发起人名称
     */
    private String initiatorName;
}
```

#### TaskManagerComponent
Modify `convertFlowableTaskToTaskInfo` method to retrieve initiator information:

```java
// backend/workflow-engine-core/src/main/java/com/workflow/component/TaskManagerComponent.java
private TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(Task task) {
    // ... existing code ...
    
    // Get initiator from process instance
    String initiatorId = null;
    String initiatorName = null;
    
    if (task.getProcessInstanceId() != null) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .singleResult();
        
        if (processInstance != null) {
            initiatorId = processInstance.getStartUserId();
            if (initiatorId != null) {
                initiatorName = resolveUserDisplayName(initiatorId);
            }
        }
    }
    
    return TaskListResult.TaskInfo.builder()
        // ... existing fields ...
        .initiatorId(initiatorId)
        .initiatorName(initiatorName)
        .build();
}

private String resolveUserDisplayName(String userId) {
    try {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(userId);
        if (userInfo != null) {
            String displayName = (String) userInfo.get("displayName");
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
            // Fallback to username
            String username = (String) userInfo.get("username");
            if (username != null) {
                return username;
            }
        }
    } catch (Exception e) {
        log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
    }
    return userId; // Return userId as fallback
}
```

### 2. User Portal Backend Changes

#### TaskQueryComponent
Update `convertMapToTaskInfo` to include initiator fields:

```java
// backend/user-portal/src/main/java/com/portal/component/TaskQueryComponent.java
private TaskInfo convertMapToTaskInfo(Map<String, Object> taskMap) {
    return TaskInfo.builder()
        // ... existing fields ...
        .initiatorId((String) taskMap.get("initiatorId"))
        .initiatorName((String) taskMap.get("initiatorName"))
        .build();
}
```

#### TaskInfo DTO
The existing TaskInfo already has `initiatorId` and `initiatorName` fields, no changes needed.

### 3. User Portal Frontend Changes

#### Task List View
Add initiator column to the table:

```vue
<!-- frontend/user-portal/src/views/tasks/index.vue -->
<el-table-column prop="initiatorName" :label="t('task.initiator')" width="120">
  <template #default="{ row }">
    {{ row.initiatorName || '-' }}
  </template>
</el-table-column>
```

#### i18n Locale Files
Add translations for the initiator label:

```typescript
// frontend/user-portal/src/i18n/locales/en.ts
task: {
  // ... existing keys ...
  initiator: 'Initiator',
}

// frontend/user-portal/src/i18n/locales/zh-CN.ts
task: {
  // ... existing keys ...
  initiator: '发起人',
}

// frontend/user-portal/src/i18n/locales/zh-TW.ts
task: {
  // ... existing keys ...
  initiator: '發起人',
}
```

#### TaskInfo Interface
Update the TypeScript interface:

```typescript
// frontend/user-portal/src/api/task.ts
export interface TaskInfo {
  // ... existing fields ...
  initiatorId: string
  initiatorName?: string
}
```

## Data Models

### Task Information Flow

```
Flowable ProcessInstance
    └── startUserId ─────────────────────────────────────────┐
                                                              │
admin-center User                                             │
    └── displayName ─────────────────────────────────────────┤
                                                              │
workflow-engine-core TaskListResult.TaskInfo                  │
    ├── initiatorId <─────────────────────────────────────────┘
    └── initiatorName <── resolved from admin-center
                                                              │
user-portal TaskInfo                                          │
    ├── initiatorId <─────────────────────────────────────────┘
    └── initiatorName
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Initiator Display Consistency
*For any* task returned by the workflow engine where the process instance has a start user, the task info SHALL include the initiatorId matching the process instance's startUserId.
**Validates: Requirements 2.1, 2.2**

### Property 2: Initiator Name Resolution
*For any* valid user ID in the system, resolving the display name SHALL return either the user's displayName, username, or the userId itself (in that order of preference).
**Validates: Requirements 2.3, 2.4**

### Property 3: Filter by Initiator Matching
*For any* task list and search keyword, filtering by initiator SHALL return only tasks where the initiatorName contains the keyword (case-insensitive).
**Validates: Requirements 3.1, 3.2**

### Property 4: Filter Reset Completeness
*For any* filtered task list, clearing the filter SHALL restore the complete unfiltered task list.
**Validates: Requirements 3.3**

### Property 5: Locale-Specific Label Display
*For any* supported locale (en, zh-CN, zh-TW), the initiator column label SHALL display the correct translation for that locale.
**Validates: Requirements 4.1, 4.2, 4.3, 4.4**

## Error Handling

| Scenario | Handling |
|----------|----------|
| Process instance not found | Return null for initiatorId and initiatorName |
| startUserId is null | Return null for initiatorId and initiatorName |
| admin-center unavailable | Return userId as initiatorName (fallback) |
| User not found in admin-center | Return userId as initiatorName (fallback) |

## Testing Strategy

### Unit Tests
- Test `resolveUserDisplayName` with valid user, invalid user, and admin-center unavailable scenarios
- Test `convertFlowableTaskToTaskInfo` includes initiator fields correctly
- Test frontend filter logic with various search keywords

### Property-Based Tests
- **Property 1**: Generate random process instances with startUserId, verify task queries return matching initiatorId
- **Property 2**: Generate random user IDs, verify name resolution returns appropriate fallback values
- **Property 3**: Generate random task lists and search keywords, verify filter results match expected behavior
- **Property 4**: Generate random filtered states, verify clearing filter restores original list
- **Property 5**: Generate random locale settings, verify correct label is displayed

### Integration Tests
- End-to-end test: Start process → Query tasks → Verify initiator displayed in frontend
- Test with real Flowable engine and admin-center integration

### Test Configuration
- Property-based tests: Minimum 100 iterations per property
- Use jqwik for Java property-based testing
- Use fast-check for TypeScript property-based testing
