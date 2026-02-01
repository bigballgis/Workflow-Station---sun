# Database-Flyway Consistency Fix - Tasks

## Task Status Legend
- `[ ]` - Not started
- `[~]` - Queued
- `[-]` - In progress
- `[x]` - Completed

## 1. Preparation Tasks

- [ ] 1.1 Verify current database state
  - [ ] 1.1.1 Check sys_users CHECK constraint values
  - [ ] 1.1.2 List all indexes on sys_users table
  - [ ] 1.1.3 Query for any users with INACTIVE status
  - [ ] 1.1.4 Verify sys_departments table is removed

- [ ] 1.2 Backup current database
  - [ ] 1.2.1 Create full database backup
  - [ ] 1.2.2 Verify backup can be restored
  - [ ] 1.2.3 Document backup location

- [ ] 1.3 Create test database
  - [ ] 1.3.1 Create test database copy
  - [ ] 1.3.2 Restore production data to test database
  - [ ] 1.3.3 Verify test database is accessible

## 2. Flyway Migration Script Updates

- [ ] 2.1 Update V1__init_schema.sql
  - [ ] 2.1.1 Update sys_users CHECK constraint to 4 values (ACTIVE, DISABLED, LOCKED, PENDING)
  - [ ] 2.1.2 Remove INACTIVE from CHECK constraint
  - [ ] 2.1.3 Add comment explaining status values
  - [ ] 2.1.4 Verify index definitions use idx_sys_users_* naming
  - [ ] 2.1.5 Remove any duplicate index definitions

- [ ] 2.2 Create V2__fix_user_status_constraint.sql
  - [ ] 2.2.1 Add DROP CONSTRAINT statement for old constraint
  - [ ] 2.2.2 Add CREATE CONSTRAINT statement with 4 values
  - [ ] 2.2.3 Add DROP INDEX statements for duplicate indexes
  - [ ] 2.2.4 Add CREATE INDEX statements (idempotent)
  - [ ] 2.2.5 Add validation query to check for invalid status values
  - [ ] 2.2.6 Add comments and documentation

## 3. JPA Entity Updates

- [ ] 3.1 Update platform-security User entity
  - [ ] 3.1.1 Remove @Index annotations from @Table
  - [ ] 3.1.2 Add comment explaining indexes are in Flyway
  - [ ] 3.1.3 Verify entity still compiles

- [ ] 3.2 Verify admin-center User entity
  - [ ] 3.2.1 Confirm no @Index annotations exist
  - [ ] 3.2.2 Confirm UserStatus enum is imported correctly
  - [ ] 3.2.3 Verify entity matches database schema

## 4. Documentation Updates

- [ ] 4.1 Update development-guidelines.md
  - [ ] 4.1.1 Add section on UserStatus enum usage
  - [ ] 4.1.2 Document the two UserStatus enums
  - [ ] 4.1.3 Add status value mapping table
  - [ ] 4.1.4 Add guidance on which enum to use

- [ ] 4.2 Update database-flyway-consistency-report.md
  - [ ] 4.2.1 Mark sys_users constraint issue as resolved
  - [ ] 4.2.2 Mark duplicate indexes issue as resolved
  - [ ] 4.2.3 Update status to "Fixed"

## 5. Testing - Fresh Deployment

- [ ] 5.1 Test V1 migration on fresh database
  - [ ] 5.1.1 Create new test database
  - [ ] 5.1.2 Configure service to use test database
  - [ ] 5.1.3 Start service (Flyway runs V1)
  - [ ] 5.1.4 Verify CHECK constraint has 4 values
  - [ ] 5.1.5 Verify indexes are created correctly
  - [ ] 5.1.6 Verify no duplicate indexes exist

- [ ] 5.2 Test user creation with all status values
  - [ ] 5.2.1 Create user with ACTIVE status
  - [ ] 5.2.2 Create user with DISABLED status
  - [ ] 5.2.3 Create user with LOCKED status
  - [ ] 5.2.4 Create user with PENDING status
  - [ ] 5.2.5 Verify no constraint violations

## 6. Testing - Existing Database Migration

- [ ] 6.1 Test V2 migration on existing database
  - [ ] 6.1.1 Restore old schema to test database
  - [ ] 6.1.2 Add test users with various statuses
  - [ ] 6.1.3 Start service (Flyway runs V2)
  - [ ] 6.1.4 Verify V2 migration completes successfully
  - [ ] 6.1.5 Verify CHECK constraint is updated
  - [ ] 6.1.6 Verify duplicate indexes are removed
  - [ ] 6.1.7 Verify existing users are not affected

- [ ] 6.2 Test status transitions
  - [ ] 6.2.1 Test ACTIVE → DISABLED transition
  - [ ] 6.2.2 Test DISABLED → ACTIVE transition
  - [ ] 6.2.3 Test ACTIVE → LOCKED transition
  - [ ] 6.2.4 Test LOCKED → ACTIVE transition
  - [ ] 6.2.5 Test PENDING → ACTIVE transition
  - [ ] 6.2.6 Test PENDING → DISABLED transition
  - [ ] 6.2.7 Verify all transitions work without errors

## 7. Unit and Integration Tests

- [ ] 7.1 Write/update unit tests
  - [ ] 7.1.1 Test User entity with each status value
  - [ ] 7.1.2 Test UserManagerComponent.validateStatusTransition()
  - [ ] 7.1.3 Test status transition validation rules
  - [ ] 7.1.4 Run all unit tests and verify they pass

- [ ] 7.2 Write/update integration tests
  - [ ] 7.2.1 Test user creation through API
  - [ ] 7.2.2 Test user status update through API
  - [ ] 7.2.3 Test authentication with different statuses
  - [ ] 7.2.4 Run all integration tests and verify they pass

## 8. Functional Testing

- [ ] 8.1 Test admin-center user management
  - [ ] 8.1.1 Create new user with PENDING status
  - [ ] 8.1.2 Activate pending user (PENDING → ACTIVE)
  - [ ] 8.1.3 Disable active user (ACTIVE → DISABLED)
  - [ ] 8.1.4 Re-enable disabled user (DISABLED → ACTIVE)
  - [ ] 8.1.5 Lock user (ACTIVE → LOCKED)
  - [ ] 8.1.6 Unlock user (LOCKED → ACTIVE)
  - [ ] 8.1.7 Verify all operations succeed

- [ ] 8.2 Test authentication with different statuses
  - [ ] 8.2.1 Test login with ACTIVE user (should succeed)
  - [ ] 8.2.2 Test login with DISABLED user (should fail)
  - [ ] 8.2.3 Test login with LOCKED user (should fail)
  - [ ] 8.2.4 Test login with PENDING user (should fail)

## 9. Performance Validation

- [ ] 9.1 Verify index usage
  - [ ] 9.1.1 Run EXPLAIN on user queries
  - [ ] 9.1.2 Verify indexes are being used
  - [ ] 9.1.3 Compare query performance before/after

- [ ] 9.2 Verify no performance regression
  - [ ] 9.2.1 Test user list query performance
  - [ ] 9.2.2 Test user search query performance
  - [ ] 9.2.3 Test user update performance

## 10. Deployment to Test Environment

- [ ] 10.1 Deploy code changes
  - [ ] 10.1.1 Build all backend services
  - [ ] 10.1.2 Deploy to test environment
  - [ ] 10.1.3 Verify services start successfully

- [ ] 10.2 Run V2 migration
  - [ ] 10.2.1 Backup test database
  - [ ] 10.2.2 Start services (Flyway runs V2)
  - [ ] 10.2.3 Verify migration completes successfully
  - [ ] 10.2.4 Check logs for any errors

- [ ] 10.3 Validate test environment
  - [ ] 10.3.1 Run post-migration validation queries
  - [ ] 10.3.2 Test user management operations
  - [ ] 10.3.3 Monitor logs for 24 hours
  - [ ] 10.3.4 Document any issues found

## 11. Production Deployment

- [ ] 11.1 Pre-deployment preparation
  - [ ] 11.1.1 Schedule maintenance window
  - [ ] 11.1.2 Notify stakeholders
  - [ ] 11.1.3 Prepare rollback plan
  - [ ] 11.1.4 Backup production database

- [ ] 11.2 Deploy to production
  - [ ] 11.2.1 Stop all backend services
  - [ ] 11.2.2 Deploy updated code
  - [ ] 11.2.3 Start services (Flyway runs V2)
  - [ ] 11.2.4 Verify services start successfully

- [ ] 11.3 Post-deployment validation
  - [ ] 11.3.1 Run post-migration validation queries
  - [ ] 11.3.2 Test critical user operations
  - [ ] 11.3.3 Monitor logs for errors
  - [ ] 11.3.4 Verify no constraint violations

## 12. Monitoring and Validation

- [ ] 12.1 Monitor application health
  - [ ] 12.1.1 Check user login success rate
  - [ ] 12.1.2 Check user creation success rate
  - [ ] 12.1.3 Check status update success rate
  - [ ] 12.1.4 Monitor for constraint violation errors

- [ ] 12.2 Run validation queries
  - [ ] 12.2.1 Verify CHECK constraint definition
  - [ ] 12.2.2 Verify index list
  - [ ] 12.2.3 Check for invalid status values
  - [ ] 12.2.4 Verify no duplicate indexes

- [ ] 12.3 Monitor for 48 hours
  - [ ] 12.3.1 Check logs daily
  - [ ] 12.3.2 Monitor error rates
  - [ ] 12.3.3 Respond to any issues
  - [ ] 12.3.4 Document any problems

## 13. Cleanup and Documentation

- [ ] 13.1 Update documentation
  - [ ] 13.1.1 Mark tasks as completed
  - [ ] 13.1.2 Update consistency report
  - [ ] 13.1.3 Document lessons learned
  - [ ] 13.1.4 Update development guidelines

- [ ] 13.2 Code cleanup
  - [ ] 13.2.1 Remove any temporary test code
  - [ ] 13.2.2 Update comments in code
  - [ ] 13.2.3 Commit all changes

- [ ] 13.3 Knowledge transfer
  - [ ] 13.3.1 Document migration process
  - [ ] 13.3.2 Share with team
  - [ ] 13.3.3 Update runbooks

## 14. Future Enhancements (Optional)

- [ ]* 14.1 Consolidate UserStatus enums
  - [ ]* 14.1.1 Create shared UserStatus enum in platform-common
  - [ ]* 14.1.2 Update all modules to use shared enum
  - [ ]* 14.1.3 Deprecate old enums
  - [ ]* 14.1.4 Remove old enums in next major version

- [ ]* 14.2 Add status audit trail
  - [ ]* 14.2.1 Create status_history table
  - [ ]* 14.2.2 Add trigger on status change
  - [ ]* 14.2.3 Update user management to log changes

## Notes

- Tasks marked with `*` are optional enhancements
- All tasks should be completed in order
- Each task should be tested before moving to the next
- Document any issues or deviations from the plan
- Rollback plan should be ready at each deployment step
