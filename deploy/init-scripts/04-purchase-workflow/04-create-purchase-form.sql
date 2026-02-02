-- =====================================================
-- Purchase Workflow - Form Definition
-- =====================================================
-- This script creates the purchase request form
-- =====================================================

\echo '========================================='
\echo 'Creating Purchase Request Form...'
\echo '========================================='

-- Create Purchase Request Form
INSERT INTO dw_forms (id, function_unit_id, code, name, description, type, status, created_at, updated_at, created_by)
VALUES 
(
    'form-purchase-request',
    'fu-purchase-001',
    'purchase_request_form',
    '采购申请表单',
    '用于提交采购申请的表单',
    'MAIN',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (function_unit_id, code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Purchase request form created'
\echo ''

-- Bind Main Table to Form
INSERT INTO dw_form_table_bindings (id, form_id, table_id, binding_type, created_at, created_by)
VALUES 
(
    'ftb-purchase-main',
    'form-purchase-request',
    'tbl-purchase-main',
    'MAIN',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (form_id, table_id) DO NOTHING;

-- Bind Sub Table to Form
INSERT INTO dw_form_table_bindings (id, form_id, table_id, binding_type, created_at, created_by)
VALUES 
(
    'ftb-purchase-items',
    'form-purchase-request',
    'tbl-purchase-items',
    'SUB',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (form_id, table_id) DO NOTHING;

\echo '✓ Form table bindings created'
\echo ''

\echo '========================================='
\echo 'Form Definition Summary'
\echo '========================================='
\echo 'Form: purchase_request_form (采购申请表单)'
\echo 'Type: MAIN'
\echo 'Status: ACTIVE'
\echo ''
\echo 'Bound Tables:'
\echo '  - purchase_request (MAIN)'
\echo '  - purchase_items (SUB)'
\echo '========================================='
