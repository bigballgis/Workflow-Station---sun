# Digital Lending - Action Button Implementation Solution

## Problem Summary

User Portal task detail page shows generic buttons (Approve, Reject, Delegate, Transfer, Urge) instead of BPMN-defined action buttons (Verify Documents, Approve Loan, Reject Loan, Request Additional Info).

## Root Cause

**The User Portal backend API does not return task actions.**

- `TaskInfo` DTO lacks an `actions` field
- No service exists to parse BPMN and extract action bindings
- Frontend has no data to render custom action buttons

## Complete Solution

### Step 1: Create TaskActionInfo DTO

**File**: `backend/user-portal/src/main/java/com/portal/dto/TaskActionInfo.java`

```java
package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskActionInfo {
    private Long actionId;
    private String actionName;
    private String actionType;  // APPROVE, REJECT, FORM_POPUP, etc.
    private String description;
    private Long formId;  // For FORM_POPUP actions
    private String formName;
}
```

### Step 2: Extend TaskInfo DTO

**File**: `backend/user-portal/src/main/java/com/portal/dto/TaskInfo.java`

Add this field:
```java
/** Available actions for this task */
private List<TaskActionInfo> actions;
```

### Step 3: Create TaskActionService

**File**: `backend/user-portal/src/main/java/com/portal/service/TaskActionService.java`

```java
package com.portal.service;

import com.portal.dto.TaskActionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActionService {
    
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final RestTemplate restTemplate;
    
    public List<TaskActionInfo> getTaskActions(String taskId) {
        try {
            // 1. Get task from Flowable
            Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (task == null) {
                log.warn("Task not found: {}", taskId);
                return Collections.emptyList();
            }
            
            // 2. Get BPMN model
            BpmnModel bpmnModel = repositoryService.getBpmnModel(
                task.getProcessDefinitionId()
            );
            
            // 3. Find userTask element
            UserTask userTask = (UserTask) bpmnModel.getFlowElement(
                task.getTaskDefinitionKey()
            );
            
            if (userTask == null) {
                log.warn("UserTask not found in BPMN: {}", task.getTaskDefinitionKey());
                return Collections.emptyList();
            }
            
            // 4. Extract actionIds from extensionElements
            List<Long> actionIds = extractActionIds(userTask);
            
            if (actionIds.isEmpty()) {
                log.info("No actions defined for task: {}", taskId);
                return Collections.emptyList();
            }
            
            // 5. Fetch action definitions from Developer Workstation
            return fetchActionDefinitions(actionIds);
            
        } catch (Exception e) {
            log.error("Error getting task actions for task: " + taskId, e);
            return Collections.emptyList();
        }
    }
    
    private List<Long> extractActionIds(UserTask userTask) {
        Map<String, List<ExtensionElement>> extensions = 
            userTask.getExtensionElements();
        
        if (extensions == null || extensions.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ExtensionElement> properties = extensions.get("properties");
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyList();
        }
        
        for (ExtensionElement prop : properties) {
            Map<String, List<ExtensionElement>> childElements = 
                prop.getChildElements();
            
            if (childElements == null) {
                continue;
            }
            
            List<ExtensionElement> customProps = childElements.get("property");
            if (customProps == null) {
                continue;
            }
            
            for (ExtensionElement customProp : customProps) {
                String name = customProp.getAttributeValue(null, "name");
                if ("actionIds".equals(name)) {
                    String value = customProp.getAttributeValue(null, "value");
                    return parseActionIds(value);
                }
            }
        }
        
        return Collections.emptyList();
    }
    
    private List<Long> parseActionIds(String value) {
        try {
            // Remove brackets and whitespace: "[12,22]" -> "12,22"
            String cleaned = value.replaceAll("[\\[\\]\\s]", "");
            
            if (cleaned.isEmpty()) {
                return Collections.emptyList();
            }
            
            return Arrays.stream(cleaned.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error parsing actionIds: " + value, e);
            return Collections.emptyList();
        }
    }
    
    private List<TaskActionInfo> fetchActionDefinitions(List<Long> actionIds) {
        // Call Developer Workstation API to get action definitions
        String url = "http://admin-center:8080/api/v1/admin/actions/batch?ids=" + 
            actionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        try {
            // Assuming Developer Workstation has this API
            // If not, you'll need to create it
            ActionDefinitionResponse[] actions = restTemplate.getForObject(
                url, 
                ActionDefinitionResponse[].class
            );
            
            if (actions == null) {
                return Collections.emptyList();
            }
            
            return Arrays.stream(actions)
                .map(this::toTaskActionInfo)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error fetching action definitions", e);
            return Collections.emptyList();
        }
    }
    
    private TaskActionInfo toTaskActionInfo(ActionDefinitionResponse action) {
        return TaskActionInfo.builder()
            .actionId(action.getId())
            .actionName(action.getActionName())
            .actionType(action.getActionType())
            .description(action.getDescription())
            .formId(action.getFormId())
            .formName(action.getFormName())
            .build();
    }
    
    // Helper class for API response
    @Data
    private static class ActionDefinitionResponse {
        private Long id;
        private String actionName;
        private String actionType;
        private String description;
        private Long formId;
        private String formName;
    }
}
```

### Step 4: Update TaskQueryComponent

**File**: `backend/user-portal/src/main/java/com/portal/component/TaskQueryComponent.java`

Add injection and update method:

```java
@Autowired
private TaskActionService taskActionService;

public Optional<TaskInfo> getTaskById(String taskId) {
    // ... existing code ...
    
    // Add actions
    List<TaskActionInfo> actions = taskActionService.getTaskActions(taskId);
    taskInfo.setActions(actions);
    
    return Optional.of(taskInfo);
}
```

### Step 5: Update Frontend

**File**: `frontend/user-portal/src/views/tasks/detail.vue` (or similar)

```vue
<template>
  <div class="task-detail">
    <!-- ... existing task info ... -->
    
    <!-- Action Buttons -->
    <div class="action-buttons">
      <template v-if="task.actions && task.actions.length > 0">
        <!-- Custom actions from BPMN -->
        <el-button
          v-for="action in task.actions"
          :key="action.actionId"
          :type="getButtonType(action.actionType)"
          @click="handleCustomAction(action)"
        >
          {{ action.actionName }}
        </el-button>
      </template>
      
      <template v-else>
        <!-- Fallback generic buttons -->
        <el-button type="success" @click="handleApprove">Approve</el-button>
        <el-button type="danger" @click="handleReject">Reject</el-button>
        <el-button @click="handleDelegate">Delegate</el-button>
        <el-button @click="handleTransfer">Transfer</el-button>
        <el-button @click="handleUrge">Urge</el-button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { completeTask } from '@/api/task';

const task = ref({});

const getButtonType = (actionType) => {
  const typeMap = {
    'APPROVE': 'success',
    'REJECT': 'danger',
    'FORM_POPUP': 'primary',
    'API_CALL': 'info',
    'WITHDRAW': 'warning'
  };
  return typeMap[actionType] || 'default';
};

const handleCustomAction = async (action) => {
  if (action.actionType === 'FORM_POPUP') {
    // Open form dialog
    openFormDialog(action.formId, action.formName);
  } else {
    // Complete task with this action
    try {
      await completeTask(task.value.taskId, {
        actionId: action.actionId,
        actionName: action.actionName,
        variables: {}
      });
      ElMessage.success('Task completed successfully');
      // Navigate back or refresh
    } catch (error) {
      ElMessage.error('Failed to complete task');
    }
  }
};
</script>
```

## Testing

After implementation:

1. Start a new Digital Lending process
2. Open the "Verify Documents" task
3. Should see 4 buttons:
   - Verify Documents
   - Approve Loan
   - Reject Loan
   - Request Additional Info

## Status

- ✅ BPMN contains correct action bindings
- ✅ Actions exist in database
- ❌ Backend API implementation needed
- ❌ Frontend integration needed

---

**Date**: 2026-02-05  
**Priority**: High  
**Estimated Effort**: 4-6 hours
