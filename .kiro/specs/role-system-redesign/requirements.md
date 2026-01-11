# Requirements Document

## Introduction

重新设计 Admin Center 的角色体系，将角色分为三大类：业务角色、管理角色和开发角色。每类角色有不同的用途和权限范围，以支持 User Portal、Admin Center 和 Developer Workstation 三个平台的访问控制。

## Glossary

- **Business_Role**: 业务角色，用于 User Portal 中的用户管理，用户可以申请加入组织结构中的角色或通过虚拟组获取
- **Admin_Role**: 管理角色，用于 Admin Center 的管理功能，默认只有系统管理员角色
- **Developer_Role**: 开发角色，用于 Developer Workstation 的功能权限控制
- **User_Portal**: 用户门户，普通用户使用的系统
- **Admin_Center**: 管理中心，系统管理员使用的后台管理系统
- **Developer_Workstation**: 开发者工作站，开发人员使用的功能开发平台
- **Virtual_Group**: 虚拟组，可以绑定一个或多个角色
- **Function_Unit**: 功能单元，开发者工作站中创建的功能包

## Requirements

### Requirement 1: 角色分类体系

**User Story:** As a 系统架构师, I want 角色按照用途分为三大类, so that 不同平台的访问控制可以独立管理。

#### Acceptance Criteria

1. THE Role_System SHALL support three role categories: BUSINESS, ADMIN, and DEVELOPER
2. WHEN a role is created, THE Role_System SHALL require specifying one of the three categories
3. THE Role_System SHALL store the role category as an enumeration field

### Requirement 2: 业务角色管理

**User Story:** As a User Portal 用户, I want 申请加入业务角色, so that 我可以获得相应的业务权限。

#### Acceptance Criteria

1. THE Business_Role SHALL be used exclusively for User Portal user management
2. THE Business_Role SHALL NOT require specific permission configuration
3. WHEN a user joins an organization structure, THE User_Portal SHALL allow the user to apply for one or more Business_Roles
4. WHEN a user joins a Virtual_Group, THE User_Portal SHALL automatically grant the user all Business_Roles bound to that Virtual_Group
5. THE Business_Role SHALL support binding to organization departments
6. THE Business_Role SHALL support binding to Virtual_Groups

### Requirement 3: 管理角色管理

**User Story:** As a 系统管理员, I want 使用管理角色访问 Admin Center, so that 我可以管理整个系统。

#### Acceptance Criteria

1. THE Admin_Role SHALL have only one default role: System Administrator (系统管理员)
2. THE System_Administrator role SHALL have access to all Admin Center functions
3. THE Admin_Role SHALL be a system-level role that cannot be deleted
4. THE Admin_Role SHALL NOT be assignable through Virtual_Group binding
5. WHEN a user has the System_Administrator role, THE Admin_Center SHALL grant full access to all management functions

### Requirement 4: 开发角色管理

**User Story:** As a 开发团队成员, I want 根据我的开发角色获得相应的开发权限, so that 我可以在开发者工作站中执行允许的操作。

#### Acceptance Criteria

1. THE Developer_Role SHALL have three predefined roles: Technical Director (技术主管), Team Leader (技术组长), and Developer (开发工程师)
2. THE Technical_Director role SHALL have access to all Developer Workstation functions
3. THE Team_Leader role SHALL be able to create and manage Function_Units
4. THE Developer role SHALL only be able to develop existing Function_Units
5. THE Developer role SHALL NOT be able to create or delete Function_Units
6. WHEN a user has a Developer_Role, THE Developer_Workstation SHALL enforce the corresponding permission restrictions

### Requirement 5: 角色权限映射

**User Story:** As a 系统架构师, I want 开发角色有明确的权限定义, so that 系统可以自动执行权限控制。

#### Acceptance Criteria

1. THE Developer_Workstation SHALL define permission codes for each operation type
2. THE Technical_Director role SHALL have all permission codes
3. THE Team_Leader role SHALL have permission codes for: create_function_unit, update_function_unit, delete_function_unit, view_function_unit, develop_function_unit
4. THE Developer role SHALL have permission codes for: view_function_unit, develop_function_unit
5. WHEN a user attempts an operation, THE Developer_Workstation SHALL check if the user's role has the required permission code

### Requirement 6: 角色数据初始化

**User Story:** As a 系统部署人员, I want 系统自动创建默认角色, so that 系统可以开箱即用。

#### Acceptance Criteria

1. WHEN the system initializes, THE Role_System SHALL create the System Administrator role with category ADMIN
2. WHEN the system initializes, THE Role_System SHALL create Technical Director, Team Leader, and Developer roles with category DEVELOPER
3. THE default roles SHALL be marked as system roles that cannot be deleted
4. THE default roles SHALL have predefined permission configurations

### Requirement 7: 功能单元访问权限分配

**User Story:** As a 系统管理员, I want 将功能单元分配给业务角色, so that 拥有该角色的用户可以在 User Portal 中发起相应流程。

#### Acceptance Criteria

1. THE Function_Unit access SHALL only be assignable to Business_Roles
2. THE Function_Unit access SHALL NOT be assignable to Virtual_Groups, Departments, or individual Users
3. WHEN a Function_Unit is assigned to a Business_Role, THE User_Portal SHALL allow users with that role to initiate the corresponding workflow
4. WHEN a Function_Unit is NOT assigned to any Business_Role, THE User_Portal SHALL hide the Function_Unit from all users
5. WHEN a user does NOT have any Business_Role with access to a Function_Unit, THE User_Portal SHALL NOT display that Function_Unit to the user
6. THE Function_Unit availability in User_Portal SHALL be determined solely by the user's Business_Roles

### Requirement 8: 角色类型枚举更新

**User Story:** As a 开发人员, I want 角色类型枚举反映新的分类体系, so that 代码可以正确处理不同类型的角色。

#### Acceptance Criteria

1. THE RoleType enumeration SHALL include: BUSINESS, ADMIN, DEVELOPER
2. THE existing SYSTEM, FUNCTIONAL, TEMPORARY types SHALL be deprecated or removed
3. WHEN migrating existing data, THE Role_System SHALL map old role types to new categories appropriately

### Requirement 9: 简化功能单元访问配置

**User Story:** As a 系统管理员, I want 功能单元访问配置只支持角色分配, so that 权限管理更加简洁清晰。

#### Acceptance Criteria

1. THE FunctionUnitAccessType enumeration SHALL only include ROLE type
2. THE Function_Unit access configuration UI SHALL only show Business_Roles as selectable targets
3. WHEN configuring Function_Unit access, THE Admin_Center SHALL filter and display only Business_Roles
4. THE existing DEPARTMENT, DEPARTMENT_WITH_CHILDREN, VIRTUAL_GROUP access types SHALL be removed
