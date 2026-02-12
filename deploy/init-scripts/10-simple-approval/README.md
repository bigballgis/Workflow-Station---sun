# Simple Approval Workflow - Database Export

This directory contains the SQL scripts to recreate the Simple Approval Workflow function unit from the database.

## Export Information

- **Export Date**: 2026-02-12
- **Source Database**: workflow_platform_dev
- **Function Unit Code**: SIMPLE_APPROVAL
- **Current Version**: 1.0.5
- **Status**: PUBLISHED

## Files

### 00-create-simple-approval.sql
Creates the function unit, forms, and actions:
- **Function Unit**: Simple Approval Workflow
- **Forms**:
  - Request Form (ID: 6) - Main form for request submission
  - Approval Form (ID: 7) - Form for manager approval
- **Actions**:
  - Submit Request (ID: 16) - PROCESS_SUBMIT action
  - Approve (ID: 17) - APPROVE action
  - Reject (ID: 18) - REJECT action

### 01-insert-bpmn-process.sql
Creates the BPMN process definition with the following workflow:
1. Start Event
2. Submit Request (User Task)
3. Manager Approval (User Task)
4. Approved? (Exclusive Gateway)
   - Yes → Approved (End Event)
   - No → Rejected (End Event)

## Installation

Run the scripts in order:

```bash
# Connect to the database
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# Run the scripts
\i /path/to/00-create-simple-approval.sql
\i /path/to/01-insert-bpmn-process.sql
```

Or using psql from host:

```bash
psql -h localhost -p 5432 -U platform_dev -d workflow_platform_dev -f 00-create-simple-approval.sql
psql -h localhost -p 5432 -U platform_dev -d workflow_platform_dev -f 01-insert-bpmn-process.sql
```

## Features

### Request Form Fields
- **Primary key** (inputNumber) - Required
- **Request number** (input, max 50 chars) - Required, unique
- **Request date** (datePicker, datetime) - Required
- **Request title** (input, max 200 chars) - Required
- **Request description** (textarea) - Required
- **Request status** (input, max 30 chars) - Required

### Approval Form Fields
- **Approval comments** (textarea) - Optional
- **Request status** (input, max 30 chars) - Required

### Workflow Logic
- The workflow uses the `decision` variable to determine the flow:
  - `decision == 'yes'` → Approved path
  - `decision == 'no'` → Rejected path
- The Manager Approval task displays the Request Form in read-only mode
- Actions automatically set the decision variable:
  - Approve action sets `decision = 'yes'`
  - Reject action sets `decision = 'no'`

## Notes

- The scripts use `ON CONFLICT` clauses to allow re-running without errors
- Form and action IDs are dynamically replaced in the BPMN XML using placeholders
- The BPMN process uses custom extension elements for form and action bindings
- All timestamps use `CURRENT_TIMESTAMP` for consistency

## Differences from 09-simple-approval

This export (10-simple-approval) contains the latest version from the database, which may include:
- Updated form configurations
- Modified action definitions
- BPMN process improvements
- Bug fixes and enhancements

The main differences are:
1. Manager Approval task now uses Request Form (ID: 6) instead of Approval Form
2. Form is set to read-only mode for the approval task
3. Updated form field configurations based on actual usage
4. Improved action configurations with proper validation

## Related Documentation

- [CURRENT_NODE_UPDATE_FIX.md](../../../CURRENT_NODE_UPDATE_FIX.md) - Backend补偿机制
- [CURRENT_NODE_REFRESH_FIX.md](../../../CURRENT_NODE_REFRESH_FIX.md) - 前端刷新按钮
- [DEPLOYMENT_SUMMARY.md](../../../DEPLOYMENT_SUMMARY.md) - 部署总结
