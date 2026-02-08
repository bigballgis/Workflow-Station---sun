-- =====================================================
-- Admin User Only Initialization
-- =====================================================
-- Creates ONLY the admin user and assigns to System Administrators group.
-- Other users should be created manually via Admin Center UI.
-- Password: password (BCrypt hash)
-- =====================================================

\echo '========================================='
\echo 'Creating Admin User Only...'
\echo '========================================='

-- Admin User
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-admin', 'admin', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa', 'admin@example.com', 'System Admin', 'System Administrator', 'ACTIVE', 'en', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Admin user created'

-- Add admin to System Administrators group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-admin-001', 'vg-sys-admins', 'user-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

\echo '✓ Admin user assigned to System Administrators group'
\echo ''
\echo 'Login: admin / password'
\echo 'IMPORTANT: Change the default password after first login!'
