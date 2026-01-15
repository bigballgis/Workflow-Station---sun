# Task Detail Page Access Fix - Design

## Architecture Overview

```
┌─────────────────────┐     HTTP      ┌──────────────────────┐     HTTP      ┌─────────────────────┐
│   user-portal       │ ────────────> │   user-portal        │ ────────────> │ workflow-engine-core│
│   (Frontend)        │               │   (Backend)          │               │                     │
│                     │               │                      │               │ + GlobalException   │
│ - detail.vue        │               │ - TaskController     │               │   Handler (NEW)     │
│ - Better error      │               │ - TaskQueryComponent │               │                     │
│   handling          │               │                      │               │                     │
└─────────────────────┘               └──────────────────────┘               └─────────────────────┘
```

## Component Design

### 1. GlobalExceptionHandler (workflow-engine-core)

New class to handle exceptions and return proper HTTP status codes.

```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(WorkflowValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(WorkflowValidationException e) {
        // Check if it's a "not found" error
        if (e.getErrors().stream().anyMatch(err -> 
            err.getMessage().contains("不存在") || err.getMessage().contains("not found"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(WorkflowBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(WorkflowBusinessException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
    }
}
```

### 2. Frontend Error Handling (detail.vue)

Update the task detail page to handle errors gracefully:

```typescript
// Separate loading states for each section
const taskLoading = ref(true)
const processLoading = ref(true)
const historyLoading = ref(true)

// Separate error states
const taskError = ref<string | null>(null)
const processError = ref<string | null>(null)
const historyError = ref<string | null>(null)

const loadTaskDetail = async () => {
  taskLoading.value = true
  taskError.value = null
  try {
    const res = await getTaskDetail(taskId)
    // ... handle success
  } catch (error: any) {
    if (error.response?.status === 404) {
      taskError.value = t('task.notFound')
    } else if (error.response?.status === 403) {
      taskError.value = t('task.noPermission')
    } else {
      taskError.value = t('common.serverError')
    }
  } finally {
    taskLoading.value = false
  }
}
```

### 3. i18n Translations

Add new translation keys:

```typescript
// zh-CN
task: {
  notFound: '任务不存在或已完成',
  noPermission: '您没有权限访问此任务',
  loadFailed: '加载失败',
  processLoadFailed: '流程图加载失败',
  formLoadFailed: '表单加载失败',
  historyLoadFailed: '流转记录加载失败'
}

// zh-TW
task: {
  notFound: '任務不存在或已完成',
  noPermission: '您沒有權限訪問此任務',
  loadFailed: '載入失敗',
  processLoadFailed: '流程圖載入失敗',
  formLoadFailed: '表單載入失敗',
  historyLoadFailed: '流轉記錄載入失敗'
}

// en
task: {
  notFound: 'Task not found or completed',
  noPermission: 'You do not have permission to access this task',
  loadFailed: 'Failed to load',
  processLoadFailed: 'Failed to load process diagram',
  formLoadFailed: 'Failed to load form',
  historyLoadFailed: 'Failed to load flow history'
}
```

## Error Response Mapping

| Exception Type | HTTP Status | Error Code | User Message |
|---------------|-------------|------------|--------------|
| WorkflowValidationException (not found) | 404 | NOT_FOUND | 任务不存在 |
| WorkflowValidationException (other) | 400 | VALIDATION_ERROR | 参数错误 |
| WorkflowBusinessException | 500 | {code} | {message} |
| Other Exception | 500 | INTERNAL_ERROR | 服务器内部错误 |

## Data Flow

### Task Detail Loading Sequence

```
1. User navigates to /tasks/{taskId}
2. Frontend calls GET /api/portal/tasks/{taskId}
3. user-portal backend calls workflow-engine-core GET /api/v1/tasks/{taskId}
4. workflow-engine-core queries Flowable TaskService
5. If task found: return TaskInfo
   If task not found: throw WorkflowValidationException
6. GlobalExceptionHandler converts exception to 404 response
7. Frontend shows appropriate error message
```

### Graceful Degradation Flow

```
1. Load task details (required)
   - Success: Continue to load other sections
   - Failure: Show error, stop loading other sections

2. Load function unit content (optional)
   - Success: Parse BPMN and form
   - Failure: Show "流程图加载失败" in process section

3. Load task history (optional)
   - Success: Display history records
   - Failure: Show "流转记录加载失败" in history section
```

## Testing Strategy

1. **Unit Tests**: Test GlobalExceptionHandler with different exception types
2. **Integration Tests**: Test full flow from frontend to backend
3. **Manual Tests**: 
   - Access non-existent task
   - Access completed task
   - Access task without permission
