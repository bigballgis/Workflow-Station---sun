-- =====================================================
-- Purchase Request Workflow - Test Users and Data Setup
-- =====================================================
-- This script creates test users and configures them for testing
-- the 9 new assignee types in the purchase request workflow.
--
-- Assignee Types:
-- 1. INITIATOR - 流程发起人 (直接分配)
-- 2. ENTITY_MANAGER - 实体经理 (直接分配)
-- 3. FUNCTION_MANAGER - 职能经理 (直接分配)
-- 4. CURRENT_BU_ROLE - 当前人业务单元角色 (认领)
-- 5. CURRENT_PARENT_BU_ROLE - 当前人上级业务单元角色 (认领)
-- 6. INITIATOR_BU_ROLE - 发起人业务单元角色 (认领)
-- 7. INITIATOR_PARENT_BU_ROLE - 发起人上级业务单元角色 (认领)
-- 8. FIXED_BU_ROLE - 指定业务单元角色 (认领)
-- 9. BU_UNBOUNDED_ROLE - BU无关型角色 (认领)
-- =====================================================

-- =====================================================
-- Step 1: Create BU_BOUNDED Roles for Purchase Workflow
-- =====================================================

-- Create Purchase Reviewer role (BU_BOUNDED) - for department review
INSERT INTO sys_roles (id, name, code, type, description, is_system, created_at)
VALUES ('PURCHASE_REVIEWER_ROLE', 'Purchase Reviewer', 'PURCHASE_REVIEWER', 'BU_BOUNDED', 
        'Can review purchase requests in their business unit', false, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Create Finance Reviewer role (BU_BOUNDED) - for finance department
INSERT INTO sys_roles (id, name, code, type, description, is_system, created_at)
VALUES ('FINANCE_REVIEWER_ROLE', 'Finance Reviewer', 'FINANCE_REVIEWER', 'BU_BOUNDED', 
        'Can review purchase requests from finance perspective', false, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Create Senior Approver role (BU_BOUNDED) - for parent BU approval
INSERT INTO sys_roles (id, name, code, type, description, is_system, created_at)
VALUES ('SENIOR_APPROVER_ROLE', 'Senior Approver', 'SENIOR_APPROVER', 'BU_BOUNDED', 
        'Can approve high-value purchase requests', false, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Step 2: Create BU_UNBOUNDED Role for Countersign
-- =====================================================

-- Create Countersign Approver role (BU_UNBOUNDED) - for multi-department countersign
INSERT INTO sys_roles (id, name, code, type, description, is_system, created_at)
VALUES ('COUNTERSIGN_APPROVER_ROLE', 'Countersign Approver', 'COUNTERSIGN_APPROVER', 'BU_UNBOUNDED', 
        'Can participate in multi-department countersign', false, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Step 3: Create Virtual Group for Countersign Approvers
-- =====================================================

INSERT INTO sys_virtual_groups (id, name, code, type, description, created_at)
VALUES ('vg-countersign-approvers', 'Countersign Approvers', 'COUNTERSIGN_APPROVERS', 'CUSTOM',
        'Virtual group for multi-department countersign approvers', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Bind the Countersign Approver role to the virtual group
-- Note: sys_virtual_group_roles requires id column
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at)
VALUES ('vgr-countersign-approvers', 'vg-countersign-approvers', 'COUNTERSIGN_APPROVER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Step 4: Configure Business Unit Eligible Roles
-- =====================================================

-- IT Department can have Purchase Reviewer role
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at)
VALUES ('bur-it-purchase-reviewer', 'DEPT-IT', 'PURCHASE_REVIEWER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- IT-DEV can have Purchase Reviewer role
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at)
VALUES ('bur-itdev-purchase-reviewer', 'DEPT-IT-DEV', 'PURCHASE_REVIEWER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Finance Department can have Finance Reviewer role
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at)
VALUES ('bur-finance-reviewer', 'DEPT-FINANCE', 'FINANCE_REVIEWER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- HQ can have Senior Approver role
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at)
VALUES ('bur-hq-senior-approver', 'DEPT-HQ', 'SENIOR_APPROVER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- IT can have Senior Approver role (for parent BU approval)
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at)
VALUES ('bur-it-senior-approver', 'DEPT-IT', 'SENIOR_APPROVER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Step 5: Create Test Users for Purchase Workflow
-- =====================================================
-- Note: sys_users uses password_hash instead of password

-- User 1: Purchase Requester (Initiator) - IT-DEV department
-- Tests: INITIATOR, ENTITY_MANAGER, FUNCTION_MANAGER, INITIATOR_BU_ROLE, INITIATOR_PARENT_BU_ROLE
INSERT INTO sys_users (id, username, password_hash, full_name, display_name, email, status, 
                       function_manager_id, entity_manager_id, created_at)
VALUES ('purchase-requester-001', 'purchase.requester', 
        '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',
        'Tom Wilson', 'Tom', 'tom.wilson@example.com', 'ACTIVE',
        'tech-director-001', 'core-lead-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET 
    function_manager_id = 'tech-director-001',
    entity_manager_id = 'core-lead-001';

-- User 2: Department Reviewer - IT-DEV department (same as requester)
-- Tests: INITIATOR_BU_ROLE (Purchase Reviewer in IT-DEV)
INSERT INTO sys_users (id, username, password_hash, full_name, display_name, email, status, 
                       function_manager_id, entity_manager_id, created_at)
VALUES ('dept-reviewer-001', 'dept.reviewer', 
        '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',
        'Alice Johnson', 'Alice', 'alice.johnson@example.com', 'ACTIVE',
        'tech-director-001', 'core-lead-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET 
    function_manager_id = 'tech-director-001',
    entity_manager_id = 'core-lead-001';

-- User 3: Parent BU Reviewer - IT department (parent of IT-DEV)
-- Tests: INITIATOR_PARENT_BU_ROLE (Senior Approver in IT)
INSERT INTO sys_users (id, username, password_hash, full_name, display_name, email, status, 
                       function_manager_id, entity_manager_id, created_at)
VALUES ('parent-reviewer-001', 'parent.reviewer', 
        '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',
        'Bob Smith', 'Bob', 'bob.smith@example.com', 'ACTIVE',
        NULL, NULL, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET 
    full_name = 'Bob Smith';

-- User 4: Finance Reviewer - Finance department
-- Tests: FIXED_BU_ROLE (Finance Reviewer in DEPT-FINANCE)
INSERT INTO sys_users (id, username, password_hash, full_name, display_name, email, status, 
                       function_manager_id, entity_manager_id, created_at)
VALUES ('finance-reviewer-001', 'finance.reviewer', 
        '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',
        'Carol Davis', 'Carol', 'carol.davis@example.com', 'ACTIVE',
        NULL, NULL, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET 
    full_name = 'Carol Davis';

-- User 5: Countersign Approver 1
-- Tests: BU_UNBOUNDED_ROLE (Countersign Approver via virtual group)
INSERT INTO sys_users (id, username, password_hash, full_name, display_name, email, status, 
                       function_manager_id, entity_manager_id, created_at)
VALUES ('countersign-001', 'countersign.approver1', 
        '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',
        'Daniel Brown', 'Daniel', 'daniel.brown@example.com', 'ACTIVE',
        NULL, NULL, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET 
    full_name = 'Daniel Brown';

-- User 6: Countersign Approver 2
INSERT INTO sys_users (id, username, password_hash, full_name, display_name, email, status, 
                       function_manager_id, entity_manager_id, created_at)
VALUES ('countersign-002', 'countersign.approver2', 
        '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',
        'Eva Martinez', 'Eva', 'eva.martinez@example.com', 'ACTIVE',
        NULL, NULL, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET 
    full_name = 'Eva Martinez';

-- =====================================================
-- Step 6: Assign Users to Business Units with Roles
-- =====================================================

-- Purchase Requester in IT-DEV (no special role, just member)
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at)
VALUES ('ubur-requester-itdev', 'purchase-requester-001', 'DEPT-IT-DEV', 'USER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Department Reviewer in IT-DEV with Purchase Reviewer role
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at)
VALUES ('ubur-deptreviewer-itdev', 'dept-reviewer-001', 'DEPT-IT-DEV', 'PURCHASE_REVIEWER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Parent BU Reviewer in IT with Senior Approver role
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at)
VALUES ('ubur-parentreviewer-it', 'parent-reviewer-001', 'DEPT-IT', 'SENIOR_APPROVER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Finance Reviewer in Finance with Finance Reviewer role
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at)
VALUES ('ubur-financereviewer-finance', 'finance-reviewer-001', 'DEPT-FINANCE', 'FINANCE_REVIEWER_ROLE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Step 7: Add Countersign Approvers to Virtual Group
-- =====================================================
-- Note: sys_virtual_group_members uses group_id instead of virtual_group_id

INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-countersign-001', 'vg-countersign-approvers', 'countersign-001', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
VALUES ('vgm-countersign-002', 'vg-countersign-approvers', 'countersign-002', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Step 8: Update existing users' manager relationships
-- =====================================================

-- Ensure core.lead has proper managers set
UPDATE sys_users SET 
    function_manager_id = 'tech-director-001',
    entity_manager_id = 'tech-director-001'
WHERE id = 'core-lead-001';

-- Ensure tech-director-001 exists and has proper setup
UPDATE sys_users SET 
    function_manager_id = NULL,
    entity_manager_id = NULL
WHERE id = 'tech-director-001';

-- =====================================================
-- Verification Queries (for testing)
-- =====================================================

-- Check test users
-- SELECT id, username, full_name, function_manager_id, entity_manager_id FROM sys_users WHERE id LIKE 'purchase%' OR id LIKE 'dept-%' OR id LIKE 'parent-%' OR id LIKE 'finance-%' OR id LIKE 'countersign-%';

-- Check business unit role assignments
-- SELECT u.username, ubr.business_unit_id, r.name as role_name FROM sys_user_business_unit_roles ubr JOIN sys_users u ON ubr.user_id = u.id JOIN sys_roles r ON ubr.role_id = r.id WHERE u.id LIKE 'purchase%' OR u.id LIKE 'dept-%' OR u.id LIKE 'parent-%' OR u.id LIKE 'finance-%';

-- Check virtual group members
-- SELECT vg.name as group_name, u.username FROM sys_virtual_group_members vgm JOIN sys_virtual_groups vg ON vgm.group_id = vg.id JOIN sys_users u ON vgm.user_id = u.id WHERE vg.id = 'vg-countersign-approvers';

-- Check business unit eligible roles
-- SELECT bu.name as bu_name, r.name as role_name FROM sys_business_unit_roles bur JOIN sys_business_units bu ON bur.business_unit_id = bu.id JOIN sys_roles r ON bur.role_id = r.id;
