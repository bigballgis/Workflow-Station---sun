# Digital Lending System - Test Checklist

## Pre-Deployment Checks

- [ ] PostgreSQL database is running
- [ ] Database `workflow_platform_dev` exists
- [ ] User `platform_dev` has access
- [ ] psql command is available
- [ ] PowerShell is available

## Installation Checks

- [ ] Ran `00-run-all.ps1` successfully
- [ ] OR ran all 3 steps manually
- [ ] No errors in console output
- [ ] Function unit created (verify in database)
- [ ] 7 tables created
- [ ] 5 forms created
- [ ] 12 actions created
- [ ] BPMN process inserted
- [ ] Action bindings created

## Deployment Checks

- [ ] Developer Workstation accessible (http://localhost:3002)
- [ ] Can login to Developer Workstation
- [ ] "Digital Lending System" appears in Function Units list
- [ ] Can open function unit details
- [ ] Can view tables (7 tables)
- [ ] Can view forms (5 forms)
- [ ] Can view process (BPMN diagram)
- [ ] Can view actions (12 actions)
- [ ] Clicked "Deploy" button
- [ ] Deployment succeeded
- [ ] Status changed to "DEPLOYED"

## User Portal Checks

- [ ] User Portal accessible (http://localhost:3001)
- [ ] Can login to User Portal
- [ ] "Digital Lending System" appears in Start Process list
- [ ] Can click "Start" button
- [ ] Application form loads

## Form Popup Action Tests

### Test 1: Credit Check Popup (Editable)
- [ ] Started loan application
- [ ] Completed application submission
- [ ] Logged in as Credit Officer
- [ ] Opened "Perform Credit Check" task
- [ ] Clicked "Perform Credit Check" button
- [ ] **Popup opened** (800px width)
- [ ] Popup title shows "Credit Bureau Check"
- [ ] Form fields are editable
- [ ] Can enter credit bureau name
- [ ] Can enter credit score
- [ ] Can enter other credit data
- [ ] Clicked "Submit" in popup
- [ ] **Popup closed**
- [ ] Success message appeared
- [ ] Data saved to database
- [ ] Can complete task

### Test 2: Risk Assessment Popup (Editable)
- [ ] Logged in as Risk Officer
- [ ] Opened "Assess Risk" task
- [ ] Clicked "Assess Risk" button
- [ ] **Popup opened** (900px width)
- [ ] Popup title shows "Risk Assessment & Evaluation"
- [ ] Can see applicant info (read-only)
- [ ] Can see financial info (read-only)
- [ ] Can see credit check results (read-only)
- [ ] Can edit risk rating field
- [ ] Can enter assessment comments
- [ ] Clicked "Submit" in popup
- [ ] **Popup closed**
- [ ] Success message appeared
- [ ] Risk rating saved
- [ ] Can complete task

### Test 3: View Credit Report Popup (Read-only)
- [ ] At any approval stage
- [ ] Clicked "View Credit Report" button
- [ ] **Popup opened** (800px width)
- [ ] Popup title shows "Credit Report"
- [ ] Can see credit check data
- [ ] All fields are read-only
- [ ] **No submit button** visible
- [ ] Can close popup (X button or click outside)
- [ ] **Popup closed**
- [ ] No data changed

### Test 4: Request Additional Info Popup (Comment)
- [ ] At any approval stage
- [ ] Clicked "Request Additional Info" button
- [ ] **Popup opened** (600px width)
- [ ] Popup title shows "Request Additional Information"
- [ ] Comment field is visible
- [ ] Can enter required information details
- [ ] Clicked "Submit" in popup
- [ ] **Popup closed**
- [ ] Success message appeared
- [ ] Application status changed to "INFO_REQUIRED"
- [ ] Application returned to applicant

## Workflow Tests

### Test 5: Complete Approval Flow
- [ ] Started new loan application
- [ ] Filled all required fields
- [ ] Submitted application
- [ ] **Document Verification** task created
- [ ] Verified documents
- [ ] Approved documents
- [ ] **Credit Check** task created
- [ ] Performed credit check (popup)
- [ ] Completed credit check task
- [ ] **Risk Assessment** task created
- [ ] Assessed risk (popup)
- [ ] Set risk rating to "Low"
- [ ] Completed risk assessment task
- [ ] **Manager Approval** task created
- [ ] Manager approved loan
- [ ] **Senior Manager Approval** task created
- [ ] Senior manager approved loan
- [ ] **Disbursement** task created
- [ ] Processed disbursement
- [ ] Process completed successfully
- [ ] End event: "Loan Disbursed"

### Test 6: Rejection Flow
- [ ] Started new loan application
- [ ] Submitted application
- [ ] Documents verified
- [ ] Credit check completed
- [ ] Risk assessment: Set risk to "High"
- [ ] Process automatically rejected
- [ ] End event: "Loan Rejected"
- [ ] Rejection reason recorded

### Test 7: Manager Rejection
- [ ] Started new loan application
- [ ] Completed all checks
- [ ] Risk rating: "Medium"
- [ ] Manager opened approval task
- [ ] Manager clicked "Reject Loan"
- [ ] Entered rejection reason
- [ ] Confirmed rejection
- [ ] Process ended as rejected
- [ ] Rejection recorded in approval history

### Test 8: Return for More Info
- [ ] Started new loan application
- [ ] Completed initial checks
- [ ] Risk officer clicked "Request Additional Info"
- [ ] Entered required info details (popup)
- [ ] Application returned to applicant
- [ ] Applicant can see info request
- [ ] Applicant provided additional info
- [ ] Resubmitted application
- [ ] Process continued from where it left off

## Action Tests

### Test 9: All Actions Visible
- [ ] Submit Loan Application - visible on submit task
- [ ] Withdraw Application - visible on submit task
- [ ] Verify Documents - visible on verification task
- [ ] Perform Credit Check - visible on credit check task
- [ ] View Credit Report - visible on multiple tasks
- [ ] Assess Risk - visible on risk assessment task
- [ ] Approve Loan - visible on approval tasks
- [ ] Reject Loan - visible on approval tasks
- [ ] Request Additional Info - visible on multiple tasks
- [ ] Process Disbursement - visible on disbursement task
- [ ] Calculate EMI - (if implemented)
- [ ] Query Applications - (if implemented)

### Test 10: Action Ordering
- [ ] Actions appear in correct sort order
- [ ] Primary actions appear first
- [ ] Secondary actions appear after
- [ ] Destructive actions (Reject) appear last

## Data Integrity Tests

### Test 11: Database Records
- [ ] Loan application record created in main table
- [ ] Applicant information saved in sub-table
- [ ] Financial information saved in sub-table
- [ ] Collateral details saved (if provided)
- [ ] Credit check results saved in related table
- [ ] Approval history records created
- [ ] Documents records created (if uploaded)
- [ ] All foreign keys correct
- [ ] No orphaned records

### Test 12: Form Bindings
- [ ] Application form shows all bound tables
- [ ] Credit check popup shows correct tables
- [ ] Risk assessment popup shows correct tables
- [ ] Approval form shows all tables
- [ ] Disbursement form shows correct tables
- [ ] Read-only fields are not editable
- [ ] Editable fields can be modified

## Performance Tests

### Test 13: Popup Performance
- [ ] Credit check popup opens quickly (< 1 second)
- [ ] Risk assessment popup opens quickly (< 1 second)
- [ ] View credit popup opens quickly (< 1 second)
- [ ] Popup submit is fast (< 2 seconds)
- [ ] Popup close is instant
- [ ] No lag when opening multiple popups

### Test 14: Workflow Performance
- [ ] Task creation is fast
- [ ] Task completion is fast
- [ ] Process transitions are smooth
- [ ] No delays between stages
- [ ] End-to-end process completes in reasonable time

## Browser Compatibility

### Test 15: Different Browsers
- [ ] Chrome: All popups work
- [ ] Firefox: All popups work
- [ ] Edge: All popups work
- [ ] Safari: All popups work (if available)

## Error Handling

### Test 16: Error Scenarios
- [ ] Empty required fields: Shows validation error
- [ ] Invalid data: Shows validation error
- [ ] Network error: Shows appropriate message
- [ ] Popup close without save: Data not saved
- [ ] Concurrent edits: Handled gracefully

## Cleanup Tests

### Test 17: Withdrawal
- [ ] Can withdraw application from submit stage
- [ ] Can withdraw from in-progress stage
- [ ] Cannot withdraw from completed stage
- [ ] Withdrawal reason required
- [ ] Withdrawal recorded in history

## Documentation Tests

### Test 18: Documentation Complete
- [ ] README.md exists and is complete
- [ ] QUICK_START.md exists and is helpful
- [ ] EXECUTE.txt has clear instructions
- [ ] TEST_CHECKLIST.md (this file) is complete
- [ ] All SQL scripts have comments
- [ ] PowerShell script has comments
- [ ] BPMN file is well-structured

## Final Verification

### Test 19: Complete System Check
- [ ] All 7 tables exist
- [ ] All 5 forms exist
- [ ] All 12 actions exist
- [ ] All 30+ action bindings exist
- [ ] BPMN process is valid
- [ ] Function unit is deployed
- [ ] Can start new applications
- [ ] Can complete full workflow
- [ ] All popup actions work
- [ ] Data is saved correctly
- [ ] No errors in console
- [ ] No errors in database logs

### Test 20: Production Readiness
- [ ] System is stable
- [ ] No memory leaks
- [ ] No performance issues
- [ ] Error handling is robust
- [ ] User experience is smooth
- [ ] Documentation is complete
- [ ] Ready for production use

---

## Test Results Summary

**Date**: _______________  
**Tester**: _______________  
**Environment**: _______________

**Total Tests**: 20  
**Passed**: _____  
**Failed**: _____  
**Skipped**: _____

**Overall Status**: ⬜ PASS / ⬜ FAIL

**Notes**:
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________

**Issues Found**:
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________

**Recommendations**:
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________

---

## Sign-off

**Tested By**: _______________  
**Date**: _______________  
**Signature**: _______________

**Approved By**: _______________  
**Date**: _______________  
**Signature**: _______________

---

**Version**: 1.0.0  
**Last Updated**: 2026-02-05
