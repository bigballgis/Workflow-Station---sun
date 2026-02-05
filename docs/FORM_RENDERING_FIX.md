# Form Rendering Issue Fix

## Date
2026-02-05

## Issue
When starting a process in User Portal, the form shows "暂无表单配置" (No form configuration) instead of rendering the form fields.

## Root Cause
The demo function unit was created with SQL scripts that only included basic form metadata (name, layout settings) but no actual form field definitions (`rule` array in the form-create configuration).

## Solution Implemented

### 1. Updated Form Configurations
Created SQL script to add complete form field definitions:
- **Leave Application Form**: 9 fields (Employee Name, Employee ID, Leave Type, Start Date, End Date, Total Days, Reason, Contact Phone, Emergency Contact)
- **Leave Approval Form**: 9 fields (Read-only employee info + Approval Status, Approver Comments)

**Script**: `deploy/init-scripts/05-demo-leave-management/04-update-form-configurations.sql`

### 2. Executed Update
```bash
Get-Content deploy/init-scripts/05-demo-leave-management/04-update-form-configurations.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

**Result**:
```
 id |       form_name        | field_count | config_size
----+------------------------+-------------+-------------
  4 | Leave Application Form |           9 |        2408
  5 | Leave Approval Form    |           9 |        2172
```

## Important Note: Deployment Required

The form configurations are currently in the **developer-workstation database** (`dw_form_definitions` table). To use them in the User Portal, you need to:

### Option 1: Deploy the Function Unit (Recommended)
1. Login to Developer Workstation: http://localhost:3002
2. Login with username `44027893` (you need to logout and login again to get TECH_LEAD role)
3. Open "Employee Leave Management" function unit
4. Click "Deploy" button
5. Wait for deployment to complete
6. The function unit and forms will be copied to admin-center database (`sys_function_units`, `sys_function_unit_contents`)
7. User Portal will then be able to access the forms

### Option 2: Test in Developer Workstation
If you want to test the forms before deploying:
1. The Developer Workstation may have a preview/test feature
2. Or you can manually copy the data to the admin-center database (not recommended)

## Form Configuration Format

The forms use **form-create** configuration format:

```json
{
  "size": "default",
  "layout": "vertical",
  "labelWidth": "120px",
  "rule": [
    {
      "type": "input",
      "field": "employeeName",
      "title": "Employee Name",
      "value": "",
      "props": {
        "placeholder": "Enter employee name"
      },
      "validate": [
        {
          "required": true,
          "message": "Employee name is required"
        }
      ],
      "col": {
        "span": 12
      }
    },
    // ... more fields
  ]
}
```

## Form Fields

### Leave Application Form
1. **Employee Name** (text, required, span 12)
2. **Employee ID** (text, required, span 12)
3. **Leave Type** (select, required, span 12)
   - Options: Annual, Sick, Personal, Maternity, Paternity
4. **Start Date** (date picker, required, span 12)
5. **End Date** (date picker, required, span 12)
6. **Total Days** (number, required, span 12, min: 0.5, max: 365, step: 0.5)
7. **Reason** (textarea, required, span 24, rows: 4)
8. **Contact Phone** (text, optional, span 12)
9. **Emergency Contact** (text, optional, span 12)

### Leave Approval Form
1. **Employee Name** (text, disabled, span 12)
2. **Employee ID** (text, disabled, span 12)
3. **Leave Type** (select, disabled, span 12)
4. **Start Date** (date picker, disabled, span 12)
5. **End Date** (date picker, disabled, span 12)
6. **Total Days** (number, disabled, span 12)
7. **Reason** (textarea, disabled, span 24, rows: 4)
8. **Approval Status** (select, required, span 12)
   - Options: Approved, Rejected, Pending
9. **Approver Comments** (textarea, required, span 24, rows: 4)

## Verification

### Check Form Configuration in Database
```sql
SELECT 
    id,
    form_name,
    jsonb_array_length(config_json->'rule') as field_count,
    LENGTH(config_json::text) as config_size
FROM dw_form_definitions
WHERE function_unit_id = 3
ORDER BY id;
```

Expected output:
- Leave Application Form: 9 fields, ~2400 bytes
- Leave Approval Form: 9 fields, ~2200 bytes

### Check Deployed Forms (After Deployment)
```sql
SELECT 
    id,
    function_unit_id,
    content_name,
    content_type,
    LENGTH(content_data) as data_length
FROM sys_function_unit_contents
WHERE function_unit_id = '3' AND content_type = 'FORM';
```

## Next Steps

1. **Deploy the function unit** from Developer Workstation
2. **Verify deployment** succeeded
3. **Test in User Portal** - forms should now render correctly
4. **Start a process** - you should see all 9 form fields

## Related Files
- Form configuration script: `deploy/init-scripts/05-demo-leave-management/04-update-form-configurations.sql`
- User Portal start process page: `frontend/user-portal/src/views/processes/start.vue`
- Form renderer component: `frontend/user-portal/src/components/FormRenderer.vue`

## Summary

✅ **Form configurations updated** with complete field definitions
✅ **9 fields added** to each form
⚠️ **Deployment required** to use in User Portal
📝 **Next action**: Deploy the function unit from Developer Workstation
