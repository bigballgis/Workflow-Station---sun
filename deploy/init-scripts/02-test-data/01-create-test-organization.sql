-- =====================================================
-- Test Organization Data
-- =====================================================
-- This script creates a sample organization structure for testing
-- =====================================================

\echo '========================================='
\echo 'Creating Test Organization Structure...'
\echo '========================================='

-- Root Company
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, status, description, created_at, updated_at)
VALUES 
('bu-root', 'ROOT', '示例科技有限公司', NULL, 1, '/ROOT/', 'ACTIVE', '公司总部', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- Level 2: Departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, status, description, created_at, updated_at)
VALUES 
('bu-it', 'IT_DEPT', 'IT部门', 'bu-root', 2, '/ROOT/IT_DEPT/', 'ACTIVE', 'IT技术部门', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-hr', 'HR_DEPT', '人力资源部', 'bu-root', 2, '/ROOT/HR_DEPT/', 'ACTIVE', '人力资源管理部门', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-finance', 'FINANCE_DEPT', '财务部', 'bu-root', 2, '/ROOT/FINANCE_DEPT/', 'ACTIVE', '财务管理部门', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-sales', 'SALES_DEPT', '销售部', 'bu-root', 2, '/ROOT/SALES_DEPT/', 'ACTIVE', '销售业务部门', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-purchase', 'PURCHASE_DEPT', '采购部', 'bu-root', 2, '/ROOT/PURCHASE_DEPT/', 'ACTIVE', '采购管理部门', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- Level 3: Teams
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, status, description, created_at, updated_at)
VALUES 
('bu-it-dev', 'IT_DEV_TEAM', '开发团队', 'bu-it', 3, '/ROOT/IT_DEPT/IT_DEV_TEAM/', 'ACTIVE', '软件开发团队', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-it-ops', 'IT_OPS_TEAM', '运维团队', 'bu-it', 3, '/ROOT/IT_DEPT/IT_OPS_TEAM/', 'ACTIVE', '系统运维团队', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-hr-recruit', 'HR_RECRUIT_TEAM', '招聘团队', 'bu-hr', 3, '/ROOT/HR_DEPT/HR_RECRUIT_TEAM/', 'ACTIVE', '人才招聘团队', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-finance-ap', 'FINANCE_AP_TEAM', '应付账款团队', 'bu-finance', 3, '/ROOT/FINANCE_DEPT/FINANCE_AP_TEAM/', 'ACTIVE', '应付账款管理团队', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Organization structure created successfully'
\echo ''

\echo '========================================='
\echo 'Assigning Users to Business Units...'
\echo '========================================='

-- Create user-business unit associations
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
VALUES 
('ubu-manager-finance', 'user-manager', 'bu-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-developer-it', 'user-developer', 'bu-it-dev', CURRENT_TIMESTAMP, 'system'),
('ubu-designer-it', 'user-designer', 'bu-it', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

\echo '✓ Users assigned to business units'
\echo ''

\echo '========================================='
\echo 'Organization Structure Summary'
\echo '========================================='
\echo 'Company:'
\echo '  └─ 示例科技有限公司 (ROOT)'
\echo ''
\echo 'Departments (5):'
\echo '  ├─ IT部门 (IT_DEPT)'
\echo '  ├─ 人力资源部 (HR_DEPT)'
\echo '  ├─ 财务部 (FINANCE_DEPT)'
\echo '  ├─ 销售部 (SALES_DEPT)'
\echo '  └─ 采购部 (PURCHASE_DEPT)'
\echo ''
\echo 'Teams (4):'
\echo '  ├─ 开发团队 (IT_DEV_TEAM)'
\echo '  ├─ 运维团队 (IT_OPS_TEAM)'
\echo '  ├─ 招聘团队 (HR_RECRUIT_TEAM)'
\echo '  └─ 应付账款团队 (FINANCE_AP_TEAM)'
\echo '========================================='
