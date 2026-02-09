# Employee Leave Management - Testing Guide

## Quick Start

### 1. Access Developer Workstation
Open your browser and navigate to:
```
http://localhost:3002
```

### 2. Login
Use your developer credentials to log in.

### 3. Navigate to Function Unit
- Click on "Function Units" in the sidebar
- Find "Employee Leave Management" (Code: LEAVE_MGMT)
- Click to open

## Component Testing

### Test 1: Tables Tab
**Expected**: 3 tables displayed

1. Click on "Tables" tab
2. Verify you see:
   - ✓ Leave Request (MAIN) - 19 fields
   - ✓ Leave Details (SUB) - 6 fields
   - ✓ Approval Records (RELATION) - 8 fields
3. Click on each table to view field definitions
4. Verify all fields are present

**Result**: ✅ PASS / ❌ FAIL

---

### Test 2: Forms Tab
**Expected**: 2 forms with table bindings

1. Click on "Forms" tab
2. Verify you see:
   - ✓ Leave Application Form
   - ✓ Leave Approval Form
3. Click on "Leave Application Form"
4. Verify table bindings:
   - ✓ Leave Request (PRIMARY, EDITABLE)
   - ✓ Leave Details (SUB, EDITABLE)
5. Click on "Leave Approval Form"
6. Verify table bindings:
   - ✓ Leave Request (PRIMARY, READONLY)
   - ✓ Approval Records (RELATED, EDITABLE)

**Result**: ✅ PASS / ❌ FAIL

---

### Test 3: Actions Tab
**Expected**: 5 actions with node bindings

1. Click on "Actions" tab
2. Verify you see 5 actions:
   - ✓ Submit Leave Request (PROCESS_SUBMIT)
   - ✓ Approve Leave Request (APPROVE)
   - ✓ Reject Leave Request (REJECT)
   - ✓ Withdraw Leave Request (WITHDRAW)
   - ✓ Query Leave Requests (API_CALL)

3. Check "Bound Nodes" column:
   - Submit Leave Request → "Submit Leave Application"
   - Approve Leave Request → "Manager Approval", "HR Approval"
   - Reject Leave Request → "Manager Approval", "HR Approval"
   - Withdraw Leave Request → "HR Approval"
   - Query Leave Requests → "Not Bound" (this is correct)

4. Click on "Submit Leave Request" to edit
5. Verify configuration shows:
   - Action Type: PROCESS_SUBMIT
   - Binding Type: node
   - Selected Nodes: "Submit Leave Application" checked

**Result**: ✅ PASS / ❌ FAIL

---

### Test 4: Process Tab
**Expected**: BPMN diagram with 3 user tasks

1. Click on "Process" tab
2. Verify BPMN diagram displays
3. Verify workflow structure:
   ```
   Start → Submit Leave Application → Manager Approval → [Gateway]
                                                             ↓
                                                         HR Approval → [Gateway] → Approved
                                                                          ↓
                                                                      Rejected
   ```
4. Click on "Task_Submit" node
5. Verify properties panel shows:
   - Form: Leave Application Form
   - Actions: Submit Leave Request

6. Click on "Task_ManagerApproval" node
7. Verify properties panel shows:
   - Form: Leave Approval Form
   - Actions: Approve Leave Request, Reject Leave Request

8. Click on "Task_HRApproval" node
9. Verify properties panel shows:
   - Form: Leave Approval Form
   - Actions: Approve Leave Request, Reject Leave Request, Withdraw Leave Request

**Result**: ✅ PASS / ❌ FAIL

---

## Advanced Testing

### Test 5: Action Binding Modification

1. Go to "Actions" tab
2. Click on "Approve Leave Request"
3. Try changing binding from "node" to "global"
4. Click "Save Binding"
5. Verify success message
6. Go to "Process" tab
7. Verify "Approve Leave Request" now appears at process level
8. Change back to "node" binding
9. Select "Manager Approval" and "HR Approval" nodes
10. Save and verify

**Result**: ✅ PASS / ❌ FAIL

---

### Test 6: Form Designer

1. Go to "Forms" tab
2. Click on "Leave Application Form"
3. Click "Design Form" button (if available)
4. Verify form designer loads
5. Verify you can see form fields
6. Close designer

**Result**: ✅ PASS / ❌ FAIL

---

### Test 7: Table Designer

1. Go to "Tables" tab
2. Click on "Leave Request" table
3. Click "Edit" button
4. Verify you can see all 19 fields
5. Try adding a new field (optional)
6. Cancel or save changes

**Result**: ✅ PASS / ❌ FAIL

---

## Validation Testing

### Test 8: Function Unit Validation

1. Click on "Validate" button (if available in UI)
2. Verify validation results show:
   - ✓ All tables have fields
   - ✓ All forms have table bindings
   - ✓ Process has user tasks
   - ✓ Actions are properly configured

**Result**: ✅ PASS / ❌ FAIL

---

## Troubleshooting

### Issue: Actions not showing bound nodes
**Solution**: 
1. Refresh the page (Ctrl+F5 or Cmd+Shift+R)
2. Clear browser cache
3. Verify backend is running: `docker ps | grep developer-workstation`

### Issue: BPMN diagram not loading
**Solution**:
1. Check browser console for errors (F12)
2. Verify process definition exists in database
3. Restart frontend: `docker restart platform-developer-workstation-frontend-dev`

### Issue: Forms not showing table bindings
**Solution**:
1. Verify form-table bindings in database
2. Check API response in browser network tab
3. Restart backend: `docker restart platform-developer-workstation-dev`

---

## Test Results Summary

| Test | Component | Status | Notes |
|------|-----------|--------|-------|
| 1 | Tables | ⬜ | |
| 2 | Forms | ⬜ | |
| 3 | Actions | ⬜ | |
| 4 | Process | ⬜ | |
| 5 | Action Binding | ⬜ | |
| 6 | Form Designer | ⬜ | |
| 7 | Table Designer | ⬜ | |
| 8 | Validation | ⬜ | |

**Legend**: ✅ PASS | ❌ FAIL | ⬜ NOT TESTED

---

## Next Steps After Testing

1. **If all tests pass**: 
   - Publish the function unit
   - Deploy to workflow engine
   - Create test data
   - Perform end-to-end workflow testing

2. **If any tests fail**:
   - Document the failure
   - Check logs: `docker logs platform-developer-workstation-dev`
   - Verify database state
   - Report issues for investigation

---

## Support

For issues or questions:
1. Check the verification report: `docs/DEMO_FUNCTION_UNIT_VERIFICATION.md`
2. Review the fix documentation: `docs/DEMO_FUNCTION_UNIT_ACTIONS_FIX.md`
3. Check backend logs: `docker logs platform-developer-workstation-dev --tail 100`
4. Check frontend logs in browser console (F12)
