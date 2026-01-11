-- =====================================================
-- Admin User Initialization
-- This file creates the system administrator account
-- Run this AFTER schema and system data initialization
-- =====================================================

-- System Admin User
-- Password: admin123 (BCrypt hash)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, position, language, created_at, updated_at)
VALUES (
    'admin-001',
    'admin',
    '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',
    'admin@example.com',
    'System Admin',
    'System Administrator',
    'ACTIVE',
    'System Administrator',
    'zh_CN',
    NOW(),
    NOW()
) ON CONFLICT (username) DO UPDATE SET 
    display_name = EXCLUDED.display_name,
    position = EXCLUDED.position,
    updated_at = NOW();

-- Assign SYS_ADMIN role to admin user (using sys_role_assignments)
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES ('ra-user-admin-sysadmin', 'SYS_ADMIN_ROLE', 'USER', 'admin-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- =====================================================
-- ADMIN ACCOUNT
-- =====================================================
-- Username: admin
-- Password: admin123
-- Role: System Administrator (SYS_ADMIN)
-- =====================================================
