# Digital Lending System V2 - Documentation Index

## 📚 Documentation Overview

This directory contains the complete English version of the Digital Lending System V2, a comprehensive loan application and approval system that demonstrates all core capabilities of the workflow platform.

## 📖 Documentation Files

### 1. README.md
**Complete System Documentation**
- System overview and features
- Data model details (7 tables, 92 fields)
- Form types and configurations (5 forms)
- Business actions (15 actions)
- Workflow process (8 nodes, 3 gateways)
- Deployment instructions
- Verification queries
- Troubleshooting guide

**When to read**: For comprehensive understanding of the system

### 2. QUICK_START.md
**Quick Start Guide**
- 5-minute deployment guide
- Prerequisites checklist
- Verification commands
- Test workflow scenarios
- Common operations
- Troubleshooting tips

**When to read**: For rapid deployment and testing

### 3. This File (INDEX.md)
**Documentation Index**
- Overview of all documentation
- Quick reference guide
- File descriptions

**When to read**: To navigate documentation

## 🗂️ SQL and Script Files

### Deployment Scripts

| File | Purpose | Execution Order |
|------|---------|-----------------|
| `00-create-virtual-groups.sql` | Create 4 virtual groups | 1 (Optional) |
| `01-create-digital-lending-complete.sql` | Create function unit with tables, forms, actions | 2 (Required) |
| `02-insert-bpmn-process.ps1` | Insert BPMN workflow definition | 3 (Required) |
| `03-bind-actions.sql` | Verify action bindings | 4 (Optional) |
| `deploy-all.ps1` | One-click deployment (runs all above) | - (Recommended) |

### BPMN Definition

| File | Purpose |
|------|---------|
| `digital-lending-process-v2-en.bpmn` | Complete workflow definition with 8 nodes and 3 gateways |

### Utility Scripts

| File | Purpose |
|------|---------|
| `translate-to-english.ps1` | Translation script (used during creation) |

## 🚀 Quick Reference

### Deployment Commands

```powershell
# One-click deployment
.\deploy-all.ps1

# Skip virtual groups if they exist
.\deploy-all.ps1 -SkipVirtualGroups

# Manual step-by-step
docker cp 00-create-virtual-groups.sql platform-postgres-dev:/tmp/
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/00-create-virtual-groups.sql
# ... (see QUICK_START.md for complete steps)
```

### Verification Commands

```powershell
# Check function unit
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT code, name, status FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN';"

# Check tables
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT COUNT(*) FROM dw_table_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');"

# Check forms
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT COUNT(*) FROM dw_form_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');"

# Check actions
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT COUNT(*) FROM dw_action_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');"
```

## 📊 System Components Summary

### Data Model
- **7 Tables**: 1 Main + 3 Sub + 3 Relation
- **92 Fields**: Complete loan application data model
- **Foreign Keys**: Proper relational structure

### Forms
- **5 Forms**: 3 Main + 2 Popup
- **20 Table Bindings**: Multiple tables per form
- **Read-only & Editable Modes**: Flexible data access

### Actions
- **15 Actions**: 6 different types
- **Process Control**: Submit, Withdraw, Approve, Reject
- **Form Popups**: Credit Check, Risk Assessment, Additional Info
- **API Calls**: EMI Calculation, Account Verification, Query
- **Risk Management**: Low Risk, High Risk markers

### Workflow
- **8 Nodes**: Complete approval process
- **3 Gateways**: Document, Credit Score, Risk Level
- **5 Assignment Methods**: Virtual Group, Role, User, Expression, Automatic
- **4 Virtual Groups**: Document Verifiers, Credit Officers, Risk Officers, Finance Team

## 🎯 Use Cases

### For Developers
- **Learn**: Study complete function unit structure
- **Reference**: Use as template for new function units
- **Test**: Verify platform capabilities

### For Business Analysts
- **Understand**: See how business processes map to workflows
- **Design**: Use as example for requirement gathering
- **Validate**: Test business rules and decision logic

### For System Administrators
- **Deploy**: Install complete working system
- **Configure**: Understand virtual groups and permissions
- **Monitor**: Track workflow execution

## 🔗 Related Documentation

### In This Directory
- README.md - Complete documentation
- QUICK_START.md - Quick start guide
- INDEX.md - This file

### In Parent Directories
- `../../docs/AI_FUNCTION_UNIT_GENERATION_FRAMEWORK.md` - AI generation framework
- `../../docs/AI_FUNCTION_UNIT_GENERATION_SUMMARY.md` - Framework summary
- `../07-digital-lending-v2/` - Chinese version

### External Links
- Developer Workstation: http://localhost:3002
- User Portal: http://localhost:3001

## 📋 Checklists

### Pre-Deployment Checklist
- [ ] Docker container running
- [ ] Database accessible
- [ ] Ports 3001 and 3002 available
- [ ] Virtual groups exist (or use -SkipVirtualGroups)

### Post-Deployment Checklist
- [ ] Function unit created
- [ ] Tables created (7 tables)
- [ ] Forms created (5 forms)
- [ ] Actions created (15 actions)
- [ ] BPMN process inserted
- [ ] Action bindings verified

### Testing Checklist
- [ ] Deploy in Developer Workstation
- [ ] Create loan application in User Portal
- [ ] Test document verification
- [ ] Test credit check
- [ ] Test risk assessment
- [ ] Test manager approval
- [ ] Test loan disbursement
- [ ] Verify approval history

## 🆘 Getting Help

### Troubleshooting Steps
1. Check README.md troubleshooting section
2. Review QUICK_START.md common problems
3. Verify Docker container status
4. Check PostgreSQL logs
5. Review deployment script output

### Common Issues
- **Container not running**: Start Docker container
- **Virtual groups exist**: Use -SkipVirtualGroups parameter
- **Function unit exists**: Delete existing or change code
- **BPMN fails**: Verify function unit created first

## 📈 Version Information

- **Version**: 1.0.0
- **Created**: 2026-02-06
- **Status**: Production Ready
- **Language**: English
- **Database**: workflow_platform_dev
- **Environment**: Development (Docker)

## 🎓 Learning Path

### Beginner
1. Read QUICK_START.md
2. Run deployment script
3. Test in User Portal
4. Review approval history

### Intermediate
1. Read README.md
2. Study data model structure
3. Understand form configurations
4. Explore action types

### Advanced
1. Review SQL files in detail
2. Study BPMN workflow definition
3. Understand decision gateway logic
4. Customize for specific requirements

---

**Start Here**: 
- New users → QUICK_START.md
- Detailed info → README.md
- Navigation → INDEX.md (this file)

**Deploy Now**: `.\deploy-all.ps1`
