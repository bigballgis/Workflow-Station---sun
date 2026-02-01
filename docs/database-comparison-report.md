# Database Comparison Report
**Date:** 2026-01-22  
**SQL File:** workflow_platform_executable_clean_fixed.sql  
**Database:** workflow_platform (PostgreSQL)

## Executive Summary

The SQL file and actual database have **several key differences**. The SQL file represents a "cleaned" version with improvements, but the actual database has not been updated to match these changes.

## Critical Differences

### 1. ❌ sys_users Table - department_id Column

**SQL File (Expected):**
- Does NOT have `department_id` column
- Comment states: "移除 sys_users.department_id 列和相关索引"

**Actual Database:**
- STILL HAS `department_id` column
- Column definition: `department_id character varying(64)`

**Impact:** HIGH - This is a structural difference that affects queries and application code.

---

### 2. ❌ sys_role_assignments - Role Assignment IDs

**SQL File (Expected):**
```sql
-- Two specific IDs mentioned in comments:
'ra-admin-sys-admin'
'ra-tech-director-tech-director'
```

**Actual Database:**
```sql
-- Different IDs exist:
'ra-admin-sys-admin-admin-001'
'ra-tech-director-tech-director-tech-director-001'
```

**Impact:** MEDIUM - IDs are longer in database, includes redundant user ID suffix.

---

### 3. ❌ sys_user_roles - Missing Data

**SQL File (Expected):**
- Should have 4+ entries:
  - `ur-admin-001-SYS_ADMIN_ROLE`
  - `ur-tech-director-001-TECH_DIRECTOR_ROLE`
  - `ur-core-lead-001`
  - `ur-dev-john-001`
- Plus auto-sync from sys_role_assignments

**Actual Database:**
- Only has 1 entry:
  - `ur-tech-director-001-TECH_DIRECTOR_ROLE`

**Impact:** HIGH - Missing role assignments for most users.

---

## Matching Features ✅

### 1. ✅ sys_users - CHECK Constraint

**Both have the 4-value constraint:**
```sql
CHECK (status::text = ANY (ARRAY[
  'ACTIVE'::character varying,
  'DISABLED'::character varying,
  'LOCKED'::character varying,
  'PENDING'::character varying
]::text[]))
```

---

### 2. ✅ sys_users - Manager Indexes

**Both have these indexes:**
- `idx_sys_users_entity_manager` on `entity_manager_id`
- `idx_sys_users_function_manager` on `function_manager_id`

---

### 3. ✅ dw_form_table_bindings - Unique Constraint

**Both have:**
- `uk_form_table_binding` UNIQUE constraint on `(form_id, table_id)`

---

## Index Comparison

### sys_users Indexes

| Index Name | SQL File | Database | Status |
|------------|----------|----------|--------|
| sys_users_pkey | ✅ | ✅ | Match |
| sys_users_username_key | ✅ | ✅ | Match |
| idx_sys_users_username | ❌ Removed | ✅ Exists | **Difference** |
| idx_sys_users_email | ❌ Removed | ✅ Exists | **Difference** |
| idx_sys_users_status | ❌ Removed | ✅ Exists | **Difference** |
| idx_sys_users_employee_id | ✅ | ✅ | Match |
| idx_sys_users_entity_manager | ✅ | ✅ | Match |
| idx_sys_users_function_manager | ✅ | ✅ | Match |
| idx_sys_users_deleted | ✅ | ✅ | Match |
| idx_sys_users_department_id | ❌ Removed | ❓ Unknown | **Needs Check** |

**Note:** SQL file comment states "移除重复索引 (idx_user_username, idx_user_email, idx_user_status)" but these still exist in the database.

---

## Table Count Comparison

**Application Tables (sys_*, dw_*, admin_*, up_*, wf_*):**
- Database: 69 tables
- SQL File: Should match (not fully verified)

**Flowable Tables (act_*, flw_*):**
- Both have complete Flowable engine tables

---

## Recommendations

### Priority 1 - Critical Issues

1. **Remove department_id column from sys_users**
   ```sql
   ALTER TABLE sys_users DROP COLUMN IF EXISTS department_id CASCADE;
   ```

2. **Sync sys_user_roles table**
   - Execute the sync logic from SQL file (line 3274+)
   - Ensure all role assignments are reflected

### Priority 2 - Data Consistency

3. **Update sys_role_assignments IDs**
   - Consider if shorter IDs are preferred
   - Update existing records or accept current format

4. **Remove redundant indexes**
   ```sql
   DROP INDEX IF EXISTS idx_sys_users_username;
   DROP INDEX IF EXISTS idx_sys_users_email;
   DROP INDEX IF EXISTS idx_sys_users_status;
   ```
   Note: These are redundant because:
   - `sys_users_username_key` (UNIQUE) already indexes username
   - Email and status indexes may not be needed if queries are infrequent

### Priority 3 - Verification

5. **Verify all table structures match**
   - Run detailed comparison for each table
   - Check constraints, defaults, and foreign keys

6. **Verify data completeness**
   - Check all reference data is present
   - Verify user accounts and permissions

---

## SQL File Issues Found

### ⚠️ CRITICAL: Syntax Error in sys_users INSERT Statements

**Lines 3130-3155+:** Multiple INSERT statements have double comma syntax error

**Problem:**
```sql
-- WRONG (current):
INSERT INTO public.sys_users (..., employee_id, , position, ...)
                                              ^^
                                        Double comma!
```

**Should be:**
```sql
-- CORRECT:
INSERT INTO public.sys_users (..., employee_id, position, ...)
```

**Impact:** CRITICAL - The SQL file will **FAIL to execute** due to this syntax error. At least 25+ INSERT statements are affected (lines 3130-3155 and possibly more).

**Root Cause:** The `department_id` column was removed from the table definition but not properly removed from the INSERT statements, leaving a double comma where it used to be.

---

## Conclusion

The SQL file represents an **improved schema** with:
- Removed deprecated `department_id` column
- Cleaner index structure (removed redundant indexes)
- Better role assignment ID format
- Complete sys_user_roles synchronization

However, the **actual database has NOT been updated** to match these improvements. To bring the database in line with the SQL file, you need to:

1. Apply schema changes (remove column, drop indexes)
2. Update data (role assignments, user roles)
3. Fix SQL file syntax errors before re-execution

**Recommendation:** Create a migration script to safely update the production database rather than dropping and recreating everything.
