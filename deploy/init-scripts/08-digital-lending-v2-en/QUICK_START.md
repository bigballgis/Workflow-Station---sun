# Digital Lending System V2 - Quick Start Guide

## 🚀 Quick Deployment (5 Minutes)

### Step 1: Navigate to Directory
```powershell
cd deploy/init-scripts/08-digital-lending-v2-en
```

### Step 2: Run Deployment Script
```powershell
.\deploy-all.ps1
```

### Step 3: Deploy in Developer Workstation
1. Open http://localhost:3002
2. Find "Digital Lending System V2"
3. Click "Deploy"

### Step 4: Test in User Portal
1. Open http://localhost:3001
2. Create new loan application
3. Submit and track workflow

## ✅ What Gets Deployed

| Component | Count | Description |
|-----------|-------|-------------|
| Tables | 7 | 1 Main + 3 Sub + 3 Relation |
| Fields | 92 | Complete data model |
| Forms | 5 | 3 Main + 2 Popup |
| Actions | 15 | 6 different types |
| Process Nodes | 8 | Including 3 gateways |
| Virtual Groups | 4 | Task assignment groups |

## 📋 Prerequisites Checklist

- [ ] Docker container `platform-postgres-dev` is running
- [ ] Database `workflow_platform_dev` is accessible
- [ ] Port 3001 (User Portal) is available
- [ ] Port 3002 (Developer Workstation) is available

## 🔍 Verification Commands

### Check Deployment Status
```powershell
# Check function unit
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT code, name, status FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN';"

# Check table count
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT COUNT(*) FROM dw_table_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');"

# Check action count
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT COUNT(*) FROM dw_action_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN');"
```

## 🎯 Test Workflow

### Complete Test Scenario

1. **Submit Application** (Customer)
   - Fill in applicant information
   - Enter financial details
   - Add collateral information
   - Upload documents
   - Submit

2. **Verify Documents** (Document Verifier)
   - Review uploaded documents
   - Verify completeness
   - Approve or reject

3. **Perform Credit Check** (Credit Officer)
   - Open credit check form
   - Enter credit bureau data
   - Record credit score
   - Save results

4. **Assess Risk** (Risk Officer)
   - Review application details
   - Evaluate credit report
   - Assign risk rating
   - Complete assessment

5. **Manager Approval** (Manager)
   - Review complete application
   - Check risk assessment
   - Approve or reject
   - Add comments

6. **Process Disbursement** (Finance Team)
   - Verify account details
   - Process payment
   - Record disbursement date
   - Complete workflow

## 🛠️ Common Operations

### Skip Virtual Group Creation
If virtual groups already exist:
```powershell
.\deploy-all.ps1 -SkipVirtualGroups
```

### Manual Step-by-Step Deployment
```powershell
# Step 1: Virtual groups
docker cp 00-create-virtual-groups.sql platform-postgres-dev:/tmp/
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/00-create-virtual-groups.sql

# Step 2: Function unit
docker cp 01-create-digital-lending-complete.sql platform-postgres-dev:/tmp/
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/01-create-digital-lending-complete.sql

# Step 3: BPMN process
.\02-insert-bpmn-process.ps1

# Step 4: Verify bindings
docker cp 03-bind-actions.sql platform-postgres-dev:/tmp/
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/03-bind-actions.sql
```

## 🐛 Troubleshooting

### Problem: Container not running
```powershell
# Check container status
docker ps | Select-String "platform-postgres-dev"

# Start container if needed
docker start platform-postgres-dev
```

### Problem: Virtual groups already exist
```powershell
# Use skip parameter
.\deploy-all.ps1 -SkipVirtualGroups
```

### Problem: Function unit already exists
```sql
-- Delete existing function unit
DELETE FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN';
```

### Problem: BPMN insertion fails
```powershell
# Check if function unit exists
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN';"

# Re-run BPMN insertion
.\02-insert-bpmn-process.ps1
```

## 📊 Expected Output

### Successful Deployment
```
========================================
Digital Lending System V2 - Deployment
========================================

Checking Docker container status...
  ✓ Docker container is running

Step 1/4: Creating virtual groups...
  ✓ Virtual groups created successfully

Step 2/4: Creating function unit (tables, forms, actions)...
  ✓ Function unit created successfully

Step 3/4: Inserting BPMN process...
  ✓ BPMN process inserted successfully

Step 4/4: Verifying action bindings...
  ✓ Action bindings verified successfully

========================================
Deployment Complete!
========================================
```

## 🎓 Learning Resources

### Key Concepts Demonstrated
1. **Hierarchical Data Model**: Main → Sub → Relation tables
2. **Form Types**: Main forms vs Popup forms
3. **Action Types**: 6 different action types
4. **Task Assignment**: 5 different assignment methods
5. **Decision Gateways**: Conditional workflow routing
6. **Virtual Groups**: Role-based task assignment

### Files to Study
- `01-create-digital-lending-complete.sql` - Complete data model
- `digital-lending-process-v2-en.bpmn` - Workflow definition
- `README.md` - Detailed documentation

## 🔗 Quick Links

- **Developer Workstation**: http://localhost:3002
- **User Portal**: http://localhost:3001
- **Full Documentation**: README.md
- **AI Generation Framework**: ../../docs/AI_FUNCTION_UNIT_GENERATION_FRAMEWORK.md

## ⏱️ Time Estimates

| Task | Time |
|------|------|
| Deployment | 2-3 minutes |
| Deploy in Workstation | 1 minute |
| Complete Test Workflow | 10-15 minutes |
| **Total** | **15-20 minutes** |

## 📝 Next Steps

After successful deployment:

1. ✅ Explore the data model in Developer Workstation
2. ✅ Test each form type (main and popup)
3. ✅ Try all 15 business actions
4. ✅ Complete a full workflow from submission to disbursement
5. ✅ Review approval history and audit trail
6. ✅ Test decision gateway conditions
7. ✅ Experiment with different risk ratings

## 💡 Tips

- **Use realistic test data** for better understanding
- **Test rejection scenarios** to see error handling
- **Try withdrawing applications** to test cancellation flow
- **Review approval history** after each step
- **Check virtual group assignments** in each task
- **Test popup forms** for quick data entry
- **Verify API actions** like EMI calculation

---

**Need Help?** Check README.md for detailed documentation or review troubleshooting section above.

**Ready to Deploy?** Run `.\deploy-all.ps1` now!
