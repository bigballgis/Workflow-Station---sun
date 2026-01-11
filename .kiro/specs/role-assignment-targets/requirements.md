# Requirements Document

## Introduction

This feature extends the role assignment system to support four types of assignment targets:
1. Individual users (existing functionality)
2. A specific department level (all users in that department)
3. A department and all its descendants (hierarchical assignment)
4. Virtual groups (all members of a virtual group)

This enables administrators to efficiently assign roles to groups of users based on organizational structure or virtual groupings, rather than assigning roles one user at a time.

## Glossary

- **Role_Assignment_System**: The system component responsible for managing role-to-target assignments
- **Assignment_Target**: The entity to which a role is assigned (user, department, department hierarchy, or virtual group)
- **Target_Type**: The classification of assignment target (USER, DEPARTMENT, DEPARTMENT_HIERARCHY, VIRTUAL_GROUP)
- **Department_Hierarchy**: A department and all its descendant departments in the organizational tree
- **Effective_User**: A user who has a role through any assignment type (direct or inherited)
- **Role_Assignment_Record**: A database record representing a role assignment to a target

## Requirements

### Requirement 1: Role Assignment Target Types

**User Story:** As a system administrator, I want to assign roles to different types of targets, so that I can efficiently manage permissions for groups of users.

#### Acceptance Criteria

1. THE Role_Assignment_System SHALL support four target types: USER, DEPARTMENT, DEPARTMENT_HIERARCHY, and VIRTUAL_GROUP
2. WHEN a role is assigned to a USER target, THE Role_Assignment_System SHALL grant the role only to that specific user
3. WHEN a role is assigned to a DEPARTMENT target, THE Role_Assignment_System SHALL grant the role to all users currently in that department
4. WHEN a role is assigned to a DEPARTMENT_HIERARCHY target, THE Role_Assignment_System SHALL grant the role to all users in that department and all descendant departments
5. WHEN a role is assigned to a VIRTUAL_GROUP target, THE Role_Assignment_System SHALL grant the role to all members of that virtual group

### Requirement 2: Role Assignment Management

**User Story:** As a system administrator, I want to create, view, and delete role assignments, so that I can manage who has access to what.

#### Acceptance Criteria

1. WHEN creating a role assignment, THE Role_Assignment_System SHALL require a role ID, target type, and target ID
2. WHEN creating a role assignment, THE Role_Assignment_System SHALL validate that the target exists
3. WHEN creating a duplicate assignment (same role, target type, and target ID), THE Role_Assignment_System SHALL reject the request with an appropriate error
4. THE Role_Assignment_System SHALL allow viewing all assignments for a specific role
5. THE Role_Assignment_System SHALL allow deleting an assignment by its ID
6. WHEN an assignment is deleted, THE Role_Assignment_System SHALL remove the role from all affected users

### Requirement 3: Effective Role Calculation

**User Story:** As a system administrator, I want to see which users effectively have a role, so that I can audit access permissions.

#### Acceptance Criteria

1. THE Role_Assignment_System SHALL calculate effective users by combining all assignment types
2. WHEN calculating effective users for a DEPARTMENT assignment, THE Role_Assignment_System SHALL include only users directly in that department
3. WHEN calculating effective users for a DEPARTMENT_HIERARCHY assignment, THE Role_Assignment_System SHALL include users in the target department and all descendant departments
4. WHEN calculating effective users for a VIRTUAL_GROUP assignment, THE Role_Assignment_System SHALL include all active members of the virtual group
5. THE Role_Assignment_System SHALL return a deduplicated list of effective users (a user appears once even if matched by multiple assignments)

### Requirement 4: Dynamic Membership Updates

**User Story:** As a system administrator, I want role assignments to automatically reflect organizational changes, so that permissions stay current without manual intervention.

#### Acceptance Criteria

1. WHEN a user is added to a department with a DEPARTMENT or DEPARTMENT_HIERARCHY assignment, THE user SHALL automatically gain the assigned roles
2. WHEN a user is removed from a department with a DEPARTMENT or DEPARTMENT_HIERARCHY assignment, THE user SHALL automatically lose the assigned roles (unless granted through another assignment)
3. WHEN a user is added to a virtual group with a VIRTUAL_GROUP assignment, THE user SHALL automatically gain the assigned roles
4. WHEN a user is removed from a virtual group with a VIRTUAL_GROUP assignment, THE user SHALL automatically lose the assigned roles (unless granted through another assignment)
5. WHEN a department is moved in the hierarchy, THE Role_Assignment_System SHALL recalculate affected DEPARTMENT_HIERARCHY assignments

### Requirement 5: Assignment Audit Trail

**User Story:** As an auditor, I want to see the history of role assignments, so that I can track permission changes over time.

#### Acceptance Criteria

1. WHEN a role assignment is created, THE Role_Assignment_System SHALL record the assignment details, timestamp, and operator
2. WHEN a role assignment is deleted, THE Role_Assignment_System SHALL record the deletion details, timestamp, and operator
3. THE Role_Assignment_System SHALL provide an API to query assignment history for a specific role

### Requirement 6: User Interface for Role Assignment

**User Story:** As a system administrator, I want a user-friendly interface to manage role assignments, so that I can efficiently configure permissions.

#### Acceptance Criteria

1. WHEN viewing role members, THE UI SHALL display two sections: assignment records and effective users
2. THE assignment records section SHALL show target type, target name, assignment time, operator, and affected user count
3. THE effective users section SHALL show all users who have the role and their role sources
4. THE UI SHALL provide a form to create new assignments with target type selection
5. WHEN DEPARTMENT or DEPARTMENT_HIERARCHY is selected, THE UI SHALL show a department tree picker
6. WHEN VIRTUAL_GROUP is selected, THE UI SHALL show a virtual group selector
7. WHEN USER is selected, THE UI SHALL show a user search/selector
8. THE UI SHALL allow deleting assignments with confirmation

### Requirement 7: Login Integration

**User Story:** As a user, I want to automatically receive all roles assigned to me through any method, so that I have the correct permissions when I log in.

#### Acceptance Criteria

1. WHEN a user logs in, THE Role_Assignment_System SHALL calculate all effective roles for that user
2. THE effective roles calculation SHALL include roles directly assigned to the user (USER type)
3. THE effective roles calculation SHALL include roles assigned to the user's department (DEPARTMENT type)
4. THE effective roles calculation SHALL include roles assigned to any ancestor department (DEPARTMENT_HIERARCHY type)
5. THE effective roles calculation SHALL include roles assigned to any virtual group the user belongs to (VIRTUAL_GROUP type)
6. THE login response SHALL include the merged list of role codes and permissions
7. THE login response SHALL include role source information (rolesWithSources) for each role

### Requirement 8: Multi-Frontend Login Consistency

**User Story:** As a platform administrator, I want all three frontends to use the same role calculation logic, so that users have consistent permissions across the platform.

#### Acceptance Criteria

1. THE Admin_Center backend SHALL use the unified role calculation service for login
2. THE User_Portal backend SHALL use the unified role calculation service for login
3. THE Developer_Workstation backend SHALL use the unified role calculation service for login
4. THE role calculation service SHALL be implemented in a shared module (platform-security or platform-common)
5. WHEN a user logs in to any frontend, THE system SHALL return the same effective roles and permissions
