# Task Detail Page Access Fix - Implementation Tasks

## Task 1: Add GlobalExceptionHandler to workflow-engine-core
- [x] Create `GlobalExceptionHandler.java` in `backend/workflow-engine-core/src/main/java/com/workflow/exception/`
- [x] Handle `WorkflowValidationException` - return 404 for "not found" errors, 400 for other validation errors
- [x] Handle `WorkflowBusinessException` - return 500 with error code and message
- [x] Handle generic `Exception` - return 500 with generic error message
- [x] Add `@ControllerAdvice` annotation

**Requirements**: US-1

## Task 2: Add i18n translations for error messages
- [x] Add error message keys to `frontend/user-portal/src/i18n/locales/zh-CN.ts`
- [x] Add error message keys to `frontend/user-portal/src/i18n/locales/zh-TW.ts`
- [x] Add error message keys to `frontend/user-portal/src/i18n/locales/en.ts`
- [x] Keys added: `task.notFound`, `task.noPermission`, `task.loadFailed`, `task.processLoadFailed`, `task.formLoadFailed`, `task.historyLoadFailed`, `task.serverError`

**Requirements**: US-2

## Task 3: Update task detail page error handling
- [x] Add separate error states for each section (taskError, processError, historyError)
- [x] Update `loadTaskDetail` to handle 404 and 403 errors specifically
- [x] Update `loadFunctionUnitContent` to handle errors gracefully
- [x] Update `loadTaskHistory` to handle errors gracefully
- [x] Show appropriate error messages using i18n translations
- [x] Show partial content when some sections fail to load
- [x] Add error display UI with el-result and el-alert components

**Requirements**: US-2, US-3

## Task 4: Test the fix
- [x] Test accessing a non-existent task ID
- [x] Test accessing a completed task
- [x] Test normal task access
- [x] Verify error messages are displayed correctly in all 3 languages

## Implementation Summary

All tasks completed:
1. GlobalExceptionHandler created in workflow-engine-core to convert exceptions to proper HTTP responses
2. i18n translations added for error messages in all 3 locale files
3. Frontend detail.vue updated with proper error handling and display
4. Testing verified - the fix properly handles 404/403/500 errors
