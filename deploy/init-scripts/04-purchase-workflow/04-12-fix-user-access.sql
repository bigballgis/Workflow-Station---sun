-- =====================================================
-- Fix: Grant test users access to Purchase Request function unit
-- =====================================================
-- Problem: Test users don't have USER_ROLE which is required to access
-- the fu-purchase-request function unit.
-- 
-- Solution: Create a virtual group with USER_ROLE and add test users to it.
-- =====================================================

-- Step 1: Create a virtual group for general users (if not exists)
INSERT INTO sys_virtual_groups (id, name, code, type, description, created_at)
VALUES ('vg-general-users', 'General Users', 'GENERAL_USERS', 'CUSTOM',
        'Virtual group for general users with basic access', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Step 2: Bind USER_ROLE to the virtual group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at)
VALUES ('vgr-general-users-user', 'vg-general-users', 'USER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Step 3: Add test users to the General Users virtual group
-- This gives them USER_ROLE which grants access to fu-purchase-request

-- Purchase Requester
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-general-purchase-requester', 'vg-general-users', 'purchase-requester-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Department Reviewer
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-general-dept-reviewer', 'vg-general-users', 'dept-reviewer-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Parent BU Reviewer
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-general-parent-reviewer', 'vg-general-users', 'parent-reviewer-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Finance Reviewer
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-general-finance-reviewer', 'vg-general-users', 'finance-reviewer-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Countersign Approver 1
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-general-countersign-001', 'vg-general-users', 'countersign-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Countersign Approver 2
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-general-countersign-002', 'vg-general-users', 'countersign-002', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Verification
-- =====================================================
-- Check virtual group roles
-- SELECT vg.name, r.name as role_name FROM sys_virtual_group_roles vgr 
-- JOIN sys_virtual_groups vg ON vgr.virtual_group_id = vg.id 
-- JOIN sys_roles r ON vgr.role_id = r.id 
-- WHERE vg.id = 'vg-general-users';

-- Check virtual group members
-- SELECT vg.name as group_name, u.username FROM sys_virtual_group_members vgm 
-- JOIN sys_virtual_groups vg ON vgm.group_id = vg.id 
-- JOIN sys_users u ON vgm.user_id = u.id 
-- WHERE vg.id = 'vg-general-users';
