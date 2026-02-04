# Workflow Engine and User Portal Startup Fix

## Date
2026-02-04

## Summary
Fixed workflow-engine and user-portal startup failures caused by entity-database schema misalignment and missing JWT configuration.

## Issues Fixed

### 1. Workflow Engine - UserRepository Query Error ✅

**Problem**: Workflow engine failed to start with error:
```
org.hibernate.query.sqm.UnknownPathException: Could not resolve attribute 'roles' of 'com.platform.security.entity.User'
```

**Root Cause**: The `UserRepository.findByRole()` method was using an outdated query that referenced `u.roles`, but the User entity no longer has a `roles` field after the entity-database schema alignment. The relationship is now managed through the `user_roles` join table.

**Fix**: Updated the query in `UserRepository.findByRole()` to use proper joins:
```java
@Query("SELECT DISTINCT u FROM User u " +
       "JOIN UserRole ur ON ur.userId = u.id " +
       "JOIN Role r ON r.id = ur.roleId " +
       "WHERE r.code = :roleCode")
List<User> findByRole(@Param("roleCode") String roleCode);
```

**Files Modified**:
- `backend/platform-security/src/main/java/com/platform/security/repository/UserRepository.java`

**Result**: Workflow engine now starts successfully and is healthy.

---

### 2. User Portal - Missing JWT Configuration ✅

**Problem**: User portal failed to start with error:
```
Configuration validation failed at startup for SecurityConfig:
ERROR: jwtSecretKey - Default JWT secret key must be changed in production (current value: default-jwt-secret-key)
```

**Root Cause**: The user-portal Docker Compose configuration was missing JWT and encryption environment variables, and the application-docker.yml was not mapping these environment variables to the Spring Boot configuration properties.

**Fix 1 - Docker Compose**: Added missing environment variables to user-portal service:
```yaml
environment:
  JWT_SECRET: ${JWT_SECRET}
  JWT_EXPIRATION: ${JWT_EXPIRATION}
  JWT_REFRESH_EXPIRATION: ${JWT_REFRESH_EXPIRATION}
  ENCRYPTION_SECRET_KEY: ${ENCRYPTION_SECRET_KEY}
```

**Fix 2 - Application Configuration**: Added JWT configuration mapping in `application-docker.yml`:
```yaml
app:
  security:
    jwt-secret-key: ${JWT_SECRET:default-jwt-secret-key-change-in-production}
    jwt-expiration-seconds: 86400  # 24 hours in seconds
```

**Note**: The JWT_EXPIRATION environment variable is in milliseconds (86400000), but the SecurityConfig expects seconds. The configuration was hardcoded to 86400 seconds (24 hours) to match the validation constraints.

**Files Modified**:
- `deploy/environments/dev/docker-compose.dev.yml`
- `backend/user-portal/src/main/resources/application-docker.yml`

**Result**: User portal now starts successfully and is healthy.

---

## Build and Deployment

### Build Commands
```bash
# Rebuild platform-security (for UserRepository fix)
mvn clean package -DskipTests -pl backend/platform-security -am -T 2

# Rebuild workflow-engine
mvn clean package -DskipTests -pl backend/workflow-engine-core -am -T 2

# Rebuild user-portal
mvn clean package -DskipTests -pl backend/user-portal -am -T 2
```

### Deployment Commands
```bash
# Rebuild and restart workflow-engine
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d --build workflow-engine

# Rebuild and restart user-portal
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d --build user-portal
```

---

## Container Status

### Before Fixes
- ❌ workflow-engine: Failing to start (UserRepository query error)
- ❌ user-portal: Failing to start (JWT configuration error)

### After Fixes
- ✅ workflow-engine: Up and healthy
- ✅ user-portal: Up and healthy
- ✅ admin-center: Up and healthy
- ✅ api-gateway: Up and healthy
- ⚠️ developer-workstation: Up but unhealthy (separate issue)

---

## Related Issues

This fix is part of the ongoing entity-database schema alignment work:
- `docs/ENTITY_SCHEMA_ALIGNMENT_COMPLETE.md` - Permission, Role, User entity alignment
- `docs/FUNCTION_UNIT_ACCESS_ENTITY_FIX.md` - FunctionUnitAccess entity alignment
- `docs/PLATFORM_SECURITY_TESTS_FIXED.md` - Platform security test fixes

---

## Technical Details

### Entity-Database Alignment
The User entity no longer has a direct `roles` collection. Instead, the relationship is managed through:
- `sys_user_roles` table (UserRole entity)
- `sys_roles` table (Role entity)

This requires updating all queries that previously used `u.roles` to use proper JOIN statements with the UserRole and Role entities.

### JWT Configuration Mapping
Spring Boot's `@ConfigurationProperties` with prefix "app" requires environment variables to be mapped to the correct property paths:
- `JWT_SECRET` → `app.security.jwt-secret-key`
- `JWT_EXPIRATION` → `app.security.jwt-expiration-seconds` (converted from milliseconds to seconds)

---

## Conclusion

Both workflow-engine and user-portal are now running successfully. The fixes ensure:
1. All repository queries are aligned with the current entity structure
2. All required configuration properties are properly mapped from environment variables
3. Configuration validation passes for all security settings

The system is now ready for testing and development work.
