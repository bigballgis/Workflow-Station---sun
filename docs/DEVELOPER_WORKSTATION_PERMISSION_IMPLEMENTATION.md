# Developer Workstation Permission Implementation

## Date
2026-02-04

## Summary
Implemented role-based permission controls in the Developer Workstation backend service using Spring Security's `@PreAuthorize` annotations.

## Permission Matrix Implementation

### FunctionUnitComponent

| Method | Permission | Roles Allowed |
|--------|-----------|---------------|
| `create()` | CREATE | TECH_LEAD, TEAM_LEAD |
| `update()` | EDIT | TECH_LEAD, TEAM_LEAD, DEVELOPER |
| `delete()` | DELETE | TECH_LEAD only |
| `publish()` | PUBLISH | TECH_LEAD, TEAM_LEAD, DEVELOPER |
| `clone()` | CREATE (clone creates new) | TECH_LEAD, TEAM_LEAD |
| `getById()` | READ | All authenticated users |
| `list()` | READ | All authenticated users |
| `validate()` | READ | All authenticated users |

### DeploymentComponent

| Method | Permission | Roles Allowed |
|--------|-----------|---------------|
| `deployToAdminCenter()` | DEPLOY | TECH_LEAD, TEAM_LEAD, DEVELOPER |
| `getDeploymentStatus()` | READ | All authenticated users |
| `getDeploymentHistory()` | READ | All authenticated users |

## Code Changes

### File: `FunctionUnitComponentImpl.java`

#### 1. Create Function Unit
```java
@Override
@Transactional
@PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
public FunctionUnit create(FunctionUnitRequest request) {
    // Implementation
}
```

**Rationale**: Only Technical Leads and Team Leads can create new function units. Regular developers cannot create new units.

#### 2. Update Function Unit
```java
@Override
@Transactional
@PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
public FunctionUnit update(Long id, FunctionUnitRequest request) {
    // Implementation
}
```

**Rationale**: All developer roles can edit existing function units.

#### 3. Delete Function Unit
```java
@Override
@Transactional
@PreAuthorize("hasRole('TECH_LEAD')")
public void delete(Long id) {
    // Implementation
}
```

**Rationale**: Only Technical Leads can delete function units. This is a destructive operation that requires the highest level of permission.

#### 4. Publish Function Unit
```java
@Override
@Transactional
@PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
public FunctionUnit publish(Long id, String changeLog) {
    // Implementation
}
```

**Rationale**: All developer roles can publish function units after making changes.

#### 5. Clone Function Unit
```java
@Override
@Transactional
@PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
public FunctionUnit clone(Long id, String newName) {
    // Implementation
}
```

**Rationale**: Cloning creates a new function unit, so it requires CREATE permission (TECH_LEAD or TEAM_LEAD).

### File: `DeploymentComponentImpl.java`

#### Deploy to Admin Center
```java
@Override
@PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
public DeployResponse deployToAdminCenter(Long functionUnitId, DeployRequest request) {
    // Implementation
}
```

**Rationale**: All developer roles can deploy function units to the admin center.

## Spring Security Configuration

### Enable Method Security

Ensure that method-level security is enabled in your Spring Security configuration:

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    // Configuration
}
```

### Role Prefix

Spring Security by default adds a `ROLE_` prefix to role names. The `@PreAuthorize` annotation handles this automatically:
- `hasRole('TECH_LEAD')` checks for authority `ROLE_TECH_LEAD`
- `hasAnyRole('TECH_LEAD', 'TEAM_LEAD')` checks for `ROLE_TECH_LEAD` or `ROLE_TEAM_LEAD`

## Error Handling

### Access Denied

When a user attempts an operation without proper permissions, Spring Security throws an `AccessDeniedException`:

```json
{
  "code": "SECURITY_ACCESS_DENIED",
  "message": "Access is denied",
  "timestamp": "2026-02-04T10:00:00Z",
  "path": "/api/v1/function-units/123",
  "traceId": "abc123"
}
```

**HTTP Status**: 403 Forbidden

### Unauthenticated

When an unauthenticated user attempts an operation:

```json
{
  "code": "SECURITY_AUTHENTICATION_REQUIRED",
  "message": "Full authentication is required to access this resource",
  "timestamp": "2026-02-04T10:00:00Z",
  "path": "/api/v1/function-units/123",
  "traceId": "abc123"
}
```

**HTTP Status**: 401 Unauthorized

## Testing

### Test Scenarios

#### 1. TECH_LEAD User
```bash
# Should succeed - all operations
POST   /api/v1/function-units          # Create ✅
PUT    /api/v1/function-units/1        # Update ✅
DELETE /api/v1/function-units/1        # Delete ✅
POST   /api/v1/function-units/1/publish # Publish ✅
POST   /api/v1/function-units/1/deploy  # Deploy ✅
```

#### 2. TEAM_LEAD User
```bash
# Should succeed
POST   /api/v1/function-units          # Create ✅
PUT    /api/v1/function-units/1        # Update ✅
POST   /api/v1/function-units/1/publish # Publish ✅
POST   /api/v1/function-units/1/deploy  # Deploy ✅

# Should fail
DELETE /api/v1/function-units/1        # Delete ❌ (403 Forbidden)
```

#### 3. DEVELOPER User
```bash
# Should succeed
PUT    /api/v1/function-units/1        # Update ✅
POST   /api/v1/function-units/1/publish # Publish ✅
POST   /api/v1/function-units/1/deploy  # Deploy ✅

# Should fail
POST   /api/v1/function-units          # Create ❌ (403 Forbidden)
DELETE /api/v1/function-units/1        # Delete ❌ (403 Forbidden)
```

## Frontend Integration

### Role-Based UI

The frontend should hide/disable actions based on user roles:

```typescript
// Example: Vue.js computed property
computed: {
  canCreate() {
    return this.hasAnyRole(['TECH_LEAD', 'TEAM_LEAD']);
  },
  canEdit() {
    return this.hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER']);
  },
  canDelete() {
    return this.hasRole('TECH_LEAD');
  },
  canPublish() {
    return this.hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER']);
  },
  canDeploy() {
    return this.hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER']);
  }
}
```

### Button Visibility

```vue
<template>
  <div>
    <button v-if="canCreate" @click="createFunctionUnit">Create</button>
    <button v-if="canEdit" @click="editFunctionUnit">Edit</button>
    <button v-if="canDelete" @click="deleteFunctionUnit">Delete</button>
    <button v-if="canPublish" @click="publishFunctionUnit">Publish</button>
    <button v-if="canDeploy" @click="deployFunctionUnit">Deploy</button>
  </div>
</template>
```

## Security Best Practices

### 1. Defense in Depth
- ✅ Backend enforces permissions (cannot be bypassed)
- ✅ Frontend hides unauthorized actions (better UX)
- ✅ Both layers work together

### 2. Fail-Safe Defaults
- ✅ No annotation = no access (Spring Security default)
- ✅ Explicit permissions required for all operations
- ✅ Read operations generally allowed for authenticated users

### 3. Principle of Least Privilege
- ✅ DEVELOPER: Minimum permissions (edit, publish, deploy)
- ✅ TEAM_LEAD: Add create permission
- ✅ TECH_LEAD: Full permissions including delete

### 4. Audit Trail
- ✅ All operations logged with user context
- ✅ `getCurrentOperator()` method tracks who performed actions
- ✅ Version history includes `publishedBy` field

## Migration Notes

### Existing Users

If you have existing users without the new roles:

1. **Assign Roles Based on Responsibilities**
   ```sql
   -- Example: Assign TECH_LEAD to senior developers
   INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
   VALUES ('ur-user1-tech-lead', 'user-id-1', 'role-tech-lead', CURRENT_TIMESTAMP, 'admin');
   
   -- Example: Assign DEVELOPER to junior developers
   INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
   VALUES ('ur-user2-developer', 'user-id-2', 'role-developer', CURRENT_TIMESTAMP, 'admin');
   ```

2. **Test Access**
   - Have users test their access to ensure correct permissions
   - Adjust role assignments as needed

3. **Document Role Assignments**
   - Keep a record of who has which roles
   - Review periodically

## Files Modified

1. `backend/developer-workstation/src/main/java/com/developer/component/impl/FunctionUnitComponentImpl.java`
   - Added `@PreAuthorize` annotations to create, update, delete, publish, clone methods

2. `backend/developer-workstation/src/main/java/com/developer/component/impl/DeploymentComponentImpl.java`
   - Added `@PreAuthorize` annotation to deployToAdminCenter method

## Next Steps

### 1. Build and Deploy
```bash
# Rebuild developer-workstation
mvn clean package -DskipTests -pl backend/developer-workstation -am -T 2

# Restart service
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d --build developer-workstation
```

### 2. Frontend Updates
- Update UI to show/hide buttons based on user roles
- Add role checking utilities
- Test with different user roles

### 3. Documentation
- Update user documentation with role descriptions
- Create role assignment guide for administrators
- Document permission matrix for end users

### 4. Testing
- Create integration tests for permission enforcement
- Test with users having different roles
- Verify error messages are user-friendly

## Status

✅ Permission annotations added to FunctionUnitComponent
✅ Permission annotations added to DeploymentComponent
✅ Role-based access control implemented
✅ Documentation created

⚠️ **Pending**:
1. Build and restart developer-workstation service
2. Update frontend UI for role-based visibility
3. Assign roles to existing users
4. Integration testing

## Related Documentation

- `docs/DEVELOPER_ROLES_RESTRUCTURE.md` - Role restructure details
- `docs/ROLES_QUICK_REFERENCE.md` - Quick reference guide
- `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql` - Role initialization
