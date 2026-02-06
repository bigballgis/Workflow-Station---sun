# Digital Lending System

A comprehensive digital loan application and approval workflow system that demonstrates all platform features, with special emphasis on **Form Popup Actions**.

## Overview

This system provides a complete end-to-end loan processing workflow including:
- Customer loan application submission
- Document verification
- Credit bureau checks
- Risk assessment
- Multi-level approvals
- Loan disbursement

## Features Demonstrated

### 1. **Form Popup Actions** ⭐
- **Credit Check Form**: Popup form for credit officers to record bureau results
- **Risk Assessment Form**: Popup form for risk evaluation
- **View Credit Report**: Read-only popup to view credit information
- **Request Additional Info**: Comment form popup for requesting more information

### 2. **Complex Table Structure**
- **Main Table**: Loan Application
- **Sub Tables**: Applicant Information, Financial Information, Collateral Details
- **Related Tables**: Credit Check Results, Approval History, Documents

### 3. **Multiple Form Types**
- Application Form (Main form with multiple sub-tables)
- Credit Check Form (Popup form)
- Risk Assessment Form (Popup form)
- Approval Form (Read-only with editable approval history)
- Disbursement Form

### 4. **Comprehensive Workflow**
- 8 User Tasks
- 3 Decision Gateways
- Multiple approval stages
- Risk-based routing
- Return loops for additional information

### 5. **Rich Action Types**
- **FORM_POPUP**: Open forms in popup dialogs
- **APPROVE**: Approval actions
- **REJECT**: Rejection actions
- **API_CALL**: Calculate EMI, query applications
- **CUSTOM**: Document verification, disbursement
- **WITHDRAW**: Application withdrawal

## Installation

### Prerequisites
- PostgreSQL database running
- Database: `workflow_platform_dev`
- User: `platform_dev`
- Password: `dev_password_123`
- psql command-line tool installed

### Quick Start

Run the complete setup with one command:

```powershell
cd deploy/init-scripts/06-digital-lending
.\00-run-all.ps1
```

### Manual Installation

If you prefer to run steps individually:

```powershell
# Step 1: Create tables, forms, and actions
psql -h localhost -p 5432 -U platform_dev -d workflow_platform_dev -f 01-create-digital-lending.sql

# Step 2: Insert BPMN process
.\02-insert-bpmn-process.ps1

# Step 3: Bind actions to tasks
psql -h localhost -p 5432 -U platform_dev -d workflow_platform_dev -f 03-bind-actions.sql
```

### Custom Database Connection

```powershell
.\00-run-all.ps1 -DbHost "your-host" -DbPort "5432" -DbName "your-db" -DbUser "your-user" -DbPassword "your-password"
```

## System Architecture

### Tables (7 Total)

#### Main Table: Loan Application
- Application number, dates, amounts
- Loan type, tenure, interest rate, EMI
- Status, risk rating, credit score
- Approval and disbursement tracking

#### Sub Tables
1. **Applicant Information**: Personal details, ID, contact info
2. **Financial Information**: Employment, income, expenses, bank details
3. **Collateral Details**: Security/collateral for secured loans

#### Related Tables
1. **Credit Check Results**: Bureau reports, credit scores, payment history
2. **Approval History**: All approval/rejection actions with comments
3. **Documents**: Supporting documents with verification status

### Forms (5 Total)

1. **Loan Application Form** (MAIN)
   - Binds: Loan Application, Applicant Info, Financial Info, Collateral, Documents
   - Mode: Editable

2. **Credit Check Form** (POPUP) ⭐
   - Binds: Loan Application (readonly), Credit Check Results (editable)
   - Width: 800px
   - Used by: Credit Officers

3. **Risk Assessment Form** (POPUP) ⭐
   - Binds: Loan Application (editable), Applicant Info, Financial Info, Credit Check (all readonly)
   - Width: 900px
   - Used by: Risk Officers

4. **Loan Approval Form** (MAIN)
   - Binds: All tables in readonly mode, Approval History (editable)
   - Used by: Managers

5. **Loan Disbursement Form** (MAIN)
   - Binds: Loan Application (editable), Applicant Info, Financial Info (readonly)
   - Used by: Finance Team

### Workflow Process

```
Start
  ↓
Submit Application (Initiator)
  ↓
Verify Documents (Document Verifiers)
  ↓
[Documents OK?]
  ├─ No → Rejected
  └─ Yes → Perform Credit Check (Credit Officers) ⭐ FORM POPUP
            ↓
          Assess Risk (Risk Officers) ⭐ FORM POPUP
            ↓
          [Risk Acceptable?]
            ├─ High Risk → Rejected
            ├─ Need More Info → Return to Applicant
            └─ Low/Medium Risk → Manager Approval
                                  ↓
                                [Manager Approved?]
                                  ├─ No → Rejected
                                  └─ Yes → Senior Manager Approval
                                            ↓
                                          [Senior Manager Approved?]
                                            ├─ No → Rejected
                                            └─ Yes → Process Disbursement
                                                      ↓
                                                    Loan Disbursed
```

### Actions (12 Total)

#### Form Popup Actions ⭐
1. **Perform Credit Check** - Opens credit check form in popup
2. **View Credit Report** - Read-only credit report popup
3. **Assess Risk** - Opens risk assessment form in popup
4. **Request Additional Info** - Comment form popup

#### Standard Actions
5. **Submit Loan Application** - Process submit
6. **Approve Loan** - Approval action
7. **Reject Loan** - Rejection action
8. **Verify Documents** - Custom action
9. **Process Disbursement** - Custom action
10. **Withdraw Application** - Withdraw action
11. **Calculate EMI** - API call action
12. **Query Applications** - API call action

### Action Bindings

Actions are bound to specific workflow tasks:

- **Submit Application**: Submit, Withdraw
- **Document Verification**: Verify Documents, Approve, Reject, Request Info
- **Credit Check**: Perform Credit Check ⭐, View Credit Report, Approve, Request Info
- **Risk Assessment**: Assess Risk ⭐, View Credit Report, Approve, Reject, Request Info
- **Manager Approval**: Approve, Reject, Request Info, View Credit Report
- **Senior Manager Approval**: Approve, Reject, Request Info, View Credit Report
- **Disbursement**: Process Disbursement, View Credit Report

## Usage

### 1. Deploy the Function Unit

1. Open Developer Workstation: http://localhost:3002
2. Navigate to **Function Units**
3. Find **Digital Lending System**
4. Click **Deploy** button
5. Confirm deployment

### 2. Test the Workflow

1. Open User Portal: http://localhost:3001
2. Navigate to **Start Process**
3. Find **Digital Lending System**
4. Click **Start** to begin a new loan application

### 3. Test Form Popup Actions

#### Credit Check Popup
1. As a Credit Officer, open a task in "Perform Credit Check" stage
2. Click **Perform Credit Check** button
3. A popup form will appear (800px width)
4. Fill in credit bureau information
5. Submit the form
6. The data is saved to Credit Check Results table

#### Risk Assessment Popup
1. As a Risk Officer, open a task in "Assess Risk" stage
2. Click **Assess Risk** button
3. A popup form will appear (900px width)
4. Review applicant info, financial info, and credit check results
5. Set risk rating and assessment
6. Submit the form

#### View Credit Report
1. At any approval stage, click **View Credit Report**
2. A read-only popup displays credit check results
3. No submit button (view only)

## Testing Scenarios

### Scenario 1: Successful Loan Approval
1. Submit application with good credit profile
2. Documents verified successfully
3. Credit check shows high credit score
4. Risk assessment: Low risk
5. Manager approves
6. Senior manager approves
7. Finance team disburses loan

### Scenario 2: Rejection Due to High Risk
1. Submit application
2. Documents verified
3. Credit check shows low credit score
4. Risk assessment: High risk
5. Application automatically rejected

### Scenario 3: Request Additional Information
1. Submit application with incomplete info
2. Documents verified
3. Credit check completed
4. Risk officer requests additional information
5. Application returns to applicant
6. Applicant provides additional info
7. Process continues

### Scenario 4: Manager Rejection
1. Submit application
2. All checks pass
3. Risk assessment: Medium risk
4. Manager reviews and rejects
5. Application ends as rejected

## Database Schema

### Key Tables

```sql
-- Main table
dw_function_units (id, code='DIGITAL_LENDING', name, description, status)

-- Table definitions
dw_table_definitions (id, function_unit_id, table_name, table_type)

-- Field definitions
dw_field_definitions (id, table_id, field_name, data_type, ...)

-- Form definitions
dw_form_definitions (id, function_unit_id, form_name, form_type, config_json)

-- Form-table bindings
dw_form_table_bindings (id, form_id, table_id, binding_type, binding_mode)

-- Process definitions
dw_process_definitions (id, function_unit_id, bpmn_xml)

-- Action definitions
dw_action_definitions (id, function_unit_id, action_name, action_type, config_json)

-- Action bindings
dw_action_bindings (id, function_unit_id, action_id, binding_type, binding_target)
```

## Troubleshooting

### Issue: psql command not found
**Solution**: Install PostgreSQL client tools or add psql to your PATH

### Issue: Connection refused
**Solution**: Ensure PostgreSQL is running and accessible at the specified host/port

### Issue: Function unit not found
**Solution**: Run step 1 first (01-create-digital-lending.sql)

### Issue: Process not found
**Solution**: Run step 2 (02-insert-bpmn-process.ps1) after step 1

### Issue: Actions not appearing in tasks
**Solution**: Run step 3 (03-bind-actions.sql) and redeploy the function unit

## Customization

### Adding New Actions

1. Insert into `dw_action_definitions`:
```sql
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, config_json, ...
) VALUES (...);
```

2. Bind to tasks in `dw_action_bindings`:
```sql
INSERT INTO dw_action_bindings (
    function_unit_id, action_id, binding_type, binding_target, sort_order
) VALUES (...);
```

### Modifying Forms

Update `config_json` in `dw_form_definitions`:
```sql
UPDATE dw_form_definitions
SET config_json = '{"popupWidth": "1000px", ...}'::jsonb
WHERE form_name = 'Credit Check Form';
```

### Changing Workflow

1. Edit `digital-lending-process.bpmn`
2. Run `02-insert-bpmn-process.ps1` again
3. Redeploy the function unit

## Files

- `01-create-digital-lending.sql` - Creates tables, forms, and actions
- `02-insert-bpmn-process.ps1` - Inserts BPMN process definition
- `03-bind-actions.sql` - Binds actions to workflow tasks
- `00-run-all.ps1` - Runs all scripts in sequence
- `digital-lending-process.bpmn` - BPMN workflow definition
- `README.md` - This file

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the database logs
3. Check Developer Workstation console for errors
4. Verify all prerequisites are met

## License

Internal use only - Part of the Workflow Platform project

---

**Created**: 2026-02-05  
**Version**: 1.0.0  
**Status**: Production Ready


## Recent Updates

### 2026-02-06: Action ID Migration Complete

Completed migration from numeric action IDs to String action IDs:

**New Scripts**:
- `13-add-submit-withdraw-actions.sql` - Adds Submit and Withdraw actions with String IDs to `sys_action_definitions`
- `14-update-bpmn-submit-actions.ps1` - Updates BPMN XML in database with String action IDs

**Changes**:
- All 21 actions now use String IDs (e.g., `action-dl-submit-application`)
- BPMN XML updated to use String IDs instead of numeric IDs
- Bound nodes now display correctly in Developer Workstation

**To apply these updates**:
```powershell
# Add new action definitions
Get-Content 13-add-submit-withdraw-actions.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# Update BPMN in database
.\14-update-bpmn-submit-actions.ps1
```

**Verification**:
1. Open Developer Workstation at http://localhost:3002
2. Navigate to Digital Lending System → Action Design tab
3. Verify all 21 actions show correct bound nodes

See `BOUND_NODES_FIX_COMPLETE.md` for detailed information.
