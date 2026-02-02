-- =====================================================
-- Purchase Workflow - Function Unit
-- =====================================================
-- This script creates the Purchase Management function unit
-- =====================================================

\echo '========================================='
\echo 'Creating Purchase Function Unit...'
\echo '========================================='

-- Create Purchase Management Function Unit
INSERT INTO dw_function_units (code, name, description, status, created_by, created_at, updated_at)
VALUES 
(
    'PURCHASE',
    '采购管理',
    '采购申请、审批和管理流程',
    'PUBLISHED',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Purchase function unit created'
\echo ''

\echo '========================================='
\echo 'Function Unit Summary'
\echo '========================================='
\echo 'Code: PURCHASE'
\echo 'Name: 采购管理'
\echo 'Description: 采购申请、审批和管理流程'
\echo 'Status: ACTIVE'
\echo '========================================='
