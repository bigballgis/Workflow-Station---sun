# Database-Flyway Consistency Fix - Summary

## Executive Summary

This spec addresses inconsistencies between Flyway migration scripts and actual database usage, using the **current code as the source of truth** (specifically the admin-center module).

## Key Problems Identified

### 1. UserStatus CHECK Constraint Mismatch ⚠️ HIGH PRIORITY

**Problem**: 
- **Code (admin-center)**: Uses 4 status values: `ACTIVE`, `DISABLED`, `LOCKED`, `PENDING`
- **Database**: Only allows 3 values: `ACTIVE`, `INACTIVE`, `LOCKED`
- **Impact**: Admin-center cannot set users to DISABLED or PENDING status (constraint violation)

**Root Cause**: Two different UserStatus enums exist:
- `com.admin.enums.UserStatus` (4 values) - used by admin-center
- `com.platform.security.model.UserStatus` (3 values) - used by platform-security

**Solution**: Update database CHECK constraint to match admin-center's 4 values

### 2. Duplicate Indexes ⚠️ MEDIUM PRIORITY

**Problem**: 
- `sys_users` table has duplicate indexes:
  - `idx_sys_users_username` + `idx_user_username`
  - `idx_sys_users_email` + `idx_user_email`
  - `idx_sys_users_status` + `idx_user_status`

**Root Cause**: Both Flyway scripts and JPA Entity annotations define indexes

**Solution**: Remove JPA index annotations, keep Flyway-defined indexes

### 3. sys_departments Table ✅ RESOLVED

**Status**: Already cleaned up on 2026-01-22
- Table removed from database
- No code references exist
- No action needed

## Proposed Solution

### Phase 1: Update V1 Migration (New Deployments)

Update `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`:
- Change CHECK constraint to: `CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING'))`
- Remove `INACTIVE` (not used by admin-center)
- Ensure consistent index naming: `idx_sys_users_*`

### Phase 2: Create V2 Migration (Existing Databases)

Create `backend/platform-security/src/main/resources/db/migration/V2__fix_user_status_constraint.sql`:
- Drop old CHECK constraint
- Add new CHECK constraint with 4 values
- Drop duplicate indexes (`idx_user_*`)
- Ensure correct indexes exist (`idx_sys_users_*`)
- Validate no users have invalid status

### Phase 3: Update JPA Entities

Update `backend/platform-security/src/main/java/com/platform/security/model/User.java`:
- Remove `@Index` annotations from `@Table`
- Add comment: "Indexes are defined in Flyway migration scripts"

### Phase 4: Update Documentation

Update `.kiro/steering/development-guidelines.md`:
- Document the two UserStatus enums
- Explain which modules use which enum
- Provide status value mapping table
- Recommend which enum to use in different contexts

## Implementation Plan

### Timeline
- **Preparation**: 1 day (backup, test database setup)
- **Development**: 1 day (update scripts, entities, docs)
- **Testing**: 2 days (fresh deployment, migration, functional tests)
- **Deployment**: 1 day (test environment, then production)
- **Monitoring**: 2 days (post-deployment validation)

**Total**: ~1 week

### Risk Level: LOW-MEDIUM
- Changes are schema-only (no data modification)
- Migration is idempotent (safe to run multiple times)
- Rollback plan available
- Extensive testing before production

## Success Criteria

- ✅ Database CHECK constraint allows all 4 status values
- ✅ No duplicate indexes on sys_users table
- ✅ Fresh deployments create correct schema
- ✅ Existing databases migrate successfully
- ✅ All user management operations work correctly
- ✅ No constraint violation errors in logs
- ✅ All tests pass

## Files to Modify

### Flyway Migrations
1. `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` (update)
2. `backend/platform-security/src/main/resources/db/migration/V2__fix_user_status_constraint.sql` (new)

### JPA Entities
3. `backend/platform-security/src/main/java/com/platform/security/model/User.java` (update)

### Documentation
4. `.kiro/steering/development-guidelines.md` (update)
5. `docs/database-flyway-consistency-report.md` (update status)

## Testing Strategy

### 1. Fresh Deployment Test
- Create new database
- Run V1 migration
- Verify schema correctness
- Test user creation with all 4 status values

### 2. Migration Test
- Restore old schema
- Run V2 migration
- Verify constraint updated
- Verify indexes cleaned up
- Verify existing data intact

### 3. Functional Test
- Test all user management operations
- Test status transitions
- Test authentication with different statuses
- Verify no errors in logs

### 4. Performance Test
- Verify index usage
- Compare query performance
- Ensure no regression

## Rollback Plan

If issues occur:
1. Stop all services
2. Restore database from backup
3. Revert code changes
4. Restart services
5. Investigate and fix issues
6. Retry with corrected scripts

## Next Steps

1. Review and approve this spec
2. Create backup of production database
3. Set up test environment
4. Begin implementation following tasks.md
5. Test thoroughly before production deployment

## Questions for Review

1. **Status Values**: Confirm we should use 4 values (ACTIVE, DISABLED, LOCKED, PENDING) and exclude INACTIVE?
   - **Recommendation**: Yes, match admin-center usage

2. **Index Naming**: Confirm we should keep `idx_sys_users_*` and remove `idx_user_*`?
   - **Recommendation**: Yes, Flyway should be source of truth

3. **UserStatus Enums**: Should we consolidate the two enums in future?
   - **Recommendation**: Yes, but as separate enhancement (not in this spec)

4. **Deployment Window**: Do we need maintenance window for production?
   - **Recommendation**: Brief window (5-10 minutes) for safety, but migration is fast

## Related Documents

- **Requirements**: `.kiro/specs/database-flyway-consistency-fix/requirements.md`
- **Design**: `.kiro/specs/database-flyway-consistency-fix/design.md`
- **Tasks**: `.kiro/specs/database-flyway-consistency-fix/tasks.md`
- **Consistency Report**: `docs/database-flyway-consistency-report.md`
- **Development Guidelines**: `.kiro/steering/development-guidelines.md`

## Contact

For questions or concerns about this spec, please contact the development team.
