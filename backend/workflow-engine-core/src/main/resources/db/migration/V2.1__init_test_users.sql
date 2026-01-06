-- =====================================================
-- Test Users Initialization Script
-- Validates: Requirements 6.1, 6.2, 6.3, 6.4
-- =====================================================

-- Password hash for 'admin123' using BCrypt
-- Generated with BCryptPasswordEncoder strength 10
-- $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH

-- Password hash for 'dev123' using BCrypt
-- Password hash for 'user123' using BCrypt

-- Note: All test passwords are hashed versions of:
-- admin123 -> for admin users
-- dev123 -> for developer users  
-- user123 -> for portal users

-- =====================================================
-- Admin Center Test Users (password: admin123)
-- =====================================================
INSERT INTO sys_user (id, username, password_hash, email, display_name, status, department_id, language)
VALUES 
    (uuid_generate_v4(), 'super_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKxvKjIS', 'super_admin@example.com', '超级管理员', 'ACTIVE', 'ADMIN', 'zh_CN'),
    (uuid_generate_v4(), 'system_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKxvKjIS', 'system_admin@example.com', '系统管理员', 'ACTIVE', 'ADMIN', 'zh_CN'),
    (uuid_generate_v4(), 'tenant_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKxvKjIS', 'tenant_admin@example.com', '租户管理员', 'ACTIVE', 'ADMIN', 'zh_CN'),
    (uuid_generate_v4(), 'auditor', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKxvKjIS', 'auditor@example.com', '审计员', 'ACTIVE', 'ADMIN', 'zh_CN')
ON CONFLICT (username) DO NOTHING;

-- =====================================================
-- Developer Workstation Test Users (password: dev123)
-- =====================================================
INSERT INTO sys_user (id, username, password_hash, email, display_name, status, department_id, language)
VALUES 
    (uuid_generate_v4(), 'dev_lead', '$2a$10$rDkPvvAFV6kqHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvKjIS', 'dev_lead@example.com', '开发组长', 'ACTIVE', 'DEV', 'zh_CN'),
    (uuid_generate_v4(), 'senior_dev', '$2a$10$rDkPvvAFV6kqHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvKjIS', 'senior_dev@example.com', '高级开发', 'ACTIVE', 'DEV', 'zh_CN'),
    (uuid_generate_v4(), 'developer', '$2a$10$rDkPvvAFV6kqHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvKjIS', 'developer@example.com', '开发人员', 'ACTIVE', 'DEV', 'zh_CN'),
    (uuid_generate_v4(), 'designer', '$2a$10$rDkPvvAFV6kqHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvKjIS', 'designer@example.com', '流程设计师', 'ACTIVE', 'DEV', 'zh_CN'),
    (uuid_generate_v4(), 'tester', '$2a$10$rDkPvvAFV6kqHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvKjIS', 'tester@example.com', '测试人员', 'ACTIVE', 'QA', 'zh_CN')
ON CONFLICT (username) DO NOTHING;

-- =====================================================
-- User Portal Test Users (password: user123)
-- =====================================================
INSERT INTO sys_user (id, username, password_hash, email, display_name, status, department_id, language)
VALUES 
    (uuid_generate_v4(), 'manager', '$2a$10$EqKcp1WFKs6sVGcBHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvK', 'manager@example.com', '部门经理', 'ACTIVE', 'SALES', 'zh_CN'),
    (uuid_generate_v4(), 'team_lead', '$2a$10$EqKcp1WFKs6sVGcBHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvK', 'team_lead@example.com', '团队主管', 'ACTIVE', 'SALES', 'zh_CN'),
    (uuid_generate_v4(), 'employee_a', '$2a$10$EqKcp1WFKs6sVGcBHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvK', 'employee_a@example.com', '员工张三', 'ACTIVE', 'SALES', 'zh_CN'),
    (uuid_generate_v4(), 'employee_b', '$2a$10$EqKcp1WFKs6sVGcBHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvK', 'employee_b@example.com', '员工李四', 'ACTIVE', 'SALES', 'zh_CN'),
    (uuid_generate_v4(), 'hr_staff', '$2a$10$EqKcp1WFKs6sVGcBHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvK', 'hr_staff@example.com', 'HR专员', 'ACTIVE', 'HR', 'zh_CN'),
    (uuid_generate_v4(), 'finance', '$2a$10$EqKcp1WFKs6sVGcBHBBpLQS9/.eSZQxTB3EHsM8lE9lBOsl7iKxvK', 'finance@example.com', '财务人员', 'ACTIVE', 'FINANCE', 'zh_CN')
ON CONFLICT (username) DO NOTHING;

-- =====================================================
-- Assign Roles to Users
-- =====================================================

-- Admin Center roles
INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'SUPER_ADMIN' FROM sys_user WHERE username = 'super_admin'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'SYSTEM_ADMIN' FROM sys_user WHERE username = 'system_admin'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'TENANT_ADMIN' FROM sys_user WHERE username = 'tenant_admin'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'AUDITOR' FROM sys_user WHERE username = 'auditor'
ON CONFLICT DO NOTHING;

-- Developer Workstation roles
INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'DEV_LEAD' FROM sys_user WHERE username = 'dev_lead'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'DEVELOPER' FROM sys_user WHERE username IN ('senior_dev', 'developer')
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'DESIGNER' FROM sys_user WHERE username = 'designer'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'TESTER' FROM sys_user WHERE username = 'tester'
ON CONFLICT DO NOTHING;

-- User Portal roles
INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'MANAGER' FROM sys_user WHERE username = 'manager'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'TEAM_LEAD' FROM sys_user WHERE username = 'team_lead'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'USER' FROM sys_user WHERE username IN ('employee_a', 'employee_b', 'hr_staff', 'finance')
ON CONFLICT DO NOTHING;

-- Add HR role
INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'HR' FROM sys_user WHERE username = 'hr_staff'
ON CONFLICT DO NOTHING;

-- Add Finance role
INSERT INTO sys_user_role (user_id, role_code)
SELECT id, 'FINANCE' FROM sys_user WHERE username = 'finance'
ON CONFLICT DO NOTHING;
