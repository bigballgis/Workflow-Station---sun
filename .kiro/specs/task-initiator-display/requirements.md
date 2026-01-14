# Requirements Document

## Introduction

在待办任务列表中显示流程发起人信息，使用户能够快速识别任务来源，并支持按发起人筛选任务。当前待办任务列表缺少发起人信息，用户无法直观了解任务是由谁发起的，影响任务处理效率。

## Glossary

- **Task_List**: 用户门户中的待办任务列表页面
- **Initiator**: 流程发起人，即启动工作流程的用户
- **Workflow_Engine**: 基于 Flowable 的工作流引擎核心服务
- **User_Portal**: 面向最终用户的工作流门户前端应用
- **TaskInfo**: 任务信息数据传输对象

## Requirements

### Requirement 1: Display Initiator in Task List

**User Story:** As a task handler, I want to see who initiated the process for each task, so that I can quickly identify the task source and prioritize my work.

#### Acceptance Criteria

1. WHEN a user views the task list, THE Task_List SHALL display the initiator name for each task
2. WHEN the initiator information is available, THE Task_List SHALL show the initiator name in a dedicated column
3. WHEN the initiator information is not available, THE Task_List SHALL display a placeholder text (e.g., "-")
4. THE Task_List SHALL display the initiator column between the "Assignment Type" and "Priority" columns

### Requirement 2: Backend Initiator Data Support

**User Story:** As a system, I want to retrieve and provide initiator information for tasks, so that the frontend can display this information.

#### Acceptance Criteria

1. WHEN querying tasks from Flowable, THE Workflow_Engine SHALL retrieve the process instance's start user ID
2. WHEN returning task information, THE Workflow_Engine SHALL include initiatorId and initiatorName fields in the TaskInfo response
3. WHEN the initiator user exists in the system, THE Workflow_Engine SHALL resolve the initiator's display name from admin-center
4. IF the initiator information cannot be retrieved, THEN THE Workflow_Engine SHALL return null for initiatorId and initiatorName fields

### Requirement 3: Filter Tasks by Initiator

**User Story:** As a task handler, I want to filter tasks by initiator, so that I can focus on tasks from specific users.

#### Acceptance Criteria

1. WHEN a user enters an initiator name in the search field, THE Task_List SHALL filter tasks to show only those matching the initiator name
2. WHEN filtering by initiator, THE Task_List SHALL perform case-insensitive partial matching on the initiator name
3. WHEN the initiator filter is cleared, THE Task_List SHALL show all tasks again

### Requirement 4: Internationalization Support

**User Story:** As a user, I want the initiator column label to be displayed in my preferred language, so that I can understand the interface.

#### Acceptance Criteria

1. THE Task_List SHALL display the initiator column label in the user's selected language (English, Simplified Chinese, Traditional Chinese)
2. WHEN the language is English, THE Task_List SHALL display "Initiator" as the column label
3. WHEN the language is Simplified Chinese, THE Task_List SHALL display "发起人" as the column label
4. WHEN the language is Traditional Chinese, THE Task_List SHALL display "發起人" as the column label
