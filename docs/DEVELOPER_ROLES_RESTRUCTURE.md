# Developer Workstation Roles Restructure

## Date
2026-02-04

## Summary
Restructured the Developer Workstation role system from 2 generic roles (DEVELOPER, DESIGNER) to 3 specific roles with granular permissions (TECH_LEAD, TEAM_LEAD, DEVELOPER).

## Previous Role Structure

### Old Roles (Removed)
1. **DEVELOPER** (Workflow Developer)
   - Generic developer role
   - Unclear permission boundaries
   
2. **DESIGNER** (Workflow Designer)
   - Generic designer role
   - Unclear permission boundaries

### Issues with Old Structure
- ❌ No clear permission hierarchy
- ❌ All developers had same permissions
- ❌ No distinction between create, edit, and delete permissions
- ❌ Not aligned with real-world team structures

## New Role Structure

### New Roles (Created)

#### 1. TECH_LEAD (Technical Lead) - 技术主管
**Full Permissions**: ✅ CREATE | ✅ EDIT | ✅ DELETE | ✅ DEPLOY | ✅ PUBLISH

- Role Code: `TECH_LEAD`
- Role Type: `DEVELOPER`
- Description: Technical lead with full permissions on function units
- Virtual Group: `TECH_LEADS` (Technical Leads)

**Use Case**: Senior technical leaders who need complete control over function unit lifecycle.

#### 2. TEAM_LEAD (Team Lead) - 技术组长
**Permissions**: ✅ CREATE | ✅ EDIT | ❌ DELETE | ✅ DEPLOY | ✅ PUBLISH

- Role Code: `TEAM_LEAD`
- Role Type: `DEVELOPER`
- Description: Team lead with permissions to create, edit, deploy, and publish function units (cannot delete)
- Virtual Group: `TEAM_LEADS` (Team Leads)

**Use Case**: Team leaders who can create and manage function units but cannot delete existing ones (safety measure).

#### 3. DEVELOPER (Developer) - 开发工程师
**Permissions**: ❌ CREATE | ✅ EDIT | ❌ DELETE | ✅ DEPLOY | ✅ PUBLISH

- Role Code: `DEVELOPER`
- Role Type: `DEVELOPER`
- Description: Developer with permissions to edit, deploy, and publish existing function units (cannot create or delete)
- Virtual Group: `DEVELOPERS` (Developers)

**Use Case**: Regular developers who work on existing function units but cannot create new ones or delete existing ones.

## Permission Matrix

| Role | Create | Edit | Delete | Deploy | Publish |
|------|--------|------|--------|--------|---------|
| **TECH_LEAD** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **TEAM_LEAD** | ✅ | ✅ | ❌ | ✅ | ✅ |
| **DEVELOPER** | ❌ | ✅ | ❌ | ✅ | ✅ |

## Implementation

### Database Changes

#### Cleanup Script
Created: `deploy/init-scripts/01-admin/04-restructure-developer-roles.sql`

**Actions Performed**:
1. ✅ Removed old role assignments
2. ✅ Removed old virtual group role bindings
3. ✅ Removed old user role assignments
4. ✅ Deleted old virtual groups (DESIGNERS, old DEVELOPERS)
5. ✅ Deleted old roles (DESIGNER, old DEVELOPER)
6. ✅ Created 3 new roles (TECH_LEAD, TEAM_LEAD, DEVELOPER)
7. ✅ Created 3 new virtual groups
8. ✅ Bound roles to virtual groups

#### Updated Initialization Script
Updated: `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql`

- Replaced old DEVELOPER and DESIGNER role definitions
- Replaced old virtual group definitions
- Updated role-to-group bindings
- Updated summary documentation

### Execution

```bash
# Execute restructure script
Get-Content deploy/init-scripts/01-admin/04-restructure-developer-roles.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

**Results**:
```
DELETE 0  # role_assignments
DELETE 2  # virtual_group_roles
DELETE 0  # user_roles
DELETE 2  # virtual_groups
DELETE 2  # roles
INSERT 0 1  # TECH_LEAD role
INSERT 0 1  # TEAM_LEAD role
INSERT 0 1  # DEVELOPER role
INSERT 0 1  # TECH_LEADS group
INSERT 0 1  # TEAM_LEADS group
INSERT 0 1  # DEVELOPERS group
INSERT 0 1  # TECH_LEAD binding
INSERT 0 1  # TEAM_LEAD binding
INSERT 0 1  # DEVELOPER binding
```

## Verification

### Roles Query
```sql
SELECT id, code, name, type, description FROM sys_roles ORDER BY code;
```

**Results**:
```
role-auditor   | AUDITOR   | Auditor              | ADMIN      | System auditor...
role-developer | DEVELOPER | Developer            | DEVELOPER  | Developer with permissions to edit, deploy, and publish...
role-manager   | MANAGER   | Department Manager   | BU_BOUNDED | Department manager...
role-sys-admin | SYS_ADMIN | System Administrator | ADMIN      | System administrator...
role-team-lead | TEAM_LEAD | Team Lead            | DEVELOPER  | Team lead with permissions to create, edit, deploy...
role-tech-lead | TECH_LEAD | Technical Lead       | DEVELOPER  | Technical lead with full permissions...
```

### Virtual Groups Query
```sql
SELECT id, code, name, type FROM sys_virtual_groups ORDER BY code;
```

**Results**:
```
vg-auditors   | AUDITORS              | Auditors              | SYSTEM
vg-developers | DEVELOPERS            | Developers            | SYSTEM
vg-managers   | MANAGERS              | Department Managers   | SYSTEM
vg-sys-admins | SYSTEM_ADMINISTRATORS | System Administrators | SYSTEM
vg-team-leads | TEAM_LEADS            | Team Leads            | SYSTEM
vg-tech-leads | TECH_LEADS            | Technical Leads       | SYSTEM
```

### Role Bindings Query
```sql
SELECT vgr.id, vg.code as group_code, r.code as role_code 
FROM sys_virtual_group_roles vgr 
JOIN sys_virtual_groups vg ON vgr.virtual_group_id = vg.id 
JOIN sys_roles r ON vgr.role_id = r.id 
ORDER BY vg.code;
```

**Results**:
```
vgr-auditor-001   | AUDITORS              | AUDITOR
vgr-developer-001 | DEVELOPERS            | DEVELOPER
vgr-manager-001   | MANAGERS              | MANAGER
vgr-sys-admin-001 | SYSTEM_ADMINISTRATORS | SYS_ADMIN
vgr-team-lead-001 | TEAM_LEADS            | TEAM_LEAD
vgr-tech-lead-001 | TECH_LEADS            | TECH_LEAD
```

## Backend Implementation Required

### Developer Workstation Service

The permission enforcement must be implemented in the Developer Workstation backend service:

**File**: `backend/developer-workstation/src/main/java/com/developer/service/FunctionUnitService.java`

**Required Changes**:

1. **Create Function Unit**
   ```java
   @PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
   public FunctionUnit createFunctionUnit(CreateFunctionUnitRequest request) {
       // Implementation
   }
   ```

2. **Edit Function Unit**
   ```java
   @PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
   public FunctionUnit updateFunctionUnit(String id, UpdateFunctionUnitRequest request) {
       // Implementation
   }
   ```

3. **Delete Function Unit**
   ```java
   @PreAuthorize("hasRole('TECH_LEAD')")
   public void deleteFunctionUnit(String id) {
       // Implementation
   }
   ```

4. **Deploy Function Unit**
   ```java
   @PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
   public void deployFunctionUnit(String id, DeployRequest request) {
       // Implementation
   }
   ```

5. **Publish Function Unit**
   ```java
   @PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
   public void publishFunctionUnit(String id, PublishRequest request) {
       // Implementation
   }
   ```

## Migration Guide

### For Existing Users

If you have existing users with old DEVELOPER or DESIGNER roles:

1. **Identify User Responsibilities**
   - Determine which users should be Technical Leads
   - Determine which users should be Team Leads
   - Determine which users should be Developers

2. **Assign New Roles**
   ```sql
   -- Example: Assign TECH_LEAD role to a user
   INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
   VALUES ('ur-user-tech-lead', 'user-id-here', 'role-tech-lead', CURRENT_TIMESTAMP, 'admin');
   
   -- Or assign via virtual group membership
   INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, joined_by)
   VALUES ('vgm-user-tech-leads', 'vg-tech-leads', 'user-id-here', CURRENT_TIMESTAMP, 'admin');
   ```

3. **Remove Old Assignments** (if any remain)
   ```sql
   -- Clean up any remaining old role assignments
   DELETE FROM sys_user_roles WHERE role_id IN ('role-designer');
   ```

## Benefits

### Clear Permission Hierarchy
- ✅ Three distinct levels of access
- ✅ Aligned with real-world team structures
- ✅ Easy to understand and communicate

### Security
- ✅ Principle of least privilege
- ✅ Prevents accidental deletion by junior developers
- ✅ Limits creation rights to leads

### Flexibility
- ✅ Easy to promote developers to team leads
- ✅ Easy to promote team leads to technical leads
- ✅ Can assign multiple roles if needed

### Audit Trail
- ✅ Clear role-based action tracking
- ✅ Easy to identify who can perform which actions
- ✅ Better compliance and governance

## Status

✅ Old roles and groups cleaned up
✅ New roles created with clear descriptions
✅ New virtual groups created
✅ Role-to-group bindings established
✅ Initialization script updated for future deployments
✅ Database verified and consistent

⚠️ **Next Steps**:
1. Implement permission enforcement in Developer Workstation backend
2. Update frontend UI to show/hide actions based on user roles
3. Migrate existing users to new roles
4. Update user documentation

## Files Modified

1. `deploy/init-scripts/01-admin/04-restructure-developer-roles.sql` - New cleanup and restructure script
2. `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql` - Updated initialization script
3. `docs/DEVELOPER_ROLES_RESTRUCTURE.md` - This documentation

## Related Documentation

- `docs/ROLES_AND_GROUPS_ENGLISH_UPDATE.md` - Previous role name updates
- `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql` - Main initialization script
