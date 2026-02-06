# Digital Lending System - Quick Start Guide

## 🚀 One-Command Setup

```powershell
cd deploy/init-scripts/06-digital-lending
.\00-run-all.ps1
```

**Note**: Action bindings are now embedded directly in the BPMN XML, so no separate binding step is needed!

## ✅ What Gets Created

| Component | Count | Details |
|-----------|-------|---------|
| **Function Unit** | 1 | Digital Lending System |
| **Tables** | 7 | Main (1), Sub (3), Related (3) |
| **Forms** | 5 | Including 2 POPUP forms |
| **BPMN Process** | 1 | 8 tasks, 3 gateways |
| **Actions** | 12 | Including 4 FORM_POPUP actions |
| **Action Bindings** | 30+ | Actions bound to tasks |

## 🎯 Key Features

### Form Popup Actions ⭐
- **Perform Credit Check**: Opens 800px popup for credit data entry
- **Assess Risk**: Opens 900px popup for risk evaluation
- **View Credit Report**: Read-only popup to view credit info
- **Request Additional Info**: Comment popup for info requests

### Workflow Stages
1. Submit Application (Initiator)
2. Verify Documents (Document Team)
3. Perform Credit Check (Credit Officers) - **POPUP FORM**
4. Assess Risk (Risk Officers) - **POPUP FORM**
5. Manager Approval
6. Senior Manager Approval
7. Process Disbursement (Finance Team)

## 📋 Quick Test Steps

### 1. Deploy (2 minutes)
```
1. Open http://localhost:3002 (Developer Workstation)
2. Go to Function Units
3. Find "Digital Lending System"
4. Click "Deploy"
```

### 2. Test Application (5 minutes)
```
1. Open http://localhost:3001 (User Portal)
2. Go to "Start Process"
3. Find "Digital Lending System"
4. Click "Start"
5. Fill loan application form
6. Submit
```

### 3. Test Popup Actions (3 minutes)
```
1. Login as Credit Officer
2. Open "Perform Credit Check" task
3. Click "Perform Credit Check" button
4. Popup form appears (800px)
5. Fill credit bureau data
6. Submit popup form
7. Task completes with data saved
```

## 🎨 Form Popup Action Configuration

### Example: Credit Check Popup
```json
{
  "formId": 123,
  "formName": "Credit Check Form",
  "popupWidth": "800px",
  "popupTitle": "Credit Bureau Check",
  "requireComment": false,
  "allowedRoles": ["CREDIT_OFFICER", "RISK_MANAGER"],
  "successMessage": "Credit check results saved successfully"
}
```

### Example: Read-Only Popup
```json
{
  "formId": 123,
  "formName": "Credit Check Form",
  "popupWidth": "800px",
  "popupTitle": "Credit Report",
  "readOnly": true,
  "showSubmitButton": false
}
```

## 🔧 Customization Examples

### Change Popup Width
```sql
UPDATE dw_action_definitions
SET config_json = jsonb_set(
    config_json,
    '{popupWidth}',
    '"1000px"'
)
WHERE action_name = 'Perform Credit Check';
```

### Add New Popup Action
```sql
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    config_json
) VALUES (
    (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING'),
    'My Custom Popup',
    'FORM_POPUP',
    '{
        "formId": YOUR_FORM_ID,
        "popupWidth": "700px",
        "popupTitle": "My Custom Form"
    }'::jsonb
);
```

## 📊 Database Quick Queries

### Check Installation
```sql
-- Verify function unit
SELECT id, code, name, status 
FROM dw_function_units 
WHERE code = 'DIGITAL_LENDING';

-- Count tables
SELECT COUNT(*) as table_count
FROM dw_table_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING'
);

-- Count forms
SELECT COUNT(*) as form_count
FROM dw_form_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING'
);

-- Count actions
SELECT COUNT(*) as action_count
FROM dw_action_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING'
);

-- List popup actions
SELECT action_name, action_type, config_json->>'popupWidth' as width
FROM dw_action_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING'
)
AND action_type = 'FORM_POPUP';
```

### View Action Bindings
```sql
-- Action bindings are embedded in BPMN XML
-- To view them, decode the BPMN:
SELECT 
    convert_from(decode(bpmn_xml, 'base64'), 'UTF8') as decoded_bpmn
FROM dw_process_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING'
);

-- Look for <custom:property name="actionIds" value="[...]" /> in userTask elements
```

## 🐛 Common Issues

### Issue: "Function unit not found"
```powershell
# Run step 1 first
psql -h localhost -p 5432 -U platform_dev -d workflow_platform_dev -f 01-create-digital-lending.sql
```

### Issue: "Process not found"
```powershell
# Run step 2
.\02-insert-bpmn-process.ps1
```

### Issue: "Actions not showing in tasks"
```powershell
# Actions are embedded in BPMN - verify BPMN was inserted correctly
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT id, function_unit_id FROM dw_process_definitions WHERE function_unit_id = 4;"

# If no process found, re-run:
cd deploy/init-scripts/06-digital-lending
.\insert-bpmn-base64.ps1

# Then redeploy in Developer Workstation
```

### Issue: "Popup not opening"
- Check browser console for errors
- Verify formId in action config matches actual form ID
- Ensure form type is set correctly in dw_form_definitions

## 📱 Testing Checklist

- [ ] Function unit created
- [ ] All 7 tables created
- [ ] All 5 forms created
- [ ] BPMN process inserted
- [ ] All 12 actions created
- [ ] Actions bound to tasks
- [ ] Function unit deployed
- [ ] Can start new application
- [ ] Credit Check popup opens
- [ ] Risk Assessment popup opens
- [ ] View Credit Report popup opens (read-only)
- [ ] Request Info popup opens
- [ ] Workflow completes successfully

## 🎓 Learning Points

This demo showcases:
1. **Form Popup Actions** - The main feature
2. **Complex table relationships** - Main, Sub, Related
3. **Multiple form types** - Main, Popup, Read-only
4. **Rich action types** - FORM_POPUP, APPROVE, REJECT, API_CALL
5. **Action bindings** - Connecting actions to workflow tasks
6. **Multi-stage workflow** - 8 tasks with decision gateways
7. **Role-based assignment** - Different roles for different tasks

## 📚 Next Steps

1. **Explore the code**: Review SQL scripts to understand structure
2. **Customize**: Modify forms, actions, or workflow
3. **Extend**: Add new tables, forms, or actions
4. **Integrate**: Connect to real credit bureau APIs
5. **Deploy**: Move to production environment

## 💡 Pro Tips

- Use FORM_POPUP for data entry that doesn't need full page
- Set appropriate popup widths (600-1000px recommended)
- Use read-only popups for viewing related data
- Bind multiple actions to tasks for flexibility
- Test with different user roles

---

**Need Help?** Check README.md for detailed documentation

**Version**: 1.0.0  
**Last Updated**: 2026-02-05
