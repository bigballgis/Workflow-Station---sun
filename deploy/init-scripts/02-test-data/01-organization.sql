-- =====================================================
-- TEST DATA: Organization Structure
-- Foreign Enterprise Bank Organization
-- Uses sys_business_units table (migrated from sys_departments)
-- =====================================================

-- Clear existing business unit data (except ROOT if exists)
DELETE FROM sys_business_units WHERE id NOT LIKE 'ROOT%';

-- Level 1: Head Office
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, created_at, updated_at)
VALUES ('DEPT-HQ', 'HQ', 'Head Office', NULL, 1, '/HQ', 1, 'ACTIVE', 'Foreign Enterprise Bank Head Office', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Front Office Departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-CORP-BANKING', 'CORP_BANKING', 'Corporate Banking', 'DEPT-HQ', 2, '/HQ/CORP_BANKING', 1, 'ACTIVE', 'Corporate Banking Division', 'CC-100', NOW(), NOW()),
('DEPT-RETAIL-BANKING', 'RETAIL_BANKING', 'Retail Banking', 'DEPT-HQ', 2, '/HQ/RETAIL_BANKING', 2, 'ACTIVE', 'Retail Banking Division', 'CC-200', NOW(), NOW()),
('DEPT-TREASURY', 'TREASURY', 'Treasury and Markets', 'DEPT-HQ', 2, '/HQ/TREASURY', 3, 'ACTIVE', 'Treasury and Markets Division', 'CC-300', NOW(), NOW()),
('DEPT-WEALTH-MGMT', 'WEALTH_MGMT', 'Wealth Management', 'DEPT-HQ', 2, '/HQ/WEALTH_MGMT', 4, 'ACTIVE', 'Wealth Management Division', 'CC-400', NOW(), NOW()),
('DEPT-INTL-BANKING', 'INTL_BANKING', 'International Banking', 'DEPT-HQ', 2, '/HQ/INTL_BANKING', 5, 'ACTIVE', 'International Banking Division', 'CC-500', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Middle Office Departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-RISK', 'RISK', 'Risk Management', 'DEPT-HQ', 2, '/HQ/RISK', 10, 'ACTIVE', 'Risk Management Division', 'CC-600', NOW(), NOW()),
('DEPT-COMPLIANCE', 'COMPLIANCE', 'Compliance', 'DEPT-HQ', 2, '/HQ/COMPLIANCE', 11, 'ACTIVE', 'Compliance Division', 'CC-610', NOW(), NOW()),
('DEPT-LEGAL', 'LEGAL', 'Legal Affairs', 'DEPT-HQ', 2, '/HQ/LEGAL', 12, 'ACTIVE', 'Legal Affairs Division', 'CC-620', NOW(), NOW()),
('DEPT-CREDIT', 'CREDIT', 'Credit Approval', 'DEPT-HQ', 2, '/HQ/CREDIT', 13, 'ACTIVE', 'Credit Approval Division', 'CC-630', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Back Office Departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-OPERATIONS', 'OPERATIONS', 'Operations', 'DEPT-HQ', 2, '/HQ/OPERATIONS', 20, 'ACTIVE', 'Operations Division', 'CC-700', NOW(), NOW()),
('DEPT-IT', 'IT', 'Information Technology', 'DEPT-HQ', 2, '/HQ/IT', 21, 'ACTIVE', 'Information Technology Division', 'CC-710', NOW(), NOW()),
('DEPT-FINANCE', 'FINANCE', 'Finance', 'DEPT-HQ', 2, '/HQ/FINANCE', 22, 'ACTIVE', 'Finance Division', 'CC-720', NOW(), NOW()),
('DEPT-HR', 'HR', 'Human Resources', 'DEPT-HQ', 2, '/HQ/HR', 23, 'ACTIVE', 'Human Resources Division', 'CC-730', NOW(), NOW()),
('DEPT-ADMIN', 'ADMIN', 'Administration', 'DEPT-HQ', 2, '/HQ/ADMIN', 24, 'ACTIVE', 'Administration Division', 'CC-740', NOW(), NOW()),
('DEPT-AUDIT', 'AUDIT', 'Internal Audit', 'DEPT-HQ', 2, '/HQ/AUDIT', 25, 'ACTIVE', 'Internal Audit Division', 'CC-750', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();
