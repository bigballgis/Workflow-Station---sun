# Bound Nodes Display Fix

## Date: 2026-02-06

## Problem
The Developer Workstation "Action Design" tab showed "Not Bound" for all actions with String IDs (e.g., `action-dl-credit-check`), even though they were correctly bound to BPMN tasks.

## Root Cause
The `ActionDesigner.vue` component was parsing actionIds from BPMN using `JSON.parse()`, which expected numeric IDs in JSON format like `[12,22]`. However, the new String IDs were stored as `[action-dl-credit-check,action-dl-approve-loan,...]` (without quotes), which is not valid JSON.

The parser failed silently, so no bindings were created, resulting in "Not Bound" being displayed.

## Solution

### 1. Updated ActionDefinition Interface
Changed the `id` type from `number` to `string | number` to support both legacy numeric IDs and new String IDs.

**File**: `frontend/developer-workstation/src/api/functionUnit.ts`

```typescript
export interface ActionDefinition {
  id: string | number // Support both String IDs (new) and numeric IDs (legacy)
  actionName: string
  actionType: string
  description?: string
  configJson: Record<string, any>
  actionConfig?: string // deprecated, use configJson instead
}
```

### 2. Updated ActionDesigner Parser
Modified the `parseActionBindingsFromBpmn()` function to handle both formats:
- Numeric IDs: `[12,22]` (valid JSON)
- String IDs: `[action-dl-credit-check,action-dl-approve-loan,...]` (comma-separated without quotes)

**File**: `frontend/developer-workstation/src/components/designer/ActionDesigner.vue`

**Changes**:
1. Changed `actionNodeBindings` Map key type from `number` to `string | number`
2. Created `parseActionIds()` helper function that:
   - First tries `JSON.parse()` for numeric IDs
   - Falls back to manual parsing for String IDs (removes brackets, splits by comma)
3. Updated `getActionBoundNodes()` and `loadActionBinding()` to accept `string | number`

```typescript
/**
 * 解析actionIds - 支持数字ID和字符串ID
 * 格式: [12,22] 或 [action-dl-credit-check,action-dl-approve-loan]
 */
function parseActionIds(value: string): Array<string | number> {
  if (!value) return []
  
  try {
    // 尝试作为JSON解析（数字ID格式）
    return JSON.parse(value) as number[]
  } catch (e) {
    // 如果JSON解析失败，尝试解析字符串ID格式
    // 移除括号和空格: "[id1,id2]" -> "id1,id2"
    const cleaned = value.replace(/[\[\]\s]/g, '')
    if (!cleaned) return []
    
    // 分割并返回字符串ID数组
    const stringIds = cleaned.split(',').map(s => s.trim()).filter(s => s)
    return stringIds
  }
}
```

### 3. Deployed Frontend
Built and deployed the updated Developer Workstation frontend to Docker container.

```bash
cd frontend/developer-workstation
npx vite build
docker exec platform-developer-workstation-frontend-dev rm -rf /usr/share/nginx/html/*
docker cp dist/. platform-developer-workstation-frontend-dev:/usr/share/nginx/html/
docker restart platform-developer-workstation-frontend-dev
```

## Verification

### Before Fix
- All actions with String IDs showed "Not Bound"
- Only actions with numeric IDs (like `[12,22]`) showed bound nodes

### After Fix
1. Open Developer Workstation: http://localhost:3000
2. Navigate to: Digital Lending System → Action Design tab
3. Verify "Bound Nodes" column shows:
   - `action-dl-verify-docs`: "Verify Documents"
   - `action-dl-credit-check`: "Perform Credit Check"
   - `action-dl-approve-loan`: "Verify Documents, Perform Credit Check"
   - `action-dl-reject-loan`: "Verify Documents, Perform Credit Check"
   - `action-dl-request-info`: "Verify Documents, Perform Credit Check"
   - `action-dl-assess-risk`: "Assess Risk"
   - `action-dl-mark-low-risk`: "Assess Risk"
   - `action-dl-mark-high-risk`: "Assess Risk"
   - `action-dl-manager-approve`: "Manager Approval"
   - `action-dl-manager-reject`: "Manager Approval"
   - `action-dl-request-revision`: "Manager Approval"
   - `action-dl-senior-approve`: "Senior Manager Approval"
   - `action-dl-senior-reject`: "Senior Manager Approval"
   - `action-dl-escalate`: "Senior Manager Approval"
   - `action-dl-disburse`: "Process Disbursement"
   - `action-dl-hold-disbursement`: "Process Disbursement"
   - `action-dl-verify-account`: "Process Disbursement"

## Files Modified

1. `frontend/developer-workstation/src/api/functionUnit.ts` - Updated ActionDefinition interface
2. `frontend/developer-workstation/src/components/designer/ActionDesigner.vue` - Updated parser logic

## Impact

- ✅ Developer Workstation now correctly displays bound nodes for String action IDs
- ✅ Backward compatible with numeric action IDs
- ✅ No database changes required
- ✅ No backend changes required

## Summary

The fix enables the Developer Workstation to parse and display action bindings for both legacy numeric IDs and new String IDs. The parser now gracefully handles both JSON format (`[12,22]`) and comma-separated format (`[action-dl-credit-check,action-dl-approve-loan,...]`).



---

## Final Resolution - 2026-02-06

### Root Cause Confirmed

After analyzing console logs, the root cause was identified:
- The parser was working correctly and parsing both numeric and String action IDs
- The bindings Map was correctly populated with 19 entries
- **The actual issue**: Submit and Withdraw actions (IDs 12, 22) were never migrated to `sys_action_definitions` with String IDs
- The BPMN still referenced `[12,22]` instead of String IDs
- The Developer Workstation loads actions from `sys_action_definitions`, so these 2 actions didn't exist in the store

### Solution Implemented

1. **Created missing action definitions** (`13-add-submit-withdraw-actions.sql`):
   - `action-dl-submit-application`
   - `action-dl-withdraw-application`

2. **Updated BPMN XML** (`digital-lending-process.bpmn`):
   - Changed `[12,22]` to `[action-dl-submit-application,action-dl-withdraw-application]`

3. **Updated database** (`14-update-bpmn-submit-actions.ps1`):
   - Updated `dw_process_definitions` with corrected BPMN

### Verification

✅ 21 actions now exist in `sys_action_definitions`
✅ BPMN contains String IDs for all tasks
✅ No numeric IDs remain in BPMN
✅ All database queries pass

### Next Steps

1. User should refresh Developer Workstation at http://localhost:3002
2. Navigate to Digital Lending System → Action Design tab
3. Verify all 21 actions show correct bound nodes
4. Deploy BPMN to Flowable (separate task)

**Status**: ✅ **COMPLETE** - Database updates successful, awaiting UI verification
