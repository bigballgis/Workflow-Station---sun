# Digital Lending System V2 - Complete Documentation

## Overview

The Digital Lending System V2 is a comprehensive, full-featured loan application and approval system built using the AI Function Unit Generation Framework. It demonstrates all core capabilities of the workflow platform including complex data models, multiple form types, rich business actions, and sophisticated approval workflows.

## System Features

### 1. Complete Data Model (7 Tables)
- **1 Main Table**: Loan Application - Core loan information
- **3 Sub Tables**: 
  - Applicant Information - Personal details (supports primary and co-applicants)
  - Financial Information - Financial status and employment details
  - Collateral Details - Security/collateral information for secured loans
- **3 Relation Tables**:
  - Credit Check Results - Credit bureau check records
  - Approval History - Complete audit trail of all approval actions
  - Documents - Supporting documents and attachments

**Total**: 7 tables with 92 fields

### 2. Multiple Form Types (5 Forms)
- **3 Main Forms**:
  - Loan Application Form - Complete application form for customers
  - Loan Approval Form - Manager approval interface
  - Loan Disbursement Form - Finance team disbursement processing
- **2 Popup Forms**:
  - Credit Check Form - Quick credit check data entry
  - Risk Assessment Form - Risk evaluation interface

### 3. Rich Business Actions (15 Actions)
- **Process Control** (4 actions):
  - Submit Application
  - Withdraw Application
  - Approve
  - Reject
- **Form Popups** (4 actions):
  - Perform Credit Check
  - View Credit Report
  - Assess Risk
  - Request Additional Info
- **Verification** (1 action):
  - Verify Documents
- **API Calls** (3 actions):
  - Calculate EMI
  - Query Applications
  - Verify Account
- **Risk Management** (2 actions):
  - Mark as Low Risk
  - Mark as High Risk
- **Disbursement** (1 action):
  - Process Disbursement

### 4. Complex Approval Workflow
- **8 Process Nodes**:
  1. Start Event
  2. Document Verification (User Task)
  3. Credit Check (User Task)
  4. Risk Assessment (User Task)
  5. Manager Approval (User Task)
  6. Loan Disbursement (User Task)
  7. Notification (Service Task)
  8. End Event

- **3 Decision Gateways**:
  1. Document Verification Gateway
  2. Credit Score Gateway (≥650 threshold)
  3. Risk Level Gateway

- **5 Task Assignment Methods**:
  - Virtual Group Assignment
  - Role Assignment
  - User Assignment
  - Expression Assignment
  - Automatic Tasks

### 5. Virtual Groups (4 Groups)
- **DOCUMENT_VERIFIERS**: Document Verifiers
- **CREDIT_OFFICERS**: Credit Officers
- **RISK_OFFICERS**: Risk Officers
- **FINANCE_TEAM**: Finance Team

## File Structure

```
08-digital-lending-v2-en/
├── 00-create-virtual-groups.sql          # Create virtual groups
├── 01-create-digital-lending-complete.sql # Create function unit (tables, forms, actions)
├── 02-insert-bpmn-process.ps1            # Insert BPMN process
├── 03-bind-actions.sql                   # Verify action bindings
├── digital-lending-process-v2-en.bpmn    # BPMN workflow definition
├── deploy-all.ps1                        # One-click deployment script
├── README.md                             # This file
└── QUICK_START.md                        # Quick start guide
```

## Deployment Instructions

### Prerequisites
- Docker container `platform-postgres-dev` must be running
- PostgreSQL database `workflow_platform_dev` must be accessible
- Virtual groups must exist (or use `-SkipVirtualGroups` parameter)

### One-Click Deployment

```powershell
cd deploy/init-scripts/08-digital-lending-v2-en
.\deploy-all.ps1
```

### Step-by-Step Deployment

If you prefer manual deployment:

```powershell
# Step 1: Create virtual groups (optional if already exist)
docker cp 00-create-virtual-groups.sql platform-postgres-dev:/tmp/
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/00-create-virtual-groups.sql

# Step 2: Create function unit
docker cp 01-create-digital-lending-complete.sql platform-postgres-dev:/tmp/
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/01-create-digital-lending-complete.sql

# Step 3: Insert BPMN process
.\02-insert-bpmn-process.ps1

# Step 4: Verify action bindings
docker cp 03-bind-actions.sql platform-postgres-dev:/tmp/
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/03-bind-actions.sql
```

## Post-Deployment Steps

### 1. Deploy in Developer Workstation
1. Access Developer Workstation: http://localhost:3002
2. Find "Digital Lending System V2" in the function unit list
3. Click the "Deploy" button
4. Wait for deployment to complete

### 2. Test in User Portal
1. Access User Portal: http://localhost:3001
2. Create a new loan application
3. Fill in applicant information, financial details, and collateral information
4. Upload supporting documents
5. Submit the application
6. Track the approval workflow

## Workflow Process

### Complete Loan Application Flow

```
1. Customer Submits Application
   ↓
2. Document Verification (DOCUMENT_VERIFIERS)
   ├─ Documents Valid → Continue
   └─ Documents Invalid → Reject
   ↓
3. Credit Check (CREDIT_OFFICERS)
   ├─ Credit Score ≥ 650 → Continue
   └─ Credit Score < 650 → Reject
   ↓
4. Risk Assessment (RISK_OFFICERS)
   ├─ Low/Medium Risk → Continue
   └─ High Risk → Reject
   ↓
5. Manager Approval (MANAGER role)
   ├─ Approved → Continue
   └─ Rejected → End
   ↓
6. Loan Disbursement (FINANCE_TEAM)
   ↓
7. Notification (Automatic)
   ↓
8. Process Complete
```

## Data Model Details

### Main Table: Loan Application
- Application number (unique identifier)
- Loan type, amount, tenure
- Interest rate and EMI
- Status and current stage
- Risk rating and credit score
- Approval and disbursement dates

### Sub Tables
- **Applicant Information**: Personal details, contact information, addresses
- **Financial Information**: Employment, income, expenses, existing loans
- **Collateral Details**: Type, description, valuation, ownership proof

### Relation Tables
- **Credit Check Results**: Bureau name, scores, credit history, payment history
- **Approval History**: Stage, approver, decision, comments, conditions
- **Documents**: Type, name, file path, verification status

## Action Configuration

### Process Control Actions
- **Submit Application**: Initiates workflow, requires confirmation
- **Withdraw Application**: Cancels application, requires reason
- **Approve**: Approves application, requires comment
- **Reject**: Rejects application, requires reason

### Form Popup Actions
- **Perform Credit Check**: Opens popup form for credit data entry
- **View Credit Report**: Opens read-only popup to view credit results
- **Assess Risk**: Opens popup form for risk evaluation
- **Request Additional Info**: Opens popup to request more information

### API Call Actions
- **Calculate EMI**: Calculates monthly payment based on amount and tenure
- **Query Applications**: Searches applications with filters
- **Verify Account**: Validates applicant's bank account

## Technical Highlights

### 1. Advanced Data Architecture
- Hierarchical table structure (Main → Sub → Relation)
- Foreign key relationships
- Comprehensive field definitions with proper data types

### 2. Flexible Form Design
- Main forms for complete data display and editing
- Popup forms for quick operations
- Read-only and editable modes
- Multiple table bindings per form

### 3. Rich Action Types
- 6 different action types (PROCESS_SUBMIT, WITHDRAW, APPROVE, REJECT, FORM_POPUP, API_CALL)
- Configurable confirmation messages
- Role-based access control
- Dynamic field updates

### 4. Sophisticated Workflow
- Multiple decision gateways
- Conditional routing based on business rules
- Various task assignment strategies
- Automatic and manual tasks

## Verification Queries

### Check Function Unit
```sql
SELECT id, code, name, status, version 
FROM dw_function_units 
WHERE code = 'DIGITAL_LENDING_V2_EN';
```

### Check Tables
```sql
SELECT id, table_name, table_type 
FROM dw_table_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');
```

### Check Forms
```sql
SELECT id, form_name, form_type 
FROM dw_form_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');
```

### Check Actions
```sql
SELECT id, action_name, action_type 
FROM dw_action_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');
```

### Check Process
```sql
SELECT id, function_unit_id, function_unit_version_id 
FROM dw_process_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');
```

## Troubleshooting

### Issue: Virtual groups already exist
**Solution**: Use `-SkipVirtualGroups` parameter when running deploy-all.ps1

### Issue: Function unit code conflict
**Solution**: Delete existing function unit first or change the code in SQL file

### Issue: BPMN process insertion fails
**Solution**: Ensure function unit is created successfully before inserting BPMN

### Issue: Action IDs not found
**Solution**: Verify all actions are created in step 2 before running step 3

## Best Practices

1. **Always deploy to development environment first**
2. **Test complete workflow before promoting to production**
3. **Review all action configurations and permissions**
4. **Verify virtual group memberships**
5. **Test all decision gateway conditions**
6. **Validate form data bindings**
7. **Check API endpoint availability**

## Related Documentation

- **QUICK_START.md**: Quick start guide for rapid deployment
- **AI_FUNCTION_UNIT_GENERATION_FRAMEWORK.md**: Framework documentation
- **DEPLOYMENT_SUCCESS.md**: Deployment success report

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the verification queries
3. Examine Docker container logs
4. Check PostgreSQL database logs

## Version History

- **v1.0.0** (2026-02-06): Initial release
  - Complete data model with 7 tables
  - 5 forms (3 main + 2 popup)
  - 15 business actions
  - Complex approval workflow with 8 nodes
  - 4 virtual groups

---

**Created**: 2026-02-06  
**Status**: Production Ready  
**Environment**: Development (Docker)  
**Database**: workflow_platform_dev
