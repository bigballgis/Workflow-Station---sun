# Implementation Plan: Role Assignment Targets

## Overview

This implementation plan covers the role assignment targets feature, which allows roles to be assigned to users, departments, department hierarchies, and virtual groups. The implementation is divided into backend (shared module + admin-center) and frontend phases.

## Tasks

- [x] 1. Create shared module components (platform-security)
  - [x] 1.1 Create AssignmentTargetType enum
    - Define enum with USER, DEPARTMENT, DEPARTMENT_HIERARCHY, VIRTUAL_GROUP values
    - _Requirements: 1.1_

  - [x] 1.2 Create RoleAssignment entity
    - Create entity with role, targetType, targetId, includeDescendants fields
    - Add JPA annotations and audit fields
    - _Requirements: 2.1_

  - [x] 1.3 Create RoleAssignmentRepository
    - Add methods for finding by roleId, targetType, targetId
    - Add unique constraint check method
    - _Requirements: 2.4, 2.5_

  - [x] 1.4 Create database migration script
    - Create sys_role_assignments table
    - Add indexes and unique constraint
    - _Requirements: 2.1_

- [x] 2. Implement target resolvers
  - [x] 2.1 Create TargetResolver interface
    - Define methods: targetExists, resolveUsers, getTargetDisplayName
    - _Requirements: 2.2_

  - [x] 2.2 Implement UserTargetResolver
    - Resolve single user by ID
    - _Requirements: 1.2_

  - [x] 2.3 Implement DepartmentTargetResolver
    - Resolve all users in a specific department
    - _Requirements: 1.3_

  - [x] 2.4 Implement DepartmentHierarchyTargetResolver
    - Resolve all users in department and descendants using path prefix
    - _Requirements: 1.4_

  - [x] 2.5 Implement VirtualGroupTargetResolver
    - Resolve all active members of a virtual group
    - _Requirements: 1.5_

  - [x] 2.6 Write property tests for target resolvers
    - **Property 1: USER Assignment Grants Role to Single User**
    - **Property 2: DEPARTMENT Assignment Grants Role to Department Members**
    - **Property 3: DEPARTMENT_HIERARCHY Assignment Grants Role to Hierarchy Members**
    - **Property 4: VIRTUAL_GROUP Assignment Grants Role to Group Members**
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.5**

- [x] 3. Implement UserRoleService (shared module)
  - [x] 3.1 Create UserRoleService interface
    - Define getEffectiveRolesForUser method
    - _Requirements: 7.1_

  - [x] 3.2 Implement UserRoleServiceImpl
    - Query all assignment types for user
    - Merge and deduplicate roles
    - Include source information
    - _Requirements: 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 3.3 Write property tests for UserRoleService
    - **Property 5: Effective Users Deduplication**
    - **Property 9: Dynamic Membership - User Gains Role**
    - **Property 10: Dynamic Membership - User Loses Role**
    - **Validates: Requirements 3.5, 4.1, 4.3**

- [x] 4. Checkpoint - Verify shared module
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement RoleAssignmentComponent (admin-center)
  - [x] 5.1 Create RoleAssignmentComponent
    - Implement createAssignment, deleteAssignment, getAssignmentsForRole
    - Implement getEffectiveUsers with sources
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1_

  - [x] 5.2 Implement assignment validation
    - Validate target exists
    - Check for duplicate assignments
    - _Requirements: 2.2, 2.3_

  - [x] 5.3 Write property tests for RoleAssignmentComponent ✓
    - **Property 6: Assignment Uniqueness** ✓
    - **Property 7: Target Validation** ✓
    - **Property 8: Assignment Deletion Removes Role Access** ✓
    - **Validates: Requirements 2.2, 2.3, 2.6**

- [x] 6. Implement API endpoints (admin-center)
  - [x] 6.1 Create RoleAssignmentController
    - POST /roles/{roleId}/assignments
    - GET /roles/{roleId}/assignments
    - DELETE /roles/{roleId}/assignments/{id}
    - GET /roles/{roleId}/effective-users
    - _Requirements: 2.1, 2.4, 2.5, 3.1_

  - [x] 6.2 Create DTOs
    - CreateAssignmentRequest, RoleAssignmentResponse
    - EffectiveUserResponse, RoleSource
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 7. Update login services for three backends
  - [x] 7.1 Update Admin Center AuthServiceImpl
    - Use UserRoleService for role calculation
    - Include rolesWithSources in response
    - _Requirements: 8.1_

  - [x] 7.2 Update User Portal AuthServiceImpl
    - Use UserRoleService for role calculation
    - Include rolesWithSources in response
    - _Requirements: 8.2_

  - [x] 7.3 Update Developer Workstation AuthServiceImpl
    - Use UserRoleService for role calculation
    - Include rolesWithSources in response
    - _Requirements: 8.3_

  - [x] 7.4 Update LoginResponse DTOs in all three backends
    - Add rolesWithSources field
    - _Requirements: 7.7, 8.5_

- [x] 8. Checkpoint - Verify backend integration
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement frontend UI (admin-center)
  - [x] 9.1 Create role assignment API module
    - Add API calls for assignments CRUD
    - Add API call for effective users
    - _Requirements: 6.1_

  - [x] 9.2 Update RoleMembersDialog component
    - Add two tabs: Assignment Records and Effective Users
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 9.3 Create AssignmentRecordsList component
    - Display assignment records with target type, name, time, count
    - Add delete button with confirmation
    - _Requirements: 6.2, 6.8_

  - [x] 9.4 Create EffectiveUsersList component
    - Display users with role sources
    - Show source type and name for each user
    - _Requirements: 6.3_

  - [x] 9.5 Create AddAssignmentDialog component
    - Target type selector
    - Department tree picker for DEPARTMENT/DEPARTMENT_HIERARCHY
    - Virtual group selector for VIRTUAL_GROUP
    - User selector for USER
    - _Requirements: 6.4, 6.5, 6.6, 6.7_

- [x] 10. Update frontend login handling
  - [x] 10.1 Update Admin Center auth types
    - Add RoleWithSource type
    - Update UserInfo type with rolesWithSources
    - _Requirements: 7.7_

  - [x] 10.2 Update User Portal auth types
    - Add RoleWithSource type
    - Update UserInfo type with rolesWithSources
    - _Requirements: 7.7_

  - [x] 10.3 Update Developer Workstation auth types
    - Add RoleWithSource type
    - Update UserInfo type with rolesWithSources
    - _Requirements: 7.7_

- [x] 11. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks including property-based tests are required
- The shared module (platform-security) must be implemented first as other modules depend on it
- Database migration should be run before testing backend changes
- Frontend changes can be developed in parallel with backend API implementation
