-- =====================================================
-- Purchase Workflow - Process Definition
-- =====================================================
-- This script creates a simple purchase approval process
-- =====================================================

\echo '========================================='
\echo 'Creating Purchase Approval Process...'
\echo '========================================='

-- Create Purchase Approval Process
INSERT INTO dw_process_definitions (
    id, 
    function_unit_id, 
    code, 
    name, 
    description, 
    version, 
    bpmn_xml, 
    status, 
    created_at, 
    updated_at, 
    created_by
)
VALUES 
(
    'proc-purchase-approval',
    'fu-purchase-001',
    'purchase_approval',
    '采购审批流程',
    '采购申请的审批流程：申请人提交 → 部门经理审批 → 财务审批 → 完成',
    1,
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://workflow.platform.com/purchase">
  <process id="purchase_approval" name="采购审批流程" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <userTask id="dept_manager_approval" name="部门经理审批" flowable:candidateGroups="MANAGERS"/>
    <userTask id="finance_approval" name="财务审批" flowable:candidateGroups="MANAGERS"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow sourceRef="start" targetRef="dept_manager_approval"/>
    <sequenceFlow sourceRef="dept_manager_approval" targetRef="finance_approval"/>
    <sequenceFlow sourceRef="finance_approval" targetRef="end"/>
  </process>
</definitions>',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (function_unit_id, code, version) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    bpmn_xml = EXCLUDED.bpmn_xml,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Purchase approval process created'
\echo ''

\echo '========================================='
\echo 'Process Definition Summary'
\echo '========================================='
\echo 'Process: purchase_approval (采购审批流程)'
\echo 'Version: 1'
\echo 'Status: ACTIVE'
\echo ''
\echo 'Process Flow:'
\echo '  1. 开始 (Start)'
\echo '  2. 部门经理审批 (Department Manager Approval)'
\echo '  3. 财务审批 (Finance Approval)'
\echo '  4. 结束 (End)'
\echo ''
\echo 'Approval Groups:'
\echo '  - Department Manager: MANAGERS group'
\echo '  - Finance: MANAGERS group'
\echo '========================================='
