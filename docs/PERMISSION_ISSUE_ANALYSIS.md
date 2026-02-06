# Permission Issue Analysis - Tech Lead Cannot Deploy

## Date
2026-02-05

## Issue
User reports being a "Tech Lead" but not having permission to deploy function units.

## Current State

### User Information
- **Username**: `44027893`
- **Email**: `54517051@qq.com`
- **Full Name**: Sun Y L SUN
- **User ID**: `7f963dd4-6cc4-4df8-8846-c6009f1de6c5`

### Role Assignment
**Problem**: User has **NO roles assigned**
```sql
SELECT * FROM sys_user_roles WHERE user_id = '7f963dd4-6cc4-4df8-8846-c6009f1de6c5';
-- Returns 0 rows
```

### Available Roles
The system has a `DEVELOPER` role defined:
- **Role ID**: `role-developer`
- **Role Name**: Developer
- **Role Code**: DEVELOPER
- **Description**: Developer with permissions to edit, deploy, and publish existing function units (cannot create or delete)

### Role Permissions
**Problem**: The `DEVELOPER` role has **NO permissions assigned**
```sql
SELECT * FROM sys_role_permissions WHERE role_id = 'role-developer';
-- Returns 0 rows
```

### System Permissions
**Problem**: No function unit permissions are defined in the system
```sql
SELECT * FROM sys_permissions WHERE code LIKE 'FUNCTION_UNIT%';
-- Returns 0 rows
```

## Root Cause

The permission system is not properly configured:

1. **Missing Permission Definitions**: No `FUNCTION_UNIT_*` permissions exist in `sys_permissions` table
2. **Empty Role Permissions**: The `DEVELOPER` role has no permissions assigned
3. **No User Role Assignment**: User `44027893` has no roles assigned

## Expected Permission Structure

For a Tech Lead / Developer role, the following permissions should exist:

### Function Unit Permissions
- `FUNCTION_UNIT_VIEW` - View function units
- `FUNCTION_UNIT_CREATE` - Create new function units
- `FUNCTION_UNIT_UPDATE` - Edit existing function units
- `FUNCTION_UNIT_DELETE` - Delete function units
- `FUNCTION_UNIT_PUBLISH` - Publish function units
- `FUNCTION_UNIT_DEPLOY` - Deploy function units to runtime
- `FUNCTION_UNIT_CLONE` - Clone existing function units

### Developer Workstation Permissions
Based on the backend code (`@RequireDeveloperPermission` annotations):
- `FUNCTION_UNIT_CREATE`
- `FUNCTION_UNIT_UPDATE`
- `FUNCTION_UNIT_DELETE`
- `FUNCTION_UNIT_VIEW`
- `FUNCTION_UNIT_PUBLISH`

## Why Deployment is Blocked

The deployment functionality is likely protected by a permission check. Since:
1. No permissions are defined in the system
2. The DEVELOPER role has no permissions
3. Your user has no roles assigned

**Result**: All permission-protected operations are blocked, including deployment.

## Solutions

### Option 1: Assign Admin Role (Quick Fix)
Assign the admin role to your user temporarily:
```sql
INSERT INTO public.sys_user_roles (user_id, role_id)
VALUES ('7f963dd4-6cc4-4df8-8846-c6009f1de6c5', 'role-admin');
```

### Option 2: Configure Developer Role Properly (Recommended)

#### Step 1: Create Permission Definitions
```sql
-- Insert function unit permissions
INSERT INTO public.sys_permissions (id, code, name, description) VALUES
('perm-fu-view', 'FUNCTION_UNIT_VIEW', 'View Function Units', 'View function unit details'),
('perm-fu-create', 'FUNCTION_UNIT_CREATE', 'Create Function Units', 'Create new function units'),
('perm-fu-update', 'FUNCTION_UNIT_UPDATE', 'Update Function Units', 'Edit existing function units'),
('perm-fu-delete', 'FUNCTION_UNIT_DELETE', 'Delete Function Units', 'Delete function units'),
('perm-fu-publish', 'FUNCTION_UNIT_PUBLISH', 'Publish Function Units', 'Publish function units'),
('perm-fu-deploy', 'FUNCTION_UNIT_DEPLOY', 'Deploy Function Units', 'Deploy function units to runtime');
```

#### Step 2: Assign Permissions to Developer Role
```sql
-- Assign permissions to DEVELOPER role (excluding DELETE for safety)
INSERT INTO public.sys_role_permissions (role_id, permission_id) VALUES
('role-developer', 'perm-fu-view'),
('role-developer', 'perm-fu-update'),
('role-developer', 'perm-fu-publish'),
('role-developer', 'perm-fu-deploy');
```

#### Step 3: Assign Developer Role to User
```sql
-- Assign DEVELOPER role to user
INSERT INTO public.sys_user_roles (user_id, role_id)
VALUES ('7f963dd4-6cc4-4df8-8846-c6009f1de6c5', 'role-developer');
```

### Option 3: Create Tech Lead Role (Best Practice)

```sql
-- Create Tech Lead role
INSERT INTO public.sys_roles (id, code, name, description) VALUES
('role-techlead', 'TECH_LEAD', 'Tech Lead', 'Technical lead with full development permissions except deployment');

-- Assign permissions (all except deploy)
INSERT INTO public.sys_role_permissions (role_id, permission_id) VALUES
('role-techlead', 'perm-fu-view'),
('role-techlead', 'perm-fu-create'),
('role-techlead', 'perm-fu-update'),
('role-techlead', 'perm-fu-delete'),
('role-techlead', 'perm-fu-publish');
-- Note: DEPLOY permission is NOT included

-- Assign to user
INSERT INTO public.sys_user_roles (user_id, role_id)
VALUES ('7f963dd4-6cc4-4df8-8846-c6009f1de6c5', 'role-techlead');
```

## Current Workaround

Since the permission system is not configured, you have two options:

### 1. Use Admin Account
Login with the admin account:
- **Username**: `admin`
- **Email**: `admin@example.com`
- **User ID**: `user-admin`

The admin account likely has full permissions.

### 2. Disable Permission Checks (Development Only)
Temporarily disable permission checks in the backend code by:
- Removing `@RequireDeveloperPermission` annotations
- Or configuring security to allow all authenticated users

**⚠️ Warning**: This is only for development/testing. Never do this in production!

## Recommended Action

For a proper setup:

1. **Define all required permissions** in `sys_permissions`
2. **Configure role permissions** in `sys_role_permissions`
3. **Assign appropriate roles** to users in `sys_user_roles`
4. **Create a Tech Lead role** that has all permissions except deployment
5. **Create a DevOps role** that has deployment permissions

This ensures proper separation of concerns and follows the principle of least privilege.

## Related Files
- Backend permission checks: `backend/developer-workstation/src/main/java/com/developer/security/RequireDeveloperPermission.java`
- Permission configuration: Database tables `sys_permissions`, `sys_roles`, `sys_role_permissions`, `sys_user_roles`

## Status
**Unresolved** - Permission system needs to be properly configured before deployment functionality can be used.

## Next Steps
1. Decide on permission strategy (Option 1, 2, or 3 above)
2. Execute SQL scripts to configure permissions
3. Restart backend service to reload permissions
4. Test deployment functionality
5. Document the permission model for the team
