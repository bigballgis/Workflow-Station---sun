-- =====================================================
-- Purchase Workflow - Business Tables
-- =====================================================
-- This script creates business tables for the purchase workflow
-- =====================================================

\echo '========================================='
\echo 'Creating Purchase Business Tables...'
\echo '========================================='

-- Main Purchase Request Table
INSERT INTO dw_tables (id, function_unit_id, code, name, description, type, status, created_at, updated_at, created_by)
VALUES 
(
    'tbl-purchase-main',
    'fu-purchase-001',
    'purchase_request',
    '采购申请主表',
    '存储采购申请的主要信息',
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

-- Purchase Items Sub Table
INSERT INTO dw_tables (id, function_unit_id, code, name, description, type, status, created_at, updated_at, created_by)
VALUES 
(
    'tbl-purchase-items',
    'fu-purchase-001',
    'purchase_items',
    '采购明细子表',
    '存储采购申请的明细项目',
    'SUB',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (function_unit_id, code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Purchase business tables created'
\echo ''

\echo '========================================='
\echo 'Business Tables Summary'
\echo '========================================='
\echo 'Main Table:'
\echo '  - purchase_request (采购申请主表)'
\echo ''
\echo 'Sub Tables:'
\echo '  - purchase_items (采购明细子表)'
\echo '========================================='
