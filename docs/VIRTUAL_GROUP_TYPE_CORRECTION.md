# Virtual Group Type Correction - Department Managers

## Date
2026-02-05

## Issue
"Department Managers" virtual group was incorrectly set as type 'SYSTEM', but it should be type 'CUSTOM'. The system should only have 5 default virtual groups.

## System Default Virtual Groups (5)

The following 5 virtual groups are system defaults and cannot be deleted:

1. **System Administrators** (SYSTEM_ADMINISTRATORS)
   - Code: `SYSTEM_ADMINISTRATORS`
   - ID: `vg-sys-admins`
   - Type: `SYSTEM`
   - Description: Virtual group for system administrators with full system access

2. **Auditors** (AUDITORS)
   - Code: `AUDITORS`
   - ID: `vg-auditors`
   - Type: `SYSTEM`
   - Description: Virtual group for system auditors with monitoring and audit access

3. **Technical Leads** (TECH_LEADS)
   - Code: `TECH_LEADS`
   - ID: `vg-tech-leads`
   - Type: `SYSTEM`
   - Description: Virtual group for technical leads with full function unit management permissions

4. **Team Leads** (TEAM_LEADS)
   - Code: `TEAM_LEADS`
   - ID: `vg-team-leads`
   - Type: `SYSTEM`
   - Description: Virtual group for team leads with create and deployment permissions

5. **Developers** (DEVELOPERS)
   - Code: `DEVELOPERS`
   - ID: `vg-developers`
   - Type: `SYSTEM`
   - Description: Virtual group for developers with edit and deployment permissions

## Custom Virtual Groups

The following virtual groups are custom and can be modified or deleted by administrators:

1. **Department Managers** (MANAGERS)
   - Code: `MANAGERS`
   - ID: `vg-managers`
   - Type: `CUSTOM` ✅ (corrected from SYSTEM)
   - Description: Virtual group for department managers

## Solution Implemented

### 1. Updated Database
```sql
UPDATE sys_virtual_groups 
SET type = 'CUSTOM' 
WHERE code = 'MANAGERS';
```

### 2. Updated Initialization Script
Modified `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql`:
- Changed type from 'SYSTEM' to 'CUSTOM' for Department Managers virtual group
- Added comment to clarify it's not a system default

### 3. Verification
```sql
SELECT id, code, name, type 
FROM sys_virtual_groups 
ORDER BY type DESC, code;
```

**Result**:
```
      id       |         code          |         name          |  type  
---------------+-----------------------+-----------------------+--------
 vg-auditors   | AUDITORS              | Auditors              | SYSTEM
 vg-developers | DEVELOPERS            | Developers            | SYSTEM
 vg-sys-admins | SYSTEM_ADMINISTRATORS | System Administrators | SYSTEM
 vg-team-leads | TEAM_LEADS            | Team Leads            | SYSTEM
 vg-tech-leads | TECH_LEADS            | Technical Leads       | SYSTEM
 vg-managers   | MANAGERS              | Department Managers   | CUSTOM
```

✅ 5 SYSTEM virtual groups
✅ 1 CUSTOM virtual group (Department Managers)

## Virtual Group Type Behavior

### SYSTEM Type
- **Cannot be deleted** by administrators
- **Core system functionality** depends on these groups
- **Predefined** during system initialization
- **Fixed structure** - only members can be modified

### CUSTOM Type
- **Can be deleted** by administrators
- **User-defined** for specific organizational needs
- **Flexible** - can be created, modified, or removed
- **Optional** - not required for system operation

## Impact

### Before Fix
- 6 SYSTEM virtual groups (incorrect)
- Department Managers could not be deleted
- Inconsistent with system design

### After Fix
- 5 SYSTEM virtual groups (correct)
- Department Managers can be deleted if needed
- Consistent with system design
- Administrators have flexibility to manage department-specific groups

## Related Files
- Database update: Direct SQL execution
- Initialization script: `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql`
- Schema definition: `deploy/init-scripts/00-schema/01-platform-security-schema.sql`

## Future Considerations

### Creating Custom Virtual Groups
Administrators can create additional custom virtual groups for:
- Department-specific groups (Sales, Marketing, Engineering, etc.)
- Project teams
- Regional groups
- Temporary working groups

### Best Practices
1. Use SYSTEM groups for core platform roles
2. Use CUSTOM groups for organizational structure
3. Regularly review and clean up unused custom groups
4. Document the purpose of each custom group

## Summary

✅ **Department Managers virtual group corrected** from SYSTEM to CUSTOM type
✅ **System now has exactly 5 default virtual groups** as designed
✅ **Initialization script updated** to prevent future inconsistencies
✅ **Administrators have flexibility** to manage department-specific groups

The system virtual group structure is now consistent with the platform design.
