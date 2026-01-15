# Task Detail Page Access Fix

## Problem Statement

When opening a task detail page in user-portal, users see "没有权限访问" (No permission to access) error, and the process diagram, form, and flow history are not displayed.

## Root Cause Analysis

1. **Missing Exception Handler in workflow-engine-core**: When a task is not found in Flowable, `TaskManagerComponent.getTaskInfo()` throws `WorkflowValidationException`, but there's no global exception handler to convert this to a proper HTTP 404 response. This results in a 500 error.

2. **Frontend Error Handling**: The frontend request interceptor shows "没有权限访问" for all 403 errors, but the actual error might be 404 (task not found) or 500 (server error).

3. **Task Query Flow**: The task detail page calls multiple APIs:
   - `GET /tasks/{taskId}` - Get task details
   - `GET /processes/function-units/{processKey}/content` - Get BPMN and form definitions
   - `GET /tasks/{taskId}/history` - Get flow history

## User Stories

### US-1: Proper Error Responses from workflow-engine-core
As a developer, I want workflow-engine-core to return proper HTTP status codes for different error types, so that the frontend can display appropriate error messages.

**Acceptance Criteria:**
- [ ] 404 is returned when a task is not found
- [ ] 400 is returned for validation errors
- [ ] 500 is returned for unexpected server errors
- [ ] Error responses include meaningful error messages

### US-2: Better Error Messages in Frontend
As a user, I want to see clear error messages when something goes wrong on the task detail page, so that I understand what happened.

**Acceptance Criteria:**
- [ ] "任务不存在" is shown when task is not found (404)
- [ ] "没有权限访问" is shown only for actual permission errors (403)
- [ ] "服务器错误" is shown for server errors (500)
- [ ] Loading state is properly handled

### US-3: Graceful Degradation
As a user, I want the task detail page to show available information even if some parts fail to load, so that I can still see partial data.

**Acceptance Criteria:**
- [ ] If task details load but BPMN fails, show task info with "流程图加载失败" message
- [ ] If task details load but history fails, show task info with "流转记录加载失败" message
- [ ] Each section handles its own loading/error state independently

## Technical Requirements

1. Add `GlobalExceptionHandler` to workflow-engine-core module
2. Update frontend error handling in task detail page
3. Add proper i18n translations for error messages

## Files to Modify

### Backend (workflow-engine-core)
- `backend/workflow-engine-core/src/main/java/com/workflow/exception/GlobalExceptionHandler.java` (new)

### Frontend (user-portal)
- `frontend/user-portal/src/views/tasks/detail.vue`
- `frontend/user-portal/src/i18n/locales/zh-CN.ts`
- `frontend/user-portal/src/i18n/locales/zh-TW.ts`
- `frontend/user-portal/src/i18n/locales/en.ts`
