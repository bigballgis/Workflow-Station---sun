-- =====================================================
-- 补丁：添加缺失的业务单元和用户业务单元关联数据
-- 执行位置：在 sys_users 数据插入之后，sys_virtual_groups 数据插入之前
-- =====================================================

-- =====================================================
-- 1. Organization Structure (Business Units)
-- =====================================================

-- Level 1: Head Office
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, created_at, updated_at)
VALUES ('DEPT-HQ', 'HQ', 'Head Office', NULL, 1, '/HQ', 1, 'ACTIVE', 'Foreign Enterprise Bank Head Office', NOW(), NOW());

-- Level 2: Front Office Departments
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-CORP-BANKING', 'CORP_BANKING', 'Corporate Banking', 'DEPT-HQ', 2, '/HQ/CORP_BANKING', 1, 'ACTIVE', 'Corporate Banking Division', 'CC-100', NOW(), NOW()),
('DEPT-RETAIL-BANKING', 'RETAIL_BANKING', 'Retail Banking', 'DEPT-HQ', 2, '/HQ/RETAIL_BANKING', 2, 'ACTIVE', 'Retail Banking Division', 'CC-200', NOW(), NOW()),
('DEPT-TREASURY', 'TREASURY', 'Treasury and Markets', 'DEPT-HQ', 2, '/HQ/TREASURY', 3, 'ACTIVE', 'Treasury and Markets Division', 'CC-300', NOW(), NOW()),
('DEPT-INTL-BANKING', 'INTL_BANKING', 'International Banking', 'DEPT-HQ', 2, '/HQ/INTL_BANKING', 4, 'ACTIVE', 'International Banking Division', 'CC-400', NOW(), NOW()),
('DEPT-WEALTH-MGMT', 'WEALTH_MGMT', 'Wealth Management', 'DEPT-HQ', 2, '/HQ/WEALTH_MGMT', 5, 'ACTIVE', 'Wealth Management Division', 'CC-500', NOW(), NOW());

-- Level 2: Middle Office Departments
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-RISK', 'RISK', 'Risk Management', 'DEPT-HQ', 2, '/HQ/RISK', 10, 'ACTIVE', 'Risk Management Division', 'CC-600', NOW(), NOW()),
('DEPT-COMPLIANCE', 'COMPLIANCE', 'Compliance', 'DEPT-HQ', 2, '/HQ/COMPLIANCE', 11, 'ACTIVE', 'Compliance Division', 'CC-610', NOW(), NOW()),
('DEPT-CREDIT', 'CREDIT', 'Credit Approval', 'DEPT-HQ', 2, '/HQ/CREDIT', 12, 'ACTIVE', 'Credit Approval Division', 'CC-620', NOW(), NOW()),
('DEPT-LEGAL', 'LEGAL', 'Legal Affairs', 'DEPT-HQ', 2, '/HQ/LEGAL', 13, 'ACTIVE', 'Legal Affairs Division', 'CC-630', NOW(), NOW()),
('DEPT-AUDIT', 'AUDIT', 'Internal Audit', 'DEPT-HQ', 2, '/HQ/AUDIT', 14, 'ACTIVE', 'Internal Audit Division', 'CC-640', NOW(), NOW());

-- Level 2: Back Office Departments
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-OPERATIONS', 'OPERATIONS', 'Operations', 'DEPT-HQ', 2, '/HQ/OPERATIONS', 20, 'ACTIVE', 'Operations Division', 'CC-700', NOW(), NOW()),
('DEPT-IT', 'IT', 'Information Technology', 'DEPT-HQ', 2, '/HQ/IT', 21, 'ACTIVE', 'Information Technology Division', 'CC-710', NOW(), NOW()),
('DEPT-FINANCE', 'FINANCE', 'Finance', 'DEPT-HQ', 2, '/HQ/FINANCE', 22, 'ACTIVE', 'Finance Division', 'CC-720', NOW(), NOW()),
('DEPT-HR', 'HR', 'Human Resources', 'DEPT-HQ', 2, '/HQ/HR', 23, 'ACTIVE', 'Human Resources Division', 'CC-730', NOW(), NOW()),
('DEPT-ADMIN', 'ADMIN', 'Administration', 'DEPT-HQ', 2, '/HQ/ADMIN', 24, 'ACTIVE', 'Administration Division', 'CC-740', NOW(), NOW());

-- Level 2: Branches
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-BRANCH-BJ', 'BRANCH_BJ', 'Beijing Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_BJ', 30, 'ACTIVE', 'Beijing Branch', 'CC-800', NOW(), NOW()),
('DEPT-BRANCH-SH', 'BRANCH_SH', 'Shanghai Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_SH', 31, 'ACTIVE', 'Shanghai Branch', 'CC-810', NOW(), NOW()),
('DEPT-BRANCH-GZ', 'BRANCH_GZ', 'Guangzhou Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_GZ', 32, 'ACTIVE', 'Guangzhou Branch', 'CC-820', NOW(), NOW()),
('DEPT-BRANCH-SZ', 'BRANCH_SZ', 'Shenzhen Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_SZ', 33, 'ACTIVE', 'Shenzhen Branch', 'CC-830', NOW(), NOW());

-- Level 3: Corporate Banking Sub-departments
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-CORP-CLIENT', 'CORP_CLIENT', 'Corporate Client Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CORP_CLIENT', 1, 'ACTIVE', 'Corporate Client Department', 'CC-101', NOW(), NOW()),
('DEPT-CORP-CREDIT', 'CORP_CREDIT', 'Corporate Credit Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CORP_CREDIT', 2, 'ACTIVE', 'Corporate Credit Department', 'CC-102', NOW(), NOW()),
('DEPT-TRADE-FINANCE', 'TRADE_FINANCE', 'Trade Finance Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/TRADE_FINANCE', 3, 'ACTIVE', 'Trade Finance Department', 'CC-103', NOW(), NOW()),
('DEPT-CASH-MGMT', 'CASH_MGMT', 'Cash Management Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CASH_MGMT', 4, 'ACTIVE', 'Cash Management Department', 'CC-104', NOW(), NOW()),
('DEPT-TRANSACTION', 'TRANSACTION', 'Transaction Banking', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/TRANSACTION', 5, 'ACTIVE', 'Transaction Banking Department', 'CC-105', NOW(), NOW());

-- Level 3: IT Sub-departments
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-IT-DEV', 'IT_DEV', 'Application Development', 'DEPT-IT', 3, '/HQ/IT/IT_DEV', 1, 'ACTIVE', 'Application Development', 'CC-711', NOW(), NOW()),
('DEPT-IT-INFRA', 'IT_INFRA', 'Infrastructure', 'DEPT-IT', 3, '/HQ/IT/IT_INFRA', 2, 'ACTIVE', 'Infrastructure Department', 'CC-712', NOW(), NOW()),
('DEPT-IT-SECURITY', 'IT_SECURITY', 'Information Security', 'DEPT-IT', 3, '/HQ/IT/IT_SECURITY', 3, 'ACTIVE', 'Information Security', 'CC-713', NOW(), NOW()),
('DEPT-IT-OPS', 'IT_OPS', 'IT Operations Center', 'DEPT-IT', 3, '/HQ/IT/IT_OPS', 4, 'ACTIVE', 'IT Operations Center', 'CC-714', NOW(), NOW()),
('DEPT-IT-DATA', 'IT_DATA', 'Data Management', 'DEPT-IT', 3, '/HQ/IT/IT_DATA', 5, 'ACTIVE', 'Data Management', 'CC-715', NOW(), NOW()),
('DEPT-IT-ARCH', 'IT_ARCH', 'Enterprise Architecture', 'DEPT-IT', 3, '/HQ/IT/IT_ARCH', 6, 'ACTIVE', 'Enterprise Architecture', 'CC-716', NOW(), NOW()),
('DEPT-IT-PMO', 'IT_PMO', 'Project Management Office', 'DEPT-IT', 3, '/HQ/IT/IT_PMO', 7, 'ACTIVE', 'Project Management Office', 'CC-717', NOW(), NOW()),
('DEPT-IT-BA', 'IT_BA', 'Business Analysis Dept', 'DEPT-IT', 3, '/HQ/IT/IT_BA', 8, 'ACTIVE', 'Business Analysis Department', 'CC-718', NOW(), NOW()),
('DEPT-IT-TEST', 'IT_TEST', 'Testing Center', 'DEPT-IT', 3, '/HQ/IT/IT_TEST', 9, 'ACTIVE', 'Testing Center', 'CC-719', NOW(), NOW()),
('DEPT-IT-DEVOPS', 'IT_DEVOPS', 'DevOps Center', 'DEPT-IT', 3, '/HQ/IT/IT_DEVOPS', 10, 'ACTIVE', 'DevOps Center', 'CC-720', NOW(), NOW());

-- Level 4: Development Teams
INSERT INTO public.sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-DEV-CORE', 'DEV_CORE', 'Core Banking Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_CORE', 1, 'ACTIVE', 'Core Banking Development Team', 'CC-7111', NOW(), NOW()),
('DEPT-DEV-CHANNEL', 'DEV_CHANNEL', 'Channel Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_CHANNEL', 2, 'ACTIVE', 'Channel Development Team', 'CC-7112', NOW(), NOW()),
('DEPT-DEV-RISK', 'DEV_RISK', 'Risk System Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_RISK', 3, 'ACTIVE', 'Risk System Development Team', 'CC-7113', NOW(), NOW()),
('DEPT-DEV-DATA', 'DEV_DATA', 'Data Platform Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_DATA', 4, 'ACTIVE', 'Data Platform Development Team', 'CC-7114', NOW(), NOW()),
('DEPT-DEV-MOBILE', 'DEV_MOBILE', 'Mobile Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_MOBILE', 5, 'ACTIVE', 'Mobile Development Team', 'CC-7115', NOW(), NOW()),
('DEPT-DEV-WEB', 'DEV_WEB', 'Web Frontend Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_WEB', 6, 'ACTIVE', 'Web Frontend Team', 'CC-7116', NOW(), NOW()),
('DEPT-DEV-API', 'DEV_API', 'API Platform Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_API', 7, 'ACTIVE', 'API Platform Team', 'CC-7117', NOW(), NOW()),
('DEPT-DEV-AI', 'DEV_AI', 'AI/ML Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_AI', 8, 'ACTIVE', 'AI/ML Team', 'CC-7118', NOW(), NOW()),
('DEPT-DEV-QA', 'DEV_QA', 'Quality Assurance Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_QA', 9, 'ACTIVE', 'Quality Assurance Team', 'CC-7119', NOW(), NOW());


-- =====================================================
-- 2. Assign Users to Business Units (many-to-many)
-- =====================================================
INSERT INTO public.sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
VALUES 
    -- Admin users
    ('ubu-admin-hq', 'admin-001', 'DEPT-HQ', NOW(), 'system'),
    ('ubu-admin-it', 'admin-001', 'DEPT-IT', NOW(), 'system'),
    ('ubu-auditor-compliance', '90140d0a-6fbb-4432-b07d-e208fb6ebd55', 'DEPT-COMPLIANCE', NOW(), 'system'),
    ('ubu-designer-it', 'aa220ecd-bb5b-4ba5-aa0a-27af144b9679', 'DEPT-IT', NOW(), 'system'),
    ('ubu-developer-it', '635281da-5dbb-4118-9610-dd4d6318dcd6', 'DEPT-IT', NOW(), 'system'),
    ('ubu-devlead-it', 'b4fe69e8-7313-48c5-865b-878231c24b9f', 'DEPT-IT', NOW(), 'system'),
    ('ubu-seniordev-it', '7e468949-05ea-4c41-8ab5-484fb0626185', 'DEPT-IT', NOW(), 'system'),
    ('ubu-superadmin-it', 'e9c974a2-3b71-4eba-9082-3b8d8cd03f08', 'DEPT-IT', NOW(), 'system'),
    ('ubu-sysadmin-it', 'f64d52ad-be7a-45ed-9b49-21138310b67c', 'DEPT-IT', NOW(), 'system'),
    ('ubu-teamlead-ops', 'b7890c89-ef16-491b-ba2f-ef559817eb8a', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-tenantadmin-it', 'fbba7111-5efb-4623-9239-27807c66fede', 'DEPT-IT', NOW(), 'system'),
    ('ubu-tester-it', 'e23b8e53-b9b0-4a7a-a704-9b1af77f97e3', 'DEPT-IT', NOW(), 'system'),
    -- Business users
    ('ubu-manager-ops', '9ad52216-f42b-4259-84eb-5e53a8fb0a3b', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-employeea-ops', 'e7eb22f1-aa8a-4eda-b4b0-f52d53622b3a', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-employeeb-ops', 'c7529039-05aa-4cdc-9b12-c8efa34bd61e', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-finance-treasury', '48b3dad7-c5a9-4000-8b6b-80453e59a6da', 'DEPT-TREASURY', NOW(), 'system'),
    ('ubu-hrstaff-hq', '7a55eeb0-d0cf-4b58-911d-61334643a374', 'DEPT-HQ', NOW(), 'system'),
    -- Bank staff
    ('ubu-channellead-devchannel', 'channel-lead-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-channellead-devcore', 'channel-lead-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-corelead-corpbanking', 'core-lead-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corelead-devcore', 'core-lead-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-corelead-devchannel', 'core-lead-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-corpanalyst-corpbanking', 'corp-analyst-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpdirector-corpbanking', 'corp-director-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpdirector-corpclient', 'corp-director-001', 'DEPT-CORP-CLIENT', NOW(), 'system'),
    ('ubu-corpdirector-corpcredit', 'corp-director-001', 'DEPT-CORP-CREDIT', NOW(), 'system'),
    ('ubu-corpmanager-corpbanking', 'corp-manager-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpmanager-branchbj', 'corp-manager-001', 'DEPT-BRANCH-BJ', NOW(), 'system'),
    ('ubu-corpofficer-corpbanking', 'corp-officer-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpofficer-compliance', 'corp-officer-001', 'DEPT-COMPLIANCE', NOW(), 'system'),
    ('ubu-corpofficer-branchbj', 'corp-officer-001', 'DEPT-BRANCH-BJ', NOW(), 'system'),
    ('ubu-corpofficer-it', 'corp-officer-001', 'DEPT-IT', NOW(), 'system'),
    ('ubu-hrmanager-hr', 'hr-manager-001', 'DEPT-HR', NOW(), 'system'),
    ('ubu-hrmanager-admin', 'hr-manager-001', 'DEPT-ADMIN', NOW(), 'system'),
    ('ubu-hrmanager-branchsh', 'hr-manager-001', 'DEPT-BRANCH-SH', NOW(), 'system'),
    ('ubu-hrrecruiter-hr', 'hr-recruiter-001', 'DEPT-HR', NOW(), 'system'),
    ('ubu-hrspecialist-hr', 'hr-specialist-001', 'DEPT-HR', NOW(), 'system'),
    ('ubu-risklead-risk', 'risk-lead-001', 'DEPT-RISK', NOW(), 'system'),
    ('ubu-risklead-devrisk', 'risk-lead-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-risklead-devcore', 'risk-lead-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-risklead-devchannel', 'risk-lead-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-techdirector-it', 'tech-director-001', 'DEPT-IT', NOW(), 'system'),
    ('ubu-techdirector-itdev', 'tech-director-001', 'DEPT-IT-DEV', NOW(), 'system'),
    -- Developers
    ('ubu-devalex-devrisk', 'dev-alex-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-devemma-devrisk', 'dev-emma-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-devjohn-devrisk', 'dev-john-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-devjohn-devcore', 'dev-john-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-devlisa-devchannel', 'dev-lisa-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-devmary-devcore', 'dev-mary-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-devpeter-devchannel', 'dev-peter-001', 'DEPT-DEV-CHANNEL', NOW(), 'system');
