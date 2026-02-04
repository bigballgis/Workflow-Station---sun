# Roles and Virtual Groups English Update

## Date
2026-02-04

## Summary
Updated all role and virtual group names and descriptions from Chinese to English for better internationalization support.

## Changes Made

### Roles (sys_roles)

| Code | Old Name (Chinese) | New Name (English) | Description |
|------|-------------------|-------------------|-------------|
| SYS_ADMIN | 系统管理员 | System Administrator | System administrator with full access to all system functions |
| AUDITOR | 审计员 | Auditor | System auditor with read-only access to audit logs and system monitoring |
| DESIGNER | 工作流设计师 | Workflow Designer | Workflow designer with access to process and form design tools |
| DEVELOPER | 工作流开发者 | Workflow Developer | Workflow developer with access to developer workstation |
| MANAGER | 部门经理 | Department Manager | Department manager with access to team workflows and approvals |

### Virtual Groups (sys_virtual_groups)

| Code | Old Name (Chinese) | New Name (English) | Description |
|------|-------------------|-------------------|-------------|
| SYSTEM_ADMINISTRATORS | 系统管理员组 | System Administrators | Virtual group for system administrators with full system access |
| AUDITORS | 审计员组 | Auditors | Virtual group for system auditors with monitoring and audit access |
| DESIGNERS | 工作流设计师组 | Workflow Designers | Virtual group for workflow designers |
| DEVELOPERS | 工作流开发者组 | Workflow Developers | Virtual group for workflow developers |
| MANAGERS | 部门经理组 | Department Managers | Virtual group for department managers |

## Implementation

### SQL Script Created
- `deploy/init-scripts/01-admin/03-update-roles-and-groups-to-english.sql`

### Execution
```bash
Get-Content deploy/init-scripts/01-admin/03-update-roles-and-groups-to-english.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### Results
✅ 5 roles updated successfully
✅ 5 virtual groups updated successfully

## Verification

### Roles Query
```sql
SELECT id, code, name, description FROM sys_roles ORDER BY id;
```

**Results**:
- ✅ All role names are now in English
- ✅ All role descriptions are in English

### Virtual Groups Query
```sql
SELECT id, code, name, description FROM sys_virtual_groups ORDER BY id;
```

**Results**:
- ✅ All virtual group names are now in English
- ✅ All virtual group descriptions are in English

## Impact

### User Interface
The admin center and user portal will now display role and virtual group names in English, providing:
- Better internationalization support
- Consistency with code values (which are already in English)
- Easier understanding for international users

### API Responses
Login and user info API responses will now return English names:
```json
{
  "user": {
    "roles": ["SYS_ADMIN"],
    "rolesWithSources": [{
      "roleCode": "SYS_ADMIN",
      "roleName": "System Administrator",  // Now in English
      ...
    }]
  }
}
```

### Database
- No schema changes required
- Only data updates (UPDATE statements)
- Backward compatible with existing code

## Future Considerations

For full internationalization support, consider:
1. Adding i18n translation keys for role/group names
2. Storing translations in separate i18n tables
3. Using the `code` field as the translation key
4. Allowing users to select their preferred language

## Related Files

- `deploy/init-scripts/01-admin/03-update-roles-and-groups-to-english.sql` - Update script
- `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql` - Original creation script (updated for future deployments)
- `backend/admin-center/src/main/java/com/admin/service/impl/AuthServiceImpl.java` - Updated to fetch role names from database

## Code Changes

### AuthServiceImpl.java

Added `getRoleNames()` helper method to fetch role names from database:

```java
private Map<String, String> getRoleNames(List<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
        return Map.of();
    }
    
    try {
        // Build placeholders for IN clause
        String placeholders = roleCodes.stream()
                .map(code -> "?")
                .collect(Collectors.joining(","));
        
        String sql = "SELECT code, name FROM sys_roles WHERE code IN (" + placeholders + ")";
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, roleCodes.toArray());
        
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("code"),
                        row -> (String) row.get("name")
                ));
    } catch (Exception e) {
        log.warn("Failed to get role names: {}", e.getMessage());
        return Map.of();
    }
}
```

Updated login method to use role names instead of codes:

```java
// Get role names from database
Map<String, String> roleCodeToName = getRoleNames(roles);

List<LoginResponse.RoleWithSource> rolesWithSources = roles.stream()
        .map(code -> LoginResponse.RoleWithSource.builder()
                .roleCode(code)
                .roleName(roleCodeToName.getOrDefault(code, code))  // Use name from DB
                .sourceType(null)
                .sourceId(user.getId())
                .sourceName("Direct Assignment")
                .build())
        .collect(Collectors.toList());
```

### Build and Deployment

```bash
# Rebuild admin-center
mvn clean package -DskipTests -pl backend/admin-center -am -T 2

# Restart service
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d --build admin-center
```

## Recommendation

Update the original creation script (`01-create-roles-and-groups.sql`) to use English names by default for future deployments.

---

## Status

✅ All roles updated to English in database
✅ All virtual groups updated to English in database
✅ Original creation script updated for future deployments
✅ AuthServiceImpl updated to fetch role names from database
✅ Login API now returns English role names
✅ Changes verified - roleName shows "System Administrator" instead of "SYS_ADMIN"
✅ System ready for use with English role/group names

## Test Results

### Login Response (After Fix)
```json
{
  "user": {
    "userId": "user-admin",
    "username": "admin",
    "roles": ["SYS_ADMIN"],
    "rolesWithSources": [{
      "roleCode": "SYS_ADMIN",
      "roleName": "System Administrator",  // ✅ Now shows English name
      "sourceType": null,
      "sourceId": "user-admin",
      "sourceName": "Direct Assignment"
    }]
  }
}
```
