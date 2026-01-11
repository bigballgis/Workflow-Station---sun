-- =====================================================
-- TEST DATA: Test Users (excluding admin)
-- Password for all test users: admin123
-- BCrypt: $2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO
-- =====================================================

-- =====================================================
-- Manager Hierarchy:
-- entity_manager_id: 实体经理 (行政汇报线，如部门经�?
-- function_manager_id: 职能经理 (业务汇报线，如项目经�?
-- =====================================================

-- =====================================================
-- 1. HR Department Users
-- =====================================================

-- HR Manager (no manager - top of HR)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('hr-manager-001', 'hr.manager', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'hr.manager@bank.com', 'Sarah Chen', 'Sarah Chen', 'EMP-HR-001', 'DEPT-HR', 'HR Manager', 
     NULL, NULL, 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();

-- HR Staff (report to HR Manager)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('hr-specialist-001', 'hr.specialist', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'hr.specialist@bank.com', 'Michael Wang', 'Michael Wang', 'EMP-HR-002', 'DEPT-HR', 'HR Specialist', 
     'hr-manager-001', 'hr-manager-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('hr-recruiter-001', 'hr.recruiter', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'hr.recruiter@bank.com', 'Emily Liu', 'Emily Liu', 'EMP-HR-003', 'DEPT-HR', 'Recruiter', 
     'hr-manager-001', 'hr-manager-001', 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();

-- =====================================================
-- 2. Corporate Banking Users
-- =====================================================

-- Director (top of Corporate Banking)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('corp-director-001', 'corp.director', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'corp.director@bank.com', 'James Zhang', 'James Zhang', 'EMP-CORP-001', 'DEPT-CORP-BANKING', 'Director', 
     NULL, NULL, 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();

-- Senior Manager (reports to Director)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('corp-manager-001', 'corp.manager', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'corp.manager@bank.com', 'Linda Li', 'Linda Li', 'EMP-CORP-002', 'DEPT-CORP-BANKING', 'Senior Manager', 
     'corp-director-001', 'corp-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();

-- Staff (entity reports to Manager, function reports to Director)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('corp-analyst-001', 'corp.analyst', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'corp.analyst@bank.com', 'David Wu', 'David Wu', 'EMP-CORP-003', 'DEPT-CORP-BANKING', 'Business Analyst', 
     'corp-manager-001', 'corp-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('corp-officer-001', 'corp.officer', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'corp.officer@bank.com', 'Amy Zhao', 'Amy Zhao', 'EMP-CORP-004', 'DEPT-CORP-BANKING', 'Relationship Officer', 
     'corp-manager-001', 'corp-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();

-- =====================================================
-- 3. IT Department Users (Developer Workstation)
-- =====================================================

-- Technical Director (top of IT)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('tech-director-001', 'tech.director', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'tech.director@bank.com', 'Robert Sun', 'Robert Sun', 'EMP-IT-001', 'DEPT-IT', 'Technical Director', 
     NULL, NULL, 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();

-- Team Leaders (report to Technical Director)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('core-lead-001', 'core.lead', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'core.lead@bank.com', 'Kevin Huang', 'Kevin Huang', 'EMP-IT-002', 'DEPT-DEV-CORE', 'Team Leader', 
     'tech-director-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('channel-lead-001', 'channel.lead', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'channel.lead@bank.com', 'Grace Lin', 'Grace Lin', 'EMP-IT-003', 'DEPT-DEV-CHANNEL', 'Team Leader', 
     'tech-director-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('risk-lead-001', 'risk.lead', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'risk.lead@bank.com', 'Tony Chen', 'Tony Chen', 'EMP-IT-004', 'DEPT-DEV-RISK', 'Team Leader', 
     'tech-director-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();

-- Developers (entity reports to Team Lead, function reports to Tech Director)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, department_id, position, 
    entity_manager_id, function_manager_id, status, language, created_at, updated_at)
VALUES 
    ('dev-john-001', 'dev.john', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'john.dev@bank.com', 'John Developer', 'John Smith', 'EMP-IT-005', 'DEPT-DEV-CORE', 'Senior Developer', 
     'core-lead-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-mary-001', 'dev.mary', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'mary.dev@bank.com', 'Mary Johnson', 'Mary Johnson', 'EMP-IT-006', 'DEPT-DEV-CORE', 'Developer', 
     'core-lead-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-peter-001', 'dev.peter', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'peter.dev@bank.com', 'Peter Lee', 'Peter Lee', 'EMP-IT-007', 'DEPT-DEV-CHANNEL', 'Senior Developer', 
     'channel-lead-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-lisa-001', 'dev.lisa', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'lisa.dev@bank.com', 'Lisa Wang', 'Lisa Wang', 'EMP-IT-008', 'DEPT-DEV-CHANNEL', 'Developer', 
     'channel-lead-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-alex-001', 'dev.alex', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'alex.dev@bank.com', 'Alex Zhou', 'Alex Zhou', 'EMP-IT-009', 'DEPT-DEV-RISK', 'Senior Developer', 
     'risk-lead-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-emma-001', 'dev.emma', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 
     'emma.dev@bank.com', 'Emma Liu', 'Emma Liu', 'EMP-IT-010', 'DEPT-DEV-RISK', 'Developer', 
     'risk-lead-001', 'tech-director-001', 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    department_id = EXCLUDED.department_id,
    position = EXCLUDED.position,
    entity_manager_id = EXCLUDED.entity_manager_id,
    function_manager_id = EXCLUDED.function_manager_id,
    updated_at = NOW();
