# Digital Lending V2 English Version - Deployment Issue Fix

## Issue Date
2026-02-07

## Problem Description

After deploying the Digital Lending System V2 (English Version), the Developer Workstation frontend encountered a 422 error when trying to fetch function units:

```
GET http://localhost:3002/api/v1/function-units?page=0&size=20 422 (Unprocessable Content)
```

## Root Cause Analysis

### Backend Error
The backend service (developer-workstation) was throwing a Hibernate exception:

```
org.hibernate.HibernateException: More than one row with the given identifier was found: 10, 
for class: com.developer.entity.ProcessDefinition
```

### Database Investigation
Query revealed duplicate process definition records:

```sql
SELECT id, function_unit_id, function_unit_version_id 
FROM dw_process_definitions 
WHERE function_unit_id = 10;

 id | function_unit_id | function_unit_version_id
----+------------------+--------------------------
 12 |               10 |                       10
 13 |               10 |                       10
```

### Cause
The deployment script (`deploy-all.ps1`) was executed multiple times, creating duplicate process definition records for the same function unit. Hibernate's entity loading mechanism expects a unique result when querying by `function_unit_id`, causing the application to fail.

## Solution

### Step 1: Remove Duplicate Record
```sql
DELETE FROM dw_process_definitions WHERE id = 13;
```

### Step 2: Restart Service
```bash
docker restart platform-developer-workstation-dev
```

### Step 3: Verify Fix
```sql
SELECT fu.id, fu.code, fu.name, fu.status, COUNT(pd.id) as process_count 
FROM dw_function_units fu 
LEFT JOIN dw_process_definitions pd ON fu.id = pd.function_unit_id 
WHERE fu.code = 'DIGITAL_LENDING_V2_EN' 
GROUP BY fu.id, fu.code, fu.name, fu.status;

 id |         code          |              name              |  status   | process_count
----+-----------------------+--------------------------------+-----------+---------------
 10 | DIGITAL_LENDING_V2_EN | Digital Lending System V2 (EN) | PUBLISHED |             1
```

## Resolution Status

✅ **RESOLVED**

- Duplicate process definition removed
- Service restarted successfully
- Application now running without errors
- Function unit list loads correctly in Developer Workstation

## Prevention Measures

### 1. Improve Deployment Script
Add duplicate check before inserting process definitions:

```sql
-- Check if process definition already exists
DO $$
DECLARE
    v_process_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_process_count
    FROM dw_process_definitions
    WHERE function_unit_id = v_function_unit_id;
    
    IF v_process_count > 0 THEN
        RAISE NOTICE 'Process definition already exists for function unit %, skipping insertion', v_function_unit_id;
    ELSE
        -- Insert process definition
        INSERT INTO dw_process_definitions (...) VALUES (...);
    END IF;
END $$;
```

### 2. Add Unique Constraint
Consider adding a unique constraint to prevent duplicate process definitions:

```sql
ALTER TABLE dw_process_definitions 
ADD CONSTRAINT uk_process_def_function_unit 
UNIQUE (function_unit_id, function_unit_version_id);
```

### 3. Deployment Best Practices
- Always check if function unit exists before deploying
- Use transaction rollback on errors
- Add idempotency checks in deployment scripts
- Log all deployment operations for audit trail

## Verification Steps

### 1. Check Service Health
```bash
docker ps --filter "name=platform-developer-workstation-dev"
```

Expected: Status shows "healthy"

### 2. Check Application Logs
```bash
docker logs platform-developer-workstation-dev --tail 50
```

Expected: No Hibernate exceptions, "Started DeveloperWorkstationApplication" message present

### 3. Test Frontend
- Access: http://localhost:3002
- Login with developer credentials
- Navigate to Function Units page
- Verify "Digital Lending System V2 (EN)" appears in the list

### 4. Database Verification
```sql
-- Verify no duplicate process definitions
SELECT function_unit_id, COUNT(*) as count
FROM dw_process_definitions
GROUP BY function_unit_id
HAVING COUNT(*) > 1;
```

Expected: No rows returned

## Related Files

- Deployment Script: `deploy/init-scripts/08-digital-lending-v2-en/deploy-all.ps1`
- BPMN Insertion: `deploy/init-scripts/08-digital-lending-v2-en/02-insert-bpmn-process.ps1`
- Function Unit SQL: `deploy/init-scripts/08-digital-lending-v2-en/01-create-digital-lending-complete.sql`

## Lessons Learned

1. **Idempotency is Critical**: Deployment scripts must be idempotent to handle re-runs safely
2. **Database Constraints**: Unique constraints help prevent data integrity issues
3. **Error Handling**: Better error messages in deployment scripts would have identified the issue faster
4. **Testing**: Always test deployment scripts in a clean environment before production use

## Next Steps

1. ✅ Issue resolved - service operational
2. ⏳ Update deployment scripts with duplicate checks
3. ⏳ Add database constraints for data integrity
4. ⏳ Document deployment procedures
5. ⏳ Create deployment validation checklist

---

**Fixed By**: AI Assistant  
**Fix Date**: 2026-02-07  
**Status**: ✅ Resolved  
**Impact**: No data loss, service restored
