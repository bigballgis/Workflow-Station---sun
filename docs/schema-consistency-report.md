# Database Schema Consistency Report

**Generated:** 2026-01-22  
**Database:** workflow_platform  
**Status:** ⚠️ Inconsistencies Found

## Executive Summary

The database schema has **3 inconsistencies** compared to Flyway migration files. The main issue is with the `sys_virtual_groups` table which has extra columns not defined in the migration file.

## Detailed Findings

### ❌ Issue 1: sys_virtual_groups - Extra Columns

**Table:** `sys_virtual_groups`  
**Module:** platform-security  
**Migration File:** `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`

**Problem:**
- Database has columns `valid_from` and `valid_to` that are NOT in the migration file
- These columns were likely added manually to the database

**Database Schema:**
```sql
CREATE TABLE sys_virtual_groups (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    type VARCHAR(50) DEFAULT 'CUSTOM',
    rule_expression TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    valid_from TIMESTAMP,           -- ❌ NOT in migration
    valid_to TIMESTAMP,             -- ❌ NOT in migration
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    ad_group VARCHAR(100),
    CONSTRAINT chk_virtual_group_type CHECK (type IN ('SYSTEM', 'CUSTOM'))
);
```

**Migration File Schema:**
```sql
CREATE TABLE IF NOT EXISTS sys_virtual_groups (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    type VARCHAR(50) DEFAULT 'CUSTOM',
    rule_expression TEXT,
    ad_group VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT chk_virtual_group_type CHECK (type IN ('SYSTEM', 'CUSTOM'))
);
```

**Impact:**
- Medium - If these columns are used by application code, removing them will break functionality
- If not used, they should be removed from database

**Recommended Action:**
1. Check if `valid_from` and `valid_to` are used in any Entity class or query
2. If used: Add them to the migration file
3. If not used: Remove them from the database

```sql
-- Option 1: Add to migration file (if used)
ALTER TABLE sys_virtual_groups ADD COLUMN valid_from TIMESTAMP;
ALTER TABLE sys_virtual_groups ADD COLUMN valid_to TIMESTAMP;

-- Option 2: Remove from database (if not used)
ALTER TABLE sys_virtual_groups DROP COLUMN valid_from;
ALTER TABLE sys_virtual_groups DROP COLUMN valid_to;
```

---

### ✅ Verified: sys_function_unit_contents

**Table:** `sys_function_unit_contents`  
**Module:** platform-security  
**Status:** ✅ Consistent

All expected columns are present:
- ✅ `source_id` - Used for formId mapping
- ✅ `flowable_deployment_id` - Flowable integration
- ✅ `flowable_process_definition_id` - Flowable integration

---

### ✅ Verified: dw_form_table_bindings

**Table:** `dw_form_table_bindings`  
**Module:** developer-workstation  
**Status:** ✅ Consistent

All expected columns are present:
- ✅ `binding_type` - PRIMARY, SUB, RELATED
- ✅ `binding_mode` - EDITABLE, READONLY
- ✅ `foreign_key_field` - Foreign key field name

---

## Tables Checked

| Table | Module | Status | Issues |
|-------|--------|--------|--------|
| sys_virtual_groups | platform-security | ❌ | Extra columns: valid_from, valid_to |
| sys_function_unit_contents | platform-security | ✅ | None |
| dw_form_table_bindings | developer-workstation | ✅ | None |
| dw_function_units | developer-workstation | ✅ | None |
| dw_form_definitions | developer-workstation | ✅ | None |
| dw_action_definitions | developer-workstation | ✅ | None |

---

## Action Items

### High Priority

- [ ] **Investigate sys_virtual_groups columns**
  - Check if `valid_from` and `valid_to` are used in code
  - Search for references in Entity classes
  - Search for references in queries

### Medium Priority

- [ ] **Update migration file or database**
  - If columns are used: Add to `V1__init_schema.sql`
  - If columns are not used: Remove from database

### Low Priority

- [ ] **Document the decision**
  - Update this report with the resolution
  - Add comments in migration file explaining the columns

---

## Investigation Commands

```bash
# Check if valid_from/valid_to are used in Entity
grep -r "validFrom\|valid_from" backend/platform-security/src/main/java/

# Check if columns have data
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT COUNT(*) FROM sys_virtual_groups WHERE valid_from IS NOT NULL OR valid_to IS NOT NULL;"

# Check table usage
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT id, name, valid_from, valid_to FROM sys_virtual_groups LIMIT 5;"
```

---

## Flyway Migration Status

**Note:** No Flyway history table found in database. This suggests:
1. Flyway may not be enabled/configured
2. Or migrations are managed manually
3. Or database was created from SQL dump

**Recommendation:** Enable Flyway for proper migration tracking.

---

## Next Steps

1. Run investigation commands above
2. Make decision on valid_from/valid_to columns
3. Update migration file or database accordingly
4. Re-run consistency check to verify
5. Document resolution in this report

---

## Related Files

- Migration: `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`
- Entity: `backend/platform-security/src/main/java/com/security/entity/VirtualGroup.java`
- Guidelines: `.kiro/steering/development-guidelines.md`
