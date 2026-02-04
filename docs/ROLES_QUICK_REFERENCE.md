# System Roles Quick Reference

## Overview
This document provides a quick reference for all system roles and their permissions.

## Role Categories

### 1. Administrative Roles

#### SYS_ADMIN (System Administrator)
- **Type**: ADMIN
- **Access**: Full system access
- **Permissions**: All administrative functions
- **Virtual Group**: SYSTEM_ADMINISTRATORS

#### AUDITOR (Auditor)
- **Type**: ADMIN
- **Access**: Read-only audit and monitoring
- **Permissions**: View audit logs, system monitoring
- **Virtual Group**: AUDITORS

### 2. Business Roles

#### MANAGER (Department Manager)
- **Type**: BU_BOUNDED (Business Unit Bounded)
- **Access**: Department-level management
- **Permissions**: Team workflow management, approvals
- **Virtual Group**: MANAGERS

### 3. Developer Workstation Roles

#### TECH_LEAD (Technical Lead)
- **Type**: DEVELOPER
- **Permissions**:
  - ✅ CREATE function units
  - ✅ EDIT function units
  - ✅ DELETE function units
  - ✅ DEPLOY function units
  - ✅ PUBLISH function units
- **Virtual Group**: TECH_LEADS
- **Description**: Full control over function unit lifecycle

#### TEAM_LEAD (Team Lead)
- **Type**: DEVELOPER
- **Permissions**:
  - ✅ CREATE function units
  - ✅ EDIT function units
  - ❌ DELETE function units
  - ✅ DEPLOY function units
  - ✅ PUBLISH function units
- **Virtual Group**: TEAM_LEADS
- **Description**: Can create and manage but cannot delete

#### DEVELOPER (Developer)
- **Type**: DEVELOPER
- **Permissions**:
  - ❌ CREATE function units
  - ✅ EDIT function units
  - ❌ DELETE function units
  - ✅ DEPLOY function units
  - ✅ PUBLISH function units
- **Virtual Group**: DEVELOPERS
- **Description**: Can only work with existing function units

## Permission Matrix

### Developer Workstation Permissions

| Role | Create | Edit | Delete | Deploy | Publish |
|------|:------:|:----:|:------:|:------:|:-------:|
| **TECH_LEAD** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **TEAM_LEAD** | ✅ | ✅ | ❌ | ✅ | ✅ |
| **DEVELOPER** | ❌ | ✅ | ❌ | ✅ | ✅ |

### Admin Center Access

| Role | Admin Center Access | Full Admin | Audit Only |
|------|:------------------:|:----------:|:----------:|
| **SYS_ADMIN** | ✅ | ✅ | ✅ |
| **AUDITOR** | ✅ | ❌ | ✅ |
| **MANAGER** | ❌ | ❌ | ❌ |
| **TECH_LEAD** | ❌ | ❌ | ❌ |
| **TEAM_LEAD** | ❌ | ❌ | ❌ |
| **DEVELOPER** | ❌ | ❌ | ❌ |

## Virtual Groups

| Virtual Group | Role Code | Description |
|--------------|-----------|-------------|
| SYSTEM_ADMINISTRATORS | SYS_ADMIN | System administrators with full access |
| AUDITORS | AUDITOR | System auditors with monitoring access |
| MANAGERS | MANAGER | Department managers |
| TECH_LEADS | TECH_LEAD | Technical leads with full function unit control |
| TEAM_LEADS | TEAM_LEAD | Team leads with create and manage permissions |
| DEVELOPERS | DEVELOPER | Developers with edit and deploy permissions |

## Role Assignment

### Direct Role Assignment
```sql
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES ('ur-unique-id', 'user-id', 'role-id', CURRENT_TIMESTAMP, 'admin');
```

### Virtual Group Membership
```sql
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, joined_by)
VALUES ('vgm-unique-id', 'vg-group-id', 'user-id', CURRENT_TIMESTAMP, 'admin');
```

## Common Use Cases

### Scenario 1: New Developer Joins Team
**Assign**: DEVELOPER role
- Can edit existing function units
- Can deploy and publish changes
- Cannot create new function units
- Cannot delete function units

### Scenario 2: Developer Promoted to Team Lead
**Assign**: TEAM_LEAD role
- Gains ability to create new function units
- Still cannot delete function units (safety measure)
- Retains all developer permissions

### Scenario 3: Team Lead Promoted to Technical Lead
**Assign**: TECH_LEAD role
- Gains ability to delete function units
- Full control over function unit lifecycle
- Retains all team lead permissions

### Scenario 4: Need Admin Center Access
**Assign**: SYS_ADMIN or AUDITOR role
- SYS_ADMIN: Full administrative access
- AUDITOR: Read-only audit and monitoring access

## Security Best Practices

1. **Principle of Least Privilege**
   - Assign the minimum role required for job function
   - Start with DEVELOPER, promote as needed

2. **Separation of Duties**
   - Not all developers need create/delete permissions
   - Use TECH_LEAD role sparingly

3. **Regular Review**
   - Periodically review role assignments
   - Remove unnecessary permissions

4. **Audit Trail**
   - All role assignments are logged
   - Track who assigned roles and when

## Database Queries

### Check User Roles
```sql
SELECT u.username, r.code, r.name
FROM sys_users u
JOIN sys_user_roles ur ON u.id = ur.user_id
JOIN sys_roles r ON ur.role_id = r.id
WHERE u.id = 'user-id';
```

### Check Virtual Group Memberships
```sql
SELECT u.username, vg.code, vg.name, r.code as role_code
FROM sys_users u
JOIN sys_virtual_group_members vgm ON u.id = vgm.user_id
JOIN sys_virtual_groups vg ON vgm.group_id = vg.id
JOIN sys_virtual_group_roles vgr ON vg.id = vgr.virtual_group_id
JOIN sys_roles r ON vgr.role_id = r.id
WHERE u.id = 'user-id';
```

### List All Roles
```sql
SELECT code, name, type, description 
FROM sys_roles 
WHERE status = 'ACTIVE' 
ORDER BY code;
```

## Related Documentation

- `docs/DEVELOPER_ROLES_RESTRUCTURE.md` - Detailed restructure documentation
- `docs/ROLES_AND_GROUPS_ENGLISH_UPDATE.md` - Role name updates
- `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql` - Initialization script
