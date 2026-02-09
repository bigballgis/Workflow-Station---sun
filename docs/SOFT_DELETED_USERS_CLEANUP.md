# Soft Deleted Users Cleanup

**Date**: 2026-02-05  
**Status**: ✅ Complete

## Issue

Users were soft-deleted (marked as `deleted=true`) but remained in the database. This caused username/email uniqueness constraint violations when trying to create new users with the same username or email as a soft-deleted user.

**Error Message**:
```
ERROR: duplicate key value violates unique constraint "sys_users_username_key"
Detail: Key (username)=(44027893) already exists.
```

## Solution

Instead of modifying the database constraints or application logic to support soft-delete uniqueness, we chose to permanently delete (hard delete) all soft-deleted users from the database.

## Cleanup Process

### 1. Identified Soft-Deleted Users

Query to find soft-deleted users:
```sql
SELECT id, username, email, full_name, deleted, deleted_at 
FROM sys_users 
WHERE deleted = true 
ORDER BY deleted_at DESC;
```

**Result**: Found 17 soft-deleted users

### 2. Deleted Related Data

Due to foreign key constraints, we had to delete related data first:

```sql
-- Delete password history
DELETE FROM admin_password_history 
WHERE user_id IN (SELECT id FROM sys_users WHERE deleted = true);
-- Result: 1 row deleted

-- Delete user role assignments
DELETE FROM sys_user_roles 
WHERE user_id IN (SELECT id FROM sys_users WHERE deleted = true);
-- Result: 3 rows deleted

-- Delete user business unit associations
DELETE FROM sys_user_business_units 
WHERE user_id IN (SELECT id FROM sys_users WHERE deleted = true);
-- Result: 3 rows deleted
```

### 3. Deleted Soft-Deleted Users

```sql
DELETE FROM sys_users WHERE deleted = true;
-- Result: 17 rows deleted
```

### 4. Verification

```sql
SELECT COUNT(*) as soft_deleted_users 
FROM sys_users 
WHERE deleted = true;
-- Result: 0
```

## Maintenance Script

Created a reusable cleanup script at:
`deploy/init-scripts/maintenance/cleanup-soft-deleted-users.sql`

This script can be used in the future to clean up soft-deleted users.

## Usage

To run the cleanup script:

```bash
# Using docker exec
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev < deploy/init-scripts/maintenance/cleanup-soft-deleted-users.sql

# Or using psql directly
psql -U platform_dev -d workflow_platform_dev -f deploy/init-scripts/maintenance/cleanup-soft-deleted-users.sql
```

## Impact

- ✅ All soft-deleted users have been permanently removed
- ✅ Username and email uniqueness constraints now work correctly
- ✅ Can now create new users with usernames/emails that were previously used by deleted users
- ✅ Database is cleaner and more maintainable

## Recommendations

1. **Regular Cleanup**: Consider running this cleanup script periodically (e.g., monthly) to prevent accumulation of soft-deleted users
2. **Retention Policy**: Define a retention policy for soft-deleted users (e.g., keep for 30 days before permanent deletion)
3. **Automated Cleanup**: Consider implementing an automated cleanup job that runs the script on a schedule

## Related Files

- `deploy/init-scripts/maintenance/cleanup-soft-deleted-users.sql` - Cleanup script
- `backend/admin-center/src/main/java/com/admin/component/UserManagerComponent.java` - User deletion logic (soft delete)
- `backend/admin-center/src/main/java/com/admin/repository/UserRepository.java` - User repository

## Notes

- This cleanup is safe for development environments
- For production environments, ensure you have proper backups before running the cleanup
- Consider implementing a data retention policy before performing cleanup in production
