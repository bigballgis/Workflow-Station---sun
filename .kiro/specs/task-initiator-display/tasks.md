# Implementation Plan: Task Initiator Display

## Overview

This implementation adds initiator (process starter) information to the task list, enabling users to see who started each workflow and filter tasks by initiator. The implementation follows a bottom-up approach: backend changes first, then frontend.

## Tasks

- [x] 1. Add initiator fields to workflow-engine-core TaskListResult.TaskInfo
  - Add `initiatorId` (String) field
  - Add `initiatorName` (String) field
  - _Requirements: 2.2_

- [x] 2. Implement initiator retrieval in TaskManagerComponent
  - [x] 2.1 Add method to resolve user display name from admin-center
    - Create `resolveUserDisplayName(String userId)` method
    - Query admin-center for user info
    - Return displayName, fallback to username, then userId
    - _Requirements: 2.3, 2.4_
  
  - [x] 2.2 Modify `convertFlowableTaskToTaskInfo` to include initiator
    - Query ProcessInstance by processInstanceId
    - Get startUserId from ProcessInstance
    - Resolve initiator display name
    - Set initiatorId and initiatorName in TaskInfo
    - _Requirements: 2.1, 2.2_
  
  - [x] 2.3 Write property test for initiator retrieval
    - **Property 1: Initiator Display Consistency**
    - **Validates: Requirements 2.1, 2.2**

- [x] 3. Update user-portal backend TaskQueryComponent
  - [x] 3.1 Update `convertMapToTaskInfo` to include initiator fields
    - Extract initiatorId from task map
    - Extract initiatorName from task map
    - _Requirements: 2.2_
  
  - [x] 3.2 Update keyword filter to include initiator name matching
    - Add initiatorName to keyword search fields
    - Implement case-insensitive partial matching
    - _Requirements: 3.1, 3.2_
  
  - [x] 3.3 Write property test for initiator filter
    - **Property 3: Filter by Initiator Matching**
    - **Validates: Requirements 3.1, 3.2**

- [x] 4. Checkpoint - Backend verification
  - Ensure all backend tests pass
  - Verify API returns initiator information
  - Ask the user if questions arise

- [x] 5. Add i18n translations for initiator label
  - [x] 5.1 Add English translation
    - Add `initiator: 'Initiator'` to en.ts
    - _Requirements: 4.2_
  
  - [x] 5.2 Add Simplified Chinese translation
    - Add `initiator: '发起人'` to zh-CN.ts
    - _Requirements: 4.3_
  
  - [x] 5.3 Add Traditional Chinese translation
    - Add `initiator: '發起人'` to zh-TW.ts
    - _Requirements: 4.4_

- [x] 6. Update frontend task list to display initiator
  - [x] 6.1 Update TaskInfo interface in task.ts
    - Add initiatorId field
    - Add initiatorName field (optional)
    - _Requirements: 2.2_
  
  - [x] 6.2 Add initiator column to task list table
    - Add el-table-column for initiator
    - Display initiatorName or '-' placeholder
    - Position between assignmentType and priority columns
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 7. Final checkpoint - Full integration verification
  - Ensure all tests pass
  - Verify initiator displays correctly in UI
  - Verify filter by initiator works
  - Ask the user if questions arise

## Notes

- All tasks are required for comprehensive implementation
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- The implementation follows the existing architecture patterns in the codebase
