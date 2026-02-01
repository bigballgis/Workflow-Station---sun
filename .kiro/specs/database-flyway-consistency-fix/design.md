# Database-Flyway Consistency Fix - Design

## 1. Design Overview

This design document outlines the technical approach to fix inconsistencies between Flyway migration scripts and actual database usage, using the current code (specifically admin-center module) as the source of truth.

## 2. Architecture Decisions

### 2.1 UserStatus Values Decision

**Decision**: Use 4 status values matching admin-center's UserStatus enum

**Rationale**:
- admin-center is the primary user management module
- Code already uses DISABLED and PENDING in multiple places
- Simpler than maintaining 5 values when INACTIVE is unused
- Aligns database constraints with actual code requirements

**Status Values**:
1. `ACTIVE` - User can login and use the system
2. `DISABLED` - User account is disabled by admin
3. `LOCKED` - User account is locked (security/failed logins)
4. `PENDING` - User account is pending activation

**Excluded**: `INACTIVE` (not used by admin-center, only in platform-security enum)

### 2.2 Index Naming Strategy

**Decision**: Keep Flyway-defined indexes, remove JPA auto-generated ones

**Rationale**:
- Flyway scripts should be the single source of truth for schema
- Consistent naming: `idx_sys_users_*` prefix
- Prevents duplicate index creation
- Easier to maintain and understand

**Index Naming Convention**:
- Format: `idx_{table_name}_{column_name}`
- Example: `idx_sys_users_username`, `idx_sys_users_email`, `idx_sys_users_status`

### 2.3 Migration Strategy

**Decision**: Two-phase approach

**Phase 1**: Update V1 migration (for new deployments)
- Modify existing V1__init_schema.sql
- Define correct CHECK constraint with 4 values
- Define indexes with correct names

**Phase 2**: Create V2 migration (for existing databases)
- Create V2__fix_user_status_constraint.sql
- Drop and recreate CHECK constraint
- Drop duplicate indexes
- Idempotent and safe to run multiple times

## 3. Component Design

### 3.1 Flyway Migration Scripts

#### 3.1.1 V1__init_schema.sql Updates

**File**: `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`

**Changes**:

```sql
-- sys_users table definition
CREATE TABLE IF NOT EXISTS sys_users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    display_name VARCHAR(50),
    full_name VARCHAR(100),
    employee_id VARCHAR(50),
    position VARCHAR(100),
    entity_manager_id VARCHAR(64),
    function_manager_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    language VARCHAR(10) DEFAULT 'zh_CN',
    must_change_password BOOLEAN DEFAULT false,
    password_expired_at TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    failed_login_count INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(64),
    
    -- Updated CHECK constraint with 4 values used by admin-center
    CONSTRAINT chk_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING'))
);

-- Indexes with consistent naming
CREATE INDEX IF NOT EXISTS idx_sys_users_username ON sys_users(username);
CREATE INDEX IF NOT EXISTS idx_sys_users_email ON sys_users(email);
CREATE INDEX IF NOT EXISTS idx_sys_users_status ON sys_users(status);
CREATE INDEX IF NOT EXISTS idx_sys_users_employee_id ON sys_users(employee_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_entity_manager ON sys_users(entity_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_function_manager ON sys_users(function_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_deleted ON sys_users(deleted);

-- Comment explaining the status values
COMMENT ON COLUMN sys_users.status IS 'User status: ACTIVE (can login), DISABLED (disabled by admin), LOCKED (security lock), PENDING (pending activation)';
```

**Key Changes**:
1. CHECK constraint updated to 4 values: ACTIVE, DISABLED, LOCKED, PENDING
2. Removed INACTIVE (not used by admin-center)
3. Added comment explaining status values
4. Consistent index naming with `idx_sys_users_*` prefix

#### 3.1.2 V2__fix_user_status_constraint.sql (New File)

**File**: `backend/platform-security/src/main/resources/db/migration/V2__fix_user_status_constraint.sql`

**Purpose**: Update existing databases to match V1 schema

```sql
-- V2: Fix user status constraint and clean up duplicate indexes
-- Date: 2026-01-22
-- Purpose: Update sys_users CHECK constraint to match admin-center UserStatus enum

-- Step 1: Drop existing CHECK constraint
ALTER TABLE sys_users DROP CONSTRAINT IF EXISTS chk_sys_user_status;

-- Step 2: Add updated CHECK constraint with 4 values
ALTER TABLE sys_users ADD CONSTRAINT chk_sys_user_status 
    CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING'));

-- Step 3: Add comment explaining status values
COMMENT ON COLUMN sys_users.status IS 'User status: ACTIVE (can login), DISABLED (disabled by admin), LOCKED (security lock), PENDING (pending activation)';

-- Step 4: Clean up duplicate indexes (keep idx_sys_users_* series)
DROP INDEX IF EXISTS idx_user_username;
DROP INDEX IF EXISTS idx_user_email;
DROP INDEX IF EXISTS idx_user_status;

-- Step 5: Ensure correct indexes exist (idempotent)
CREATE INDEX IF NOT EXISTS idx_sys_users_username ON sys_users(username);
CREATE INDEX IF NOT EXISTS idx_sys_users_email ON sys_users(email);
CREATE INDEX IF NOT EXISTS idx_sys_users_status ON sys_users(status);
CREATE INDEX IF NOT EXISTS idx_sys_users_employee_id ON sys_users(employee_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_entity_manager ON sys_users(entity_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_function_manager ON sys_users(function_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_deleted ON sys_users(deleted);

-- Step 6: Verify no users have invalid status (should be empty)
DO $$
DECLARE
    invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_count 
    FROM sys_users 
    WHERE status NOT IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING');
    
    IF invalid_count > 0 THEN
        RAISE NOTICE 'Warning: % users have invalid status values', invalid_count;
        -- Log the invalid users
        RAISE NOTICE 'Invalid users: %', (
            SELECT string_agg(username || ':' || status, ', ')
            FROM sys_users 
            WHERE status NOT IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING')
        );
    END IF;
END $$;
```

**Key Features**:
1. Idempotent (safe to run multiple times)
2. Drops old constraint before adding new one
3. Cleans up duplicate indexes
4. Verifies no users have invalid status values
5. Logs warnings if invalid data found

### 3.2 JPA Entity Updates

#### 3.2.1 platform-security User Entity

**File**: `backend/platform-security/src/main/java/com/platform/security/model/User.java`

**Change**: Remove `@Index` annotations to prevent duplicate index creation

**Before**:
```java
@Table(name = "sys_users", indexes = {
    @Index(name = "idx_user_username", columnList = "username"),
    @Index(name = "idx_user_status", columnList = "status"),
    @Index(name = "idx_user_email", columnList = "email")
})
```

**After**:
```java
@Table(name = "sys_users")
// Indexes are defined in Flyway migration scripts
```

**Rationale**:
- Flyway scripts are the single source of truth for schema
- Prevents duplicate index creation
- Simplifies Entity definition

#### 3.2.2 admin-center User Entity

**File**: `backend/admin-center/src/main/java/com/admin/entity/User.java`

**Change**: No changes needed (already has no index annotations)

**Status**: ✅ Already correct

### 3.3 Documentation Updates

#### 3.3.1 Development Guidelines

**File**: `.kiro/steering/development-guidelines.md`

**Add Section**: UserStatus Enum Usage

```markdown
## X. UserStatus Enum Usage

### Two UserStatus Enums Exist

**admin-center UserStatus** (Primary):
- Location: `backend/admin-center/src/main/java/com/admin/enums/UserStatus.java`
- Values: ACTIVE, DISABLED, LOCKED, PENDING
- Used by: admin-center module (user management)
- Database constraint matches these values

**platform-security UserStatus** (Legacy):
- Location: `backend/platform-security/src/main/java/com/platform/security/model/UserStatus.java`
- Values: ACTIVE, INACTIVE, LOCKED
- Used by: platform-security module (authentication)
- Note: INACTIVE is not used in database

### Which to Use?

- **User Management**: Use `com.admin.enums.UserStatus`
- **Authentication**: Use `com.platform.security.model.UserStatus`
- **Database**: Constraint allows ACTIVE, DISABLED, LOCKED, PENDING

### Status Value Mapping

| admin-center | platform-security | Database | Meaning |
|--------------|-------------------|----------|---------|
| ACTIVE | ACTIVE | ACTIVE | User can login |
| DISABLED | INACTIVE | DISABLED | Disabled by admin |
| LOCKED | LOCKED | LOCKED | Security lock |
| PENDING | - | PENDING | Pending activation |

### Future Consideration

Consider consolidating to a single UserStatus enum in a shared module.
```

## 4. Data Flow

### 4.1 User Status Lifecycle

```
[New User Created]
       ↓
   PENDING (optional)
       ↓
   [Admin Activates]
       ↓
    ACTIVE
       ↓
   [Admin Disables] → DISABLED
       ↓
   [Admin Re-enables] → ACTIVE
       ↓
   [Failed Logins] → LOCKED
       ↓
   [Admin Unlocks] → ACTIVE
```

### 4.2 Status Transition Rules

**From ACTIVE**:
- → DISABLED (admin action)
- → LOCKED (security/failed logins)

**From DISABLED**:
- → ACTIVE (admin re-enables)

**From LOCKED**:
- → ACTIVE (admin unlocks or timeout expires)

**From PENDING**:
- → ACTIVE (admin activates)
- → DISABLED (admin rejects)

## 5. Testing Strategy

### 5.1 Unit Tests

**Test Cases**:
1. User entity can be created with each status value
2. Status transitions follow validation rules
3. UserManagerComponent.validateStatusTransition() works correctly

**Files to Test**:
- `backend/admin-center/src/test/java/com/admin/component/UserManagerComponentTest.java`
- `backend/admin-center/src/test/java/com/admin/entity/UserTest.java`

### 5.2 Integration Tests

**Test Cases**:
1. Create user with DISABLED status (no constraint violation)
2. Create user with PENDING status (no constraint violation)
3. Update user status through all valid transitions
4. Verify indexes exist and are used by queries

**Test Database**:
- Use H2 or PostgreSQL test container
- Run Flyway migrations before tests
- Verify schema matches expectations

### 5.3 Migration Tests

**Test Cases**:
1. Fresh database deployment (V1 only)
   - Verify CHECK constraint has 4 values
   - Verify indexes are created correctly
   - Verify no duplicate indexes

2. Existing database migration (V1 + V2)
   - Verify V2 migration runs successfully
   - Verify CHECK constraint is updated
   - Verify duplicate indexes are removed
   - Verify existing data is not affected

**Test Script**:
```powershell
# Test fresh deployment
docker exec -i platform-postgres psql -U platform -c "DROP DATABASE IF EXISTS test_fresh;"
docker exec -i platform-postgres psql -U platform -c "CREATE DATABASE test_fresh;"
# Start service pointing to test_fresh (Flyway runs V1)
# Verify schema

# Test migration
docker exec -i platform-postgres psql -U platform -c "DROP DATABASE IF EXISTS test_migration;"
docker exec -i platform-postgres psql -U platform -c "CREATE DATABASE test_migration;"
# Restore old schema to test_migration
# Start service pointing to test_migration (Flyway runs V1 + V2)
# Verify schema
```

### 5.4 Functional Tests

**Test Cases**:
1. Admin creates user with PENDING status
2. Admin activates pending user (PENDING → ACTIVE)
3. Admin disables active user (ACTIVE → DISABLED)
4. Admin re-enables disabled user (DISABLED → ACTIVE)
5. User gets locked after failed logins (ACTIVE → LOCKED)
6. Admin unlocks locked user (LOCKED → ACTIVE)

**Test Environment**:
- Use admin-center frontend
- Test all user management operations
- Verify no errors in backend logs

## 6. Rollback Plan

### 6.1 If V2 Migration Fails

**Steps**:
1. Stop all backend services
2. Restore database from backup
3. Investigate failure cause
4. Fix V2 migration script
5. Test on copy of database
6. Retry migration

### 6.2 If Application Breaks After Migration

**Steps**:
1. Check application logs for constraint violations
2. Verify status values in database
3. If needed, revert CHECK constraint:
   ```sql
   ALTER TABLE sys_users DROP CONSTRAINT chk_sys_user_status;
   ALTER TABLE sys_users ADD CONSTRAINT chk_sys_user_status 
       CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'));
   ```
4. Restart services
5. Investigate root cause

## 7. Performance Considerations

### 7.1 Index Impact

**Before**: 6 indexes on sys_users (3 duplicates)
**After**: 7 indexes on sys_users (no duplicates)

**Impact**:
- Slightly reduced storage (removed 3 duplicate indexes)
- Slightly improved write performance (fewer indexes to update)
- No impact on read performance (same columns indexed)

### 7.2 Constraint Check Impact

**Impact**: Negligible
- CHECK constraint is evaluated on INSERT/UPDATE only
- 4 values vs 3 values: no measurable difference
- Constraint is in-memory check, very fast

## 8. Security Considerations

### 8.1 Status Value Security

**Concern**: Can users bypass status checks?

**Mitigation**:
- Status is enforced at database level (CHECK constraint)
- Application code validates status transitions
- Authentication checks user.isActive() before allowing login

### 8.2 Migration Security

**Concern**: Could migration expose sensitive data?

**Mitigation**:
- Migration only modifies schema, not data
- No SELECT statements that could log sensitive data
- Runs with database admin privileges (normal for Flyway)

## 9. Monitoring and Validation

### 9.1 Post-Migration Checks

**Automated Checks**:
```sql
-- Verify CHECK constraint
SELECT conname, pg_get_constraintdef(oid) 
FROM pg_constraint 
WHERE conrelid = 'sys_users'::regclass 
  AND contype = 'c';

-- Verify indexes
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'sys_users' 
ORDER BY indexname;

-- Verify no invalid status values
SELECT username, status 
FROM sys_users 
WHERE status NOT IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING');
```

**Expected Results**:
- CHECK constraint: `(status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING'))`
- Indexes: 7 indexes with `idx_sys_users_*` prefix
- Invalid status: 0 rows

### 9.2 Application Health Checks

**Metrics to Monitor**:
- User login success rate
- User creation success rate
- Status update success rate
- Database constraint violation errors

**Alert Conditions**:
- Any constraint violation errors in logs
- Sudden drop in user operations success rate
- Any users with invalid status values

## 10. Implementation Checklist

### Phase 1: Preparation
- [ ] Review current database schema
- [ ] Verify no users have INACTIVE status
- [ ] Backup production database
- [ ] Create test database copy

### Phase 2: Code Changes
- [ ] Update V1__init_schema.sql
- [ ] Create V2__fix_user_status_constraint.sql
- [ ] Update platform-security User entity (remove index annotations)
- [ ] Update development-guidelines.md

### Phase 3: Testing
- [ ] Test fresh deployment (V1 only)
- [ ] Test migration (V1 + V2)
- [ ] Run unit tests
- [ ] Run integration tests
- [ ] Test admin-center user management

### Phase 4: Deployment
- [ ] Deploy to test environment
- [ ] Verify migration success
- [ ] Run functional tests
- [ ] Monitor for errors

### Phase 5: Production
- [ ] Schedule maintenance window
- [ ] Backup production database
- [ ] Deploy to production
- [ ] Run V2 migration
- [ ] Verify migration success
- [ ] Monitor application health

### Phase 6: Validation
- [ ] Run post-migration checks
- [ ] Test user management operations
- [ ] Monitor logs for 24 hours
- [ ] Document any issues

## 11. Success Criteria

- ✅ V1 migration creates correct schema for new deployments
- ✅ V2 migration successfully updates existing databases
- ✅ No duplicate indexes on sys_users table
- ✅ CHECK constraint allows all 4 status values
- ✅ All user management operations work correctly
- ✅ No constraint violation errors in logs
- ✅ All tests pass
- ✅ Documentation updated

## 12. Future Enhancements

### 12.1 Consolidate UserStatus Enums

**Goal**: Single UserStatus enum in shared module

**Benefits**:
- Eliminates confusion
- Single source of truth
- Easier to maintain

**Approach**:
1. Create shared enum in platform-common
2. Update all modules to use shared enum
3. Deprecate old enums
4. Remove old enums in next major version

### 12.2 Add Status Audit Trail

**Goal**: Track all status changes

**Benefits**:
- Compliance and auditing
- Troubleshooting
- Analytics

**Approach**:
1. Add status_history table
2. Trigger on status change
3. Store old/new status, timestamp, changed_by

## 13. References

- Database-Flyway Consistency Report: `docs/database-flyway-consistency-report.md`
- Requirements Document: `.kiro/specs/database-flyway-consistency-fix/requirements.md`
- Flyway Documentation: https://flywaydb.org/documentation/
- PostgreSQL CHECK Constraints: https://www.postgresql.org/docs/current/ddl-constraints.html
