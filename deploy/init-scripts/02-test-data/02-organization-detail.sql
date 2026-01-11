-- =====================================================
-- TEST DATA: Organization Structure (Continued)
-- Level 3 and Level 4 Departments
-- =====================================================

-- Level 3: Corporate Banking Sub-departments
INSERT INTO sys_departments (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-CORP-CLIENT', 'CORP_CLIENT', 'Corporate Client Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CORP_CLIENT', 1, 'ACTIVE', 'Corporate Client Department', 'CC-101', NOW(), NOW()),
('DEPT-CORP-CREDIT', 'CORP_CREDIT', 'Corporate Credit Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CORP_CREDIT', 2, 'ACTIVE', 'Corporate Credit Department', 'CC-102', NOW(), NOW()),
('DEPT-TRADE-FINANCE', 'TRADE_FINANCE', 'Trade Finance Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/TRADE_FINANCE', 3, 'ACTIVE', 'Trade Finance Department', 'CC-103', NOW(), NOW()),
('DEPT-CASH-MGMT', 'CASH_MGMT', 'Cash Management Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CASH_MGMT', 4, 'ACTIVE', 'Cash Management Department', 'CC-104', NOW(), NOW()),
('DEPT-TRANSACTION', 'TRANSACTION', 'Transaction Banking', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/TRANSACTION', 5, 'ACTIVE', 'Transaction Banking Department', 'CC-105', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 3: IT Sub-departments
INSERT INTO sys_departments (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-IT-DEV', 'IT_DEV', 'Application Development', 'DEPT-IT', 3, '/HQ/IT/IT_DEV', 1, 'ACTIVE', 'Application Development', 'CC-711', NOW(), NOW()),
('DEPT-IT-INFRA', 'IT_INFRA', 'Infrastructure', 'DEPT-IT', 3, '/HQ/IT/IT_INFRA', 2, 'ACTIVE', 'Infrastructure Department', 'CC-712', NOW(), NOW()),
('DEPT-IT-SECURITY', 'IT_SECURITY', 'Information Security', 'DEPT-IT', 3, '/HQ/IT/IT_SECURITY', 3, 'ACTIVE', 'Information Security', 'CC-713', NOW(), NOW()),
('DEPT-IT-DATA', 'IT_DATA', 'Data Management', 'DEPT-IT', 3, '/HQ/IT/IT_DATA', 4, 'ACTIVE', 'Data Management', 'CC-714', NOW(), NOW()),
('DEPT-IT-ARCH', 'IT_ARCH', 'Enterprise Architecture', 'DEPT-IT', 3, '/HQ/IT/IT_ARCH', 5, 'ACTIVE', 'Enterprise Architecture', 'CC-715', NOW(), NOW()),
('DEPT-IT-PMO', 'IT_PMO', 'Project Management Office', 'DEPT-IT', 3, '/HQ/IT/IT_PMO', 6, 'ACTIVE', 'Project Management Office', 'CC-716', NOW(), NOW()),
('DEPT-IT-OPS', 'IT_OPS', 'IT Operations Center', 'DEPT-IT', 3, '/HQ/IT/IT_OPS', 7, 'ACTIVE', 'IT Operations Center', 'CC-717', NOW(), NOW()),
('DEPT-IT-TEST', 'IT_TEST', 'Testing Center', 'DEPT-IT', 3, '/HQ/IT/IT_TEST', 8, 'ACTIVE', 'Testing Center', 'CC-718', NOW(), NOW()),
('DEPT-IT-BA', 'IT_BA', 'Business Analysis Dept', 'DEPT-IT', 3, '/HQ/IT/IT_BA', 9, 'ACTIVE', 'Business Analysis Department', 'CC-719', NOW(), NOW()),
('DEPT-IT-DEVOPS', 'IT_DEVOPS', 'DevOps Center', 'DEPT-IT', 3, '/HQ/IT/IT_DEVOPS', 10, 'ACTIVE', 'DevOps Center', 'CC-720', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 4: Development Teams
INSERT INTO sys_departments (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-DEV-CORE', 'DEV_CORE', 'Core Banking Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_CORE', 1, 'ACTIVE', 'Core Banking Development Team', 'CC-711-1', NOW(), NOW()),
('DEPT-DEV-CHANNEL', 'DEV_CHANNEL', 'Channel Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_CHANNEL', 2, 'ACTIVE', 'Channel Development Team', 'CC-711-2', NOW(), NOW()),
('DEPT-DEV-RISK', 'DEV_RISK', 'Risk System Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_RISK', 3, 'ACTIVE', 'Risk System Development Team', 'CC-711-3', NOW(), NOW()),
('DEPT-DEV-DATA', 'DEV_DATA', 'Data Platform Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_DATA', 4, 'ACTIVE', 'Data Platform Development Team', 'CC-711-4', NOW(), NOW()),
('DEPT-DEV-QA', 'DEV_QA', 'Quality Assurance Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_QA', 5, 'ACTIVE', 'Quality Assurance Team', 'CC-711-5', NOW(), NOW()),
('DEPT-DEV-MOBILE', 'DEV_MOBILE', 'Mobile Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_MOBILE', 6, 'ACTIVE', 'Mobile Development Team', 'CC-711-6', NOW(), NOW()),
('DEPT-DEV-WEB', 'DEV_WEB', 'Web Frontend Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_WEB', 7, 'ACTIVE', 'Web Frontend Development Team', 'CC-711-7', NOW(), NOW()),
('DEPT-DEV-API', 'DEV_API', 'API Platform Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_API', 8, 'ACTIVE', 'API Platform Development Team', 'CC-711-8', NOW(), NOW()),
('DEPT-DEV-AI', 'DEV_AI', 'AI/ML Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_AI', 9, 'ACTIVE', 'AI/ML Development Team', 'CC-711-9', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Branches
INSERT INTO sys_departments (id, code, name, parent_id, level, path, sort_order, status, description, location, created_at, updated_at) VALUES 
('DEPT-BRANCH-SH', 'BRANCH_SH', 'Shanghai Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_SH', 100, 'ACTIVE', 'Shanghai Branch', 'Shanghai Pudong Lujiazui', NOW(), NOW()),
('DEPT-BRANCH-BJ', 'BRANCH_BJ', 'Beijing Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_BJ', 101, 'ACTIVE', 'Beijing Branch', 'Beijing Chaoyang Financial Street', NOW(), NOW()),
('DEPT-BRANCH-GZ', 'BRANCH_GZ', 'Guangzhou Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_GZ', 102, 'ACTIVE', 'Guangzhou Branch', 'Guangzhou Tianhe Zhujiang New Town', NOW(), NOW()),
('DEPT-BRANCH-SZ', 'BRANCH_SZ', 'Shenzhen Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_SZ', 103, 'ACTIVE', 'Shenzhen Branch', 'Shenzhen Futian CBD', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

COMMENT ON TABLE sys_departments IS 'Test organization structure for Foreign Enterprise Bank';
